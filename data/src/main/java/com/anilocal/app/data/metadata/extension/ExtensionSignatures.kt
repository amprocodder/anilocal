package com.anilocal.app.data.metadata.extension

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Reads an extension APK's identity + signing-certificate SHA-256s straight off the file (no install)
 * for the private-install trust gate: an APK is only trusted if one of its signing certs matches the
 * repo's published fingerprint, and an update must not downgrade the version or swap the signer.
 * SHA-256 of the raw cert bytes, lowercase hex, no separators — the same form repo `repo.json`
 * `signingKeyFingerprint` uses (and what Mihon/Anikku compute).
 */
object ExtensionSignatures {

    data class ApkIdentity(val pkg: String, val versionCode: Long, val signatures: Set<String>)

    /** Parse [path] once, returning package name, version code, and signing-cert SHA-256s, or null if
     *  the file isn't a readable APK. */
    fun identifyApk(pm: PackageManager, path: String): ApkIdentity? {
        val info = archiveInfo(pm, path) ?: return null
        return ApkIdentity(info.packageName, info.versionCodeCompat(), signaturesOf(info))
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return runCatching { pm.getPackageArchiveInfo(path, flags) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun signaturesOf(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = info.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else {
            info.signatures
        }
        return sigs?.mapNotNull { it?.toByteArray()?.sha256Hex() }?.toSet().orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
}
