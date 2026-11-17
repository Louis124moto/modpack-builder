package dev.louis.modpackbuilder.data.repository

import dev.louis.modpackbuilder.data.api.*
import dev.louis.modpackbuilder.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.time.Instant

class ModpackRepository {
    private val api: ModrinthApi = Retrofit.Builder()
        .baseUrl("https://api.modrinth.com/v2/")
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
        val loaderVersion = api.loaderVersions(request.loader.apiName)
            .filter { it.stable }.maxByOrNull { it.version }?.version
            ?: api.loaderVersions(request.loader.apiName).firstOrNull()?.version
            ?: error("Impossible de déterminer la version du loader ${request.loader.apiName}.")
        GeneratedModpack(
            name = request.description.take(48).ifBlank { "Modpack généré" },
            minecraftVersion = minecraft,
            loader = request.loader,
            loaderVersion = loaderVersion,
            mods = resolved.values.toList()
        )
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
