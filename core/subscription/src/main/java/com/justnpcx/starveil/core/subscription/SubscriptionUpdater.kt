package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.dedupeProfiles
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
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
    data class Success(
        val profileCount: Int,
        val dropped: Int = 0,
        /** What the server said about the quota, when it said anything. */
        val userInfo: com.justnpcx.starveil.core.model.SubscriptionUserInfo? = null,
    ) : SubscriptionUpdateOutcome

    /** [reason] is already redacted — safe to persist and show in the UI. */
    data class Failure(val reason: SubscriptionFailure) : SubscriptionUpdateOutcome
}

/**
 * One line of subscription diagnostics.
 *
 * Deliberately a function rather than a logging framework: the module has no
 * business deciding where the lines go, and a no-op default keeps every test
 * and every caller that does not care exactly as it was.
 */
fun interface SubscriptionLog {
    fun line(text: String)
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
    /**
     * Where the diagnostic lines go.
     *
     * A subscription that fails to import used to be indistinguishable from one
     * that imported nothing: the list simply stayed empty. These lines are the
     * difference — how many bytes came back, which format was recognised, how
     * many nodes survived dedupe — and they never carry the URL or the body,
     * both of which hold credentials.
     */
    private val log: SubscriptionLog = SubscriptionLog { },
) {

    suspend fun update(groupId: Long, url: String): SubscriptionUpdateOutcome {
        val fetched = try {
            fetcher.fetchWithMeta(url)
        } catch (e: HttpStatusException) {
            log.line("fetch failed: HTTP " + e.code)
            return fail(SubscriptionFailure.Http(e.code))
        } catch (e: IOException) {
            // The exception's kind, never its message: a connect failure can
            // quote the address, and the address is the credential.
            log.line("fetch failed: " + e.javaClass.simpleName)
            return fail(SubscriptionFailure.Network)
        } catch (e: Exception) {
            log.line("fetch failed: " + e.javaClass.simpleName)
            return fail(SubscriptionFailure.Network)
        }
        val body = fetched.body
        log.line("fetched " + body.length + " chars")
        val userInfo = com.justnpcx.starveil.core.model.parseSubscriptionUserInfo(fetched.userInfoHeader)

        val parser = registry.resolve(body)
        log.line("format: " + parser.javaClass.simpleName)
        val parsed = try {
            parser.parseDetailed(body)
        } catch (e: MalformedSubscriptionException) {
            // The line, not the text: a body is credentials, and the parser's
            // message can quote the offending line.
            return fail(SubscriptionFailure.MalformedDocument(e.format, e.line))
        } catch (e: Exception) {
            return fail(SubscriptionFailure.MalformedDocument(format = null, line = null))
        }

        // The same server listed twice is one node: providers repeat a host
        // across tiers, and a refresh that appended instead of replacing would
        // grow the list on every update. Deduplication is not a parse failure,
        // so it does not add to [ParseOutcome.dropped].
        val unique = dedupeProfiles(parsed.profiles)
        log.line("parsed " + parsed.profiles.size + ", kept " + unique.size)
        if (unique.isEmpty()) {
            return fail(SubscriptionFailure.NoUsableNodes)
        }

        return try {
            profiles.replaceGroupProfiles(groupId, unique)
            // The group is read back from the same column the node list reads,
            // so a save that lands somewhere else shows up as a zero here
            // instead of only as an empty screen.
            val stored = profiles.observeByGroup(groupId).first().size
            log.line("saved " + unique.size + " to group " + groupId + ", group now has " + stored)
            health.recordSuccess(clock.nowMs())
            SubscriptionUpdateOutcome.Success(unique.size, dropped = parsed.dropped, userInfo = userInfo)
        } catch (e: Exception) {
            fail(SubscriptionFailure.SaveFailed)
        }
    }

    private suspend fun fail(reason: SubscriptionFailure): SubscriptionUpdateOutcome {
        // The stored reason is the stable English diagnostic, never the copy a
        // screen shows and never anything from the body.
        health.recordFailure(clock.nowMs(), reason.diagnostic())
        return SubscriptionUpdateOutcome.Failure(reason)
    }
}
