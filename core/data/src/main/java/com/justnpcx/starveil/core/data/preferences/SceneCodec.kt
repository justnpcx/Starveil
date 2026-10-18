package com.justnpcx.starveil.core.data.preferences

import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.SceneTarget
import com.justnpcx.starveil.core.model.SceneTrigger
import com.justnpcx.starveil.core.model.json.Json

/**
 * How scenes are stored.
 *
 * A JSON array in one preference, like per-app routes: the list is small, it is
 * always read whole, and a separate table would buy indexes nothing queries.
 *
 * Decoding is total — an entry that cannot be understood is dropped, not
 * thrown. A scene written by a newer build must not stop this one from
 * starting, and a lost scene is a lost convenience rather than a lost tunnel.
 */
internal object SceneCodec {

    fun encode(scenes: List<Scene>): String =
        Json.write(
            scenes.map { scene ->
                linkedMapOf<String, Any?>(
                    "id" to scene.id,
                    "name" to scene.name,
                    "trigger" to when (val trigger = scene.trigger) {
                        is SceneTrigger.Wifi -> linkedMapOf(
                            "type" to "wifi",
                            "ssids" to trigger.ssids,
                        )

                        SceneTrigger.Mobile -> linkedMapOf("type" to "mobile")
                    },
                    "preset" to scene.preset.name,
                ).apply {
                    when (val target = scene.target) {
                        is SceneTarget.Node -> put("node", target.profileId)
                        is SceneTarget.Group -> put("group", target.groupId)
                        null -> Unit
                    }
                }
            },
        )

    fun decode(raw: String?): List<Scene> {
        if (raw.isNullOrBlank()) return emptyList()
        val parsed = runCatching { Json.parse(raw) }.getOrNull() as? List<*> ?: return emptyList()
        return parsed.mapNotNull { entry -> decodeScene(entry as? Map<*, *> ?: return@mapNotNull null) }
    }

    private fun decodeScene(entry: Map<*, *>): Scene? {
        val id = (entry["id"] as? Number)?.toLong()?.takeIf { it > 0 } ?: return null
        val name = (entry["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val preset = RoutePreset.fromStorage(entry["preset"] as? String)
        val trigger = decodeTrigger(entry["trigger"] as? Map<*, *>) ?: return null
        val target = when {
            entry["node"] is Number -> SceneTarget.Node((entry["node"] as Number).toLong())
            entry["group"] is Number -> SceneTarget.Group((entry["group"] as Number).toLong())
            else -> null
        }
        return Scene(id = id, name = name, trigger = trigger, preset = preset, target = target)
    }

    private fun decodeTrigger(raw: Map<*, *>?): SceneTrigger? = when (raw?.get("type")) {
        "mobile" -> SceneTrigger.Mobile
        "wifi" -> SceneTrigger.Wifi(
            (raw["ssids"] as? List<*>)
                ?.filterIsInstance<String>()
                ?.filter { it.isNotBlank() }
                .orEmpty(),
        )

        else -> null
    }
}
