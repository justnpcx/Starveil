package com.justnpcx.starveil.ui

import com.justnpcx.starveil.core.ui.snackbar.UndoBus
import com.justnpcx.starveil.core.ui.snackbar.UndoRequest
import com.justnpcx.starveil.core.ui.snackbar.UndoRequests
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The app's undo channel.
 *
 * Lives in :app because it is the shell that draws the Snackbar — a feature
 * module sees only the [UndoBus] port.
 *
 * `tryEmit` with no overflow policy is deliberate: an undo the user cannot see
 * is worse than one they did not ask for, so a burst would rather drop the
 * newest than block the coroutine that just deleted a row.
 */
@Singleton
class AppUndoBus @Inject constructor() : UndoBus, UndoRequests {

    private val _requests = MutableSharedFlow<UndoRequest>(extraBufferCapacity = 8)
    override val requests: Flow<UndoRequest> = _requests.asSharedFlow()

    override fun show(messageRes: Int, args: List<Any>, onUndo: () -> Unit) {
        _requests.tryEmit(UndoRequest(messageRes = messageRes, args = args, onUndo = onUndo))
    }
}
