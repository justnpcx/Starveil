package com.justnpcx.starveil.core.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a verified APK to the system installer.
 *
 * The file is shared through a [FileProvider] rather than a `file://` URI: the
 * installer is another process, and since Android 7 a raw path is refused. The
 * provider is declared in the manifest with the authority
 * `${applicationId}.updates` and grants read access per intent.
 */
object UpdateInstaller {

    /** Android 8+ gates this per app; false means the user has to allow it. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun intent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Where the user grants "install unknown apps" for this app. */
    fun permissionSettings(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
