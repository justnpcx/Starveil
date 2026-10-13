package com.justnpcx.starveil.core.data.backup

/**
 * What to do when opening the encrypted database fails — almost always a
 * failed migration.
 *
 * The decision space deliberately has **no** "wipe and recreate" option, so
 * destructive fallback is structurally impossible (the README red line). Either
 * an encrypted backup exists and we restore it, or we ask the user for one.
 *
 * Pure and synchronous so it is trivially unit-testable; the caller performs
 * the actual restore.
 */
object DatabaseRecoveryPolicy {

    sealed interface Decision {
        /** An encrypted backup is on hand: restore it, then report to the user. */
        data object RestoreFromBackup : Decision

        /** No backup available: tell the user, keep the on-disk data untouched. */
        data class AskUserForBackup(val reason: String) : Decision
    }

    fun decide(cause: Throwable, hasBackup: Boolean): Decision =
        if (hasBackup) {
            Decision.RestoreFromBackup
        } else {
            Decision.AskUserForBackup(
                "The encrypted database could not be opened " +
                    "(${cause::class.simpleName}). Your data was not deleted — " +
                    "restore from a backup to continue.",
            )
        }
}
