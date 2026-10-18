package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.dedupeProfiles
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable time source so the updater is deterministic in tests. */
fun interface Clock {
    fun nowMs(): Long
}

/** Narrow write-side view of subscription health, so the updater is easy to fake. */
interface SubscriptionHealthSink {
    suspend fun recordSuccess(atEpochMs: Long)

    /** [reason] must already be redacted. */
    suspend fun recordFailure(atEpochMs: Long, reason: String)
}

/** Result of one subscription refresh. */
sealed interface SubscriptionUpdateOutcome {
    /**
     * [profileCount] is what was stored; [dropped] is what the parser refused.
     * The two are reported separately because they mean different things: a
     * node dropped is a node the user does not have, and silently importing
     * 98 of 100 is how a broken subscription looks healthy.
     */
    data class Success(val profileCount: Int, val dropped: Int = 0) : SubscriptionUpdateOutcome

    /** [reason] is already redacted — safe to persist and show in the UI. */
    data class Failure(val reason: String) : SubscriptionUpdateOutcome
}

/**
 * Fetches a subscription, parses it, and replaces the group's profiles.
 *
 * Failure policy: on any error the existing profiles are left untouched (the
 * "last-good config" survives) and the failure — with a redacted reason — is
 * recorded for the health view. Success clears the stored error.
 *
 * Nothing here logs the URL or the body: both carry credentials.
 */
@Singleton
class SubscriptionUpdater @Inject constructor(
    private val registry: SubscriptionFormatRegistry,
    private val profiles: ProfileRepository,
    private val health: SubscriptionHealthSink,
    private val fetcher: SubscriptionFetcher,
    private val clock: Clock,
) {

    suspend fun update(groupId: Long, url: String): SubscriptionUpdateOutcome {
        val body = try {
            fetcher.fetch(url)
        } catch (e: IOException) {
            return fail(reason = e.message?.takeIf { it.startsWith("HTTP ") } ?: "network error")
        } catch (e: Exception) {
            return fail(reason = "network error")
        }

        val parsed = try {
            registry.resolve(body).parseDetailed(body)
        } catch (e: Exception) {
            return fail(reason = "could not parse subscription")
        }

        // The same server listed twice is one node: providers repeat a host
        // across tiers, and a refresh that appended instead of replacing would
        // grow the list on every update. Deduplication is not a parse failure,
        // so it does not add to [ParseOutcome.dropped].
        val unique = dedupeProfiles(parsed.profiles)
        if (unique.isEmpty()) {
            return fail(reason = "subscription contained no usable nodes")
        }

        return try {
            profiles.replaceGroupProfiles(groupId, unique)
            health.recordSuccess(clock.nowMs())
            SubscriptionUpdateOutcome.Success(unique.size, dropped = parsed.dropped)
        } catch (e: Exception) {
            fail(reason = "could not save nodes")
        }
    }

    private suspend fun fail(reason: String): SubscriptionUpdateOutcome {
        health.recordFailure(clock.nowMs(), reason)
        return SubscriptionUpdateOutcome.Failure(reason)
    }
}
