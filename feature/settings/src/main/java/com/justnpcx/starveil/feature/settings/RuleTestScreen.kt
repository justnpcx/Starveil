package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.data.ruleset.MainlandRuleSetStore
import com.justnpcx.starveil.core.kernel.config.ConfigBuilder
import com.justnpcx.starveil.core.kernel.config.DecisionReason
import com.justnpcx.starveil.core.kernel.config.RouteEvaluator
import com.justnpcx.starveil.core.kernel.config.RuleDryRun
import com.justnpcx.starveil.core.kernel.ruleset.Srs
import com.justnpcx.starveil.core.kernel.ruleset.SrsRuleSetLookup
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilChip
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.util.readClipboardText
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

@HiltViewModel
class RuleTestViewModel @Inject constructor(
    private val store: MainlandRuleSetStore,
    private val builder: ConfigBuilder,
    private val preferences: PreferencesRepository,
    private val ruleRepository: RuleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RuleTestUiState>(RuleTestUiState.Loading)
    val state = _state.asStateFlow()

    private var lookup: SrsRuleSetLookup? = null
    private var rules: List<Map<String, Any?>> = emptyList()
    private var finalTag: String = "proxy"

    init {
        viewModelScope.launch { load() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleTestScreen(
    viewModel: RuleTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = StarveilSpacing.pageMargin,
                end = StarveilSpacing.pageMargin,
                bottom = StarveilSpacing.navContentInset,
            ),
    ) {
            Text(
                text = stringResource(R.string.rule_test_intro),
                style = StarveilType.label,
                color = StarveilColors.ink2,
                modifier = Modifier.padding(bottom = StarveilSpacing.lg),
            )
            // Said before the answer, not after it: a result that quietly
            // ignored the user's own rules would be worse than no result.
            Text(
                text = stringResource(R.string.rule_test_scope),
                style = StarveilType.tech,
                color = StarveilColors.ink3,
                modifier = Modifier.padding(bottom = StarveilSpacing.md),
            )
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
            Spacer(modifier = Modifier.height(StarveilSpacing.md))
            // Three addresses to try, so the page can be understood without
            // typing anything.
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            Spacer(modifier = Modifier.height(StarveilSpacing.lg))

            // The result area takes the rest of the page. Without it the top
            // half had content and the bottom half was blank, which reads as a
            // screen that failed to finish loading.
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = StarveilSpacing.lg)) {
                ResultArea(state = state)
            }
    }
}

/** The verdict, or the sentence that says where it will appear. */
@Composable
private fun ResultArea(state: RuleTestUiState, modifier: Modifier = Modifier) {
    when (state) {
        RuleTestUiState.Loading -> PlaceholderCard(
            text = stringResource(R.string.rule_test_loading),
            modifier = modifier,
        )

        is RuleTestUiState.Failed -> PlaceholderCard(
            text = stringResource(R.string.rule_test_failed, state.message),
            modifier = modifier,
            error = true,
        )

        is RuleTestUiState.Ready -> when {
            // No sets on this device: every answer would be "final", which
            // reads like "no rule matched" when the truth is "there are no
            // rules to match". Say which it is.
            state.tags.isEmpty() -> PlaceholderCard(
                text = stringResource(R.string.rule_test_no_sets),
                modifier = modifier,
            )

            state.result == null -> PlaceholderCard(
                text = stringResource(R.string.rule_test_placeholder),
                modifier = modifier,
            )

            else -> StarveilCard(modifier = modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(StarveilSpacing.lg),
                ) {
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
}

/** The empty state of the result area: it holds the space, and says what goes there. */
@Composable
private fun PlaceholderCard(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    StarveilCard(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(StarveilSpacing.lg),
            contentAlignment = Alignment.TopStart,
        ) {
            Text(
                text = text,
                style = StarveilType.label,
                color = if (error) StarveilColors.errText else StarveilColors.ink2,
            )
        }
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

