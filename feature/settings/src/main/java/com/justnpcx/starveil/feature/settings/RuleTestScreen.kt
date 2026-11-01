package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.geo.DnsMetrics
import com.justnpcx.starveil.core.data.geo.DnsSnapshot
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.data.ruleset.MainlandRuleSetStore
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.config.DecisionReason
import com.justnpcx.starveil.core.kernel.config.RouteEvaluator
import com.justnpcx.starveil.core.kernel.config.RuleDryRun
import com.justnpcx.starveil.core.kernel.ruleset.SrsRuleSetLookup
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.ruleset.Srs
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilChip
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.components.StarveilSwitch
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.core.ui.util.readClipboardText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the screen has, and what it is waiting for. */
sealed interface RuleTestUiState {
    data object Loading : RuleTestUiState

    /** [tags] are the sets that loaded; a missing one simply cannot match. */
    data class Ready(
        val tags: List<String>,
        val result: RuleTestResult? = null,
        val error: String? = null,
    ) : RuleTestUiState

    data class Failed(val message: String) : RuleTestUiState
}

data class RuleTestResult(
    val query: String,
    val rule: String,
    val outbound: String,
    val reason: DecisionReason,
    val index: Int?,
)

/**
 * The two things on this page that are not the rule test: the last self-check,
 * and the DNS ring.
 *
 * They are here because this page is where the diagnostics live now. They used
 * to be rows in Settings, which meant the page that answers "what is going on"
 * was split in two, one half of it behind a screen full of switches.
 */
data class DiagnosticsState(
    val lastLeakCheck: LeakCheckRecord = LeakCheckRecord(),
    val dnsMetricsEnabled: Boolean = true,
    val dnsStats: DnsSnapshot = DnsSnapshot.EMPTY,
)

