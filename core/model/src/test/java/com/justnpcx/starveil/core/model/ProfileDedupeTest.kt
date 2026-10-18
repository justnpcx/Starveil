package com.justnpcx.starveil.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Deduplication, which is the difference between a subscription that stays
 * the same size and one that grows every refresh.
 */
class ProfileDedupeTest {

    private fun node(
        name: String,
        type: ProfileType = ProfileType.VLESS,
        server: String = "a.example.com",
        port: Int = 443,
    ) = Profile(name = name, type = type, server = server, port = port)

    @Test
    fun `the same protocol, address and port is one node`() {
        val deduped = dedupeProfiles(
            listOf(node("Tokyo"), node("Tokyo · premium"), node("Tokyo")),
        )

        assertEquals(1, deduped.size)
        // The fuller label is the one that told them apart.
        assertEquals("Tokyo · premium", deduped.single().name)
    }

    @Test
    fun `a different port, protocol or host is a different node`() {
        val deduped = dedupeProfiles(
            listOf(
                node("a", port = 443),
                node("b", port = 8443),
                node("c", type = ProfileType.TROJAN),
                node("d", server = "b.example.com"),
            ),
        )

        assertEquals(4, deduped.size)
    }

    @Test
    fun `the host comparison ignores case`() {
        // DNS names are case-insensitive; two spellings are one server.
        val deduped = dedupeProfiles(
            listOf(node("a", server = "A.Example.com"), node("b", server = "a.example.com")),
        )

        assertEquals(1, deduped.size)
    }

    @Test
    fun `the survivors keep the order they arrived in`() {
        val deduped = dedupeProfiles(
            listOf(node("one", port = 1), node("two", port = 2), node("one again", port = 1)),
        )

        assertEquals(listOf("one again", "two"), deduped.map { it.name })
    }
}
