package com.justnpcx.starveil.core.data.update

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads a release APK and refuses to hand it over unless it is the file
 * that was published.
 *
 * Two checks, and they fail differently on purpose:
 *
 * - **SHA-256** against the digest GitHub returns with the asset. Catches a
 *   truncated download and a swapped file on a mirror.
 * - **Signing certificate** against the installed app. Catches the case the
 *   digest cannot: a release that really was published, by someone else. The
 *   Android installer would reject it too, but rejecting it here means the file
 *   never reaches the installer.
 *
 * A build from before GitHub returned digests has no checksum; the signature is
 * still checked, and the UI says the checksum was unavailable rather than
 * pretending it passed.
 */
@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) : UpdateDownloadPort {

    override suspend fun download(info: UpdateInfo): UpdateDownloadPort.Result = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        // One file at a time: a half-downloaded older APK left behind is how a
        // cache directory grows without anyone noticing.
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, "starveil-${info.version}.apk")

        try {
            val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Starveil-UpdateChecker")
            }
            try {
                if (connection.responseCode != 200) {
                    return@withContext UpdateDownloadPort.Result.Failed(
                        UpdateFailure.Http(connection.responseCode),
                    )
                }
                connection.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            target.delete()
            return@withContext UpdateDownloadPort.Result.Failed(UpdateFailure.Network)
        }

        if (info.sizeBytes > 0 && target.length() != info.sizeBytes) {
            target.delete()
            return@withContext UpdateDownloadPort.Result.Failed(UpdateFailure.SizeMismatch)
        }
        if (info.sha256 != null && !info.sha256.equals(sha256(target), ignoreCase = true)) {
            target.delete()
            return@withContext UpdateDownloadPort.Result.Failed(UpdateFailure.ChecksumMismatch)
        }
        if (!signedByThisApp(target)) {
            target.delete()
            return@withContext UpdateDownloadPort.Result.Failed(UpdateFailure.SignatureMismatch)
        }
        UpdateDownloadPort.Result.Ready(target)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * True when the downloaded APK is signed with the same certificate as the
     * installed app. An update signed by anyone else could never be installed,
     * and a file that cannot be installed should not be offered.
     */
    @Suppress("DEPRECATION")
    private fun signedByThisApp(file: File): Boolean {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        ) ?: return false
        val downloaded = archive.signingInfo?.apkContentsSigners ?: return false
        val installed = manager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        ).signingInfo?.apkContentsSigners ?: return false
        return downloaded.any { candidate ->
            installed.any { candidate.toByteArray().contentEquals(it.toByteArray()) }
        }
    }
}
