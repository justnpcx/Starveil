package com.justnpcx.starveil.core.data.update

/**
 * Why an update could not be checked, downloaded or verified.
 *
 * A closed set rather than a message. The data layer knows *what* went wrong;
 * the layer holding the string resources is the one that knows what to call it.
 * Passing English through as the "reason" put "no published release" in front
 * of a Chinese UI — an internal diagnostic where a sentence belongs.
 *
 * One vocabulary for both the check and the download: from the user's side
 * "could not get the update" is one story, and the second half of it (a
 * truncated file, a signature that is not ours) is worth naming precisely.
 */
sealed interface UpdateFailure {

    /** The repository has no release, or nothing an unauthenticated request can see. */
    data object NoRelease : UpdateFailure

    /** GitHub's unauthenticated quota is spent for this address. */
    data object RateLimited : UpdateFailure

    /** GitHub answered, but not with a success. */
    data class Http(val code: Int) : UpdateFailure

    /** The request never completed: offline, DNS, timeout, TLS. */
    data object Network : UpdateFailure

    /** GitHub answered, but not with something this parser understands. */
    data object Unreadable : UpdateFailure

    /**
     * The release carries no APK for [abi], or for this device at all when
     * [abi] is null — the device's ABI is not one of the three that ship.
     */
    data class NoApkForDevice(val abi: String?) : UpdateFailure

    data object NoDownloadUrl : UpdateFailure

    /** Fewer bytes arrived than the release said the file has. */
    data object SizeMismatch : UpdateFailure

    /** The bytes are not the published ones — a truncated or swapped file. */
    data object ChecksumMismatch : UpdateFailure

    /** Signed by a different key: an APK the installer would refuse anyway. */
    data object SignatureMismatch : UpdateFailure
}
