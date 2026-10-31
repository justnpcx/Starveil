package com.justnpcx.starveil.feature.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.ui.components.StarveilButton
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.components.StarveilSegmented
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * Creating or editing one rule, as a page.
 *
 * It used to be an AlertDialog, which is the wrong shape for a form with a
 * four-way choice and a text field: the matcher chips wrapped to their own
 * widths inside a narrow column, the hint broke mid-sentence, and the page's
 * floating button peeked out from behind the scrim. The node editor has been a
 * full page for a long time; a rule is the same size of form, so it gets the
 * same treatment — own top bar, own bottom bar, nothing else on screen.
 */
@Composable
fun RuleEditorScreen(
    /** 0 for a new rule; otherwise the row being edited. */
    ruleId: Long,
    onDone: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val existing = remember(state.rules, ruleId) {
        if (ruleId == 0L) null else state.rules.firstOrNull { it.id == ruleId }
    }

    var kind by remember { mutableStateOf(MatcherKind.DOMAIN_SUFFIX) }
    var value by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(RuleAction.PROXY) }
    // Seeded once, when the row arrives: re-seeding on every emission would
    // undo whatever the user has typed since.
    var seeded by remember { mutableStateOf(ruleId == 0L) }
    LaunchedEffect(existing) {
        val rule: Rule = existing ?: return@LaunchedEffect
        if (!seeded) {
            kind = rule.matcher.kind
            value = rule.matcher.value
            action = rule.action
            seeded = true
        }
    }

    fun save() {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        if (existing == null) {
            viewModel.add(kind, trimmed, action)
        } else {
            viewModel.update(existing, kind, trimmed, action)
        }
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StarveilColors.bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = StarveilSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StarveilIconAction(
                icon = StarveilIcons.Back,
                contentDescription = stringResource(R.string.rules_cancel),
                onClick = onDone,
                tint = StarveilColors.ink,
            )
            Text(
                text = stringResource(
                    if (existing == null) R.string.rules_new else R.string.rules_edit_title,
                ),
                style = StarveilType.pageTitle,
                color = StarveilColors.ink,
                maxLines = 1,
                modifier = Modifier.padding(start = StarveilSpacing.xs),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StarveilSpacing.pageMargin),
        ) {
            SectionLabel(stringResource(R.string.rules_match_label))
            MatcherGrid(selected = kind, onSelect = { kind = it })

            Spacer(modifier = Modifier.height(StarveilSpacing.lg))

            StarveilTextField(
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.rules_field_value),
                placeholder = valueExample(kind),
                supportingText = valueHint(kind),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(StarveilSpacing.lg))

            SectionLabel(stringResource(R.string.rules_action_label))
            StarveilSegmented(
                options = RuleAction.entries.map { actionLabel(it) },
                selectedIndex = RuleAction.entries.indexOf(action),
                onSelect = { action = RuleAction.entries[it] },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(StarveilSpacing.xxl))
        }

        // The two decisions, at the bottom, equally weighted: a form whose
        // buttons are text in a corner makes the reader hunt for the one they
        // came to press.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    top = StarveilSpacing.md,
                    bottom = StarveilSpacing.md,
                ),
            horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
        ) {
            StarveilButton(
                text = stringResource(R.string.rules_cancel),
                secondary = true,
                onClick = onDone,
                modifier = Modifier.weight(1f),
            )
            StarveilButton(
                text = stringResource(R.string.rules_save),
                confirm = true,
                enabled = value.isNotBlank(),
                onClick = { save() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A heading above a block of the form. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = StarveilType.label.copy(fontWeight = FontWeight.SemiBold),
        color = StarveilColors.ink2,
        modifier = Modifier.padding(top = StarveilSpacing.md, bottom = StarveilSpacing.sm),
    )
}

/**
 * The four matcher kinds, two by two, every tile the same width.
 *
 * Chips sized to their own text produced a ragged block with holes in it; a
 * grid says these are four equal choices, which is what they are.
 */
@Composable
private fun MatcherGrid(selected: MatcherKind, onSelect: (MatcherKind) -> Unit) {
    val kinds = MatcherKind.entries
    Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
        kinds.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                pair.forEach { kind ->
                    MatcherTile(
                        label = kindLabel(kind),
                        selected = kind == selected,
                        onClick = { onSelect(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MatcherTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) StarveilColors.selBg else StarveilColors.card)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) StarveilColors.accent else StarveilColors.line,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = StarveilType.body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) StarveilColors.selInk else StarveilColors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** The placeholder: what a value of this kind looks like. */
@Composable
private fun valueExample(kind: MatcherKind): String = stringResource(
    when (kind) {
        MatcherKind.DOMAIN -> R.string.rules_value_example_domain
        MatcherKind.DOMAIN_SUFFIX -> R.string.rules_value_example_suffix
        MatcherKind.DOMAIN_KEYWORD -> R.string.rules_value_example_keyword
        MatcherKind.IP_CIDR -> R.string.rules_value_example_cidr
    },
)

/** The hint: one line, phrased to fit the width of the field. */
@Composable
private fun valueHint(kind: MatcherKind): String = stringResource(
    when (kind) {
        MatcherKind.DOMAIN -> R.string.rules_hint_domain
        MatcherKind.DOMAIN_SUFFIX -> R.string.rules_hint_suffix
        MatcherKind.DOMAIN_KEYWORD -> R.string.rules_hint_keyword
        MatcherKind.IP_CIDR -> R.string.rules_hint_cidr
    },
)
