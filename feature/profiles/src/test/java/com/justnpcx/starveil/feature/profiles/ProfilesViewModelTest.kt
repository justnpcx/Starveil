package com.justnpcx.starveil.feature.profiles

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.geo.CountryResolver
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.LatencySample
import com.justnpcx.starveil.core.model.NodeRegion
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.subscription.Clock
import com.justnpcx.starveil.core.subscription.LinkImporter
import com.justnpcx.starveil.core.subscription.LinkExporter
import com.justnpcx.starveil.core.subscription.LinkResolver
import com.justnpcx.starveil.core.ui.snackbar.UndoBus
import com.justnpcx.starveil.core.ui.snackbar.UndoRequest
import com.justnpcx.starveil.core.subscription.SubscriptionFetcher
import com.justnpcx.starveil.core.subscription.SubscriptionHealthSink
import com.justnpcx.starveil.core.subscription.SubscriptionRefreshScheduler
import com.justnpcx.starveil.core.subscription.SubscriptionUpdater
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deleting a node has one non-obvious consequence: if it was the selected
 * one, the selection has to go with it. Leaving it set would have the
 * dashboard name a node that no longer exists, and the next connect would
 * try to build a config from a profile that is gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleting the selected node clears the selection`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one"), profile(TWO, "two")))
        val preferences = FakePreferences(ONE)
        val viewModel = viewModel(profiles, preferences)

        viewModel.delete(ONE)

        assertNull(profiles.getById(ONE))
        assertEquals(listOf(TWO), profiles.remainingIds())
        assertEquals(0L, preferences.selectedProfileId.first())
    }

    @Test
    fun `deleting another node leaves the selection alone`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one"), profile(TWO, "two")))
        val preferences = FakePreferences(ONE)
        val viewModel = viewModel(profiles, preferences)

        viewModel.delete(TWO)

        assertNull(profiles.getById(TWO))
        assertEquals(listOf(ONE), profiles.remainingIds())
        assertEquals(ONE, preferences.selectedProfileId.first())
    }

    @Test
    fun `filtering by a group shows only that group's nodes`() = runBlocking {
        val profiles = FakeProfiles(
            mutableListOf(
                // A share-link import has no group; only "All" shows it.
                profile(ONE, "one").copy(groupId = 0L),
                profile(TWO, "two").copy(groupId = SUB),
            ),
        )
        val viewModel = viewModel(profiles, FakePreferences(ONE), groupsWithSub())
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.filterByGroup(SUB)
        yield()

        assertEquals(listOf(TWO), viewModel.uiState.value.profiles.map { it.id })
        assertFalse(viewModel.uiState.value.hasNoMatches)
        job.cancel()
    }

    @Test
    fun `a filter that matches nothing says so, rather than looking empty`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val viewModel = viewModel(profiles, FakePreferences(ONE), groupsWithSub())
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.filterByGroup(SUB)
        yield()

        val state = viewModel.uiState.value
        assertTrue(state.profiles.isEmpty())
        assertTrue(state.hasNoMatches)
        // The node exists; the filter is what hides it.
        assertTrue(state.hasProfiles)
        job.cancel()
    }

    @Test
    fun `clearing the filter shows everything again`() = runBlocking {
        val profiles = FakeProfiles(
            mutableListOf(
                profile(ONE, "one").copy(groupId = 0L),
                profile(TWO, "two").copy(groupId = SUB),
            ),
        )
        val viewModel = viewModel(profiles, FakePreferences(ONE), groupsWithSub())
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.filterByGroup(SUB)
        yield()
        viewModel.filterByGroup(null)
        yield()

        assertEquals(listOf(ONE, TWO), viewModel.uiState.value.profiles.map { it.id }.sorted())
        job.cancel()
    }

    @Test
    fun `renaming a group trims the name and keeps the id`() = runBlocking {
        val groups = FakeGroups(listOf(ProxyGroup(id = SUB, name = "old")))
        val viewModel = viewModel(FakeProfiles(mutableListOf()), FakePreferences(ONE), groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.renameGroup(ProxyGroup(id = SUB, name = "old"), "  new name  ")
        yield()

        assertEquals(SUB, groups.upserted?.id)
        assertEquals("new name", groups.upserted?.name)
        job.cancel()
    }

    @Test
    fun `a blank rename is ignored`() = runBlocking {
        val groups = FakeGroups(listOf(ProxyGroup(id = SUB, name = "old")))
        val viewModel = viewModel(FakeProfiles(mutableListOf()), FakePreferences(ONE), groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.renameGroup(ProxyGroup(id = SUB, name = "old"), "   ")
        yield()

        assertNull(groups.upserted)
        job.cancel()
    }

    @Test
    fun `deleting a group removes its nodes and drops the filter`() = runBlocking {
        val profiles = FakeProfiles(
            mutableListOf(
                profile(ONE, "one").copy(groupId = SUB),
                profile(TWO, "two").copy(groupId = 0L),
            ),
        )
        val groups = FakeGroups(listOf(ProxyGroup(id = SUB, name = "sub")))
        val scheduler = FakeRefreshScheduler()
        val viewModel = viewModel(profiles, FakePreferences(TWO), groups, scheduler)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.filterByGroup(SUB)
        yield()
        viewModel.deleteGroup(ProxyGroup(id = SUB, name = "sub", profileIds = listOf(ONE)))
        yield()

        assertEquals(SUB, groups.deletedId)
        assertEquals(listOf(TWO), profiles.remainingIds())
        // The refresh work must not outlive the group it refreshes.
        assertEquals(listOf(SUB), scheduler.cancelled)
        // The filter pointed at a group that no longer exists.
        assertNull(viewModel.uiState.value.groupFilter)
        job.cancel()
    }

    @Test
    fun `deleting a group clears the selection when it held the selected node`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one").copy(groupId = SUB)))
        val preferences = FakePreferences(ONE)
        val viewModel = viewModel(profiles, preferences, FakeGroups(listOf(ProxyGroup(id = SUB, name = "sub"))))
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.deleteGroup(ProxyGroup(id = SUB, name = "sub", profileIds = listOf(ONE)))
        yield()

        assertEquals(0L, preferences.selectedProfileId.first())
        job.cancel()
    }

    @Test
    fun `creating a group trims the name and leaves the id unset`() = runBlocking {
        val groups = FakeGroups()
        val viewModel = viewModel(FakeProfiles(mutableListOf()), FakePreferences(ONE), groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.createGroup("  my folder  ")
        yield()

        // id 0 is what makes the repository insert rather than update.
        assertEquals(0L, groups.upserted?.id)
        assertEquals("my folder", groups.upserted?.name)
        job.cancel()
    }

    @Test
    fun `a blank group name is ignored`() = runBlocking {
        val groups = FakeGroups()
        val viewModel = viewModel(FakeProfiles(mutableListOf()), FakePreferences(ONE), groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.createGroup("   ")
        yield()

        assertNull(groups.upserted)
        job.cancel()
    }

    @Test
    fun `moving a node into a group rewrites only its group`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val viewModel = viewModel(profiles, FakePreferences(ONE), groupsWithSub())
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.moveToGroup(profile(ONE, "one"), SUB)
        yield()

        val moved = profiles.upserted
        assertEquals(SUB, moved?.groupId)
        // Everything else has to survive the move.
        assertEquals("one", moved?.name)
        assertEquals("one.example", moved?.server)
        job.cancel()
    }

    @Test
    fun `moving a node out of a group uses no-group`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one").copy(groupId = SUB)))
        val viewModel = viewModel(profiles, FakePreferences(ONE), groupsWithSub())
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.moveToGroup(profile(ONE, "one").copy(groupId = SUB), null)
        yield()

        assertEquals(0L, profiles.upserted?.groupId)
        job.cancel()
    }

    @Test
    fun `moving a node to the group it is already in writes nothing`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one").copy(groupId = SUB)))
        val viewModel = viewModel(profiles, FakePreferences(ONE), groupsWithSub())
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.moveToGroup(profile(ONE, "one").copy(groupId = SUB), SUB)
        yield()

        assertNull(profiles.upserted)
        job.cancel()
    }

    @Test
    fun `connecting through a group makes it the target and drops the node`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val preferences = FakePreferences(ONE)
        val groups = FakeGroups(listOf(subGroup(GroupMode.URL_TEST)))
        val viewModel = viewModel(profiles, preferences, groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.selectGroup(subGroup(GroupMode.URL_TEST))
        yield()

        assertEquals(SUB, preferences.selectedGroupId.first())
        // A node and a group are alternatives, so picking the group must let go
        // of the node rather than leaving both set.
        assertEquals(0L, preferences.selectedProfileId.first())
        assertEquals(SUB, viewModel.uiState.value.selectedGroupId)
        assertEquals(0L, viewModel.uiState.value.selectedId)
        job.cancel()
    }

    @Test
    fun `selecting a node drops the group target`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val preferences = FakePreferences(0L)
        val groups = FakeGroups(listOf(subGroup(GroupMode.URL_TEST)))
        val viewModel = viewModel(profiles, preferences, groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.selectGroup(subGroup(GroupMode.URL_TEST))
        yield()
        viewModel.select(ONE)
        yield()

        assertEquals(ONE, preferences.selectedProfileId.first())
        assertEquals(0L, preferences.selectedGroupId.first())
        job.cancel()
    }

    @Test
    fun `turning the group in use into a folder stops using it`() = runBlocking {
        // A folder is not something to connect through, so it cannot stay the
        // target once its mode says otherwise.
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val preferences = FakePreferences(0L)
        val groups = FakeGroups(listOf(subGroup(GroupMode.URL_TEST)))
        val viewModel = viewModel(profiles, preferences, groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.selectGroup(subGroup(GroupMode.URL_TEST))
        yield()
        viewModel.setGroupMode(subGroup(GroupMode.URL_TEST), GroupMode.MANUAL)
        yield()

        assertEquals(GroupMode.MANUAL, groups.upserted?.mode)
        assertEquals(0L, preferences.selectedGroupId.first())
        job.cancel()
    }

    @Test
    fun `changing the mode of a group that is not in use leaves the target alone`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val preferences = FakePreferences(ONE)
        val groups = FakeGroups(listOf(subGroup(GroupMode.URL_TEST)))
        val viewModel = viewModel(profiles, preferences, groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.setGroupMode(subGroup(GroupMode.URL_TEST), GroupMode.MANUAL)
        yield()

        assertEquals(GroupMode.MANUAL, groups.upserted?.mode)
        assertEquals(ONE, preferences.selectedProfileId.first())
        job.cancel()
    }

    @Test
    fun `deleting the group in use clears the target`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val preferences = FakePreferences(0L)
        val groups = FakeGroups(listOf(subGroup(GroupMode.URL_TEST)))
        val viewModel = viewModel(profiles, preferences, groups)
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.selectGroup(subGroup(GroupMode.URL_TEST))
        yield()
        viewModel.deleteGroup(subGroup(GroupMode.URL_TEST))
        yield()

        assertEquals(0L, preferences.selectedGroupId.first())
        job.cancel()
    }

    @Test
    fun `refreshing a subscription replaces its nodes and re-arms the schedule`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "old").copy(groupId = SUB)))
        val scheduler = FakeRefreshScheduler()
        val fetcher = FakeFetcher(
            body = base64Links("vless://u1@h1:443#node1", "vless://u2@h2:443#node2"),
        )
        val viewModel = viewModel(
            profiles,
            FakePreferences(0L),
            FakeGroups(listOf(subGroupWithUrl())),
            scheduler,
            fetcher,
        )
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.refreshSubscription(subGroupWithUrl())
        yield()

        assertEquals(SUB_URL, fetcher.lastUrl)
        assertEquals(SUB, profiles.replacedGroupId)
        assertEquals(2, profiles.replacedCount)
        // Re-arming is the repair, not a side effect: a group imported before
        // the scheduler got its first caller has a URL and no work, so a manual
        // refresh is the only thing that can put it back on the cadence.
        assertTrue(scheduler.scheduled.contains(SUB))
        assertEquals(ProfilesMessage.Refreshed(2), awaitMessage(viewModel))
        job.cancel()
    }

    @Test
    fun `refreshing a folder is a no-op`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one").copy(groupId = SUB)))
        val scheduler = FakeRefreshScheduler()
        val fetcher = FakeFetcher(body = "ignored")
        val folder = ProxyGroup(id = SUB, name = "folder")
        val viewModel = viewModel(
            profiles,
            FakePreferences(0L),
            FakeGroups(listOf(folder)),
            scheduler,
            fetcher,
        )
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.refreshSubscription(folder)
        yield()

        // No URL means no request, no write, and nothing to say.
        assertNull(fetcher.lastUrl)
        assertEquals(0, profiles.replaceCalls)
        assertTrue(scheduler.scheduled.isEmpty())
        assertNull(viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `a failed refresh reports the reason and keeps the old nodes`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "old").copy(groupId = SUB)))
        val viewModel = viewModel(
            profiles,
            FakePreferences(0L),
            FakeGroups(listOf(subGroupWithUrl())),
            FakeRefreshScheduler(),
            FakeFetcher(error = IOException("HTTP 500")),
        )
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.refreshSubscription(subGroupWithUrl())
        yield()

        // The last-good nodes survive a failed refresh; the failure is reported
        // rather than silently leaving a stale list.
        assertEquals(0, profiles.replaceCalls)
        assertEquals(listOf(ONE), profiles.remainingIds())
        assertEquals(ProfilesMessage.RefreshFailed("HTTP 500"), awaitMessage(viewModel))
        job.cancel()
    }

    /**
     * The message reaches [ProfilesUiState] through two `combine`s, so it lands
     * a dispatch turn or two after the call returns. Asserting immediately
     * reads a stale state.
     */
    private suspend fun awaitMessage(viewModel: ProfilesViewModel): ProfilesMessage? {
        repeat(20) {
            viewModel.uiState.value.message?.let { return it }
            yield()
        }
        return viewModel.uiState.value.message
    }

    /**
     * The share sheet is reached through a second `combine`, so it lands a
     * dispatch turn or two after the call.
     */
    private suspend fun awaitShare(viewModel: ProfilesViewModel): ShareTarget? {
        repeat(20) {
            viewModel.uiState.value.share?.let { return it }
            yield()
        }
        return viewModel.uiState.value.share
    }

    @Test
    fun `sharing a node puts its link in the sheet`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val viewModel = viewModel(
            profiles,
            FakePreferences(0L),
            exporter = LinkExporter { "vless://one@example.com:443" },
        )
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.shareNode(profile(ONE, "one"))
        val share = awaitShare(viewModel)

        assertEquals("one", share?.name)
        assertEquals("vless://one@example.com:443", share?.link)

        viewModel.dismissShare()
        yield()
        assertNull(viewModel.uiState.value.share)
        job.cancel()
    }

    @Test
    fun `a node with no share format still opens the sheet, with no link`() = runBlocking {
        // The sheet has to say "no share format" rather than draw a QR code for
        // an empty string, which would scan into nothing.
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val viewModel = viewModel(profiles, FakePreferences(0L), exporter = LinkExporter { null })
        val job = launch { viewModel.uiState.collect {} }
        yield()

        viewModel.shareNode(profile(ONE, "one"))

        assertNotNull(awaitShare(viewModel))
        assertNull(viewModel.uiState.value.share?.link)
        job.cancel()
    }

    @Test
    fun `deleting a node offers the way back, and the undo restores it`() = runBlocking {
        val profiles = FakeProfiles(mutableListOf(profile(ONE, "one")))
        val undo = RecordingUndo()
        val viewModel = viewModel(profiles, FakePreferences(ONE), undo = undo)

        viewModel.deleteWithUndo(profile(ONE, "one"))
        yield()

        // Gone at once — no dialog stands between the gesture and the delete —
        // and the reversal is offered by name.
        assertNull(profiles.getById(ONE))
        assertEquals(R.string.profiles_deleted, undo.request?.messageRes)
        assertEquals(listOf("one"), undo.request?.args)

        undo.request!!.onUndo()
        yield()

        // Back exactly as it was: same id, so the row is the same row.
        assertNotNull(profiles.getById(ONE))
    }

    private fun base64Links(vararg links: String): String =
        Base64.getEncoder().encodeToString(links.joinToString("\n").toByteArray())

    private fun subGroupWithUrl() =
        ProxyGroup(id = SUB, name = "sub", subscriptionUrl = SUB_URL)

    private fun subGroup(mode: GroupMode) =
        ProxyGroup(id = SUB, name = "sub", mode = mode, profileIds = listOf(ONE))

    private fun groupsWithSub() = FakeGroups(listOf(ProxyGroup(id = SUB, name = "sub")))

    private fun viewModel(
        profiles: ProfileRepository,
        preferences: PreferencesRepository,
        groups: GroupRepository = FakeGroups(),
        refreshScheduler: SubscriptionRefreshScheduler = FakeRefreshScheduler(),
        fetcher: SubscriptionFetcher = FakeFetcher(),
        exporter: LinkExporter = LinkExporter { null },
        undo: UndoBus = RecordingUndo(),
        nodeHealth: NodeHealthRepository = FakeNodeHealth(),
    ): ProfilesViewModel {
        // A subscription body is base64'd share links, so the registry needs a
        // resolver that can turn one into a profile; otherwise every body would
        // parse to zero nodes.
        val updater = SubscriptionUpdater(
            registry = SubscriptionFormatRegistry.default { link ->
                if (link.startsWith("vless://")) {
                    Profile(
                        name = link.substringAfter('#', "node"),
                        type = ProfileType.VLESS,
                        // Read from the link, like the real resolver: a
                        // constant makes every fixture node a duplicate.
                        server = link.substringAfter('@').substringBefore(':'),
                        port = 443,
                    )
                } else {
                    null
                }
            },
            profiles = profiles,
            health = FakeHealth(),
            fetcher = fetcher,
            clock = Clock { 0L },
        )
        return ProfilesViewModel(
            profiles = profiles,
            groups = groups,
            preferences = preferences,
            refreshScheduler = refreshScheduler,
            exporter = exporter,
            updater = updater,
            undo = undo,
            nodeHealth = nodeHealth,
            countryResolver = NoCountries,
            // Import is not what this test exercises; it only has to exist.
            importer = LinkImporter(
                resolver = LinkResolver { null },
                profiles = profiles,
                groups = groups,
                updater = updater,
                refreshScheduler = refreshScheduler,
            ),
        )
    }

    private fun profile(id: Long, name: String) = Profile(
        id = id,
        name = name,
        type = ProfileType.VLESS,
        server = "$name.example",
        port = 443,
    )

    private companion object {
        const val ONE = 1L
        const val TWO = 2L
        const val SUB = 7L
        const val SUB_URL = "https://sub.example.com/abc?token=SECRET"
    }
}

