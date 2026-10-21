package com.justnpcx.starveil.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The subnets of the network the phone is on right now.
 *
 * One question, answered from the platform: "which addresses are on the same
 * link as me". The routing config needs it to tell two private ranges apart —
 * the one the phone is standing in, where the router and the NAS are one hop
 * away, and every other private range, which is only reachable through the
 * node.
 */
interface LocalSubnets {
    /** CIDR strings, e.g. `192.168.50.0/24`. Empty when there is no network. */
    fun current(): List<String>
}

@Singleton
class AndroidLocalSubnets @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalSubnets {

    override fun current(): List<String> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return emptyList()
        val network = manager.activeNetwork ?: return emptyList()
        val properties = manager.getLinkProperties(network) ?: return emptyList()
        return properties.linkAddresses.mapNotNull { link ->
            val address = link.address ?: return@mapNotNull null
            // Loopback and link-local are already always direct, and carrying
            // them here would only make the rule longer.
            if (address.isLoopbackAddress || address.isLinkLocalAddress) return@mapNotNull null
            // A scoped IPv6 address formats as `fe80::1%wlan0`; the zone is a
            // local naming detail, not part of the CIDR.
            val host = address.hostAddress?.substringBefore('%') ?: return@mapNotNull null
            "$host/${link.prefixLength}"
        }.distinct()
    }
}
