package com.justnpcx.starveil.feature.rules

import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.kernel.FlowLog
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.FlowRecord
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.ruleset.MainlandRuleSetStore
import com.justnpcx.starveil.core.data.ruleset.RulePackFailure
import com.justnpcx.starveil.core.data.ruleset.RulePackStore
import com.justnpcx.starveil.core.data.ruleset.RuleSetFailure
import com.justnpcx.starveil.core.model.InstalledRulePack
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.model.KillSwitchMode
import kotlinx.coroutines.flow.StateFlow
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene

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
        val viewModel = RulesViewModel(repository, FakeFlowLog(), FakePreferences(), NoRuleSets, NoRulePacks)
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
    fun `subscribing to a pack is a preference change and shows in force`() = runBlocking {
        val preferences = FakePreferences()
        val viewModel = RulesViewModel(FakeRules(), FakeFlowLog(), preferences, NoRuleSets, NoRulePacks)
        val job = launch { viewModel.uiState.collect {} }

        viewModel.setPackSubscribed("ads-enhanced", subscribed = true)
        yield()

        assertEquals(setOf("ads-enhanced"), viewModel.uiState.value.subscribedPacks)
        // The count the page shows includes it, so "N in force" cannot disagree
        // with the switch that was just turned on.
        assertEquals(1, viewModel.uiState.value.effectiveRuleCount - viewModel.uiState.value.presetRules.size)

        viewModel.setPackSubscribed("ads-enhanced", subscribed = false)
        yield()
        assertTrue(viewModel.uiState.value.subscribedPacks.isEmpty())
        job.cancel()
    }

    @Test
    fun `a blank value is not stored`() = runBlocking {
        val repository = FakeRules()
        val viewModel = RulesViewModel(repository, FakeFlowLog(), FakePreferences(), NoRuleSets, NoRulePacks)
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
        val viewModel = RulesViewModel(repository, FakeFlowLog(), FakePreferences(), NoRuleSets, NoRulePacks)
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
        val viewModel = RulesViewModel(repository, FakeFlowLog(), FakePreferences(), NoRuleSets, NoRulePacks)
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
        val viewModel = RulesViewModel(repository, FakeFlowLog(), FakePreferences(), NoRuleSets, NoRulePacks)
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

/** The flow view is not what these tests are about; it only has to exist. */
private class FakeFlowLog : FlowLog {
    override val records: Flow<List<FlowRecord>> = flowOf(emptyList())
}

private fun notUsed(): Nothing = throw NotImplementedError("not used by this test")


/**
 * Only [bypassMainland] is real; everything else is unreachable from this
 * ViewModel, so `notUsed()` is the honest implementation rather than a
 * plausible-looking default that nothing checks.
 */
private class FakePreferences(initiallyBypass: Boolean = false) : PreferencesRepository {
    private val bypass = MutableStateFlow(initiallyBypass)

    override val bypassMainland: Flow<Boolean> = bypass
    override val routePreset: Flow<RoutePreset> = flowOf(RoutePreset.GLOBAL_PROXY)
    private val ads = MutableStateFlow(false)
    override val blockAds: Flow<Boolean> = ads
    private val packs = MutableStateFlow<Set<String>>(emptySet())
    override val subscribedRulePacks: Flow<Set<String>> = packs
    override val localApiEnabled: Flow<Boolean> = flowOf(false)
    override val onboardingDone: Flow<Boolean> = flowOf(true)

    override val themeMode: Flow<ThemeMode> get() = notUsed()
    override val dynamicColor: Flow<Boolean> get() = notUsed()
    override val autoReconnect: Flow<Boolean> get() = notUsed()
    override val autoSwitchOnDrop: Flow<Boolean> get() = notUsed()
    override val killSwitchMode: Flow<KillSwitchMode> get() = notUsed()
    override val selectedProfileId: Flow<Long> get() = notUsed()
    override val selectedGroupId: Flow<Long> get() = notUsed()
    override val connectionTarget: Flow<ConnectionTarget> get() = notUsed()
    override val lastLeakCheck: Flow<LeakCheckRecord> get() = notUsed()
    override val scenes: Flow<List<Scene>> = flowOf(emptyList())
    override suspend fun setScenes(scenes: List<Scene>) { notUsed() }
    override val manualPresetAt: Flow<Long> = flowOf(0L)
    override suspend fun recordManualPresetChange(atEpochMs: Long) { notUsed() }
    override val lastHealthProbeAt: Flow<Long> get() = flowOf(0L)
    override suspend fun recordHealthProbe(atEpochMs: Long) { notUsed() }
    override val subscriptionLastSuccessAt: Flow<Long> get() = notUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = notUsed()
    override val subscriptionLastError: Flow<String?> get() = notUsed()
    override val perAppRoutes: Flow<Map<String, AppRoute>> get() = notUsed()

    override suspend fun setBypassMainland(enabled: Boolean) { bypass.value = enabled }
    override suspend fun setRoutePreset(preset: RoutePreset) { notUsed() }
    override suspend fun setBlockAds(enabled: Boolean) { ads.value = enabled }
    override suspend fun setRulePackSubscribed(id: String, subscribed: Boolean) {
        packs.value = if (subscribed) packs.value + id else packs.value - id
    }
    override suspend fun setLocalApiEnabled(enabled: Boolean) { notUsed() }
    override suspend fun setOnboardingDone(done: Boolean) { notUsed() }
    override suspend fun setThemeMode(mode: ThemeMode) { notUsed() }
    override suspend fun setDynamicColor(enabled: Boolean) { notUsed() }
    override suspend fun setAutoReconnect(enabled: Boolean) { notUsed() }
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) { notUsed() }
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) { notUsed() }
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) { notUsed() }
    override suspend fun recordLeakCheck(record: LeakCheckRecord) { notUsed() }
    override suspend fun setSelectedProfileId(id: Long) { notUsed() }
    override suspend fun setSelectedGroupId(id: Long) { notUsed() }
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) { notUsed() }
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) { notUsed() }

    private fun notUsed(): Nothing = error("not used by RulesViewModel")
}


/** No rule sets: the preset is not in force in these tests. */
private object NoRuleSets : MainlandRuleSetStore {
    override suspend fun ensure(): com.justnpcx.starveil.core.model.MainlandRuleSets? = null
    override suspend fun ensureAds(): String? = null
    override val failure: StateFlow<RuleSetFailure?> = MutableStateFlow(null)
}

/** No packs downloaded: these tests are about the subscription, not the files. */
private object NoRulePacks : RulePackStore {
    override suspend fun ensure(ids: Set<String>) = emptyList<InstalledRulePack>()
    override val failure: StateFlow<RulePackFailure?> = MutableStateFlow(null)
}