@HiltViewModel
class RuleTestViewModel @Inject constructor(
    private val store: MainlandRuleSetStore,
    private val builder: ConfigBuilder,
    private val preferences: PreferencesRepository,
    private val ruleRepository: RuleRepository,
    private val metrics: DnsMetrics,
) : ViewModel() {

    private val _state = MutableStateFlow<RuleTestUiState>(RuleTestUiState.Loading)
    val state = _state.asStateFlow()

    private val dnsStats = MutableStateFlow(DnsSnapshot.EMPTY)

    private var lookup: SrsRuleSetLookup? = null
    private var rules: List<Map<String, Any?>> = emptyList()
    private var finalTag: String = "proxy"

    /**
     * The switch decides whether the ring records; the metric itself has no
     * opinion, so the stored value is applied here before the first sample.
     */
    val diagnostics: StateFlow<DiagnosticsState> = combine(
        preferences.lastLeakCheck,
        preferences.dnsMetricsEnabled,
        dnsStats,
    ) { leak, enabled, stats ->
        DiagnosticsState(lastLeakCheck = leak, dnsMetricsEnabled = enabled, dnsStats = stats)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiagnosticsState(),
    )

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            metrics.enabled = preferences.dnsMetricsEnabled.first()
        }
    }

    /** Read the ring into the card; the page calls this on a slow tick. */
    fun refreshDnsStats() {
        dnsStats.value = metrics.snapshot()
    }

    fun setDnsMetricsEnabled(enabled: Boolean) {
        metrics.enabled = enabled
        viewModelScope.launch { preferences.setDnsMetricsEnabled(enabled) }
    }

    private suspend fun load() {
        try {
            val preset = preferences.routePreset.first()
            val userRules = ruleRepository.observeAllOrdered().first()
            val mainland = store.ensure()
            val ads = store.ensureAds()
            val sets = buildMap {
                ads?.let { put("ads", Srs.read(File(it).readBytes())) }
                mainland?.let {
                    put("geosite-cn", Srs.read(File(it.geositeCnPath).readBytes()))
                    put("geoip-cn", Srs.read(File(it.geoipCnPath).readBytes()))
                }
            }
            lookup = SrsRuleSetLookup(sets)
            finalTag = RuleDryRun.finalTag(preset)
            rules = RuleDryRun.rulesFor(
                builder = builder,
                rules = userRules,
                preset = preset,
                mainland = mainland,
                adsPath = ads,
            )
            _state.value = RuleTestUiState.Ready(sets.keys.toList())
        } catch (e: Exception) {
            _state.value = RuleTestUiState.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    fun test(raw: String) {
        val current = _state.value as? RuleTestUiState.Ready ?: return
        val lookup = lookup ?: return
        val query = raw.trim()
        if (query.isEmpty()) {
            _state.value = current.copy(error = null, result = null)
            return
        }
        val host = query.substringBefore('/').substringBefore(':')
        if (host.isEmpty()) {
            _state.value = current.copy(error = "empty", result = null)
            return
        }
        val isAddress = host.contains(':') || host.all { it.isDigit() || it == '.' }
        val decision = RouteEvaluator.evaluate(
            rules = rules,
            finalTag = finalTag,
            domain = if (isAddress) null else host.lowercase(),
            ip = if (isAddress) host else null,
            lookup = lookup,
        )
        _state.value = current.copy(
            error = null,
            result = RuleTestResult(
                query = query,
                rule = decision.ruleLabel,
                outbound = decision.outbound,
                reason = decision.reason,
                index = decision.ruleIndex,
            ),
        )
    }
}

/**
 * The diagnostics page: the self-check card, then the DNS card.
 *
 * Two cards rather than one long list because they answer two different
 * questions — "does my rule set send this where I think it does" and "how fast
 * is name resolution" — and a page that mixes them makes both harder to read.
 */
@Composable
fun RuleTestScreen(
    onRunLeakCheck: () -> Unit,
    onOpenDnsLog: () -> Unit,
    viewModel: RuleTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    // The ring only changes while something resolves, so the card reads it on a
    // slow tick rather than making every lookup flow into the UI.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshDnsStats()
            delay(DNS_STATS_REFRESH_MS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = StarveilSpacing.pageMargin,
                end = StarveilSpacing.pageMargin,
                bottom = StarveilSpacing.navContentInset,
            ),
    ) {
        // ---- Self-check ----------------------------------------------------
        StarveilCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
                Text(
                    text = stringResource(R.string.diagnostics_card_check_title),
                    style = StarveilType.headline,
                    color = StarveilColors.ink,
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                Text(
                    text = stringResource(R.string.rule_test_intro),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
                // Said before the answer, not after it: a result that quietly
                // ignored the user's own rules would be worse than no result.
                Spacer(modifier = Modifier.height(StarveilSpacing.sm))
                Text(
                    text = stringResource(R.string.rule_test_scope),
                    style = StarveilType.tech,
                    color = StarveilColors.ink3,
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    StarveilTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.test(it)
                        },
                        label = stringResource(R.string.rule_test_field),
                        supportingText = stringResource(R.string.rule_test_hint),
                        modifier = Modifier.weight(1f),
                    )
                    // Reading the clipboard is an interaction, which is exactly
                    // when Android 10+ will hand it over.
                    StarveilIconAction(
                        icon = StarveilIcons.Clipboard,
                        contentDescription = stringResource(R.string.rule_test_paste),
                        onClick = {
                            val text = readClipboardText(context)?.trim().orEmpty()
                            if (text.isNotEmpty()) {
                                query = text
                                viewModel.test(text)
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(StarveilSpacing.sm))
                // Three addresses to try, so the page can be understood without
                // typing anything. The row scrolls rather than wraps: inside a
                // card the third chip no longer fits, and "youtube. / com" on
                // two lines reads as a broken chip.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.rule_test_try),
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                    )
                    EXAMPLE_DOMAINS.forEach { domain ->
                        StarveilChip(
                            label = domain,
                            selected = false,
                            onClick = {
                                query = domain
                                viewModel.test(domain)
                            },
                        )
                    }
                }
            }
            StarveilDivider(startInset = StarveilSpacing.lg)
            ResultArea(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 148.dp)
                    .padding(StarveilSpacing.lg),
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            // The self-check's own status line, moved here from Settings: the
            // page that runs it is the page that should say when it last ran.
            Row(
                modifier = Modifier.padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.md,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = leakCheckSummary(diagnostics.lastLeakCheck),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.md))
                Text(
                    text = stringResource(R.string.leak_check_run),
                    style = StarveilType.label,
                    color = StarveilColors.accent,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onRunLeakCheck)
                        .padding(vertical = StarveilSpacing.xs),
                )
            }
        }

        Spacer(modifier = Modifier.height(StarveilSpacing.md))

        // ---- DNS -----------------------------------------------------------
        StarveilCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
                Text(
                    text = stringResource(R.string.diagnostics_card_dns_title),
                    style = StarveilType.headline,
                    color = StarveilColors.ink,
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                Text(
                    text = stringResource(R.string.settings_dns_metrics_desc),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = StarveilSpacing.lg,
                        end = StarveilSpacing.lg,
                        bottom = StarveilSpacing.md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_dns_metrics_title),
                    style = StarveilType.body,
                    color = StarveilColors.ink,
                    modifier = Modifier.weight(1f),
                )
                StarveilSwitch(
                    checked = diagnostics.dnsMetricsEnabled,
                    onCheckedChange = viewModel::setDnsMetricsEnabled,
                )
            }
            if (diagnostics.dnsMetricsEnabled) {
                StarveilDivider(startInset = StarveilSpacing.lg)
                // One line of state, not the telemetry: the numbers are one tap
                // away, with the per-query log beside them.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onOpenDnsLog)
                        .padding(
                            start = StarveilSpacing.lg,
                            end = StarveilSpacing.lg,
                            top = StarveilSpacing.md,
                            bottom = StarveilSpacing.md,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_dns_log_title),
                            style = StarveilType.body,
                            color = StarveilColors.ink,
                        )
                        Text(
                            text = dnsStatusLine(diagnostics.dnsStats),
                            style = StarveilType.label,
                            color = StarveilColors.ink2,
                        )
                    }
                    Spacer(modifier = Modifier.width(StarveilSpacing.sm))
                    Icon(
                        imageVector = StarveilIcons.Chevron,
                        contentDescription = null,
                        tint = StarveilColors.ink2,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** The verdict, or the sentence that says where it will appear. */
@Composable
private fun ResultArea(state: RuleTestUiState, modifier: Modifier = Modifier) {
    when (state) {
        RuleTestUiState.Loading -> Placeholder(
            text = stringResource(R.string.rule_test_loading),
            modifier = modifier,
        )

        is RuleTestUiState.Failed -> Placeholder(
            text = stringResource(R.string.rule_test_failed, state.message),
            modifier = modifier,
            error = true,
        )

        is RuleTestUiState.Ready -> when {
            // No sets on this device: every answer would be "final", which
            // reads like "no rule matched" when the truth is "there are no
            // rules to match". Say which it is.
            state.tags.isEmpty() -> Placeholder(
                text = stringResource(R.string.rule_test_no_sets),
                modifier = modifier,
            )

            state.result == null -> Placeholder(
                text = stringResource(R.string.rule_test_placeholder),
                modifier = modifier,
            )

            else -> Column(modifier = modifier) {
                Text(
                    text = outboundText(state.result.outbound),
                    style = StarveilType.display,
                    color = outcomeColour(state.result.outbound),
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.sm))
                ResultLine(stringResource(R.string.rule_test_rule), state.result.rule)
                StarveilDivider(startInset = 0.dp)
                ResultLine(stringResource(R.string.rule_test_reason), reasonText(state.result.reason))
                StarveilDivider(startInset = 0.dp)
                ResultLine(
                    stringResource(R.string.rule_test_sets_label),
                    state.tags.joinToString(", "),
                )
            }
        }
    }
}