private class FakeProfiles(private val all: MutableList<Profile>) : ProfileRepository {

    var upserted: Profile? = null
        private set

    override fun observeAll(): Flow<List<Profile>> = flowOf(all.toList())

    override fun observeByGroup(groupId: Long): Flow<List<Profile>> =
        flowOf(all.filter { it.groupId == groupId })

    override suspend fun getById(id: Long): Profile? = all.firstOrNull { it.id == id }

    override suspend fun upsert(profile: Profile): Long {
        upserted = profile
        // Mirrors the real repository: an upsert replaces the row with that id.
        // Without this the fake accepted writes and kept none of them, which
        // is how a restore test passes while the restore does nothing.
        all.removeAll { it.id == profile.id }
        all += profile
        return profile.id
    }

    override suspend fun deleteById(id: Long) {
        all.removeAll { it.id == id }
    }

    override suspend fun deleteByGroup(groupId: Long) {
        all.removeAll { it.groupId == groupId }
    }

    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) {
        replaceCalls++
        replacedGroupId = groupId
        replacedCount = profiles.size
        all.removeAll { it.groupId == groupId }
        all += profiles
    }

    var replaceCalls = 0
        private set
    var replacedGroupId: Long? = null
        private set
    var replacedCount = 0
        private set

    fun remainingIds(): List<Long> = all.map { it.id }
}

