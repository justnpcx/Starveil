package com.justnpcx.starveil.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.justnpcx.starveil.R
import com.justnpcx.starveil.core.kernel.KernelBridge
import com.justnpcx.starveil.core.kernel.KernelState
import com.justnpcx.starveil.feature.dashboard.ConnectionController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * The home-screen widget: connection state, and one tap to toggle it.
 *
 * Deliberately the same job as the tile, not a second information surface. A
 * 2×1 cell holds a state and an action; anything more would be a 4×2 widget
 * pretending to be a dashboard.
 *
 * It cannot observe anything — a widget is RemoteViews, rendered by the
 * launcher — so the process that owns the state pushes to it. See
 * [com.justnpcx.starveil.StarveilApp]: while the tunnel is up this process is
 * alive by definition, because the VPN service runs in it.
 */
class StarveilWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val connected = entry.kernelBridge().state.first() == KernelState.Running
        val stateText = context.getString(
            if (connected) R.string.widget_connected else R.string.widget_disconnected,
        )
        val actionText = context.getString(
            if (connected) R.string.widget_tap_disconnect else R.string.widget_tap_connect,
        )

        provideContent {
            WidgetContent(connected = connected, stateText = stateText, actionText = actionText)
        }
    }
}

@Composable
private fun WidgetContent(connected: Boolean, stateText: String, actionText: String) {
    // The glass palette, spelled out: Glance has its own theming, and a widget
    // is a RemoteViews tree that cannot read a CompositionLocal. These are the
    // resolved values from core/ui/theme/Palette.kt — the translucent card
    // material resolved over the page, so the widget stays readable on any
    // wallpaper.
    val surface = ColorProvider(Color(0xFFF6F8FA)) // surfaceSolid
    val dot = ColorProvider(if (connected) Color(0xFF1D7A3A) else Color(0xFF87878A)) // ok / ink3
    val strong = ColorProvider(Color(0xFF1D1D1F)) // ink
    val dim = ColorProvider(Color(0xFF6A6A6D)) // ink2

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surface)
            .cornerRadius(16.dp)
            .padding(12.dp)
            // The whole card is the target, not a small button inside it: a
            // widget cell is already a small thing to hit.
            .clickable(actionRunCallback<ToggleAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(dot)
                    .cornerRadius(4.dp),
            ) {}
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = stateText,
                style = TextStyle(
                    color = strong,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = actionText,
            style = TextStyle(color = dim, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

/**
 * Toggle, from the widget.
 *
 * Runs in the app process (started on demand if it was not running), through
 * the same controller the dashboard and the tile use — so the Kill Switch and
 * the per-app rules behave identically no matter which surface was tapped.
 */
class ToggleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val kernel = entry.kernelBridge()
        if (kernel.state.value == KernelState.Running) {
            entry.connectionController().disconnect()
        } else {
            entry.connectionController().connect()
        }
        StarveilWidget().update(context, glanceId)
    }
}

class StarveilWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StarveilWidget()
}

/** Hilt bridge: a widget receiver is not an injection target here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun kernelBridge(): KernelBridge
    fun connectionController(): ConnectionController
}
