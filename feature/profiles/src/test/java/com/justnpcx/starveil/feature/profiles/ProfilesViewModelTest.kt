package com.justnpcx.starveil.feature.profiles

import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.preferences.ThemeMode
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.subscription.Clock
import com.justnpcx.starveil.core.subscription.LinkImporter
import com.justnpcx.starveil.core.subscription.LinkResolver
import com.justnpcx.starveil.core.subscription.SubscriptionFetcher
import com.justnpcx.starveil.core.subscription.SubscriptionHealthSink
import com.justnpcx.starveil.core.subscription.SubscriptionUpdater
import com.justnpcx.starveil.core.subscription.registry.SubscriptionFormatRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun viewModel(profiles: ProfileRepository, preferences: PreferencesRepository) =
        ProfilesViewModel(
            profiles = profiles,
            preferences = preferences,
            // Import is not what this test exercises; it only has to exist.
            importer = LinkImporter(
                resolver = LinkResolver { null },
                profiles = profiles,
                groups = FakeGroups(),
                updater = SubscriptionUpdater(
                    registry = SubscriptionFormatRegistry.default { null },
                    profiles = profiles,
                    health = FakeHealth(),
                    fetcher = FakeFetcher(),
                    clock = Clock { 0L },
                ),
            ),
        )

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
    }
}

private class FakeProfiles(private val all: MutableList<Profile>) : ProfileRepository {

    override fun observeAll(): Flow<List<Profile>> = flowOf(all.toList())

    override fun observeByGroup(groupId: Long): Flow<List<Profile>> =
        flowOf(all.filter { it.groupId == groupId })

    override suspend fun getById(id: Long): Profile? = all.firstOrNull { it.id == id }

    override suspend fun upsert(profile: Profile): Long = notUsed()

    override suspend fun deleteById(id: Long) {
        all.removeAll { it.id == id }
    }

    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) = notUsed()

    fun remainingIds(): List<Long> = all.map { it.id }
}

private class FakePreferences(initialId: Long) : PreferencesRepository {

    private val id = MutableStateFlow(initialId)

    override val selectedProfileId: Flow<Long> = id

    override suspend fun setSelectedProfileId(id: Long) {
        this.id.value = id
    }

    // The rest of the preferences are not what this test is about.
    override val themeMode: Flow<ThemeMode> get() = notUsed()
    override val dynamicColor: Flow<Boolean> get() = notUsed()
    override val autoReconnect: Flow<Boolean> get() = notUsed()
    override val killSwitchMode: Flow<KillSwitchMode> get() = notUsed()
    override val speedTestUrl: Flow<String> get() = notUsed()
    override val lastLeakCheck: Flow<LeakCheckRecord> get() = notUsed()
    override val subscriptionLastSuccessAt: Flow<Long> get() = notUsed()
    override val subscriptionLastFailureAt: Flow<Long> get() = notUsed()
    override val subscriptionLastError: Flow<String?> get() = notUsed()
    override suspend fun setThemeMode(mode: ThemeMode) { notUsed() }
    override suspend fun setDynamicColor(enabled: Boolean) { notUsed() }
    override suspend fun setAutoReconnect(enabled: Boolean) { notUsed() }
    override suspend fun setKillSwitchMode(mode: KillSwitchMode) { notUsed() }
    override suspend fun recordLeakCheck(record: LeakCheckRecord) { notUsed() }
    override suspend fun setSpeedTestUrl(url: String) { notUsed() }
    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) { notUsed() }
    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) { notUsed() }
}

private class FakeGroups : GroupRepository {
    override fun observeAll(): Flow<List<ProxyGroup>> = notUsed()
    override suspend fun getById(id: Long): ProxyGroup? = notUsed()
    override suspend fun upsert(group: ProxyGroup): Long = notUsed()
    override suspend fun deleteById(id: Long) { notUsed() }
}

private class FakeHealth : SubscriptionHealthSink {
    override suspend fun recordSuccess(atEpochMs: Long) { notUsed() }
    override suspend fun recordFailure(atEpochMs: Long, reason: String) { notUsed() }
}

private class FakeFetcher : SubscriptionFetcher {
    override suspend fun fetch(url: String): String = notUsed()
}

private fun notUsed(): Nothing = throw NotImplementedError("not used by this test")