private class FakePreferences(initialId: Long) : PreferencesRepository {

    private val target = MutableStateFlow<ConnectionTarget>(
        if (initialId != 0L) ConnectionTarget.Node(initialId) else ConnectionTarget.None,
    )

    override val selectedProfileId: Flow<Long> =
        target.map { (it as? ConnectionTarget.Node)?.id ?: 0L }
    override val selectedGroupId: Flow<Long> =
        target.map { (it as? ConnectionTarget.Group)?.id ?: 0L }
    override val connectionTarget: Flow<ConnectionTarget> = target

    override suspend fun setSelectedProfileId(id: Long) {
        // Clearing the node must not disturb a group that is selected instead;
        // the real repository only touches its own key.
        target.value = when {
            id != 0L -> ConnectionTarget.Node(id)
            target.value is ConnectionTarget.Node -> ConnectionTarget.None
            else -> target.value
        }
    }

    override suspend fun setSelectedGroupId(id: Long) {
        target.value = when {
            id != 0L -> ConnectionTarget.Group(id)
            target.value is ConnectionTarget.Group -> ConnectionTarget.None
            else -> target.value
        }
    }

    // The rest of the preferences are not what this test is about.
    override val themeMode: Flow<ThemeMode> get() = notUsed()
    override val dynamicColor: Flow<Boolean> get() = notUsed()
    override val autoReconnect: Flow<Boolean> get() = notUsed()
    override val autoSwitchOnDrop: Flow<Boolean> get() = notUsed()
    override val bypassMainland: Flow<Boolean> get() = notUsed()
    override val routePreset: Flow<RoutePreset> = flowOf(RoutePreset.GLOBAL_PROXY)
    override val blockAds: Flow<Boolean> = flowOf(false)
    override val killSwitchMode: Flow<KillSwitchMode> get() = notUsed()
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
    override suspend fun setThemeMode(mode: ThemeMode) { notUsed() }
    override suspend fun setDynamicColor(enabled: Boolean) { notUsed() }
    override suspend fun setBypassMainland(enabled: Boolean) { notUsed() }
    override suspend fun setRoutePreset(preset: RoutePreset) { notUsed() }
    override suspend fun setBlockAds(enabled: Boolean) { notUsed() }
    override suspend fun setAutoReconnect(enabled: Boolean) { notUsed() }
    override suspend fun setAutoSwitchOnDrop(enabled: Boolean) { notUsed() }
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) { notUsed() }
    override suspend fun recordLeakCheck(record: LeakCheckRecord) { notUsed() }
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) { notUsed() }
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) { notUsed() }
    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) { notUsed() }
}

