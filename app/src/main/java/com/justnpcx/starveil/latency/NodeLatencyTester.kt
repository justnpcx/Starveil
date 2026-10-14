package com.justnpcx.starveil.latency

import android.content.Context
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.UrlTestResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Measures the latency of the currently selected node and stores it.
 *
 * Lives in :app because it needs the kernel, which :feature:* must not touch.
 * The Nodes screen only receives a callback.
 *
 * Always returns a user-facing message, so a failed measurement is never
 * silent — "nothing happened" was the previous behaviour and read as a broken
 * button.
 */
@Singleton
class NodeLatencyTester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kernel: KernelBridge,
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
) {

    suspend fun testSelectedNode(): String {
        val id = preferences.selectedProfileId.first()
        if (id == 0L) return context.getString(R.string.latency_no_node)
        val profile = profiles.getById(id) ?: return context.getString(R.string.latency_no_node)

        return when (val result = kernel.urlTest(SINGLE_NODE_TAG)) {
            is UrlTestResult.Success -> {
                profiles.upsert(profile.copy(latencyMs = result.latencyMs))
                context.getString(R.string.latency_measured, result.latencyMs)
            }

            UrlTestResult.Timeout -> context.getString(R.string.latency_timeout)
            UrlTestResult.Unreachable -> context.getString(R.string.latency_unreachable)
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
