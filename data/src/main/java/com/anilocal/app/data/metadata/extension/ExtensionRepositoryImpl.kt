package com.anilocal.app.data.metadata.extension

import android.content.Context
import com.anilocal.app.domain.model.ExtensionEntry
import com.anilocal.app.domain.repo.ExtensionRepository
import com.anilocal.app.domain.repo.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lists extensions from each configured repo's `index.min.json` (pre-seeded with the yuzono repo)
 * and downloads their APKs to app cache. Repo/network failures degrade to an empty list, matching
 * the app's silent-degrade convention.
 */
@Singleton
class ExtensionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ExtensionRepoApi,
    private val okHttp: OkHttpClient,
    private val settings: SettingsRepository,
) : ExtensionRepository {

    override suspend fun available(): List<ExtensionEntry> = withContext(Dispatchers.IO) {
        settings.extensionRepoBaseUrls.first()
            .flatMap { base ->
                val root = base.trimEnd('/')
                runCatching { api.index("$root/index.min.json").map { it.toEntry(root) } }
                    .getOrDefault(emptyList())
            }
            .distinctBy { it.pkg }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun downloadApk(entry: ExtensionEntry): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "extensions").apply { mkdirs() }
        val file = File(dir, entry.apkUrl.substringAfterLast('/'))
        okHttp.newCall(Request.Builder().url(entry.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("download failed (HTTP ${resp.code})")
            file.outputStream().use { out -> resp.body.byteStream().copyTo(out) }
        }
        file
    }

    private fun RepoEntryDto.toEntry(repoRoot: String) = ExtensionEntry(
        name = name.substringAfter("Aniyomi: ").ifBlank { name },
        pkg = pkg,
        apkUrl = "$repoRoot/apk/$apk",
        lang = lang,
        versionName = version,
        isNsfw = nsfw == 1,
        sourceNames = sources?.map { it.name } ?: emptyList(),
    )
}
