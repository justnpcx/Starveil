package com.justnpcx.starveil.feature.settings

import com.justnpcx.starveil.core.model.ConnectionDetail

/**
 * Rows that exist only in a debug build.
 *
 * Two states the connections table has to render and a device will not produce
 * on demand: a connection that has been open for days, and one whose start the
 * kernel never reported (which used to render as "20891617m 43s"). Seeing them
 * here is cheaper than waiting two days for the first and for a fault for the
 * second.
 *
 * The release build compiles an empty list instead — see src/release.
 */
internal object DebugConnections {

    private const val TWO_DAYS_FIVE_HOURS_MS = 190_800_000L

    fun extraRows(nowMs: Long): List<ConnectionDetail> = listOf(
        ConnectionDetail(
            id = "debug-long-running",
            appPackage = "com.example.browser",
            destination = "debug.example:443",
            domain = "debug.example",
            rule = "geosite:geolocation-!cn",
            outbound = "proxy",
            outboundType = "vless",
            startedAtMs = nowMs - TWO_DAYS_FIVE_HOURS_MS,
            closedAtMs = 0L,
            uplinkBytes = 1_048_576,
            downlinkBytes = 12_582_912,
            network = "tcp",
            protocol = "tls",
        ),
        // A start of 0 is what the kernel reports for a connection it only saw
        // the end of; negative bytes are what a lost update leaves behind.
        ConnectionDetail(
            id = "debug-no-start",
            appPackage = "com.example.browser",
            destination = "1.2.3.4:443",
            domain = "",
            rule = "",
            outbound = "direct",
            outboundType = "direct",
            startedAtMs = 0L,
            closedAtMs = 0L,
            uplinkBytes = -10,
            downlinkBytes = -10,
            network = "tcp",
            protocol = "tls",
        ),
    )
}
