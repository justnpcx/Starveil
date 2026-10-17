package com.justnpcx.starveil.core.data.preferences

import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.json.Json

/**
 * How per-app targets are stored.
 *
 * A JSON object rather than a delimiter-joined string set: it is the format the
 * rest of the app already writes and parses, and a package name is not a safe
 * delimiter carrier (they are `[a-zA-Z0-9_.]` today, which is exactly the kind
 * of "today" that breaks later).
 *
 * Values are compact because nothing reads them by eye: `"direct"` or
 * `"node:<id>"`. Anything unrecognised is dropped rather than thrown, so a
 * value written by a newer build cannot make the app unable to start.
 */
internal object PerAppRouteCodec {

    fun encode(routes: Map<String, AppRoute>): String =
        Json.write(
            routes.entries
                .sortedBy { it.key }
                .associate { (packageName, route) ->
                    packageName to when (route) {
                        AppRoute.Direct -> "direct"
                        is AppRoute.Node -> "node:${route.profileId}"
                    }
                },
        )

    fun decode(raw: String?): Map<String, AppRoute> {
        if (raw.isNullOrBlank()) return emptyMap()
        val parsed = runCatching { Json.parseObject(raw) }.getOrNull() ?: return emptyMap()
        return parsed.mapNotNull { (packageName, value) ->
            val route = when {
                value == "direct" -> AppRoute.Direct
                value is String && value.startsWith(NODE_PREFIX) ->
                    value.removePrefix(NODE_PREFIX).toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.let(AppRoute::Node)
                else -> null
            }
            route?.let { packageName to it }
        }.toMap()
    }

    private const val NODE_PREFIX = "node:"
}
