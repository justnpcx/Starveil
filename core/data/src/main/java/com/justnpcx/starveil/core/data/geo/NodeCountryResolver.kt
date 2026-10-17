package com.justnpcx.starveil.core.data.geo

import java.net.InetAddress
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
 * Lookups are cached per hostname for the process lifetime, including the
 * failures, because a subscription of several hundred nodes often points at a
 * handful of hosts and a retry that keeps failing is just latency.
 */
@Singleton
class NodeCountryResolver @Inject constructor(
    private val geo: GeoIpLookup,
) : CountryResolver {

    private val resolved = ConcurrentHashMap<String, String>()
    private val attempted = ConcurrentHashMap.newKeySet<String>()

    override suspend fun countryOf(host: String): String? = withContext(Dispatchers.IO) {
        resolved[host]?.let { return@withContext it }
        if (!attempted.add(host)) return@withContext null
        val address = runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
            ?: return@withContext null
        geo.countryCode(address)?.also { resolved[host] = it }
    }
}
