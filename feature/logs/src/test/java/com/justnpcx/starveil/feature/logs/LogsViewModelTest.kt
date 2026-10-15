package com.justnpcx.starveil.feature.logs

import com.justnpcx.starveil.core.kernel.LogStream
import com.justnpcx.starveil.core.model.AppLogLevel
import com.justnpcx.starveil.core.model.AppLogLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The log viewer's filtering. Search and the level filter narrow the same
 * list, and "nothing has arrived" has to stay distinct from "the filter hides
 * everything" — the screen shows different copy for each.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search matches the message case-insensitively`() = runBlocking {
        val viewModel = viewModel()
        val job = launch { viewModel.uiState.collect {} }

        viewModel.setQuery("TIMEOUT")
        yield()

        assertEquals(listOf("dial tcp i/o timeout"), messages(viewModel))
        job.cancel()
    }

    @Test
    fun `level filter and search apply together`() = runBlocking {
        val viewModel = viewModel()
        val job = launch { viewModel.uiState.collect {} }

        viewModel.toggleLevel(AppLogLevel.INFO)
        yield()
        assertEquals(listOf("kernel started"), messages(viewModel))

        // A search that only matches a line the level filter excludes leaves
        // nothing, and the screen must say so rather than look empty.
        viewModel.setQuery("timeout")
        yield()
        assertEquals(emptyList<String>(), messages(viewModel))
        assertTrue(viewModel.uiState.value.hasNoMatches)
        job.cancel()
    }

    @Test
    fun `no lines at all reads as empty, not as no matches`() = runBlocking {
        val viewModel = LogsViewModel(FakeLogStream(emptyList()))
        val job = launch { viewModel.uiState.collect {} }
        yield()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertFalse(viewModel.uiState.value.hasNoMatches)
        job.cancel()
    }

    private fun viewModel() = LogsViewModel(FakeLogStream(LINES))

    private fun messages(viewModel: LogsViewModel) =
        viewModel.uiState.value.lines.map { it.message }

    private companion object {
        val LINES = listOf(
            AppLogLine(AppLogLevel.INFO, "kernel", "kernel started", 1L, seq = 1L),
            AppLogLine(AppLogLevel.ERROR, "sing-box", "dial tcp i/o timeout", 2L, seq = 2L),
            AppLogLine(AppLogLevel.DEBUG, "kernel", "dns query ok", 3L, seq = 3L),
        )
    }
}

private class FakeLogStream(initial: List<AppLogLine>) : LogStream {
    private val state = MutableStateFlow(initial)
    override val lines: Flow<List<AppLogLine>> = state
}
