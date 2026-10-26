package com.justnpcx.starveil.feature.settings

import com.justnpcx.starveil.core.model.ConnectionDetail
import org.junit.Assert.assertEquals
import org.junit.Test

/** The table's filters intersect; a row must satisfy every one that is set. */
class ConnectionsFilterTest {

    private val open = row(id = "a", app = "com.a", node = "proxy", rule = "geosite:cn")
    private val other = row(id = "b", app = "com.b", node = "direct", rule = "final")
    private val closed = row(id = "c", app = "com.a", node = "proxy", rule = "geosite:cn", closedAt = 9_000)

    @Test
    fun `the default is the live table`() {
        val rows = ConnectionsViewModel.filterRows(listOf(open, other, closed), ConnectionsFilters())
        assertEquals(listOf("a", "b"), rows.map { it.id })
    }

    @Test
    fun `history is opt-in`() {
        val rows = ConnectionsViewModel.filterRows(
            listOf(open, other, closed),
            ConnectionsFilters(onlyActive = false),
        )
        assertEquals(3, rows.size)
    }

    @Test
    fun `filters intersect rather than union`() {
        val rows = ConnectionsViewModel.filterRows(
            listOf(open, other, closed),
            ConnectionsFilters(onlyActive = false, app = "com.a", node = "proxy", rule = "geosite:cn"),
        )
        assertEquals(listOf("a", "c"), rows.map { it.id })
    }

    @Test
    fun `a filter that matches nothing yields nothing`() {
        val rows = ConnectionsViewModel.filterRows(
            listOf(open, other),
            ConnectionsFilters(app = "com.b", node = "proxy"),
        )
        assertEquals(0, rows.size)
    }

    private fun row(
        id: String,
        app: String,
        node: String,
        rule: String,
        closedAt: Long = 0,
    ) = ConnectionDetail(
        id = id,
        appPackage = app,
        destination = "1.2.3.4:443",
        domain = "example.com",
        rule = rule,
        outbound = node,
        outboundType = if (node == "direct") "direct" else "vless",
        startedAtMs = 1_000,
        closedAtMs = closedAt,
        uplinkBytes = 10,
        downlinkBytes = 20,
        network = "tcp",
        protocol = "tls",
    )
}
