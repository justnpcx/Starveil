package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * The app's message bar, drawn from the host state rather than by Material's
 * stock SnackbarHost.
 *
 * Why not the stock one: on the nodes page it composed, it was handed the
 * message — showSnackbar blocked for its full four seconds, which only happens
 * when a message is on screen — and it drew nothing, while the identical
 * component in the shell drew its undo bar fine. Rather than keep guessing at
 * the animation inside Material's host, this one renders whatever the state
 * holds: if there is a message, there is a bar. It keeps the same state class,
 * so callers still call showSnackbar and still get the same timeout, action
 * and dismissal behaviour.
 */
@Composable
fun StarveilSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val data = hostState.currentSnackbarData ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = StarveilSpacing.pageMargin, vertical = StarveilSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = StarveilColors.ink,
            contentColor = StarveilColors.card,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = StarveilSpacing.lg,
                    vertical = StarveilSpacing.md,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = data.visuals.message,
                    style = StarveilType.body,
                    color = StarveilColors.card,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val action = data.visuals.actionLabel
                if (action != null) {
                    Spacer(modifier = Modifier.width(StarveilSpacing.lg))
                    Text(
                        text = action,
                        style = StarveilType.label,
                        color = StarveilColors.accent,
                        modifier = Modifier
                            .clickable(role = Role.Button) { data.performAction() }
                            .padding(vertical = StarveilSpacing.xs),
                    )
                }
            }
        }
    }
}
