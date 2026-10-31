package com.justnpcx.starveil.feature.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.kernel.TrafficStats
import com.justnpcx.starveil.core.model.AvailabilityBand
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.RecommendationReason
import com.justnpcx.starveil.core.model.NodeSelectionMode
import com.justnpcx.starveil.core.ui.components.StarveilSnackbarHost
import com.justnpcx.starveil.core.ui.components.ConnectionCore
import com.justnpcx.starveil.core.ui.components.StarveilSegmented
import com.justnpcx.starveil.core.ui.components.ConnectionCoreGeometry
import com.justnpcx.starveil.core.ui.components.ConnectionState
import com.justnpcx.starveil.core.ui.components.NodeHealthChart
import com.justnpcx.starveil.core.ui.components.StarveilBadge
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilDivider
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Dashboard — the cockpit. Tap the core to connect/disconnect.
 *
 * The page is a ring, two rates, and two glass cards. Nothing is boxed that
 * does not need an edge: the rates are the page's own type, and the cards hold
 * the things that are objects — the node, and the node's health.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    /**
     * Asks for VPN consent before connecting. Supplied by :app (which owns the
     * activity-result launcher); the default assumes consent is already held,
     * so previews and tests do not need the port.
     */
    requestVpnConsent: (onGranted: () -> Unit) -> Unit = { it() },
    /** The node card opens the list the node is chosen from. */
    onOpenNodes: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenHidden()
        }
    }
    val snackbar = remember { SnackbarHostState() }
    // The notice whose details are open, if any. Held here rather than in the
    // view model: it is whether a dialog is showing, not what the app knows.
    var details by remember { mutableStateOf<DashboardNotice?>(null) }
    var healthOpen by remember { mutableStateOf(false) }
    var exitOpen by remember { mutableStateOf(false) }

    // A persistent notice is a condition that lasts until something changes —
    // it gets the fixed slot. Everything else is a one-off confirmation and
    // goes to a Snackbar, so it can never push the layout around.
    val notice = state.notice
    val noticeText = notice?.text()
    val persistent = notice?.isPersistent() == true
    LaunchedEffect(notice) {
        if (noticeText != null && !persistent) {
            snackbar.showSnackbar(noticeText, duration = SnackbarDuration.Short)
        }
    }

    // Every page scrolls, in every orientation.
    //
    // "Portrait never scrolls" was the rule that made a short phone's health
    // card unreachable: the column overflowed, the overflow was clipped, and
    // there was no gesture that could bring it back. A tall screen pays
    // nothing for this — the column is shorter than the window, so the scroll
    // never engages and the first screen is the same pixels it always was.
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // How much of the fixed stack this window can hold. The ring never
        // changes size — everything else gives a little, in three steps, so a
        // 700dp phone shows the whole page without a scroll while an 872dp one
        // keeps the 0.9.4 composition pixel for pixel.
        val metrics = homeMetricsFor(maxHeight)
        // What the status slot would say, decided before the slot is measured:
        // an empty slot costs nothing below the standard tier.
        val firstConnectHint = !state.everConnected &&
            state.coreState == ConnectionState.DISCONNECTED &&
            noticeText == null
        // The first-connect hint is a second copy of what the ring already
        // says ("tap to connect"); on a screen that has no height to spare it
        // is the first thing to go, and the slot then costs nothing.
        val showFirstHint = firstConnectHint && !metrics.collapseEmptySlot
        val slotHasContent = showFirstHint || (persistent && noticeText != null)
        val slotHeight = if (metrics.collapseEmptySlot && !slotHasContent) 0.dp else metrics.contextSlot
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StarveilSpacing.pageMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Air above the ring: 32dp when the window can afford it, 12 when
            // it cannot. The ring itself never moves up or down in size.
            Spacer(modifier = Modifier.height(metrics.topGap))
            ConnectionCore(
                state = state.coreState,
                busy = state.isBusy,
                // Fixed in every state: everything below is measured from this
                // box, so a state change must not move any of it.
                diameter = ConnectionCoreGeometry.diameterDp.dp,
                hint = stringResource(
                    when (state.coreState) {
                        ConnectionState.DISCONNECTED -> R.string.dashboard_hint_connect
                        ConnectionState.CONNECTING -> R.string.dashboard_hint_connecting
                        ConnectionState.CONNECTED -> R.string.dashboard_hint_disconnect
                        ConnectionState.BLOCKED -> R.string.dashboard_hint_blocked
                        ConnectionState.WARNING -> R.string.dashboard_hint_disconnect
                    },
                ),
                label = stringResource(
                    when (state.coreState) {
                        ConnectionState.DISCONNECTED -> R.string.dashboard_state_disconnected
                        ConnectionState.CONNECTING -> R.string.dashboard_state_connecting
                        ConnectionState.CONNECTED -> R.string.dashboard_state_connected
                        ConnectionState.BLOCKED -> R.string.dashboard_state_blocked
                        ConnectionState.WARNING -> R.string.dashboard_state_warning
                    },
                ),
                onClick = { requestVpnConsent { viewModel.onCoreClick() } },
                // The core is the screen's primary control, so its description
                // carries both the state and what tapping does (§6.1).
                contentDescription = stringResource(
                    when (state.coreState) {
                        ConnectionState.DISCONNECTED -> R.string.dashboard_core_disconnected
                        ConnectionState.CONNECTING -> R.string.dashboard_core_connecting
                        ConnectionState.CONNECTED -> R.string.dashboard_core_connected
                        ConnectionState.WARNING -> R.string.dashboard_core_warning
                        ConnectionState.BLOCKED -> R.string.dashboard_core_blocked
                    },
                ),
            )

            SpeedSection(state = state, modifier = Modifier.height(metrics.telemetry))

            // The context line sits under the group it explains, in a fixed
            // slot, so a state change cannot move anything below it. A failure
            // that names a reason is tappable: the slot shows one line, and the
            // line is the only place the kernel's own words appear.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight),
                contentAlignment = Alignment.Center,
            ) {
                // The hint lives in the slot that already exists for one line
                // of state explanation, so showing and hiding it cannot move
                // the ring, the rates, or anything below.
                if (showFirstHint) {
                    Text(
                        text = stringResource(R.string.dashboard_first_connect_hint),
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (persistent && noticeText != null) {
                    val expandable = notice is DashboardNotice.KernelRefused
                    Text(
                        text = noticeText,
                        style = StarveilType.label,
                        color = when (notice) {
                            DashboardNotice.KillSwitchBlocking -> StarveilColors.errText
                            else -> StarveilColors.warn
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (expandable) {
                            Modifier
                                .clip(RoundedCornerShape(StarveilSpacing.xs))
                                .clickable { details = notice }
                                .padding(horizontal = StarveilSpacing.xs)
                        } else {
                            Modifier
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(metrics.cardGap))

            CurrentNodeCard(
                name = state.selectedNodeName ?: stringResource(R.string.dashboard_no_node),
                health = state.health,
                mode = state.selectionMode,
                onOpen = onOpenNodes,
                onModeChange = viewModel::setSelectionMode,
                padding = metrics.cardPadding,
            )

            // §7.2: the selected node's health over the window. Only for a
            // node — a group has no single curve — and the chart says
            // "collecting" in words until there is a shape worth drawing.
            state.health?.let { health ->
                Spacer(modifier = Modifier.height(StarveilSpacing.md))
                // A summary, not the chart: a curve with three reachable
                // samples reads as "broken" at a glance even when the tunnel is
                // fine. The chart is one tap away, with the sentence that says
                // what each mark on it means.
                NodeHealthSummary(
                    health = health,
                    onClick = { healthOpen = true },
                    padding = metrics.cardPadding,
                    showDetail = metrics.healthDetail,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Where the traffic appears to come from, and how long the round
            // trip to a third party took. Always visible while connected: a
            // proxy that silently fell back to the local connection looks
            // exactly like one that works until this line says so.
            state.exit?.let { exit ->
                Spacer(modifier = Modifier.height(StarveilSpacing.md))
                // Where the traffic appears to come from, and how long the
                // round trip took: the only visible evidence that the tunnel is
                // not silently falling back to the local connection. Two rows,
                // labelled — a bare "IP · 43 ms" line left the reader guessing
                // which number was which.
                StarveilCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { exitOpen = true },
                ) {
                    ExitRow(
                        label = stringResource(R.string.dashboard_exit_address),
                        value = exit.address,
                    )
                    StarveilDivider(startInset = StarveilSpacing.lg)
                    ExitRow(
                        label = stringResource(R.string.dashboard_exit_rtt_smoothed),
                        value = stringResource(
                            R.string.dashboard_exit_rtt_value,
                            state.exitSmoothedRttMs ?: exit.rttMs,
                        ),
                    )
                }
            }

            // Air under the last card, sized by the tier: a tall screen can
            // afford 24dp above the bar, a short one needs the height for the
            // cards instead. This replaces the old xxl + navContentInset pair,
            // which spent 44dp on nothing at the bottom of every phone.
            Spacer(modifier = Modifier.height(metrics.bottomGap))
        }
        }

        StarveilSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        details?.let { notice ->
            FailureDetailsDialog(notice = notice, onDismiss = { details = null })
        }

        if (healthOpen) {
            state.health?.let { health ->
                NodeHealthDetailsDialog(health = health, onDismiss = { healthOpen = false })
            }
        }

        if (exitOpen) {
            state.exit?.let { exit ->
                ExitDetailsDialog(exit = exit, onDismiss = { exitOpen = false })
            }
        }
    }
}

/**
 * The whole failure, and a way to keep it.
 *
 * One line in the context slot is enough to notice something went wrong; it is
 * not enough to act on, and the reason is the part a bug report needs. Copying
 * is the point of this dialog.
 */
@Composable
private fun FailureDetailsDialog(notice: DashboardNotice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = notice.text()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_failure_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                Text(text, style = StarveilType.body)
                Text(
                    text = stringResource(R.string.dashboard_failure_hint),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Starveil", text))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.dashboard_failure_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dashboard_failure_close))
            }
        },
    )
}

/**
 * The health card as a summary.
 *
 * A diagnostic chart does not belong on the home screen: with three reachable
 * samples it is a flat line and a row of red dots, which reads as "the tunnel
 * is broken" while the tunnel is fine. The home screen answers "am I
 * protected, where, how fast"; the curve answers "how has this node been
 * behaving", and that is one tap away.
 *
 * The summary still refuses to flatter a node: an empty reachable set says so
 * in words, and it says why — too few samples, or every probe unreachable.
 */
@Composable
private fun NodeHealthSummary(
    health: NodeHealth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = StarveilSpacing.lg,
    /** False on a short screen: the sentence is in the dialog one tap away. */
    showDetail: Boolean = true,
) {
    val reachable = health.samples.count { it.reachable }
    StarveilCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEALTH_HEADER_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_health_title),
                    style = StarveilType.headline,
                    color = StarveilColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!health.collecting) {
                    Text(
                        text = stringResource(
                            R.string.dashboard_health_availability,
                            health.availabilityPct.roundToInt(),
                        ),
                        style = StarveilType.label,
                        color = when (health.band) {
                            AvailabilityBand.GOOD -> StarveilColors.ok
                            AvailabilityBand.FAIR -> StarveilColors.warn
                            AvailabilityBand.POOR -> StarveilColors.errText
                        },
                    )
                }
            }
            if (showDetail) {
                Spacer(modifier = Modifier.height(StarveilSpacing.xs))
                Text(
                    text = when {
                        health.collecting -> stringResource(
                            R.string.dashboard_health_collecting,
                            health.samples.size,
                            NodeHealth.MIN_SAMPLES,
                        )
                        reachable == 0 -> stringResource(
                            R.string.dashboard_health_all_unreachable,
                            health.samples.size,
                        )
                        else -> stringResource(
                            R.string.dashboard_health_summary,
                            health.emaMs ?: 0L,
                            reachable,
                            health.samples.size,
                        )
                    },
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The curve, and what its three marks mean.
 *
 * The legend is the point of the dialog: "availability", "latency EMA" and
 * "loss dots" are three different measurements, and a user who reads them as
 * one line will think a node with 3% availability is a node that is fast.
 */
@Composable
private fun NodeHealthDetailsDialog(health: NodeHealth, onDismiss: () -> Unit) {
    val reachable = health.samples.filter { it.reachable }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_health_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                NodeHealthChart(
                    health = health,
                    contentDescription = if (reachable.isEmpty()) {
                        stringResource(R.string.dashboard_health_chart_empty)
                    } else {
                        stringResource(
                            R.string.dashboard_health_chart_desc,
                            reachable.size,
                            reachable.minOf { it.latencyMs },
                            reachable.maxOf { it.latencyMs },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = HEALTH_MIN_HEIGHT),
                )
                if (reachable.isEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.dashboard_health_all_unreachable,
                            health.samples.size,
                        ),
                        style = StarveilType.label,
                        color = StarveilColors.errText,
                    )
                }
                HealthLegend(stringResource(R.string.dashboard_health_legend_availability))
                HealthLegend(stringResource(R.string.dashboard_health_legend_ema))
                HealthLegend(stringResource(R.string.dashboard_health_legend_loss))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dashboard_failure_close))
            }
        },
    )
}

