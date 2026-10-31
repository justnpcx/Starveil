package com.justnpcx.starveil.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.crypto.LocalApiTokenStore
import com.justnpcx.starveil.core.data.diagnostics.DiagnosticBundle
import com.justnpcx.starveil.core.data.diagnostics.DiagnosticInput
import com.justnpcx.starveil.core.data.geo.DnsMetrics
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.LogStream
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The diagnostic bundle, built on demand.
 *
 * This used to be a method on [SettingsViewModel], because the row that
 * produced it lived in Settings. The row lives on the hub now, so the bundle
 * has a view model of its own — and Settings stops carrying a profile reader, a
 * group reader, a rule reader, a log stream and the DNS ring to serve a button
 * it no longer shows.
 *
 * Nothing is written anywhere: [build] hands the text back and the caller shows
 * it in a preview. The file, the share sheet and the clipboard are all one tap
 * further on, and only after the user has read what is about to leave.
 */
@HiltViewModel
class DiagnosticExportViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val kernel: KernelBridge,
    private val metrics: DnsMetrics,
    private val profiles: ProfileRepository,
    private val groups: GroupRepository,
    private val rules: RuleRepository,
    private val logs: LogStream,
    private val localApiToken: LocalApiTokenStore,
) : ViewModel() {

    private val _bundle = MutableStateFlow<String?>(null)

    /** The text to preview, or null while nothing has been built. */
    val bundle: StateFlow<String?> = _bundle.asStateFlow()

    fun build(appVersion: String) {
        viewModelScope.launch {
            val nodeList = profiles.observeAll().first()
            val groupList = groups.observeAll().first()
            val ruleList = rules.observeAllOrdered().first()
            val token = localApiToken.token()
            val leak = preferences.lastLeakCheck.first()
            // The ring is oldest-first; the bundle keeps the most recent
            // hundred, in the order they happened.
            val logLines = logs.lines.first()
                .takeLast(MAX_LOG_LINES)
                .map { line ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date(line.timestampMs))
                    time + " " + line.level.name.take(1) + " " + line.message
                }

            val input = DiagnosticInput(
                appVersion = appVersion,
                kernelVersion = kernel.version(),
                androidSdk = android.os.Build.VERSION.SDK_INT,
                dns = metrics.snapshot(),
                leakCheckedAtMs = leak.checkedAtEpochMs,
                leakLeaking = leak.leaking,
                nodeCount = nodeList.size,
                groupCount = groupList.size,
                ruleCount = ruleList.size,
                routePreset = preferences.routePreset.first().name.lowercase(),
                killSwitchMode = preferences.killSwitchMode.first().name.lowercase(),
                localApiEnabled = preferences.localApiEnabled.first(),
                perAppDirectCount = preferences.perAppRoutes.first().size,
                dnsMetricsEnabled = preferences.dnsMetricsEnabled.first(),
                logLines = logLines,
                // The exact values this device must never publish. Servers,
                // subscription URLs and the API token are the three that the
                // shapes alone cannot reliably catch.
                secrets = buildList {
                    nodeList.forEach { add(it.server) }
                    groupList.forEach { group -> group.subscriptionUrl?.let { add(it) } }
                    if (token.isNotBlank()) add(token)
                },
            )
            _bundle.value = DiagnosticBundle.build(input)
        }
    }

    fun clear() {
        _bundle.value = null
    }

    private companion object {
        /** Enough to see the last failure, small enough to paste. */
        const val MAX_LOG_LINES = 100
    }
}
