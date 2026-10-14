package com.justnpcx.starveil.feature.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.kernel.LogStream
import com.justnpcx.starveil.core.model.AppLogLevel
import com.justnpcx.starveil.core.model.AppLogLine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LogsUiState(
    val lines: List<AppLogLine> = emptyList(),
    /** null = show every level. */
    val levelFilter: AppLogLevel? = null,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * The log viewer's state. It reads [LogStream] — the narrow port — so this
 * module never touches the kernel.
 */
@HiltViewModel
class LogsViewModel @Inject constructor(
    logStream: LogStream,
) : ViewModel() {

    private val filter = MutableStateFlow<AppLogLevel?>(null)

    val uiState: StateFlow<LogsUiState> = combine(logStream.lines, filter) { lines, level ->
        LogsUiState(
            // Newest first: a log viewer is read from the bottom.
            lines = lines.asReversed().filter { level == null || it.level == level },
            levelFilter = level,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LogsUiState(),
    )

    /** Tapping the active level clears it, so one control covers "all". */
    fun toggleLevel(level: AppLogLevel) {
        filter.value = if (filter.value == level) null else level
    }
}