@Composable
private fun HealthLegend(text: String) {
    Text(text = text, style = StarveilType.tech, color = StarveilColors.ink2)
}

/**
 * What the exit line measured, in full.
 *
 * The number on the home screen is one line and has to stay one line; this is
 * where "which address, how long, and when" lives. The round trip includes DNS
 * and TLS, which is why it is called a round trip and not a ping.
 */
@Composable
private fun ExitDetailsDialog(
    exit: com.justnpcx.starveil.core.data.network.ExitInfo,
    onDismiss: () -> Unit,
) {
    val time = remember(exit.atEpochMs) {
        java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date(exit.atEpochMs))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_exit_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                DetailLine(stringResource(R.string.dashboard_exit_address), exit.address)
                DetailLine(
                    stringResource(R.string.dashboard_exit_rtt),
                    stringResource(R.string.dashboard_exit_rtt_value, exit.rttMs),
                )
                DetailLine(stringResource(R.string.dashboard_exit_measured), time)
                Text(
                    text = stringResource(R.string.dashboard_exit_note),
                    style = StarveilType.tech,
                    color = StarveilColors.ink3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dashboard_failure_close))
            }
        },
    )
}

/** One labelled fact on the exit card: the name on the left, the value right. */
@Composable
private fun ExitRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = StarveilType.label,
            color = StarveilColors.ink2,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(StarveilSpacing.md))
        Text(
            text = value,
            style = StarveilType.tech,
            color = StarveilColors.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = StarveilType.label, color = StarveilColors.ink2)
        Text(text = value, style = StarveilType.tech, color = StarveilColors.ink)
    }
}

