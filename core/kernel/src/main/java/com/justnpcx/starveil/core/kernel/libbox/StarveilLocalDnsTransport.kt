package com.justnpcx.starveil.core.kernel.libbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Backs sing-box's `local` DNS server with Android's resolver, on the
 * **underlying** network.
 *
 * Two things this has to get right, both learned the hard way on device:
 *
 * 1. It must resolve on a non-VPN network. With the tunnel up, resolving
 *    through the default network means the query goes back into our own tun,
 *    through sing-box, back here — a loop. The symptom is that the outbound
 *    cannot resolve its own server address (`api.justnpc.com`), so no proxied
 *    connection is ever established.
 *
 * 2. It must complete before it returns. The kernel calls
 *    `PlatformInterface.Lookup` and then immediately reads the addresses off
 *    the exchange context (`experimental/libbox/dns.go`). Reporting from a
 *    coroutine leaves the list empty, which surfaces as
 *    `exchanged <domain> NOERROR 0` + `lookup failed: empty result` — a
 *    "success" with no records, which is worse than an error.
 */
class StarveilLocalDnsTransport(
    private val context: Context,
    private val onDiagnostic: (String) -> Unit = {},
) : LocalDNSTransport {

    /** Raw DNS messages need a socket we cannot provide; lookups only. */
    override fun raw(): Boolean = false

    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        ctx.errorCode(ERROR_NOT_IMPLEMENTED)
    }

    /**
     * Parameter order matters and is not what it looks like. The Go interface
     * is `Lookup(ctx *ExchangeContext, network string, domain string)` and the
     * kernel calls it as `p.iif.Lookup(response, network, question.Name)`, so
     * the second argument is the query family ("ip4" / "ip6") and the third is
     * the domain. Reading them the other way round makes every lookup ask the
     * system resolver for a host called "ip4" and answer NXDOMAIN.
     */
    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        val addresses = try {
            resolve(domain)
        } catch (e: Exception) {
            onDiagnostic("local dns: $domain ($network) failed: ${e::class.java.simpleName}: ${e.message}")
            emptyList()
        }

        val wanted = addresses.filter {
            when (network) {
                "ip4" -> it is Inet4Address
                "ip6" -> it is Inet6Address
                else -> true
            }
        }
        if (wanted.isEmpty()) {
            ctx.errorCode(ERROR_NOT_FOUND)
            return
        }
        // Success() replaces the address list wholesale and splits on newlines,
        // so hand over every address in a single call.
        ctx.success(wanted.joinToString("\n") { it.hostAddress })
    }

    /**
     * Resolve the way the rest of the app does.
     *
     * [InetAddress.getAllByName] uses the process's default network. Our
     * process is excluded from the tunnel (StarveilVpnService disallows its own
     * package), so this is the real upstream — and unlike the network-bound
     * resolver it honours /etc/hosts and Private DNS, which is what the
     * subscription import relies on.
     *
     * The network-bound path stays as a fallback in case the process resolver
     * is the one that ends up looping into our own tunnel.
     */
    private fun resolve(domain: String): List<InetAddress> {
        try {
            val addresses = InetAddress.getAllByName(domain).toList()
            if (addresses.isNotEmpty()) return addresses
        } catch (e: Exception) {
            onDiagnostic("local dns: $domain default resolver failed: ${e::class.java.simpleName}")
        }
        val network = underlyingNetwork()
        if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            onDiagnostic("local dns: $domain falling back to network resolver")
            return runCatching { network.getAllByName(domain).toList() }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    /** The network to resolve on: the app's own, as long as it is not a VPN. */
    private fun underlyingNetwork(): android.net.Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null

        // The app's active network, as long as the tunnel is not the default.
        val active = manager.activeNetwork
        if (active != null) {
            val capabilities = manager.getNetworkCapabilities(active)
            if (capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return active
            }
        }

        // With the tunnel up the default network is the VPN, so fall back to
        // any non-VPN network that has internet.
        return manager.allNetworks.firstOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstOrNull false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    private companion object {
        /** DNS RCODEs; ErrorCode maps straight to dns.RcodeError. */
        const val ERROR_NOT_FOUND = 3 // NXDOMAIN
        const val ERROR_NOT_IMPLEMENTED = 4 // NOTIMP
    }
}
