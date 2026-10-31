package com.justnpcx.starveil.ui

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
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * One destination.
 *
 * The selected state is said twice on purpose: the pill and the heavier label
 * are the visible difference, and the state description is the spoken one. A
 * bar that only changed colour would fail both the accessibility rule and
 * anyone reading it in sunlight.
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
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.semantics { stateDescription = state },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        alwaysShowLabel = true,
        // Colour is the whole selection signal: the accent for the item you
        // are on, ink2 for the rest. No pill, no trough — the bar sits on the
        // card and a shape behind one glyph was a second surface for a fact
        // the colour and the label's weight already state.
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = StarveilColors.accent,
            selectedTextColor = StarveilColors.accent,
            indicatorColor = Color.Transparent,
            unselectedIconColor = StarveilColors.ink2,
            unselectedTextColor = StarveilColors.ink2,
        ),
    )
}
