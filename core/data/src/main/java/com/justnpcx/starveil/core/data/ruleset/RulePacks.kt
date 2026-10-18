package com.justnpcx.starveil.core.data.ruleset

import com.justnpcx.starveil.core.model.InstalledRulePack
import com.justnpcx.starveil.core.model.RulePack
import com.justnpcx.starveil.core.model.RulePackAction
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The official packs, phase one.
 *
 * Deliberately a small, closed list. The screen is a market in the sense that
 * there is something to choose from, not a registry: user-submitted sources are
 * phase two, and the extension point is this object plus [RulePackStore] —
 * a pack is data, so adding one is a line here and a hash, not a new code path.
 *
 * Every file comes from `SagerNet/sing-geosite` at the commit the mainland
 * preset already pins, so the whole app trusts one snapshot of one source.
 */
object RulePacks {

    const val COMMIT = "4f2dea911f1dbc3fd7516dbc2c82e03a0add71e6"

    /**
     * The built-in switch blocks `category-ads-all`. This one adds software
     * telemetry and error reporting to it, which is the half people mean by
     * "ads follow me around".
     */
    val ADS_ENHANCED = pack(
        id = "ads-enhanced",
        name = "Ads and telemetry",
        action = RulePackAction.REJECT,
        remote = "geosite-category-ads-and-telemetry.srs",
        sha256 = "8f5f9b633d65063b5ddf74cf33a2d62b8ed0e7a3d03a3dc40bebdab1a71e681f",
    )

    /**
     * Telemetry alone, for someone who wants app analytics and crash reporting
     * blocked but not advertising domains.
     */
    val ANTI_TRACKING = pack(
        id = "anti-tracking",
        name = "Telemetry",
        action = RulePackAction.REJECT,
        remote = "geosite-category-telemetry.srs",
        sha256 = "cec8db4f44d990be4c2422b1fbe139f49fef1c9d4e645101dbb4334f6ba1054d",
    )

    /**
     * Entertainment outside the mainland — Netflix, Disney+, HBO, Spotify,
     * games — through the tunnel. The source list is explicitly "outside China
     * mainland", so domestic video sites are not dragged along.
     */
    val STREAMING = pack(
        id = "streaming",
        name = "Streaming",
        action = RulePackAction.PROXY,
        remote = "geosite-category-entertainment.srs",
        sha256 = "5fe771d26560df0dc53da8b9e0b9053d45fd3a23d8034f18cfd1180db0fd0c22",
    )

    /** GitHub, npm, PyPI, Docker, distro mirrors: the developer's daily round trip. */
    val DEVELOPER = pack(
        id = "developer",
        name = "Developer",
        action = RulePackAction.PROXY,
        remote = "geosite-category-dev.srs",
        sha256 = "5ab4cba143af4b92781043f393f72970a00ab4b9a3ff06a4a1bb64674318a6a1",
    )

    /** AI services outside the mainland, which are the ones that need a tunnel. */
    val AI = pack(
        id = "ai",
        name = "AI services",
        action = RulePackAction.PROXY,
        remote = "geosite-category-ai-!cn.srs",
        sha256 = "c299c6a959a720c844a26ca77affeb0477aee18a72c15c1837639e57dbb5ac8c",
    )

    val ALL: List<RulePack> = listOf(ADS_ENHANCED, ANTI_TRACKING, STREAMING, DEVELOPER, AI)

    fun byId(id: String): RulePack? = ALL.firstOrNull { it.id == id }

    private fun pack(
        id: String,
        name: String,
        action: RulePackAction,
        remote: String,
        sha256: String,
    ): RulePack = RulePack(
        id = id,
        name = name,
        action = action,
        fileName = remote,
        sha256 = sha256,
        urls = listOf(
            "https://cdn.jsdelivr.net/gh/SagerNet/sing-geosite@$COMMIT/$remote",
            "https://raw.githubusercontent.com/SagerNet/sing-geosite/$COMMIT/$remote",
        ),
    )
}

/** A subscribed pack that could not be trusted this time. */
data class RulePackFailure(
    val id: String,
    val reason: RuleSetFailure.Reason,
)

/**
 * The subscribed packs, on disk.
 *
 * Same contract as [MainlandRuleSetStore]: a failure is `null` for that pack,
 * never an exception, because the tunnel must still come up — with one rule
 * fewer — and the screen has to be able to say which one is missing.
 */
interface RulePackStore {
    /**
     * Downloads whatever [ids] names is missing and returns only the packs that
     * verified. Order follows [RulePacks.ALL], not the set, so the same
     * subscription always produces the same config.
     */
    suspend fun ensure(ids: Set<String>): List<InstalledRulePack>

    /** The most recent rejection, or null when the last attempt was clean. */
    val failure: StateFlow<RulePackFailure?>
}

class FileRulePackStore(
    private val directory: File,
    private val download: suspend (String) -> ByteArray = FileMainlandRuleSetStore::httpGet,
    private val warn: (String) -> Unit = {},
    private val packs: List<RulePack> = RulePacks.ALL,
) : RulePackStore {

    private val cache = PinnedFileCache(directory, download, warn)
    private val _failure = MutableStateFlow<RulePackFailure?>(null)
    override val failure: StateFlow<RulePackFailure?> = _failure.asStateFlow()

    override suspend fun ensure(ids: Set<String>): List<InstalledRulePack> {
        _failure.value = null
        return packs.filter { it.id in ids }.mapNotNull { pack ->
            when (val result = cache.obtain(pack.toPinned())) {
                is PinnedFileCache.Result.Obtained ->
                    InstalledRulePack(pack, result.file.absolutePath)

                is PinnedFileCache.Result.Failed -> {
                    // First failure wins: it is the one the user needs to see,
                    // and a later success must not hide it.
                    if (_failure.value == null) {
                        _failure.value = RulePackFailure(pack.id, result.reason)
                    }
                    null
                }
            }
        }
    }

    private fun RulePack.toPinned() = PinnedRuleSet(
        name = fileName,
        sha256 = sha256,
        urls = urls,
    )
}