private class FakeGroups(private val all: List<ProxyGroup> = emptyList()) : GroupRepository {

    var upserted: ProxyGroup? = null
        private set
    var deletedId: Long? = null
        private set

    override fun observeAll(): Flow<List<ProxyGroup>> = flowOf(all)
    override suspend fun getById(id: Long): ProxyGroup? = all.firstOrNull { it.id == id }
    override suspend fun findBySubscriptionUrl(url: String): ProxyGroup? =
        all.firstOrNull { it.subscriptionUrl == url }

    override suspend fun findByRegion(region: NodeRegion): ProxyGroup? =
        all.firstOrNull { it.region == region }

    override suspend fun upsert(group: ProxyGroup): Long {
        upserted = group
        return group.id
    }

    override suspend fun deleteById(id: Long) {
        deletedId = id
    }
}

private class FakeHealth : SubscriptionHealthSink {
    var lastSuccessAt: Long? = null
        private set
    var lastReason: String? = null
        private set

    override suspend fun recordSuccess(atEpochMs: Long) {
        lastSuccessAt = atEpochMs
    }

    override suspend fun recordFailure(atEpochMs: Long, reason: String) {
        lastReason = reason
    }
}

private class FakeFetcher(
    private val body: String? = null,
    private val error: Exception? = null,
) : SubscriptionFetcher {
    var lastUrl: String? = null
        private set

    override suspend fun fetch(url: String): String {
        lastUrl = url
        error?.let { throw it }
        return body!!
    }
}

private class FakeRefreshScheduler : SubscriptionRefreshScheduler {
    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(groupId: Long) { scheduled += groupId }
    override fun cancel(groupId: Long) { cancelled += groupId }
}

/** No history: the list tests are about the list, not the curve. */
private class FakeNodeHealth : NodeHealthRepository {
    override fun observeAll(): Flow<Map<Long, List<LatencySample>>> = flowOf(emptyMap())
    override suspend fun record(
        profileId: Long,
        latencyMs: Long,
        atMs: Long,
        weightTenths: Int,
    ) = notUsed()
    override suspend fun forget(profileId: Long) = notUsed()
}

private fun notUsed(): Nothing = throw NotImplementedError("not used by this test")

/** Records the undo the view model offered, so the test can run it. */
private class RecordingUndo : UndoBus {
    var request: UndoRequest? = null
        private set

    override fun show(messageRes: Int, args: List<Any>, onUndo: () -> Unit) {
        request = UndoRequest(messageRes = messageRes, args = args, onUndo = onUndo)
    }
}

/** Nothing is looked up in these tests; the list only has to render. */
private object NoCountries : CountryResolver {
    override suspend fun countryOf(host: String): String? = null
}
