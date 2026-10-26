package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.model.InstalledRulePack
import com.justnpcx.starveil.core.model.MainlandRuleSets
import com.justnpcx.starveil.core.model.PerAppOutbound
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Rule

/**
 * The rule list the kernel would get, for a dry run.
 *
 * This exists so a caller outside this module can hand the evaluator the
 * *real* list: [ConfigBuilder.routeRules] is internal, and re-deriving the
 * order somewhere else is exactly the mistake this tool is meant to catch.
 *
 * [proxyTag] is the route's final tag under the chosen preset — "proxy" for
 * the two proxying presets and "direct" for the direct one — which is what the
 * emitted rules name when they say where to send a match.
 */
object RuleDryRun {

    fun rulesFor(
        builder: ConfigBuilder,
        rules: List<Rule> = emptyList(),
        mainland: MainlandRuleSets? = null,
        perApp: List<PerAppOutbound> = emptyList(),
        preset: RoutePreset = RoutePreset.BYPASS_MAINLAND,
        adsPath: String? = null,
        rulePacks: List<InstalledRulePack> = emptyList(),
        localSubnets: List<String> = emptyList(),
    ): List<Map<String, Any?>> {
        val proxyTag = finalTag(preset)
        // Per-app exceptions are not part of the dry run yet: they are keyed by
        // package name, and a domain- or address-shaped question cannot hit
        // them anyway. The parameter is kept so the signature does not change
        // when they are wired in.
        val perAppRules = emptyList<Map<String, Any?>>()
        return builder.routeRules(
            rules = rules,
            finalTag = proxyTag,
            proxyTag = proxyTag,
            mainland = mainland,
            perAppRules = perAppRules,
            preset = preset,
            adsPath = adsPath,
            rulePacks = rulePacks,
            localSubnets = localSubnets,
        )
    }

    /** The final tag the route block ends at, for the same reason. */
    fun finalTag(preset: RoutePreset): String =
        if (preset == RoutePreset.GLOBAL_DIRECT) "direct" else "proxy"
}