/**
 * A notice as the user reads it.
 *
 * The one place the dashboard's copy lives, so the view model can stay free of
 * it (C1) and the wording can be reviewed with the rest of the app's.
 */
@Composable
private fun DashboardNotice.text(): String = when (this) {
    DashboardNotice.KillSwitchBlocking -> stringResource(R.string.dashboard_notice_kill_switch)
    DashboardNotice.KillSwitchFailed -> stringResource(R.string.dashboard_notice_kill_switch_failed)
    DashboardNotice.TunnelDropped -> stringResource(R.string.dashboard_notice_tunnel_dropped)
    DashboardNotice.TunnelReconnecting -> stringResource(R.string.dashboard_notice_tunnel_reconnecting)
    DashboardNotice.TunnelKeepsDropping ->
        stringResource(R.string.dashboard_notice_tunnel_keeps_dropping)
    DashboardNotice.NoTarget -> stringResource(R.string.dashboard_notice_no_target)
    DashboardNotice.ConfigUnbuildable -> stringResource(R.string.dashboard_notice_config)
    // The kernel's own reason is the useful part; without one, say only that it
    // refused rather than inventing a detail.
    is DashboardNotice.KernelRefused -> reason
        ?.let { stringResource(R.string.dashboard_notice_kernel_refused_reason, it) }
        ?: stringResource(R.string.dashboard_notice_kernel_refused)
    DashboardNotice.SwitchingNode ->
        stringResource(R.string.dashboard_notice_switching_node)
    DashboardNotice.TunnelSilent ->
        stringResource(R.string.dashboard_notice_tunnel_silent)
}

