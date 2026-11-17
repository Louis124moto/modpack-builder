package dev.louis.modpackbuilder.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable data class SearchResponse(val hits: List<ProjectHit> = emptyList())
@Serializable data class ProjectHit(
    val project_id: String,
    val slug: String,
    val title: String,
    val description: String = "",
    val project_type: String = "mod"
)
@Serializable data class ModVersion(
    val id: String,
    val project_id: String,
    val name: String,
    val version_number: String,
    val game_versions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val version_type: String = "release",
    val date_published: String = "",
    val downloads: Long = 0,
    val files: List<ModFile> = emptyList(),
    val dependencies: List<VersionDependency> = emptyList()
)
@Serializable data class ModFile(
    val hashes: Map<String, String> = emptyMap(),
    val url: String,
    val filename: String,
    val primary: Boolean = false,
    val size: Long = 0
)
@Serializable data class VersionDependency(
    val version_id: String? = null,
    val project_id: String? = null,
    val dependency_type: String = "required"
)

interface ModrinthApi {
    @GET("search") suspend fun searchProjects(
        @Query("query") query: String,
        @Query("facets") facets: String = """[["project_type:mod"]]""",
        @Query("limit") limit: Int = 5
    ): SearchResponse

    @GET("project/{id}/version") suspend fun projectVersions(
        @Path("id") projectId: String,
        @Query("loaders") loaders: String,
        @Query("game_versions") gameVersions: String? = null
    ): List<ModVersion>

    @GET("version/{id}") suspend fun version(@Path("id") versionId: String): ModVersion
}
