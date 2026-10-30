package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilBrand
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * The one "create" control, and the one panel it opens.
 *
 * Every list the user can add to — nodes, rules, scenes — used to spell its own
 * entry point: a speed-dial of pills here, a text button in the page header
 * there. Three pages, three shapes, and none of them the shape a modern client
 * uses for "bring something in".
 *
 * The pills were the wrong reference to begin with. A speed-dial is for
 * creating siblings of one kind of thing (a calendar event, a task, a
 * reminder). Importing a node is the opposite problem: several channels, one
 * destination. So the button is now plain, and the panel it opens is the same
 * panel on all three pages, with only its content swapped.
 *
 * The panel is a bottom sheet at *content* height — capped at 60% of the
 * screen — not a full-screen page and not a centred dialog. Chaining a pill to
 * a dialog to a page was the step count the user complained about.
 */

/**
 * The panel itself.
 *
 * The scrim is drawn by [ModalBottomSheet] over the whole window, which is the
 * point: the old implementation dimmed the page but left the top bar bright, so
 * the screen looked like two surfaces disagreeing. The sheet's own height is
 * wrap-content up to [MAX_SHEET_FRACTION] of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarveilAddSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * MAX_SHEET_FRACTION
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = StarveilShapes.sheet,
        containerColor = StarveilColors.card,
        contentColor = StarveilColors.ink,
        scrimColor = if (StarveilColors.isDark) SCRIM_DARK else SCRIM_LIGHT,
        dragHandle = { BottomSheetDefaults.DragHandle(color = StarveilColors.chipB) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StarveilSpacing.pageMargin)
                .navigationBarsPadding()
                .padding(bottom = StarveilSpacing.lg),
            content = content,
        )
    }
}

/**
 * The three primary channels, side by side.
 *
 * Equal height, one row, icon over title over subtitle: the three are peers,
 * and a vertical list would make the third look like an afterthought.
 */
@Composable
fun AddTileRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        content()
    }
}

/**
 * One tile of [AddTileRow].
 *
 * [enabled] is false when the channel cannot run right now — a clipboard with
 * nothing importable in it — and the subtitle then says why. A tile that looks
 * live and then reports an error on tap is a worse control than a greyed one
 * that explains itself.
 */
@Composable
fun AddTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(StarveilShapes.card)
            .background(StarveilColors.card)
            .border(1.dp, StarveilColors.line, StarveilShapes.card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = StarveilSpacing.md, vertical = StarveilSpacing.md),
        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) StarveilColors.accent else StarveilColors.ink2.copy(alpha = 0.45f),
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.size(StarveilSpacing.xs))
        Text(
            text = title,
            style = StarveilType.headline,
            color = if (enabled) StarveilColors.ink else StarveilColors.ink2.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = StarveilType.label,
            color = StarveilColors.ink2.copy(alpha = if (enabled) 1f else 0.55f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A secondary channel: 56dp, a line icon, a title and a subtitle.
 *
 * Tapping one expands its input *inside* the panel. It never opens a dialog:
 * the whole point of the panel is that a short flow finishes in it.
 */
@Composable
fun AddActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StarveilDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = StarveilSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (expanded) StarveilColors.accent else StarveilColors.ink2,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = StarveilType.body, color = StarveilColors.ink)
                Text(text = subtitle, style = StarveilType.label, color = StarveilColors.ink2)
            }
            Icon(
                imageVector = if (expanded) StarveilIcons.Up else StarveilIcons.Down,
                contentDescription = null,
                tint = StarveilColors.ink2,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The clipboard suggestion: a filled block, not a card with a gradient.
 *
 * It sits at the top of the panel only when there is something to offer, so it
 * never becomes a permanent empty slot.
 */
@Composable
fun AddSuggestionCard(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(StarveilShapes.card)
            .background(StarveilColors.card)
            .border(1.dp, StarveilColors.line, StarveilShapes.card)
            .padding(horizontal = StarveilSpacing.md, vertical = StarveilSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The glyph sits on the pale accent, not on the card: the card is the
        // same surface as the sheet, and a block of colour is what makes the
        // suggestion read as something the app found rather than something the
        // user opened.
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StarveilColors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = StarveilIcons.Link,
                contentDescription = null,
                tint = StarveilColors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = StarveilType.body, color = StarveilColors.ink)
            Text(
                text = detail,
                style = StarveilType.label,
                color = StarveilColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        StarveilButton(text = action, onClick = onAction)
    }
}

/** A one-line result, shown inside the panel after a short flow finishes. */
@Composable
fun AddResultLine(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StarveilColors.segBg)
            .padding(horizontal = StarveilSpacing.md, vertical = StarveilSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = StarveilType.label,
            color = StarveilColors.ink,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
            StarveilButton(text = action, onClick = onAction, secondary = true)
        }
    }
}

/** The panel is content height, but never more than this share of the screen. */
private const val MAX_SHEET_FRACTION = 0.6f

/** Light scrim: the page dims, the top bar included. */
private val SCRIM_LIGHT = Color.Black.copy(alpha = 0.32f)

/** Dark scrim: deeper, because the page is already dark. */
private val SCRIM_DARK = Color.Black.copy(alpha = 0.55f)