/** The empty state of the result area: it holds the space, and says what goes there. */
@Composable
private fun Placeholder(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
        Text(
            text = text,
            style = StarveilType.label,
            color = if (error) StarveilColors.errText else StarveilColors.ink2,
        )
    }
}

/** The verdict in colour: direct is healthy, block is a failure, proxy is the accent. */
@Composable
private fun outcomeColour(outbound: String): Color = when (outbound) {
    "reject" -> StarveilColors.err
    "direct" -> StarveilColors.ok
    else -> StarveilColors.accent
}

/** The three addresses the chips offer. */
private val EXAMPLE_DOMAINS = listOf("google.com", "baidu.com", "youtube.com")

@Composable
private fun ResultLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = StarveilSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = StarveilType.label, color = StarveilColors.ink2)
        Text(text = value, style = StarveilType.body, color = StarveilColors.ink)
    }
}

@Composable
private fun outboundText(outbound: String): String = when (outbound) {
    "reject" -> stringResource(R.string.rule_test_outbound_reject)
    "direct" -> stringResource(R.string.rule_test_outbound_direct)
    else -> stringResource(R.string.rule_test_outbound_proxy)
}

@Composable
private fun reasonText(reason: DecisionReason): String = when (reason) {
    DecisionReason.FIRST_MATCH -> stringResource(R.string.rule_test_reason_first)
    DecisionReason.PRESET_FALLBACK -> stringResource(R.string.rule_test_reason_preset)
    DecisionReason.DEFAULT_OUTBOUND -> stringResource(R.string.rule_test_reason_default)
}

/** "No self-check has run yet", or when the last one ran and what it found. */
@Composable
private fun leakCheckSummary(record: LeakCheckRecord): String =
    if (record.checkedAtEpochMs <= 0L) {
        stringResource(R.string.leak_check_never)
    } else {
        val when_ = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(record.checkedAtEpochMs))
        stringResource(
            if (record.leaking) R.string.leak_check_leaking else R.string.leak_check_clean,
            when_,
        )
    }

/** The DNS summary line: one number, the budget, and how many samples. */
@Composable
private fun dnsStatusLine(stats: DnsSnapshot): String = when {
    stats.samples == 0 -> stringResource(R.string.settings_dns_metrics_empty)
    stats.p50Ms <= DNS_TARGET_MS -> stringResource(R.string.settings_dns_log_ok, stats.p50Ms, stats.samples)
    else -> stringResource(R.string.settings_dns_log_slow, stats.p50Ms, stats.samples)
}

/** The self-check's budget, in milliseconds. */
private const val DNS_TARGET_MS = 30L

/** Slow tick for the DNS card: the ring only moves when a name is resolved. */
private const val DNS_STATS_REFRESH_MS = 2_000L
