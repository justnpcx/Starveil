package com.justnpcx.starveil.core.kernel.libbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Backs sing-box's `local` DNS server with Android's resolver, on the
 * **underlying** network.
 *
 * Two things this has to get right, both learned the hard way on device:
 *
 * 1. It must resolve on a non-VPN network. With the tunnel up, resolving
 *    through the default network means the query goes back into our own tun,
 *    through sing-box, back here — a loop. The symptom is that the outbound
 *    cannot resolve its own server address (`node.example.com`), so no proxied
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
        val started = SystemClock.elapsedRealtime()
        val types = when (network) {
            "ip4" -> intArrayOf(DnsResolver.TYPE_A)
            "ip6" -> intArrayOf(DnsResolver.TYPE_AAAA)
            else -> intArrayOf(DnsResolver.TYPE_A, DnsResolver.TYPE_AAAA)
        }
        val addresses = types.flatMap { query(domain, it) }.distinct()
        val elapsed = SystemClock.elapsedRealtime() - started

        val wanted = addresses.filter {
            when (network) {
                "ip4" -> it is Inet4Address
                "ip6" -> it is Inet6Address
                else -> true
            }
        }
        if (wanted.isEmpty()) {
            onDiagnostic("local dns: $domain ($network) empty after ${elapsed}ms")
            ctx.errorCode(ERROR_NOT_FOUND)
            return
        }
        if (elapsed > SLOW_LOOKUP_MS) {
            onDiagnostic("local dns: $domain ($network) took ${elapsed}ms")
        }
        // Success() replaces the address list wholesale and splits on newlines,
        // so hand over every address in a single call.
        ctx.success(wanted.joinToString("\n") { it.hostAddress })
    }

    /**
     * One family, with a hard deadline.
     *
     * While the tunnel is the default network the process resolver sends the
     * query into our own tun, where sing-box routes the node's own name to the
     * proxy whose dial needs that name — a loop that only ends when the
     * caller's context expires (the ten-second `context deadline exceeded`
     * errors on the first connection after a cold start). Ask the underlying
     * network directly, and bound it: a DNS answer that takes longer than
     * [LOOKUP_TIMEOUT_MS] is not worth blocking the kernel for.
     */
    private fun query(domain: String, type: Int): List<InetAddress> {
        if (isDefaultNetworkVpn()) {
            val network = underlyingNetwork() ?: return emptyList()
            return queryOnNetwork(network, domain, type, LOOKUP_TIMEOUT_MS)
        }
        return queryDefault(domain, type, LOOKUP_TIMEOUT_MS)
    }

    /** Resolve on a named network; the callback is asynchronous, so wait on it. */
    private fun queryOnNetwork(
        network: Network,
        domain: String,
        type: Int,
        timeoutMs: Long,
    ): List<InetAddress> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val latch = CountDownLatch(1)
        val answers = java.util.Collections.synchronizedList(mutableListOf<InetAddress>())
        return try {
            DnsResolver.getInstance().query(
                network,
                domain,
                type,
                DnsResolver.FLAG_NO_RETRY,
                callbackExecutor,
                null,
                object : DnsResolver.Callback<List<InetAddress>> {
                    override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                        answers.addAll(answer)
                        latch.countDown()
                    }

                    override fun onError(error: DnsResolver.DnsException) {
                        latch.countDown()
                    }
                },
            )
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            answers.toList()
        } catch (e: Exception) {
            onDiagnostic("local dns: $domain network query failed: ${e::class.java.simpleName}")
            emptyList()
        }
    }

    /**
     * The process resolver, for the normal case where no tunnel is up. Bounded
     * for the same reason as above: it must never hold the kernel's lookup
     * thread past the caller's deadline.
     */
    private fun queryDefault(domain: String, type: Int, timeoutMs: Long): List<InetAddress> {
        val future = blockingExecutor.submit<List<InetAddress>> {
            InetAddress.getAllByName(domain).toList()
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
                .filter { type != DnsResolver.TYPE_A || it is Inet4Address }
                .filter { type != DnsResolver.TYPE_AAAA || it is Inet6Address }
        } catch (e: Exception) {
            future.cancel(true)
            onDiagnostic("local dns: $domain default resolver failed: ${e::class.java.simpleName}")
            emptyList()
        }
    }

    /** True while the VPN is the network the process would resolve on. */
    private fun isDefaultNetworkVpn(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val active = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
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

        /** A lookup that cannot answer within this window is a failure. */
        const val LOOKUP_TIMEOUT_MS = 1_500L

        /** Above this, a successful lookup is worth reporting. */
        const val SLOW_LOOKUP_MS = 300L
    }

    /** Receives DnsResolver callbacks; must never run blocking work. */
    private val callbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "starveil-dns-callback").apply { isDaemon = true }
    }

    /** Runs blocking process-resolver lookups so they can be abandoned. */
    private val blockingExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "starveil-dns-lookup").apply { isDaemon = true }
    }
}
