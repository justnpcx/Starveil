package com.justnpcx.starveil.core.data.preferences

import com.justnpcx.starveil.core.model.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stored form of the per-app map.
 *
 * Worth testing because it is the only place the two shapes are turned into
 * strings and back: a mistake here does not fail loudly, it silently drops an
 * app's route on the next launch.
 */
class PerAppRouteCodecTest {

    @Test
    fun `both kinds round-trip`() {
        val routes = mapOf(
            "com.example.bank" to AppRoute.Direct,
            "com.example.ai" to AppRoute.Node(42L),
        )

        assertEquals(routes, PerAppRouteCodec.decode(PerAppRouteCodec.encode(routes)))
    }

    @Test
    fun `the encoding is stable regardless of map order`() {
        val a = mapOf("com.b" to AppRoute.Direct, "com.a" to AppRoute.Direct)
        val b = mapOf("com.a" to AppRoute.Direct, "com.b" to AppRoute.Direct)

        assertEquals(PerAppRouteCodec.encode(a), PerAppRouteCodec.encode(b))
    }

    @Test
    fun `nothing stored is an empty map`() {
        assertEquals(emptyMap<String, AppRoute>(), PerAppRouteCodec.decode(null))
        assertEquals(emptyMap<String, AppRoute>(), PerAppRouteCodec.decode(""))
        assertEquals(emptyMap<String, AppRoute>(), PerAppRouteCodec.decode("   "))
    }

    @Test
    fun `a value from a newer build is dropped rather than thrown`() {
        // An unreadable preference must not make the app unable to start.
        val decoded = PerAppRouteCodec.decode(
            """{"com.example.a":"direct","com.example.b":"proxy-group:7","com.example.c":"node:"}""",
        )

        assertEquals(mapOf("com.example.a" to AppRoute.Direct), decoded)
    }

    @Test
    fun `malformed json is an empty map`() {
        assertEquals(emptyMap<String, AppRoute>(), PerAppRouteCodec.decode("{not json"))
    }

    @Test
    fun `a node id of zero is not a node`() {
        // Profile ids are positive; 0 is the "nothing selected" sentinel.
        assertEquals(emptyMap<String, AppRoute>(), PerAppRouteCodec.decode("""{"com.example.a":"node:0"}"""))
    }
}