/**
 * The picker's reason, as a clause.
 *
 * A clause rather than a sentence because it is used as the second half of the
 * "auto-selected X — Y" notice.
 */
@Composable
private fun RecommendationReason.text(): String = when (this) {
    is RecommendationReason.Healthiest -> healthClause(availabilityPct, latencyMs)
    is RecommendationReason.TiedOnScore -> region
        ?.let { stringResource(R.string.dashboard_reason_tied_region, healthClause(availabilityPct, latencyMs), it.displayName) }
        ?: stringResource(R.string.dashboard_reason_tied, healthClause(availabilityPct, latencyMs))
    is RecommendationReason.AllCoolingDown ->
        stringResource(R.string.dashboard_reason_cooling_down, minutesLeft)
    RecommendationReason.NothingMeasured ->
        stringResource(R.string.dashboard_reason_unmeasured)
}

/**
 * What the picker saw, in the units the user measures in.
 *
 * The score is a 0–10099 internal number. It is deliberately not shown:
 * "score 10099" is precise about the wrong thing, and the two numbers it is
 * made of are the ones a person can check against the health chart above.
 */
@Composable
private fun healthClause(availabilityPct: Int, latencyMs: Long?): String =
    latencyMs?.let { stringResource(R.string.dashboard_reason_health, availabilityPct, it) }
        ?: stringResource(R.string.dashboard_reason_health_no_latency, availabilityPct)

