package com.justnpcx.starveil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * What the app does with data, stated in the app rather than only in a file.
 *
 * A policy behind a link is a policy nobody reads. This one is short enough to
 * be a screen, and it is written as claims that can be checked against the
 * code: no account, no analytics, one crash report and a switch to turn it off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen() {
    // No bar of its own: the shell draws the title and the arrow back to the
    // page this one was opened from.
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = StarveilSpacing.lg,
                    end = StarveilSpacing.lg,
                    bottom = StarveilSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(StarveilSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.privacy_intro),
                style = StarveilType.body,
                color = StarveilColors.ink,
            )
            PolicySection(R.string.privacy_data_title, R.string.privacy_data_body)
            PolicySection(R.string.privacy_crash_title, R.string.privacy_crash_body)
            PolicySection(R.string.privacy_storage_title, R.string.privacy_storage_body)
            PolicySection(R.string.privacy_network_title, R.string.privacy_network_body)
            PolicySection(R.string.privacy_permissions_title, R.string.privacy_permissions_body)
            PolicySection(R.string.privacy_backup_title, R.string.privacy_backup_body)
            PolicySection(R.string.privacy_removal_title, R.string.privacy_removal_body)
            PolicySection(R.string.privacy_contact_title, R.string.privacy_contact_body)
        }
    }
}

@Composable
private fun PolicySection(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.xs)) {
        Text(
            text = stringResource(title),
            style = StarveilType.headline,
            color = StarveilColors.ink,
        )
        Text(
            text = stringResource(body),
            style = StarveilType.label,
            color = StarveilColors.ink2,
        )
    }
}
