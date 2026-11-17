package dev.louis.modpackbuilder.data.repository

import dev.louis.modpackbuilder.data.api.*
import dev.louis.modpackbuilder.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import java.util.concurrent.TimeUnit

class ModpackRepository(
    private val onDiagnostic: (String) -> Unit = {}
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val startedAt = System.nanoTime()
            onDiagnostic("HTTP → ${request.method} ${request.url}")
            try {
                val response = chain.proceed(request)
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                if (response.isSuccessful) {
                    onDiagnostic("HTTP ← ${response.code} ${response.message} (${elapsedMs} ms) — ${request.url}")
                } else {
                    val body = runCatching { response.peekBody(16 * 1024).string() }
                        .getOrDefault("<corps de réponse illisible>")
                    onDiagnostic(
                        "HTTP ← ERREUR ${response.code} ${response.message} (${elapsedMs} ms) — ${request.url}" +
                            if (body.isBlank()) "" else "\nCorps de réponse :\n$body"
                    )
                }
                response
            } catch (t: Throwable) {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                onDiagnostic("HTTP ✕ ÉCHEC (${elapsedMs} ms) — ${request.method} ${request.url}\n${t.javaClass.name}: ${t.message}")
                throw t
            }
        })
        .build()

    private val api: ModrinthApi = Retrofit.Builder()
        .baseUrl("https://api.modrinth.com/v2/")
        .client(httpClient)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build().create(ModrinthApi::class.java)

    suspend fun resolve(names: List<String>, request: ModpackRequest): GeneratedModpack = withContext(Dispatchers.IO) {
        require(names.isNotEmpty()) { "L'IA n'a identifié aucun mod." }
        val resolved = linkedMapOf<String, ResolvedMod>()
        val selectedVersions = linkedMapOf<String, ModVersion>()
        val queue = ArrayDeque<Pair<String, String?>>()
        names.forEach { queue.add(it to null) }

        while (queue.isNotEmpty() && resolved.size < 100) {
            val (queryOrId, forcedVersion) = queue.removeFirst()
            if (forcedVersion != null) {
                if (resolved.containsKey(forcedVersion)) continue
                val version = api.version(forcedVersion)
                addVersion(version, version.project_id, resolved, selectedVersions)?.let { deps ->
                    deps.forEach { dep -> queue.add(dep.first to dep.second) }
                }
                continue
            }
            val hit = if (queryOrId.startsWith("project:")) {
                val id = queryOrId.removePrefix("project:")
                ProjectHit(id, id, id)
            } else api.searchProjects(queryOrId).hits.firstOrNull()
                ?: continue
            if (resolved.containsKey(hit.project_id)) continue
            val versions = api.projectVersions(
                hit.project_id,
                """["${request.loader.apiName}"]""",
                if (request.automaticMinecraftVersion) null else """["${request.minecraftVersion}"]"""
            )
            val chosen = chooseVersion(versions) ?: continue
            val deps = addVersion(chosen, hit.project_id, resolved, selectedVersions).orEmpty()
            deps.forEach { dep -> queue.add(dep.first to dep.second) }
        }

        require(resolved.isNotEmpty()) { "Aucun mod compatible trouvé sur Modrinth." }
        val minecraft = request.minecraftVersion ?: chooseMinecraftVersion(selectedVersions.values.toList())
        onDiagnostic("Résolution de la version ${request.loader.apiName} pour Minecraft $minecraft")
        val loaderVersion = resolveLoaderVersion(request.loader, minecraft)
        onDiagnostic("Version du loader obtenue : ${request.loader.apiName} $loaderVersion")
        GeneratedModpack(
            name = request.description.take(48).ifBlank { "Modpack généré" },
            minecraftVersion = minecraft,
            loader = request.loader,
            loaderVersion = loaderVersion,
            mods = resolved.values.toList()
        )
    }

    private fun resolveLoaderVersion(loader: ModLoader, minecraftVersion: String): String {
        val url = when (loader) {
            ModLoader.FABRIC -> "https://meta.fabricmc.net/v2/versions/loader/$minecraftVersion"
            ModLoader.QUILT -> "https://meta.quiltmc.org/v3/versions/loader/$minecraftVersion"
            ModLoader.FORGE -> "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
            ModLoader.NEOFORGE -> "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
        }
        val request = Request.Builder().url(url).get().build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Impossible de récupérer les versions de ${loader.apiName} : HTTP ${response.code} ($url)")
            }
            response.body?.string() ?: error("Réponse vide du service de versions de ${loader.apiName} ($url)")
        }

        return when (loader) {
            ModLoader.FABRIC, ModLoader.QUILT -> {
                val entries = Json.parseToJsonElement(body).jsonArray
                val versions = entries.mapNotNull { entry ->
                    val loaderInfo = entry.jsonObject["loader"]?.jsonObject ?: return@mapNotNull null
                    val version = loaderInfo["version"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    val stable = loaderInfo["stable"]?.jsonPrimitive?.booleanOrNull ?: true
                    version to stable
                }
                versions.firstOrNull { it.second }?.first
                    ?: versions.firstOrNull()?.first
                    ?: error("Aucune version de ${loader.apiName} compatible avec Minecraft $minecraftVersion.")
            }
            ModLoader.FORGE -> {
                val promotions = Json.parseToJsonElement(body).jsonObject["promos"]?.jsonObject
                    ?: error("Format inattendu des promotions Forge.")
                promotions["$minecraftVersion-recommended"]?.jsonPrimitive?.contentOrNull
                    ?: promotions["$minecraftVersion-latest"]?.jsonPrimitive?.contentOrNull
                    ?: error("Aucune version Forge publiée pour Minecraft $minecraftVersion.")
            }
            ModLoader.NEOFORGE -> {
                val prefix = if (minecraftVersion == "1.20.1") "47.1."
                    else minecraftVersion.removePrefix("1.") + "."
                val versions = Regex("<version>([^<]+)</version>")
                    .findAll(body)
                    .map { it.groupValues[1] }
                    .filter { it.startsWith(prefix) }
                    .toList()
                versions.maxWithOrNull(::compareVersionNumbers)
                    ?: error("Aucune version NeoForge trouvée pour Minecraft $minecraftVersion (préfixe attendu : $prefix).")
            }
        }
    }

    private fun compareVersionNumbers(left: String, right: String): Int {
        val leftParts = Regex("\\d+").findAll(left).map { it.value.toLong() }.toList()
        val rightParts = Regex("\\d+").findAll(right).map { it.value.toLong() }.toList()
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val comparison = (leftParts.getOrNull(index) ?: 0L)
                .compareTo(rightParts.getOrNull(index) ?: 0L)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun chooseVersion(versions: List<ModVersion>): ModVersion? {
        val stable = versions.filter { it.version_type == "release" }
        return (stable.ifEmpty { versions }).maxWithOrNull(
            compareBy<ModVersion> { it.downloads }.thenBy { runCatching { Instant.parse(it.date_published) }.getOrDefault(Instant.EPOCH) }
        )
    }

    private fun addVersion(
        version: ModVersion, projectId: String,
        result: LinkedHashMap<String, ResolvedMod>,
        versions: LinkedHashMap<String, ModVersion>
    ): List<Pair<String, String?>>? {
        if (result.containsKey(projectId)) return null
        val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull() ?: return emptyList()
        result[projectId] = ResolvedMod(
            projectId, version.name.substringBefore(" - ").ifBlank { projectId },
            version.id, version.version_number, file.filename, file.url, file.hashes,
            file.size, version.dependencies.filter { it.dependency_type == "required" }
                .mapNotNull { it.project_id ?: it.version_id }
        )
        versions[version.id] = version
        return version.dependencies.filter { it.dependency_type == "required" }.mapNotNull {
            when {
                it.version_id != null -> "version" to it.version_id
                it.project_id != null -> "project:${it.project_id}" to null
                else -> null
            }
        }
    }

    private fun chooseMinecraftVersion(versions: List<ModVersion>): String {
        val counts = versions.flatMap { it.game_versions }.groupingBy { it }.eachCount()
        return counts.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.key
            ?: error("Impossible de déduire une version Minecraft commune. Choisis une version manuellement.")
    }
}