/**
 * The telemetry block: two columns, mirrored about the screen's centre.
 *
 * Each column is label / number / total, top to bottom, and the three rows line
 * up across the gap: both labels on one baseline, both numbers on the next, both
 * totals on the third. That is what makes the pair read as one instrument
 * rather than as two stacks that happen to be side by side.
 *
 * The unit sits on the number's baseline, not above it.
 *
 * Dimmed as a whole when there is no tunnel: three zeros say "no traffic", a
 * third of the ink says "nothing is connected", and the second is the truth.
 */
@Composable
private fun SpeedSection(state: DashboardUiState, modifier: Modifier = Modifier) {
    val dim = state.coreState == ConnectionState.DISCONNECTED
    val down = formatRateParts(state.stats.downlinkRate)
    val up = formatRateParts(state.stats.uplinkRate)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dim) 0.38f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        SpeedColumn(
            label = stringResource(R.string.dashboard_speed_down),
            parts = down,
            total = state.stats.downlinkBytes,
            modifier = Modifier.weight(1f),
        )
        SpeedColumn(
            label = stringResource(R.string.dashboard_speed_up),
            parts = up,
            total = state.stats.uplinkBytes,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedColumn(
    label: String,
    parts: RateParts,
    total: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = StarveilType.tech,
            color = StarveilColors.ink2,
        )
        Row(
            // alignByBaseline on both children, so the unit sits on the digits'
            // baseline whatever size either of them is.
            modifier = Modifier.padding(top = StarveilSpacing.sm),
        ) {
            Text(
                text = parts.value,
                // Tabular figures: a rate that ticks every second must not
                // change width, or the whole row twitches with it.
                style = StarveilType.display.copy(fontFeatureSettings = "tnum"),
                color = StarveilColors.ink,
                // One line, always. A rate that wraps is no longer a rate.
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = parts.unit,
                style = StarveilType.tech.copy(fontSize = 13.sp),
                color = StarveilColors.ink2,
                // "B/s" was breaking after the slash on a narrow column: the
                // unit is one token, not a word the layout may split.
                softWrap = false,
                maxLines = 1,
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = 6.dp),
            )
        }
        Text(
            text = stringResource(R.string.dashboard_speed_total, formatBytes(total)),
            style = StarveilType.label,
            color = StarveilColors.ink2,
            modifier = Modifier.padding(top = StarveilSpacing.xs),
        )
    }
}

/** A rate split so the number and its unit can be sized independently. */
internal data class RateParts(val value: String, val unit: String)

internal fun formatRateParts(bytesPerSecond: Long): RateParts =
    formatScaledParts(bytesPerSecond.toDouble(), RATE_UNITS)

/**
 * [value] in the largest unit that keeps it readable, with the unit separate.
 *
 * The mantissa never exceeds four characters: the unit is chosen from the
 * **rounded** value, so 999.6 KB becomes 1.0 MB rather than "1000 KB". That is
 * what stops a long number from overlapping its neighbour in a 50% column.
 */
private fun formatScaledParts(value: Double, units: List<String>): RateParts {
    var scaled = value
    for (index in units.indices) {
        val decimals = if (index <= 1) 0 else 1
        val rounded = roundTo(scaled, decimals)
        if (index == units.lastIndex || rounded < SCALE) {
            val text = if (decimals == 0) {
                rounded.toLong().toString()
            } else {
                String.format(Locale.US, "%.1f", rounded)
            }
            return RateParts(text, units[index])
        }
        scaled /= SCALE
    }
    return RateParts(value.toLong().toString(), units.first())
}

/**
 * The node the next connect will use, with the one-tap pick beside it.
 *
 * A glass card, because the node is an object and the page needs one thing with
 * an edge above the health curve. The badge sits in the name's row — it
 * modifies the node, so it belongs to that line.
 */

