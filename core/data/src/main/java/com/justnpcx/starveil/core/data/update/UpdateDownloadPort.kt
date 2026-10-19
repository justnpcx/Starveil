package com.justnpcx.starveil.core.data.update

import java.io.File

/**
 * Downloading a release APK, behind an interface so the settings view model can
 * be tested without a Context, a cache directory or a network.
 */
interface UpdateDownloadPort {

    sealed interface Result {
        data class Ready(val file: File) : Result
        data class Failed(val reason: UpdateFailure) : Result
    }

    suspend fun download(info: UpdateInfo): Result
}
