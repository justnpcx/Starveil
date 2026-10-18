package com.justnpcx.starveil.core.subscription

import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.json.Json

/**
 * SIP008 — the Shadowsocks project's own subscription format.
 *
 * ```json
 * {"version": 1, "servers": [
 *   {"id": "…", "remarks": "Tokyo", "server": "1.2.3.4", "server_port": 8388,
 *    "password": "…", "method": "aes-256-gcm"}
 * ]}
 * ```
 *
 * Only Shadowsocks: the format is defined by that project and carries no
 * other protocol. A server entry missing any of the four required fields is
 * dropped rather than imported half-built — a profile without a method fails
 * at connect with a message about the kernel, which is the wrong place to
 * learn that the subscription was malformed.
 */
class Sip008SubscriptionParser : SubscriptionParser {

    override fun parse(text: String): List<Profile> = parseDetailed(text).profiles

    override fun parseDetailed(text: String): ParseOutcome {
        val root = runCatching { Json.parseObject(text) }.getOrNull()
            ?: return ParseOutcome(emptyList())
        val servers = root["servers"] as? List<*> ?: return ParseOutcome(emptyList())
        val profiles = servers.mapNotNull { entry -> entry.toProfile() }
        return ParseOutcome(profiles, dropped = servers.size - profiles.size)
    }

    private fun Any?.toProfile(): Profile? {
        val map = this as? Map<*, *> ?: return null
        val server = (map["server"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = (map["server_port"] as? Number)?.toInt()?.takeIf { it in 1..65535 } ?: return null
        val method = (map["method"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val password = (map["password"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val remarks = (map["remarks"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        return Profile(
            name = remarks ?: "$server:$port",
            type = ProfileType.SHADOWSOCKS,
            server = server,
            port = port,
            settings = ShadowsocksSettings(method = method, password = password),
        )
    }

    companion object {
        /**
         * Cheap shape check: an object with a `servers` array.
         *
         * Registered before the base64 parser, which would otherwise see a
         * JSON document as "not base64" and hand it to the fallback.
         */
        fun sniff(text: String): Boolean {
            val trimmed = text.trimStart()
            if (!trimmed.startsWith("{")) return false
            return runCatching { Json.parseObject(trimmed)["servers"] is List<*> }
                .getOrDefault(false)
        }
    }
}
