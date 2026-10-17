package com.justnpcx.starveil.core.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The file the user picked through the system picker.
 *
 * A port rather than `ContentResolver` straight in the view model: the picker
 * hands back a `content://` URI and nothing else about it matters to the code
 * that drives export and restore, so keeping it behind an interface is what
 * lets that code be unit-tested without an Android context.
 */
interface BackupFileStore {
    suspend fun write(uri: Uri, bytes: ByteArray)
    suspend fun read(uri: Uri): ByteArray
}

class ContentResolverBackupFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupFileStore {

    override suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("the chosen file could not be opened")
        out.use { it.write(bytes) }
    }

    override suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("the chosen file could not be opened")
        input.use { it.readBytes() }
    }
}
