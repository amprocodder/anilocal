package com.anilocal.app.data.metadata.extension

import retrofit2.http.GET
import retrofit2.http.Url

/** Fetches an Aniyomi-style extension repo index (a top-level JSON array) and its `repo.json`. */
interface ExtensionRepoApi {
    @GET
    suspend fun index(@Url url: String): List<RepoEntryDto>

    /** A repo's `repo.json` — its `meta.signingKeyFingerprint` is the trust anchor for auto-install. */
    @GET
    suspend fun repoMeta(@Url url: String): RepoMetaDto
}

/** `repo.json`. Only `meta` is consumed; unknown keys are ignored by the reflection Moshi. */
data class RepoMetaDto(val meta: RepoMeta?)

data class RepoMeta(
    val name: String?,
    val signingKeyFingerprint: String?,
)

/** One entry in `index.min.json`. Parsed by the shared reflection Moshi (no codegen needed). */
data class RepoEntryDto(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Int,
    val version: String,
    val nsfw: Int,
    val sources: List<RepoSourceDto>?,
)

data class RepoSourceDto(
    val name: String,
    val lang: String,
    val id: String,
    val baseUrl: String?,
)
