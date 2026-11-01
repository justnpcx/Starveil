package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.model.ChainFrontExclusion
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.chainFrontCandidates
import com.justnpcx.starveil.core.model.chainFrontExclusion
import com.justnpcx.starveil.core.ui.R
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/** The single chain editor used by both the node menu and the node editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainProxySheet(
    exit: Profile,
    all: List<Profile>,
    groupNames: Map<Long, String>,
    onPick: (Profile?) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = exit.frontNodeKey?.let { key -> all.firstOrNull { it.nodeKey == key } }
    val candidates = chainFrontCandidates(exit, all)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StarveilColors.card,
        shape = StarveilShapes.sheet,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.core_chain_title),
                style = StarveilType.headline,
                color = StarveilColors.ink,
                modifier = Modifier.padding(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    top = StarveilSpacing.sm,
                ),
            )
            Text(
                text = stringResource(R.string.core_chain_exit, exit.name),
                style = StarveilType.label,
                color = StarveilColors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    top = StarveilSpacing.xs,
                    bottom = StarveilSpacing.sm,
                ),
            )
            Text(
                text = if (current == null) {
                    stringResource(R.string.core_chain_direct)
                } else {
                    stringResource(R.string.core_chain_path, current.name, exit.name)
                },
                style = StarveilType.tech,
                color = StarveilColors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    bottom = StarveilSpacing.sm,
                ),
            )

            ChainRow(
                title = stringResource(R.string.core_chain_none),
                selected = current == null,
                onClick = { onPick(null) },
            )
            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.core_chain_no_candidates),
                    style = StarveilType.label,
                    color = StarveilColors.ink3,
                    modifier = Modifier.padding(
                        start = StarveilSpacing.pageMargin,
                        end = StarveilSpacing.pageMargin,
                        top = StarveilSpacing.sm,
                        bottom = StarveilSpacing.md,
                    ),
                )
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(candidates) { candidate ->
                    ChainRow(
                        title = candidate.name,
                        subtitle = chainCandidateDetail(candidate, groupNames),
                        selected = candidate.nodeKey == exit.frontNodeKey,
                        onClick = { onPick(candidate) },
                    )
                }
                items(all.filter { chainFrontExclusion(exit, it) != null }) { candidate ->
                    val reason = when (chainFrontExclusion(exit, candidate)) {
                        ChainFrontExclusion.SELF -> R.string.core_chain_excluded_self
                        ChainFrontExclusion.ALREADY_CHAINED -> R.string.core_chain_excluded_chained
                        null -> return@items
                    }
                    ChainRow(
                        title = candidate.name,
                        subtitle = chainCandidateDetail(candidate, groupNames) + " · " + stringResource(reason),
                        enabled = false,
                        onClick = {},
                    )
                }
            }
            if (current != null) {
                Spacer(modifier = Modifier.height(StarveilSpacing.md))
                ChainRow(
                    title = stringResource(R.string.core_chain_clear),
                    destructive = true,
                    onClick = { onPick(null) },
                )
            }
        }
    }
}

@Composable
private fun chainCandidateDetail(candidate: Profile, groupNames: Map<Long, String>): String {
    val group = groupNames[candidate.groupId] ?: stringResource(R.string.core_chain_ungrouped)
    val latency = if (candidate.latencyMs >= 0) {
        stringResource(R.string.core_chain_latency, candidate.latencyMs)
    } else {
        stringResource(R.string.core_chain_latency_untested)
    }
    return "$group · $latency"
}

@Composable
private fun ChainRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        destructive -> StarveilColors.errText
        enabled -> StarveilColors.accent
        else -> StarveilColors.ink3
    }
    Column {
        StarveilDivider(startInset = StarveilSpacing.pageMargin, endInset = StarveilSpacing.pageMargin)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = StarveilSpacing.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected || destructive) {
                Icon(
                    imageVector = if (destructive) StarveilIcons.Close else StarveilIcons.Check,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = StarveilType.body,
                    color = if (enabled) StarveilColors.ink else StarveilColors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = StarveilType.label,
                        color = StarveilColors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
