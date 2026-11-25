package com.anilocal.app.data.metadata.extension

import com.anilocal.app.data.remote.JsonHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Fetches an Aniyomi-style extension repo index (a top-level JSON array) and its `repo.json`. */
class ExtensionRepoApi(private val http: JsonHttp) {
    suspend fun index(url: String): List<RepoEntryDto> = http.get(url.toHttpUrl(), ENTRIES)

    /** A repo's `repo.json` — its `meta.signingKeyFingerprint` is the trust anchor for auto-install. */
    suspend fun repoMeta(url: String): RepoMetaDto = http.get(url.toHttpUrl(), RepoMetaDto.serializer())

    private companion object {
        val ENTRIES = ListSerializer(RepoEntryDto.serializer())
    }
}

/** `repo.json`. Only `meta` is consumed; other keys are ignored. Also cached as JSON (`extmeta:*`). */
@Serializable
data class RepoMetaDto(val meta: RepoMeta? = null)

@Serializable
data class RepoMeta(
    val name: String? = null,
    val signingKeyFingerprint: String? = null,
)

/** One entry in `index.min.json`. */
@Serializable
data class RepoEntryDto(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Int,
    val version: String,
    val nsfw: Int,
    val sources: List<RepoSourceDto>? = null,
)

@Serializable
data class RepoSourceDto(
    val name: String,
    val lang: String,
    val id: String,
    val baseUrl: String? = null,
)
