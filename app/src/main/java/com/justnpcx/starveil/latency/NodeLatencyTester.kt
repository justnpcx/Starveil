package com.justnpcx.starveil.latency

import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.UrlTestResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Measures the latency of the currently selected node and stores it.
 *
 * Lives in :app because it needs the kernel, which :feature:* must not touch.
 * The Nodes screen only receives a `() -> Unit` callback.
 *
 * It tests the outbound tag `proxy` — the tag ConfigBuilder assigns in the
 * single-node config the Dashboard starts. If the kernel is not running the
 * bridge returns Unreachable, so this reports "not measured" rather than a
 * fabricated number.
 */
@Singleton
class NodeLatencyTester @Inject constructor(
    private val kernel: KernelBridge,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
) {

    /** @return the measured ms, or null when nothing could be measured. */
    suspend fun testSelectedNode(): Long? {
        val id = preferences.selectedProfileId.first()
        if (id == 0L) return null
        val profile = profiles.getById(id) ?: return null

        return when (val result = kernel.urlTest(SINGLE_NODE_TAG)) {
            is UrlTestResult.Success -> {
                profiles.upsert(profile.copy(latencyMs = result.latencyMs))
                result.latencyMs
            }

            UrlTestResult.Timeout, UrlTestResult.Unreachable -> null
        }
    }

    private companion object {
        /** ConfigBuilder.PROXY_TAG, mirrored so :app does not reach into it. */
        const val SINGLE_NODE_TAG = "proxy"
    }
}

/** Hilt bridge for the Composable that needs it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NodeLatencyTesterEntryPoint {
    fun nodeLatencyTester(): NodeLatencyTester
}
