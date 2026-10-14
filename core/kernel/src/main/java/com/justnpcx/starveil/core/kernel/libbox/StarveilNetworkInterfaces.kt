package com.justnpcx.starveil.core.kernel.libbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.StringIterator

/**
 * Enumerates the platform's networks for sing-box.
 *
 * This fork hardcodes `platformInterfaceWrapper.UsePlatformNetworkInterfaces()`
 * to true, so the dialer always takes the platform-interfaces path
 * (`common/dialer/default.go`) and asks us for the list. Returning null made
 * every proxied connection fail with `no available network interface` before it
 * even tried to dial:
 *
 *   common/dialer/default_parallel_interface.go: no available network interface
 *
 * The VPN's own network is deliberately left out: handing the tunnel back to the
 * dialer would let it pick our own tun and loop.
 *
 * Field formats come from the Go side's consumer
 * (`platformInterfaceWrapper.NetworkInterfaces`): addresses are prefixes
 * ("10.0.0.2/24"), DNS and gateways are bare addresses, and type is the
 * InterfaceType enum (wifi 0, cellular 1, ethernet 2, other 3).
 */
internal fun listPlatformNetworkInterfaces(
    context: Context,
    onDiagnostic: (String) -> Unit = {},
): NetworkInterfaceIterator? {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val interfaces = mutableListOf<NetworkInterface>()
    var networks = 0
    var withCapabilities = 0
    var withLink = 0
    var withName = 0

    for (network in manager.allNetworks) {
        networks++
        val capabilities = manager.getNetworkCapabilities(network) ?: continue
        withCapabilities++
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
        val link = manager.getLinkProperties(network) ?: continue
        withLink++
        val name = link.interfaceName ?: continue
        withName++
        // NetworkInterface.getByName is restricted on Android 11+; a null here
        // must not drop the interface, so fall back to index 0 and keep the
        // name (which is what the dialer binds to).
        val javaInterface = runCatching { java.net.NetworkInterface.getByName(name) }.getOrNull()
        onDiagnostic("iface: name=$name java=${javaInterface != null} index=${javaInterface?.index}")

        interfaces += NetworkInterface().apply {
            this.name = name
            index = javaInterface?.index ?: 0
            mtu = link.mtu
            type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TYPE_WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TYPE_CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TYPE_ETHERNET
                else -> TYPE_OTHER
            }
            addresses = StarveilStringIterator(
                link.linkAddresses.map { "${it.address.hostAddress}/${it.prefixLength}" },
            )
            dnsServer = StarveilStringIterator(link.dnsServers.mapNotNull { it.hostAddress })
            gateway = StarveilStringIterator(
                link.routes.filter { it.isDefaultRoute }
                    .mapNotNull { it.gateway?.hostAddress }
                    .distinct(),
            )
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            flags = javaInterface?.rawFlags() ?: IFF_UP
        }
    }
    onDiagnostic(
        "getInterfaces: networks=$networks caps=$withCapabilities link=$withLink name=$withName -> ${interfaces.size} ifaces",
    )
    return StarveilNetworkInterfaceIterator(interfaces)
}

/** Raw IFF_* bits, which is what the Go side's linkFlags() reads. */
private fun java.net.NetworkInterface.rawFlags(): Int {
    var flags = 0
    if (isUp) flags = flags or IFF_UP or IFF_RUNNING
    if (supportsMulticast()) flags = flags or IFF_MULTICAST
    if (isLoopback) flags = flags or IFF_LOOPBACK
    if (isPointToPoint) flags = flags or IFF_POINTOPOINT
    return flags
}

private const val IFF_UP = 0x1
private const val IFF_LOOPBACK = 0x8
private const val IFF_POINTOPOINT = 0x10
private const val IFF_RUNNING = 0x40
private const val IFF_MULTICAST = 0x1000

private const val TYPE_WIFI = 0
private const val TYPE_CELLULAR = 1
private const val TYPE_ETHERNET = 2
private const val TYPE_OTHER = 3

/** gobind iterators are one-shot; this wraps a fixed list. */
internal class StarveilStringIterator(private val values: List<String>) : StringIterator {
    private var position = 0
    override fun hasNext(): Boolean = position < values.size
    override fun len(): Int = values.size - position
    override fun next(): String = values[position++]
}

internal class StarveilNetworkInterfaceIterator(
    private val values: List<NetworkInterface>,
) : NetworkInterfaceIterator {
    private var position = 0
    override fun hasNext(): Boolean = position < values.size
    override fun next(): NetworkInterface = values[position++]
}
