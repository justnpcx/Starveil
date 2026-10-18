package com.justnpcx.starveil.core.data.preferences

import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.SceneTarget
import com.justnpcx.starveil.core.model.SceneTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scenes round-trip through one preference string.
 *
 * The interesting cases are the failures: a stored scene the current build
 * cannot understand must be dropped, not thrown, or an update that renames
 * something would leave the app unable to start.
 */
class SceneCodecTest {

    @Test
    fun `scenes survive a round trip`() {
        val scenes = listOf(
            Scene(
                id = 1,
                name = "Home",
                trigger = SceneTrigger.Wifi(listOf("Jim_5G", "Jim")),
                preset = RoutePreset.BYPASS_MAINLAND,
                target = SceneTarget.Node(7),
            ),
            Scene(
                id = 2,
                name = "Out",
                trigger = SceneTrigger.Mobile,
                preset = RoutePreset.GLOBAL_PROXY,
                target = SceneTarget.Group(3),
            ),
            Scene(
                id = 3,
                name = "Any Wi-Fi",
                trigger = SceneTrigger.Wifi(),
                preset = RoutePreset.GLOBAL_DIRECT,
            ),
        )

        assertEquals(scenes, SceneCodec.decode(SceneCodec.encode(scenes)))
    }

    @Test
    fun `an unreadable entry is dropped, the rest survive`() {
        val raw = """
            [
              {"id":1,"name":"ok","trigger":{"type":"mobile"},"preset":"GLOBAL_DIRECT"},
              {"name":"no id","trigger":{"type":"mobile"},"preset":"GLOBAL_DIRECT"},
              {"id":2,"trigger":{"type":"mobile"},"preset":"GLOBAL_DIRECT"},
              {"id":3,"name":"bad trigger","trigger":{"type":"geo"},"preset":"GLOBAL_DIRECT"},
              "not an object"
            ]
        """.trimIndent()

        val decoded = SceneCodec.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals("ok", decoded.single().name)
    }

    @Test
    fun `an unknown preset falls back to the default instead of dropping the scene`() {
        val raw = """[{"id":1,"name":"x","trigger":{"type":"mobile"},"preset":"FROM_THE_FUTURE"}]"""

        // The trigger is still understood, and a routing preset is a preference
        // — losing the whole scene over one unknown word would be worse than
        // using the default.
        assertEquals(RoutePreset.GLOBAL_PROXY, SceneCodec.decode(raw).single().preset)
    }

    @Test
    fun `garbage in the preference is an empty list`() {
        assertTrue(SceneCodec.decode(null).isEmpty())
        assertTrue(SceneCodec.decode("").isEmpty())
        assertTrue(SceneCodec.decode("{").isEmpty())
        assertTrue(SceneCodec.decode("{}").isEmpty())
    }
}
