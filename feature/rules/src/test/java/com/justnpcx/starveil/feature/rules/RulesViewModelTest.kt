package com.justnpcx.starveil.feature.rules

import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The rule list. Order is the routing decision — the first match wins — so
 * these cover the ordering behaviour as much as the CRUD.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `add appends after the last rule`() = runBlocking {
        val repository = FakeRules()
        val viewModel = RulesViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }

        viewModel.add(MatcherKind.DOMAIN_SUFFIX, "example.com", RuleAction.DIRECT)
        yield()
        viewModel.add(MatcherKind.IP_CIDR, "10.0.0.0/8", RuleAction.BLOCK)
        yield()

        assertEquals(listOf(0, 1), repository.ordered().map { it.order })
        assertEquals(
            RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "example.com"),
            repository.ordered()[0].matcher,
        )
        assertEquals(RuleAction.BLOCK, repository.ordered()[1].action)
        job.cancel()
    }

    @Test
    fun `a blank value is not stored`() = runBlocking {
        val repository = FakeRules()
        val viewModel = RulesViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }

        viewModel.add(MatcherKind.DOMAIN, "   ", RuleAction.PROXY)
        yield()

        assertTrue(repository.ordered().isEmpty())
        job.cancel()
    }

    @Test
    fun `move renumbers so rules that shared an order still swap`() = runBlocking {
        // Both at order 0 — what rows added before ordering existed look like.
        // Swapping the two stored values would be a no-op here.
        val repository = FakeRules().apply {
            seed(Rule(id = 1L, matcher = RuleMatcher(MatcherKind.DOMAIN, "a"), action = RuleAction.PROXY, order = 0))
            seed(Rule(id = 2L, matcher = RuleMatcher(MatcherKind.DOMAIN, "b"), action = RuleAction.PROXY, order = 0))
        }
        val viewModel = RulesViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.move(repository.ordered().first { it.id == 2L }, up = true)
        yield()

        assertEquals(listOf(2L, 1L), repository.ordered().map { it.id })
        assertEquals(listOf(0, 1), repository.ordered().map { it.order })
        job.cancel()
    }

    @Test
    fun `setEnabled persists the flag`() = runBlocking {
        val repository = FakeRules().apply {
            seed(Rule(id = 1L, matcher = RuleMatcher(MatcherKind.DOMAIN, "a"), action = RuleAction.PROXY))
        }
        val viewModel = RulesViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.setEnabled(repository.ordered().first(), enabled = false)
        yield()

        assertEquals(false, repository.ordered().first().enabled)
        job.cancel()
    }

    @Test
    fun `delete removes the rule`() = runBlocking {
        val repository = FakeRules().apply {
            seed(Rule(id = 1L, matcher = RuleMatcher(MatcherKind.DOMAIN, "a"), action = RuleAction.PROXY))
        }
        val viewModel = RulesViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.delete(repository.ordered().first())
        yield()

        assertTrue(repository.ordered().isEmpty())
        job.cancel()
    }
}

/** Reactive, so the ViewModel sees its own writes — as it does against Room. */
private class FakeRules : RuleRepository {

    private val state = MutableStateFlow<List<Rule>>(emptyList())
    private var nextId = 1L

    override fun observeAllOrdered(): Flow<List<Rule>> =
        state.map { rows -> rows.sortedWith(compareBy({ it.order }, { it.id })) }

    override suspend fun getById(id: Long): Rule? = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(rule: Rule): Long {
        val id = if (rule.id == 0L) nextId++ else rule.id
        state.value = state.value.filterNot { it.id == id } + rule.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    /** Seeds a row without going through upsert, to control the id. */
    fun seed(rule: Rule) {
        state.value = state.value + rule
    }

    fun ordered(): List<Rule> = state.value.sortedWith(compareBy({ it.order }, { it.id }))
}
