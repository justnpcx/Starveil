package com.justnpcx.starveil.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * The four places the bar can reach.
 *
 * [MORE] is not a page of its own content: it is the hub that holds the rest of
 * the app, and every screen below it keeps the hub lit. That is the whole
 * reason this bar replaced the floating capsule — the capsule had no way to say
 * where you were on a screen it did not list, so those screens had no active
 * item at all.
 */
enum class NavSection { HOME, NODES, RULES, MORE }

/**
 * Which destination a route belongs to.
 *
 * Everything that is not one of the three named pages belongs to the hub: the
 * scenes, the logs, the self-check, the settings and the about page are all
 * reached through it, and none of them is a fifth tab.
 */
fun navSectionFor(route: String?): NavSection = when {
    route == "dashboard" -> NavSection.HOME
    route == "profiles" || route == "scan" -> NavSection.NODES
    route == "rules" -> NavSection.RULES
    else -> NavSection.MORE
}

/**
 * The navigation bar: full width, 80dp, flush to the bottom.
 *
 * Material's own [NavigationBar] rather than a drawn one. The shape the design
 * asks for — 80dp, a 64x32 pill behind the selected icon, 24dp glyphs, labels
 * that never disappear — is exactly Material's, and the platform's component
 * brings the ripple, the tab semantics and the touch target with it. The one
 * thing it does not bring is a height that ignores the gesture inset, so the
 * inset is applied outside the 80dp rather than inside it: the container's
 * colour runs to the screen edge, and the bar itself stays 80.
 */
@Composable
fun StarveilNavBar(
    currentRoute: String?,
    onSelect: (NavSection) -> Unit,
) {
    val section = navSectionFor(currentRoute)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StarveilColors.card)
            .navigationBarsPadding(),
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(StarveilSpacing.navBarHeight),
            containerColor = StarveilColors.card,
            contentColor = StarveilColors.ink2,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            NavItem(
                icon = StarveilIcons.Home,
                label = stringResource(R.string.nav_home),
                selected = section == NavSection.HOME,
                onClick = { onSelect(NavSection.HOME) },
            )
            NavItem(
                icon = StarveilIcons.Nodes,
                label = stringResource(R.string.nav_nodes),
                selected = section == NavSection.NODES,
                onClick = { onSelect(NavSection.NODES) },
            )
            NavItem(
                icon = StarveilIcons.Route,
                label = stringResource(R.string.nav_rules),
                selected = section == NavSection.RULES,
                onClick = { onSelect(NavSection.RULES) },
            )
            NavItem(
                icon = StarveilIcons.Grid,
                label = stringResource(R.string.nav_more),
                selected = section == NavSection.MORE,
                onClick = { onSelect(NavSection.MORE) },
            )
        }
    }
}

/**
 * One destination, drawn here rather than by Material's NavigationBarItem.
 *
 * Material's item clips its press ripple to the 64x32 indicator pill. The pill
 * itself is transparent in this design, so it never shows at rest — but the
 * ripple is drawn inside that shape, so every tap flashed a pill-shaped block
 * and faded it out, which is exactly the shape this bar stopped showing.
 *
 * The item is therefore a plain equal-width slot: icon, label, and a ripple
 * bounded to the whole slot rather than to a shape inside it. Selection is the
 * accent colour plus a heavier label; there is no background at any moment.
 */
@Composable
private fun RowScope.NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val state = stringResource(
        if (selected) R.string.nav_item_selected else R.string.nav_item_unselected,
    )
    val tint = if (selected) StarveilColors.accent else StarveilColors.ink2
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // No press feedback at all, the way the phone's own tab bar
                // works: the only thing that changes when a tab is tapped is
                // the colour. A ripple, a slot-sized highlight or a pill all
                // put a shape on screen for a moment, and the shape is the
                // thing this bar was changed twice to stop drawing.
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                stateDescription = state
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
