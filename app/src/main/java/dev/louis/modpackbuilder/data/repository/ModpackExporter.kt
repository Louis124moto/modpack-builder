package dev.louis.modpackbuilder.data.repository

import dev.louis.modpackbuilder.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

class ModpackExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val http = OkHttpClient()

    fun indexBytes(pack: GeneratedModpack): ByteArray {
        val dependencies = mutableMapOf("minecraft" to pack.minecraftVersion)
        val loaderKey = when (pack.loader) {
            ModLoader.FABRIC -> "fabric-loader"
            ModLoader.QUILT -> "quilt-loader"
            ModLoader.FORGE -> "forge"
            ModLoader.NEOFORGE -> "neoforge"
        }
        dependencies[loaderKey] = pack.loaderVersion
        val index = ModrinthIndex(
            versionId = "1.0.0",
            name = pack.name,
            files = pack.mods.map {
                IndexFile("mods/${it.fileName}", it.hashes, downloads = listOf(it.downloadUrl), fileSize = it.fileSize)
            },
            dependencies = dependencies
        )
        return json.encodeToString(index).toByteArray()
    }

    fun zipBytes(pack: GeneratedModpack): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            pack.mods.forEach { mod ->
                val request = Request.Builder().url(mod.downloadUrl).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Téléchargement impossible pour ${mod.fileName}: HTTP ${response.code}")
                    val bytes = response.body?.bytes() ?: error("Fichier vide : ${mod.fileName}")
                    zip.putNextEntry(ZipEntry("mods/${mod.fileName}"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry("modpack-info.txt"))
            zip.write("Minecraft ${pack.minecraftVersion}\nLoader ${pack.loader.apiName} ${pack.loaderVersion}\n".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
