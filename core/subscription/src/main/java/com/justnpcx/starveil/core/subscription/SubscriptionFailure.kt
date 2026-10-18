package com.justnpcx.starveil.core.subscription

/** Which document shape a parser was reading. Used only to say what went wrong. */
enum class DocumentFormat { YAML, JSON, BASE64 }

/**
 * Why a subscription refresh failed, as data.
 *
 * This used to be a sentence built in the updater, which meant the one place
 * that could not localise it was the screen. The UI now maps this to copy, and
 * [diagnostic] is the stable English form kept for logs and the stored health
 * reason — never shown as-is to a user.
 */
sealed interface SubscriptionFailure {
    data object Network : SubscriptionFailure

    data class Http(val code: Int) : SubscriptionFailure

    /**
     * The document is the claimed format but does not parse. [line] is 1-based
     * when the parser could tell; null when it could not, which is a different
     * sentence but still the same failure.
     */
    data class MalformedDocument(val format: DocumentFormat?, val line: Int?) : SubscriptionFailure

    /** The document parsed, but every entry was unusable. */
    data object NoUsableNodes : SubscriptionFailure

    data object SaveFailed : SubscriptionFailure
}

/**
 * The redacted, stable form of a failure — for logs and for the stored health
 * reason. It never contains the URL, the body, or any credentials.
 */
fun SubscriptionFailure.diagnostic(): String = when (this) {
    SubscriptionFailure.Network -> "network error"
    is SubscriptionFailure.Http -> "HTTP $code"
    is SubscriptionFailure.MalformedDocument -> buildString {
        append("malformed ")
        append(format?.name?.lowercase() ?: "subscription")
        if (line != null) append(" at line $line")
    }
    SubscriptionFailure.NoUsableNodes -> "subscription contained no usable nodes"
    SubscriptionFailure.SaveFailed -> "could not save nodes"
}

/**
 * A parser recognised the document and could not read it.
 *
 * Thrown by [SubscriptionParser.parseDetailed] only: [SubscriptionParser.parse]
 * stays total and returns an empty list, because callers that only want the
 * profiles have nothing useful to do with an exception. The updater uses the
 * detailed form, which is where the line number matters.
 */
class MalformedSubscriptionException(
    val format: DocumentFormat?,
    val line: Int? = null,
    cause: Throwable? = null,
) : Exception("malformed ${format?.name?.lowercase() ?: "subscription"}", cause)

/** 1-based line for a byte offset, or null when the parser did not know one. */
internal fun lineAt(text: String, offset: Int?): Int? {
    if (offset == null || offset < 0) return null
    return text.take(offset.coerceAtMost(text.length)).count { it == '\n' } + 1
}
