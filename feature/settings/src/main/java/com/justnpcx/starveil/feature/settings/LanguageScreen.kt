package com.justnpcx.starveil.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/** The languages the app ships, in the order the page lists them. */
private enum class AppLanguage(val tag: String?) {
    /** Follow the device. An empty locale list is how AppCompat spells it. */
    SYSTEM(null),
    CHINESE("zh-CN"),
    ENGLISH("en"),
}

/**
 * The language page.
 *
 * The list is three rows rather than a dropdown because there are three, and a
 * row with a tick is one tap where a menu is two. The choice goes through
 * AppCompat's per-app locale: the same mechanism Android 13 shows under
 * Settings → Apps → Starveil → Language, so the two entry points cannot
 * disagree, and it survives a restart without the app keeping its own copy.
 *
 * The activity is recreated by the platform when the locale changes, which is
 * what makes every screen — including this one — come back in the new language.
 */
@Composable
fun LanguageScreen() {
    val current = remember { AppCompatDelegate.getApplicationLocales() }
    val currentTag = if (current.isEmpty) null else current.get(0)?.language

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StarveilSpacing.pageMargin),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.xs),
    ) {
        Spacer(modifier = Modifier.padding(top = StarveilSpacing.sm))
        AppLanguage.entries.forEach { language ->
            LanguageRow(
                label = stringResource(
                    when (language) {
                        AppLanguage.SYSTEM -> R.string.language_system
                        AppLanguage.CHINESE -> R.string.language_chinese
                        AppLanguage.ENGLISH -> R.string.language_english
                    },
                ),
                selected = when (language) {
                    AppLanguage.SYSTEM -> current.isEmpty
                    AppLanguage.CHINESE -> currentTag?.startsWith("zh") == true
                    AppLanguage.ENGLISH -> currentTag?.startsWith("en") == true
                },
                onSelect = {
                    val locales = language.tag
                        ?.let { LocaleListCompat.forLanguageTags(it) }
                        ?: LocaleListCompat.getEmptyLocaleList()
                    AppCompatDelegate.setApplicationLocales(locales)
                },
            )
        }
        Spacer(modifier = Modifier.padding(bottom = StarveilSpacing.xl))
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = StarveilSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = StarveilColors.accent,
                unselectedColor = StarveilColors.ink2,
            ),
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Text(
            text = label,
            style = StarveilType.body,
            color = StarveilColors.ink,
            modifier = Modifier.weight(1f),
        )
    }
}
