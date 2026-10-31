package com.justnpcx.starveil.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.BuildConfig
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.ui.R as UiR
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.components.StarveilGroupHeader
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.HubTone
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.core.ui.util.writeClipboard
import com.justnpcx.starveil.feature.settings.DiagnosticExportViewModel
import com.justnpcx.starveil.feature.settings.DiagnosticPreviewDialog

/**
 * The hub: two groups, seven ways out.
 *
 * Everything the bar does not list lives here, and nothing else does. The rows
 * are grouped by what they are for rather than by where they were built: the
 * four that answer "what is going on" — self-check, connections, logs, the
 * bundle — sit together, and the three that are the app itself — scenes,
 * settings, about — sit below them.
 *
 * The split is not decoration. Before it the hub was one list of five, and the
 * two diagnostic screens that already had pages of their own could only be
 * reached from inside Settings. A tool belongs with the tools.
 */
@Composable
fun HubScreen(
    onOpenScenes: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    exportViewModel: DiagnosticExportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val bundle by exportViewModel.bundle.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = StarveilSpacing.xs),
    ) {
        // ---- Diagnostic tools ----------------------------------------------
        StarveilGroupHeader(stringResource(R.string.hub_group_tools))
        StarveilCard(modifier = Modifier.padding(horizontal = StarveilSpacing.pageMargin)) {
            HubRow(
                icon = StarveilIcons.Activity,
                tone = StarveilColors.hubDiagnostics,
                title = stringResource(R.string.hub_diagnostics_title),
                subtitle = stringResource(R.string.hub_diagnostics_sub),
                onClick = onOpenDiagnostics,
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            // Blue, like the log row below it: a connection list is the same
            // kind of object as a log — a stream of what happened, read after
            // the fact — and the two are read together.
            HubRow(
                icon = StarveilIcons.Terminal,
                tone = StarveilColors.hubLogs,
                title = stringResource(R.string.hub_connections_title),
                subtitle = stringResource(R.string.hub_connections_sub),
                onClick = onOpenConnections,
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            HubRow(
                icon = StarveilIcons.List,
                tone = StarveilColors.hubLogs,
                title = stringResource(R.string.nav_logs),
                subtitle = stringResource(R.string.hub_logs_sub),
                onClick = onOpenLogs,
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            // Green, like the self-check: the bundle is that check's output —
            // the same numbers, written down so they can be attached to a
            // report.
            HubRow(
                icon = StarveilIcons.External,
                tone = StarveilColors.hubDiagnostics,
                title = stringResource(R.string.hub_export_title),
                subtitle = stringResource(R.string.hub_export_sub),
                onClick = { exportViewModel.build(BuildConfig.VERSION_NAME) },
            )
        }

        // ---- App -----------------------------------------------------------
        StarveilGroupHeader(stringResource(R.string.hub_group_app))
        StarveilCard(modifier = Modifier.padding(horizontal = StarveilSpacing.pageMargin)) {
            HubRow(
                icon = StarveilIcons.Scenes,
                tone = StarveilColors.hubScenes,
                title = stringResource(R.string.nav_scenes),
                subtitle = stringResource(R.string.hub_scenes_sub),
                onClick = onOpenScenes,
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            HubRow(
                icon = StarveilIcons.Settings,
                tone = StarveilColors.hubSettings,
                title = stringResource(R.string.nav_settings),
                subtitle = stringResource(R.string.hub_settings_sub),
                onClick = onOpenSettings,
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            HubAboutRow(
                title = stringResource(R.string.nav_about),
                subtitle = stringResource(R.string.hub_about_sub),
                version = BuildConfig.VERSION_NAME,
                onClick = onOpenAbout,
            )
        }
        Spacer(modifier = Modifier.size(StarveilSpacing.navContentInset))
    }

    // The preview, not a share sheet: nothing leaves until the text has been
    // read. The row only builds; this dialog is where the decision is made.
    bundle?.let { text ->
        DiagnosticPreviewDialog(
            text = text,
            onCopy = {
                writeClipboard(context, text, "Starveil diagnostics")
                exportViewModel.clear()
            },
            onDismiss = { exportViewModel.clear() },
        )
    }
}

/** One destination: a tinted square, its name, what is behind it, a chevron. */
@Composable
private fun HubRow(
    icon: ImageVector,
    tone: HubTone,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBed(tone = tone.bg) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone.ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = StarveilType.headline,
                color = StarveilColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = StarveilType.label,
                color = StarveilColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/**
 * The about row: the C2 mark, and the version at the end of the line.
 *
 * The version is here rather than in a footer because this is the row that
 * leads to the page where the version is explained; a second copy at the
 * bottom of the hub was the third place the same string appeared.
 */
@Composable
private fun HubAboutRow(
    title: String,
    subtitle: String,
    version: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBed(tone = StarveilColors.hubAboutBed) {
            Image(
                painter = painterResource(UiR.drawable.ic_starveil_mark),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = StarveilType.headline,
                color = StarveilColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = StarveilType.label,
                color = StarveilColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        Text(
            text = version,
            style = StarveilType.label,
            color = StarveilColors.ink2,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.xs))
        Icon(
            imageVector = StarveilIcons.Chevron,
            contentDescription = null,
            tint = StarveilColors.ink2,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The 38dp bed every row's glyph sits on. */
@Composable
private fun IconBed(tone: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tone),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
