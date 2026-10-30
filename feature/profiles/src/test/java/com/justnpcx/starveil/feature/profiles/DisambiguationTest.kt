package com.justnpcx.starveil.feature.profiles

import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling two identically titled nodes apart.
 *
 * The title is one line with an ellipsis, so two nodes whose names differ only
 * past the twelfth character read the same. The suffix has to be short enough
 * to sit beside that title and specific enough that the rows are actually
 * different — which is the whole point of adding it.
 */
class DisambiguationTest {

    private fun node(
        id: Long,
        name: String,
        port: Int = 443,
        server: String = "h.example",
        type: ProfileType = ProfileType.VLESS,
    ) = Profile(id = id, name = name, type = type, server = server, port = port)

    @Test
    fun `a unique name gets no suffix`() {
        assertTrue(disambiguate(listOf(node(1, "Tokyo"), node(2, "Osaka"))).isEmpty())
    }

    @Test
    fun `names that only differ past the ellipsis are disambiguated`() {
        val suffix = disambiguate(
            listOf(
                node(1, "HY2+Reality-Fast", port = 443),
                node(2, "HY2+Reality-Slow", port = 8443),
            ),
        )

        assertEquals(":443", suffix[1])
        assertEquals(":8443", suffix[2])
    }

    @Test
    fun `the same port falls back to the protocol`() {
        val suffix = disambiguate(
            listOf(
                node(1, "HY2+Reality-Fast", type = ProfileType.HYSTERIA2),
                node(2, "HY2+Reality-Slow", type = ProfileType.TUIC),
            ),
        )

        assertEquals("HY2", suffix[1])
        assertEquals("TUIC", suffix[2])
    }

    @Test
    fun `identical rows fall back to an ordinal`() {
        val suffix = disambiguate(listOf(node(1, "Same"), node(2, "Same")))

        assertEquals("#1", suffix[1])
        assertEquals("#2", suffix[2])
    }

    @Test
    fun `a short host is left alone`() {
        assertEquals("api.justnpc.com", middleEllipsis("api.justnpc.com"))
    }

    @Test
    fun `a long host keeps both ends`() {
        val out = middleEllipsis("a-very-long-subdomain.example.com")

        assertTrue(out.length <= 20)
        assertTrue(out.startsWith("a-very"))
        assertTrue(out.endsWith("ample.com"))
        assertTrue(out.contains("…"))
    }
}
