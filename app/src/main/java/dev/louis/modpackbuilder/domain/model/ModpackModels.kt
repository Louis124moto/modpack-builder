package dev.louis.modpackbuilder.domain.model

import kotlinx.serialization.Serializable

enum class ModLoader(val apiName: String) { FABRIC("fabric"), FORGE("forge"), NEOFORGE("neoforge"), QUILT("quilt") }
enum class OutputFormat { DIRECT_LINKS, MODRINTH_INDEX, ZIP }

data class ModpackRequest(
    val description: String,
    val loader: ModLoader,
    val automaticMinecraftVersion: Boolean,
    val minecraftVersion: String?,
    val outputFormat: OutputFormat
)

data class ResolvedMod(
    val projectId: String,
    val projectTitle: String,
    val versionId: String,
    val versionNumber: String,
    val fileName: String,
    val downloadUrl: String,
    val hashes: Map<String, String>,
    val fileSize: Long,
    val requiredDependencies: List<String>
)

data class GeneratedModpack(
    val name: String,
    val minecraftVersion: String,
    val loader: ModLoader,
    val loaderVersion: String,
    val mods: List<ResolvedMod>
)

@Serializable
data class ModrinthIndex(
    val formatVersion: Int = 1,
    val game: String = "minecraft",
    val versionId: String,
    val name: String,
    val files: List<IndexFile>,
    val dependencies: Map<String, String>
)

@Serializable
data class IndexFile(
    val path: String,
    val hashes: Map<String, String>,
    val env: Map<String, String> = mapOf("client" to "required", "server" to "required"),
    val downloads: List<String>,
    val fileSize: Long
)