@Composable
private fun CurrentNodeCard(
    name: String,
    health: NodeHealth?,
    mode: NodeSelectionMode,
    onOpen: () -> Unit,
    onModeChange: (NodeSelectionMode) -> Unit,
    padding: Dp = StarveilSpacing.lg,
) {
    val veil = StarveilColors.veil
    StarveilCard(onClick = onOpen) {
        Box {
            Column(modifier = Modifier.padding(padding)) {
                // The gauze covers the card's information area and stops
                // there. It used to fill the card, which put diagonal bands
                // behind the segmented control's own labels — texture and
                // control fighting for the same few pixels.
                Box {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBehind {
                                val period = 74.dp.toPx()
                                val band = 10.dp.toPx()
                                rotate(degrees = 25f) {
                                    var x = -size.height
                                    while (x < size.width + size.height) {
                                        drawRect(
                                            color = veil,
                                            topLeft = Offset(x, -size.height),
                                            size = Size(band, size.height * 3f),
                                        )
                                        x += period
                                    }
                                }
                            },
                    )
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        NodeName(name = name)
                        health?.takeIf { !it.collecting }?.emaMs?.let { latency ->
                            Text(
                                text = stringResource(R.string.dashboard_latency_label) + " " + latency + " ms",
                                style = StarveilType.label,
                                color = StarveilColors.ink2,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(StarveilSpacing.md))
                    Text(
                        text = stringResource(
                            if (mode == NodeSelectionMode.AUTO) {
                                R.string.dashboard_mode_auto
                            } else {
                                R.string.dashboard_mode_manual
                            },
                        ),
                        style = StarveilType.label,
                        color = StarveilColors.ink2,
                    )
                }
                }
                Spacer(modifier = Modifier.height(StarveilSpacing.md))
                StarveilSegmented(
                    options = listOf(
                        stringResource(R.string.dashboard_mode_auto),
                        stringResource(R.string.dashboard_mode_manual),
                    ),
                    selectedIndex = if (mode == NodeSelectionMode.AUTO) 0 else 1,
                    onSelect = { index ->
                        onModeChange(if (index == 0) NodeSelectionMode.AUTO else NodeSelectionMode.MANUAL)
                    },
                )
            }
        }
    }
}

/**
 * A node's name, with a leading flag at 22sp.
 *
 * `Profile.displayName()` puts the flag in the string, and a flag at 17sp reads
 * as punctuation. Splitting it out is a display rule, not a data one: nothing
 * outside this function knows the flag was ever separate.
 */
