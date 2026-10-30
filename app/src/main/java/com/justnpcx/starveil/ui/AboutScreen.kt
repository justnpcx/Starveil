package com.justnpcx.starveil.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.BuildConfig
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.ui.R as UiR
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import com.justnpcx.starveil.feature.settings.SettingsViewModel
import com.justnpcx.starveil.feature.settings.UpdateUiState

/**
 * About: the mark, the name, and the four things a version is asked about.
 *
 * A page rather than a card at the bottom of the settings, because it is where
 * the brand, the version and the licence live together, and the settings page
 * is about what the app does rather than what it is.
 */
@Composable
fun AboutScreen(
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    var showFeatures by remember { mutableStateOf(false) }
    var showLicences by remember { mutableStateOf(false) }

    // Asked once, when the page opens: the row's job is to answer "am I on the
    // latest build", and a row that says nothing until it is tapped answers
    // nothing at a glance.
    LaunchedEffect(Unit) {
        if (update is UpdateUiState.Idle) {
            viewModel.checkForUpdates(BuildConfig.VERSION_NAME)
        }
    }

    val kernel = state.kernelVersion
        .takeIf { it.isNotBlank() }
        ?.substringBefore('-')
        ?: "1.14.3"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StarveilSpacing.pageMargin),
    ) {
        Spacer(modifier = Modifier.height(StarveilSpacing.xl))

        // The brand: the mark at 84dp, then the name as one lock-up. The two
        // words are one brand, so they are one string and one line.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(UiR.drawable.ic_starveil_mark),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
            )
            Spacer(modifier = Modifier.height(StarveilSpacing.md))
            Text(
                text = stringResource(R.string.about_name),
                style = StarveilType.pageTitle,
                color = StarveilColors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(StarveilSpacing.xs))
            Text(
                text = stringResource(R.string.about_version_line, BuildConfig.VERSION_NAME),
                style = StarveilType.label,
                color = StarveilColors.ink2,
            )
        }

        Spacer(modifier = Modifier.height(StarveilSpacing.xl))

        StarveilCard {
            AboutRow(
                icon = StarveilIcons.Refresh,
                title = stringResource(R.string.about_update_title),
                value = updateText(update),
                onClick = { viewModel.checkForUpdates(BuildConfig.VERSION_NAME) },
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            AboutRow(
                icon = StarveilIcons.StarOutline,
                title = stringResource(R.string.about_features_title),
                onClick = { showFeatures = true },
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            AboutRow(
                icon = StarveilIcons.Nodes,
                title = stringResource(R.string.about_privacy_title),
                onClick = onOpenPrivacy,
            )
            StarveilDivider(startInset = StarveilSpacing.lg)
            AboutRow(
                icon = StarveilIcons.List,
                title = stringResource(R.string.about_licences_title),
                onClick = { showLicences = true },
            )
        }

        Spacer(modifier = Modifier.height(StarveilSpacing.xl))

        // The footer: what the app is built on, and who made it. Two lines,
        // centred, secondary grade — a colophon, not a section.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.about_footer_kernel, kernel),
                style = StarveilType.label,
                color = StarveilColors.ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(StarveilSpacing.xs))
            Text(
                text = stringResource(R.string.about_footer_copyright),
                style = StarveilType.label,
                color = StarveilColors.ink2,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(StarveilSpacing.xxl))
    }

    if (showFeatures) {
        TextDialog(
            title = stringResource(R.string.about_features_title),
            body = stringResource(R.string.about_features_body),
            onDismiss = { showFeatures = false },
        )
    }

    if (showLicences) {
        TextDialog(
            title = stringResource(R.string.about_licences_title),
            body = stringResource(R.string.about_licences_body),
            onDismiss = { showLicences = false },
        )
    }
}

/** What the update row says on the right, in one word or one number. */
@Composable
private fun updateText(state: UpdateUiState): String = when (state) {
    is UpdateUiState.Checking, is UpdateUiState.Downloading ->
        stringResource(R.string.about_update_checking)
    is UpdateUiState.UpToDate -> stringResource(R.string.about_update_latest)
    is UpdateUiState.Available ->
        stringResource(R.string.about_update_available, state.info.version)
    is UpdateUiState.Failed, is UpdateUiState.DownloadFailed ->
        stringResource(R.string.about_update_failed)
    is UpdateUiState.Ready -> stringResource(R.string.about_update_ready)
    else -> ""
}

/** One line of the card: glyph, title, optional value, chevron. */
@Composable
private fun AboutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String = "",
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
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = StarveilColors.ink2,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Text(
            text = title,
            style = StarveilType.body,
            color = StarveilColors.ink,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = StarveilType.label,
                color = StarveilColors.ink2,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.xs))
        }
        androidx.compose.material3.Icon(
            imageVector = StarveilIcons.Chevron,
            contentDescription = null,
            tint = StarveilColors.ink2,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A scrollable block of text in a dialog, for the two read-only entries. */
@Composable
private fun TextDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = StarveilColors.card,
        shape = StarveilShapes.dialog,
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = StarveilType.headline, color = StarveilColors.ink) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                Text(text = body, style = StarveilType.body, color = StarveilColors.ink2)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}
