package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.R
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * A page's title row: the title, and the actions that belong to the page.
 *
 * Hand-rolled rather than a TopAppBar because the bar's own insets and heights
 * put the title somewhere the 8dp grid does not reach, and every page here has
 * the same two things in the same place.
 */
@Composable
fun StarveilPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = StarveilSpacing.pageMargin,
                end = StarveilSpacing.md,
                top = StarveilSpacing.xl,
                bottom = StarveilSpacing.lg,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = StarveilType.title,
            color = StarveilColors.ink,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** An icon action in a header: 24dp glyph, 48dp target. */
@Composable
fun StarveilIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = StarveilColors.ink,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            // A 48dp square is the touch target, not the shape: without the
            // clip the ripple fills the square and the corners show.
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The search field: a glass capsule, as the demo draws it — the thinner 55%
 * material, a 1dp white edge, and a magnifier that is never a button.
 */
@Composable
fun StarveilSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(StarveilShapes.search)
            .background(StarveilColors.card)
            .border(
                1.dp,
                if (focused) StarveilColors.accent else StarveilColors.line,
                StarveilShapes.search,
            )
            .padding(horizontal = StarveilSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = StarveilIcons.Search,
            contentDescription = null,
            tint = StarveilColors.ink2,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = StarveilType.body,
                    color = StarveilColors.ink3,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = StarveilType.body.copy(color = StarveilColors.ink),
                cursorBrush = SolidColor(StarveilColors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        // A clear button, because a search field without one leaves the only
        // way out as the keyboard's backspace — and on a long query that is a
        // keypress per character. Hidden while empty so it does not look like a
        // control that does nothing.
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = StarveilIcons.Close,
                    contentDescription = stringResource(R.string.core_search_clear),
                    tint = StarveilColors.ink2,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * The floating add button: a glass disc with an accent edge and an ink plus.
 *
 * The edge is one of the accent's three appearances on the Nodes screen, which
 * is the budget. It sits over the list, so every list that carries one pads
 * its last row by [StarveilSpacing.fabClearance].
 */
@Composable
fun StarveilFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(
                6.dp,
                CircleShape,
                // A little stronger than a card's: it floats.
                ambientColor = StarveilColors.shadowTint.copy(alpha = 0.10f),
                spotColor = StarveilColors.shadowTint.copy(alpha = 0.10f),
            )
            .clip(CircleShape)
            .background(StarveilColors.surfaceGlass)
            .border(1.dp, StarveilColors.accent, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = StarveilIcons.Add,
            contentDescription = contentDescription,
            tint = StarveilColors.ink,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * A group caption: the mono face, uppercase, secondary ink.
 *
 * One per card in Settings, and nowhere else — a caption is what turns a list
 * into a group, and a page that captions everything has no groups.
 */
@Composable
fun StarveilGroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = StarveilType.tech,
        color = StarveilColors.ink2,
        modifier = modifier.padding(
            start = StarveilSpacing.pageMargin,
            end = StarveilSpacing.pageMargin,
            // 16dp between a group and the one before it: 32 read as a gap in
            // the page rather than a break between groups.
            top = StarveilSpacing.md,
            bottom = StarveilSpacing.sm,
        ),
    )
}

/** A row of chips, scrolling horizontally when it overflows. */
@Composable
fun StarveilChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = StarveilSpacing.pageMargin, vertical = StarveilSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
        content = content,
    )
}