@Composable
private fun NodeName(name: String, modifier: Modifier = Modifier) {
    val (flag, rest) = splitLeadingFlag(name)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (flag != null) {
            Text(
                text = flag,
                fontSize = 22.sp,
                color = StarveilColors.ink,
            )
            Spacer(modifier = Modifier.width(StarveilSpacing.sm))
        }
        Text(
            text = rest,
            style = StarveilType.headline,
            color = StarveilColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Splits a leading pair of regional-indicator symbols off a name.
 *
 * Returns (flag, rest), or (null, name) when the name does not start with one.
 * The pair is two code points; a name that begins with a single one is not a
 * flag and is left alone.
 */
internal fun splitLeadingFlag(name: String): Pair<String?, String> {
    val points = name.codePoints().toArray()
    if (points.size < 3) return null to name
    val first = points[0]
    val second = points[1]
    val isIndicator = { cp: Int -> cp in 0x1F1E6..0x1F1FF }
    if (!isIndicator(first) || !isIndicator(second)) return null to name
    val flag = String(points, 0, 2)
    val rest = String(points, 2, points.size - 2).trim()
    return flag to rest
}

/** One line of state explanation, occupied or not. */
/**
 * What the home page's fixed stack can afford at a given window height.
 *
 * Three steps, and only one number is sacred: the ring stays 204dp everywhere,
 * because it is the page's anchor and resizing it would move every other thing
 * on the screen. The rest — the air above it, the telemetry block, the status
 * slot, the gap before the node card, the cards' own padding — gives a little
 * at each step. A 700dp phone lands in COMPACT and fits without a scroll; a
 * 640dp one lands in SHORT and gives up the health card's second sentence,
 * which is in its dialog anyway.
 */
private enum class HomeTier { STANDARD, COMPACT, SHORT }

private data class HomeMetrics(
    val topGap: Dp,
    val telemetry: Dp,
    /** The status slot's height when it has something to say. */
    val contextSlot: Dp,
    val cardGap: Dp,
    val cardPadding: Dp,
    /** Air under the last card, before the navigation bar. */
    val bottomGap: Dp,
    val healthDetail: Boolean,
    /**
     * Whether an empty status slot collapses to nothing.
     *
     * True below the standard tier: the slot exists so a notice cannot move
     * the ring, and on a screen that is short of height a 20dp hole with
     * nothing in it is height spent on nothing. The standard tier keeps the
     * slot at a fixed height so its composition stays pixel-identical to 0.9.6.
     */
    val collapseEmptySlot: Boolean,
)

/**
 * The chrome the shell draws around the page: the 56dp top bar and the 80dp
 * navigation bar. The tiers are stated against the window height — 640, 720
 * and 872dp are what a phone and the acceptance viewports actually are — and
 * the page is laid out in the window minus this.
 */
private val CHROME_HEIGHT = 136.dp

private fun homeMetricsFor(contentHeight: Dp): HomeMetrics = when {
    contentHeight + CHROME_HEIGHT >= 780.dp -> HomeMetrics(
        topGap = StarveilSpacing.xxl,
        telemetry = TELEMETRY_HEIGHT,
        contextSlot = CONTEXT_SLOT_HEIGHT,
        cardGap = StarveilSpacing.xl,
        cardPadding = StarveilSpacing.lg,
        bottomGap = 24.dp,
        healthDetail = true,
        collapseEmptySlot = false,
    )
    contentHeight + CHROME_HEIGHT >= 680.dp -> HomeMetrics(
        topGap = StarveilSpacing.md,
        telemetry = 92.dp,
        contextSlot = 20.dp,
        cardGap = StarveilSpacing.sm,
        cardPadding = StarveilSpacing.lg,
        bottomGap = 12.dp,
        healthDetail = true,
        collapseEmptySlot = true,
    )
    else -> HomeMetrics(
        topGap = StarveilSpacing.sm,
        telemetry = 80.dp,
        contextSlot = 20.dp,
        cardGap = StarveilSpacing.sm,
        cardPadding = StarveilSpacing.md,
        bottomGap = 8.dp,
        healthDetail = false,
        collapseEmptySlot = true,
    )
}

private val CONTEXT_SLOT_HEIGHT = 24.dp

/** The telemetry block's height, with or without numbers: 16+8+58+4+19, rounded up. */
private val TELEMETRY_HEIGHT = 112.dp

/** The health chart never gets smaller than this. */
private val HEALTH_MIN_HEIGHT = 96.dp

/** The health header is a fixed 32 dp so the chart's share is predictable. */
private val HEALTH_HEADER_HEIGHT = 32.dp

/**
 * A condition that lasts, as opposed to a one-off confirmation.
 *
 * Only these get the slot; everything else is a Snackbar.
 */
private fun DashboardNotice.isPersistent(): Boolean = when (this) {
    // Conditions that last until something changes. A refused start is one:
    // the reason stays true until the next attempt, and it is the only place
    // the kernel's own words appear.
    DashboardNotice.KillSwitchBlocking,
    DashboardNotice.KillSwitchFailed,
    DashboardNotice.NoTarget,
    DashboardNotice.ConfigUnbuildable,
    // Up but carrying nothing: true until traffic arrives or the tunnel goes
    // down, and the whole point is that it must not scroll past.
    DashboardNotice.TunnelSilent,
    is DashboardNotice.KernelRefused,
    -> true

    // One-off: it describes something that is happening, and the state change
    // that ends it clears the notice.
    DashboardNotice.TunnelDropped,
    DashboardNotice.TunnelReconnecting,
    DashboardNotice.TunnelKeepsDropping,
    DashboardNotice.SwitchingNode,
    -> false
}

private val RATE_UNITS = listOf("B/s", "KB/s", "MB/s", "GB/s")
private val BYTE_UNITS = listOf("B", "KB", "MB", "GB")

/** One kibibyte, the step between every pair of units above. */
private const val SCALE = 1024.0

/** [value] rounded to [decimals] places. */
private fun roundTo(value: Double, decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    return round(value * factor) / factor
}

/** A rate with its unit, for copy and for tests. */
internal fun formatRate(bytesPerSecond: Long): String {
    val parts = formatRateParts(bytesPerSecond)
    return parts.value + " " + parts.unit
}

internal fun formatBytes(bytes: Long): String {
    val parts = formatScaledParts(bytes.toDouble(), BYTE_UNITS)
    return "${parts.value} ${parts.unit}"
}
