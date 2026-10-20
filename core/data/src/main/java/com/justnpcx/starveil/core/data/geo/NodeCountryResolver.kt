package com.justnpcx.starveil.core.data.geo

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which country a node's server is in.
 *
 * Two steps that can each fail, and both failures are the same answer: no flag.
 * A node whose hostname does not resolve, or whose address is not in the
 * database, is a node the app simply does not label — it still connects.
 *
 * A successful lookup is cached per hostname for the process lifetime, because a
 * subscription of several hundred nodes often points at a handful of hosts. A
 * failure is cached for [FAILURE_RETRY_MS] and then retried: the phone that ran
 * its first lookup offline should not be stuck with unlabelled nodes until the
 * process dies.
 */
@Singleton
class NodeCountryResolver(
    private val resolveHost: suspend (String) -> String?,
    private val lookupCountry: (String) -> String?,
    private val nowMs: () -> Long,
) : CountryResolver {

    @Inject
    constructor(geo: GeoIpLookup, dns: DohResolver) : this(
        resolveHost = { host -> dns.resolve(host) },
        lookupCountry = { address -> geo.countryCode(address) },
        nowMs = System::currentTimeMillis,
    )

    private val resolved = ConcurrentHashMap<String, String>()

    /**
     * When a host last failed, so the next attempt can be delayed.
     *
     * A permanent set was the old rule, and it meant a node whose first lookup
     * happened while the phone was offline never got a flag again for the life
     * of the process. The cooldown is long enough that a failing host is not
     * retried on every emission and short enough that coming back online fixes
     * the list.
     */
    private val failedAt = ConcurrentHashMap<String, Long>()

    /** Hosts with a lookup in flight, so concurrent callers share one. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    override suspend fun countryOf(host: String): String? = withContext(Dispatchers.IO) {
        resolved[host]?.let { return@withContext it }

        val failed = failedAt[host]
        if (failed != null && nowMs() - failed < FAILURE_RETRY_MS) return@withContext null

        // One lookup at a time per host: the node list asks for the same host
        // once per node, and a subscription can hold hundreds of them.
        if (!inFlight.add(host)) return@withContext null
        try {
            // Over DoH, not the system resolver: the hostname is the interesting
            // part of a node, and plain DNS announces it to the local network.
            val address = resolveHost(host)
            if (address == null) {
                failedAt[host] = nowMs()
                return@withContext null
            }
            val country = lookupCountry(address)
            if (country == null) {
                failedAt[host] = nowMs()
                return@withContext null
            }
            resolved[host] = country
            failedAt.remove(host)
            country
        } finally {
            inFlight.remove(host)
        }
    }

    private companion object {
        /** How long a failed lookup keeps a host out of the queue. */
        const val FAILURE_RETRY_MS = 60_000L
    }
}
