package com.justnpcx.starveil.core.kernel.libbox

import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Backs sing-box's `local` DNS server with Android's own resolver.
 *
 * Without this, `local` has no upstream: on Android sing-box resolves through
 * the platform interface because there is no resolv.conf, and when the platform
 * returns nothing it falls back to dialling `[::1]:53`, which is refused. The
 * visible symptom is that the *outbound's own server address* cannot be
 * resolved, so no proxied connection is ever established:
 *
 *   outbound/vless[proxy]: outbound connection to <ip>:443
 *   dns: lookup failed for api.justnpc.com:
 *     write udp [::1]:40352->[::1]:53: connection refused
 *
 * Lookups are done with [InetAddress] on an IO dispatcher and reported back
 * through [ExchangeContext]; the kernel's callback is asynchronous by design
 * (it has an onCancel hook), so blocking here would stall its goroutine.
 */
class StarveilLocalDnsTransport : LocalDNSTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Raw DNS messages need a socket we cannot provide; lookups only. */
    override fun raw(): Boolean = false

    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        ctx.errorCode(ERROR_NOT_IMPLEMENTED)
    }

    override fun lookup(ctx: ExchangeContext, domain: String, strategy: String) {
        scope.launch {
            val addresses = try {
                InetAddress.getAllByName(domain).toList()
            } catch (e: Exception) {
                emptyList()
            }
            val wanted = addresses.filter { matchesStrategy(it, strategy) }
            if (wanted.isEmpty()) {
                ctx.errorCode(ERROR_NOT_FOUND)
                return@launch
            }
            wanted.forEach { ctx.success(it.hostAddress) }
        }
    }

    private fun matchesStrategy(address: InetAddress, strategy: String): Boolean = when (strategy) {
        "ipv4_only" -> address is Inet4Address
        "ipv6_only" -> address is Inet6Address
        // prefer_* means "prefer, not require" — sing-box reorders, so let
        // both families through and let it decide.
        else -> true
    }

    private companion object {
        /** DNS RCODEs; the kernel turns these into a resolution failure. */
        const val ERROR_NOT_FOUND = 3 // NXDOMAIN
        const val ERROR_NOT_IMPLEMENTED = 4 // NOTIMP
    }
}
