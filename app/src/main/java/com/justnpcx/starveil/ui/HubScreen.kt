package com.justnpcx.starveil.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.BuildConfig
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.ui.R as UiR
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.HubTone
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * The hub: one card, five ways out.
 *
 * Everything the bar does not list lives here, and nothing else does. The page
 * used to be a brand header, a status line and a version footer around this
 * list; all three said something another page says better, and a hub whose job
 * is "where else can I go" should be a list of places.
 */
@Composable
fun HubScreen(
    onOpenScenes: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = StarveilSpacing.pageMargin,
                end = StarveilSpacing.pageMargin,
                top = StarveilSpacing.md,
            ),
    ) {
        StarveilCard {
            HubRow(
                icon = StarveilIcons.Scenes,
                tone = StarveilColors.hubScenes,
                title = stringResource(R.string.nav_scenes),
                subtitle = stringResource(R.string.hub_scenes_sub),
                onClick = onOpenScenes,
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
            HubRow(
                icon = StarveilIcons.Activity,
                tone = StarveilColors.hubDiagnostics,
                title = stringResource(R.string.hub_diagnostics_title),
                subtitle = stringResource(R.string.hub_diagnostics_sub),
                onClick = onOpenDiagnostics,
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
