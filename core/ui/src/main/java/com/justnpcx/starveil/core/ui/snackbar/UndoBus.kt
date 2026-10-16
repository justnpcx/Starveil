package com.justnpcx.starveil.core.ui.snackbar

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.justnpcx.starveil.core.ui.R
import kotlinx.coroutines.flow.Flow

/**
 * One reversible action, waiting to be offered.
 *
 * [messageRes] is a resource id rather than a sentence: the feature that
 * deletes something is the one that knows what to call it, and copy belongs in
 * resources (C1). The shell resolves it, because the shell owns the Context the
 * Snackbar is drawn with.
 */
data class UndoRequest(
    @StringRes val messageRes: Int,
    val args: List<Any> = emptyList(),
    val onUndo: () -> Unit,
)

/**
 * How a feature offers an undo.
 *
 * A port rather than a component: the feature owns the action and its
 * reversal, but there is exactly one Snackbar in this app and it belongs to
 * the shell. A feature cannot host it — the Snackbar has to outlive the screen
 * that raised it, or it disappears the moment the user navigates away from
 * what they just deleted, taking the undo with it.
 */
interface UndoBus {
    fun show(@StringRes messageRes: Int, args: List<Any> = emptyList(), onUndo: () -> Unit)
}

/** The same object seen from the shell's side. */
interface UndoRequests {
    val requests: Flow<UndoRequest>
}

/**
 * The app's Snackbar, and the one place an undo is offered.
 *
 * A single action label for every undo on purpose: "Undo" is the same word
 * wherever it appears, and a per-feature label is how an app ends up with
 * "Undo", "Revert" and "Restore" meaning one thing.
 */
@Composable
fun UndoSnackbarHost(
    requests: UndoRequests,
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actionLabel = stringResource(R.string.core_undo)

    LaunchedEffect(requests, actionLabel) {
        requests.requests.collect { request ->
            val result = hostState.showSnackbar(
                message = context.getString(request.messageRes, *request.args.toTypedArray()),
                actionLabel = actionLabel,
                // Long, and deliberately not Indefinite: the delete has
                // already happened, so a Snackbar that waits for an answer
                // would be a dialog wearing a different hat. But it is an undo
                // of something destructive, and Short is ~4 s — not enough to
                // notice a bar that appears under a finger still resting from
                // the swipe. On device it timed out before it could be tapped.
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) request.onUndo()
        }
    }

    SnackbarHost(hostState = hostState, modifier = modifier)
}
