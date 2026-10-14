package com.justnpcx.starveil.core.kernel

import java.io.File

/**
 * Filesystem roots libbox needs at setup time.
 *
 * `Libbox.setup` runs `os.MkdirAll(workingPath)` and `os.MkdirAll(tempPath)`,
 * then redirects stderr to `workingPath/CrashReport-*.log`. An empty path makes
 * MkdirAll fail and setup throws, which surfaces as
 * [KernelError.StartFailed] — so these must be real, writable directories.
 *
 * `:app` supplies them from its Context; the kernel never sees a Context.
 */
data class KernelPaths(
    /** Where libbox keeps command.sock and configuration.json. Created here. */
    val basePath: String,
    /** Working directory (crash/OOM/power reports). Must exist. */
    val workingPath: String,
    /** Scratch directory. Must exist. */
    val tempPath: String,
) {
    /** Creates any missing roots. Safe to call repeatedly. */
    fun ensureDirectories() {
        File(basePath).mkdirs()
        File(workingPath).mkdirs()
        File(tempPath).mkdirs()
    }
}
