package com.justnpcx.starveil.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.justnpcx.starveil.core.model.AppRoute
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/**
 * The one thing the tunnel points at.
 *
 * A node and a group are alternatives, so they are modelled as one value
 * rather than two flags that could disagree. Reading them as a single flow
 * also matters for correctness: two separate flows over the same DataStore can
 * emit in either order, which would show up as a momentary "nothing is
 * selected" and make the dashboard tear down a healthy tunnel.
 */
sealed interface ConnectionTarget {
    data object None : ConnectionTarget

    data class Node(val id: Long) : ConnectionTarget

    data class Group(val id: Long) : ConnectionTarget
}

/** Theme selection. Stored as a String; unknown stored values fall back to SYSTEM. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * Whether [this] mode means "dark", given whether the system is dark.
 *
 * SYSTEM follows the system; DARK and LIGHT are explicit overrides. Pure, so
 * the mapping is unit-testable without a Compose runtime.
 */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

private val Context.starveilDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "starveil_preferences",
)

/**
 * Typed user preferences, backed by DataStore. Feature modules depend on
 * this interface, never on DataStore directly.
 *
 * Keys are the single source of truth for preference names — no string
 * literals scattered across the app.
 */
interface PreferencesRepository {
    val themeMode: Flow<ThemeMode>
    val dynamicColor: Flow<Boolean>
    val autoReconnect: Flow<Boolean>
    val killSwitchMode: Flow<KillSwitchMode>

    /**
     * Route domestic traffic direct and everything else through the tunnel.
     *
     * Only a preference. Whether it can actually be applied depends on the two
     * rule set files being on disk, which is [MainlandRuleSetStore]'s business —
     * a toggle that is on while the files are missing simply builds a config
     * without the preset, rather than failing to connect.
     */
    val bypassMainland: Flow<Boolean>

    /**
     * Currently selected node; 0 = none chosen yet.
     *
     * A node and a group are alternative connection targets, so at most one is
     * ever set — the setters below clear the other. That invariant lives here
     * rather than in the UI so no caller can leave both set.
     */
    val selectedProfileId: Flow<Long>

    /** Currently selected group to connect through; 0 = none. */
    val selectedGroupId: Flow<Long>

    /**
     * The connection target, read atomically. Prefer this over watching the two
     * ids above when deciding what to connect to.
     */
    val connectionTarget: Flow<ConnectionTarget>

    /**
     * Where each listed app's traffic goes.
     *
     * Absence from the map means "follow the tunnel's default target", so this
     * is an exception list rather than a switch. Applied as `package_name`
     * route rules at connect time, so a change takes effect on the next
     * connection — the config is built then, not continuously.
     */
    val perAppRoutes: Flow<Map<String, AppRoute>>

    /** Last leak self-check (minimal M2 version). */
    val lastLeakCheck: Flow<LeakCheckRecord>

    /** Epoch ms of the last successful subscription refresh; 0 = never. */
    val subscriptionLastSuccessAt: Flow<Long>

    /** Epoch ms of the last failed subscription refresh; 0 = never. */
    val subscriptionLastFailureAt: Flow<Long>

    /** Redacted reason for the last failure; null when the last run succeeded. */
    val subscriptionLastError: Flow<String?>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAutoReconnect(enabled: Boolean)
    suspend fun setBypassMainland(enabled: Boolean)

    suspend fun setPerAppRoutes(routes: Map<String, AppRoute>)
    suspend fun setKillSwitchMode(mode: KillSwitchMode)
    suspend fun recordLeakCheck(record: LeakCheckRecord)
    suspend fun setSelectedProfileId(id: Long)

    /** Selecting a group clears the node selection, and vice versa. */
    suspend fun setSelectedGroupId(id: Long)

    /** Record a successful subscription refresh. */
    suspend fun recordSubscriptionSuccess(atEpochMs: Long)

    /**
     * Record a failed refresh. [reason] must already be redacted — it is
     * surfaced in the UI and must never contain a URL, token or credentials.
     */
    suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String)
}

class DataStorePreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PreferencesRepository {

    private val dataStore = context.starveilDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val BYPASS_MAINLAND = booleanPreferencesKey("bypass_mainland")
        val PER_APP_DIRECT = stringPreferencesKey("per_app_routes")
        val KILL_SWITCH_MODE = stringPreferencesKey("kill_switch_mode")
        val LEAK_CHECK_AT = longPreferencesKey("leak_check_at")
        val LEAK_CHECK_LEAKING = booleanPreferencesKey("leak_check_leaking")
        val SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")
        val SELECTED_GROUP_ID = longPreferencesKey("selected_group_id")
        val SUB_LAST_SUCCESS = longPreferencesKey("subscription_last_success_at")
        val SUB_LAST_FAILURE = longPreferencesKey("subscription_last_failure_at")
        val SUB_LAST_ERROR = stringPreferencesKey("subscription_last_error")
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs ->
            runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM)
        }

    override val dynamicColor: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.DYNAMIC_COLOR] ?: true }

    override val autoReconnect: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.AUTO_RECONNECT] ?: true }

    override val bypassMainland: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.BYPASS_MAINLAND] ?: false }

    override val killSwitchMode: Flow<KillSwitchMode> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> KillSwitchMode.fromStorage(prefs[Keys.KILL_SWITCH_MODE]) }

    override val perAppRoutes: Flow<Map<String, AppRoute>> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> PerAppRouteCodec.decode(prefs[Keys.PER_APP_DIRECT]) }

    override val lastLeakCheck: Flow<LeakCheckRecord> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs ->
            LeakCheckRecord(
                checkedAtEpochMs = prefs[Keys.LEAK_CHECK_AT] ?: 0L,
                leaking = prefs[Keys.LEAK_CHECK_LEAKING] ?: false,
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setBypassMainland(enabled: Boolean) {
        dataStore.edit { it[Keys.BYPASS_MAINLAND] = enabled }
    }

    override suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    override suspend fun setKillSwitchMode(mode: KillSwitchMode) {
        dataStore.edit { it[Keys.KILL_SWITCH_MODE] = mode.name }
    }

    override suspend fun setPerAppRoutes(routes: Map<String, AppRoute>) {
        dataStore.edit { it[Keys.PER_APP_DIRECT] = PerAppRouteCodec.encode(routes) }
    }

    override suspend fun recordLeakCheck(record: LeakCheckRecord) {
        dataStore.edit {
            it[Keys.LEAK_CHECK_AT] = record.checkedAtEpochMs
            it[Keys.LEAK_CHECK_LEAKING] = record.leaking
        }
    }

    override val selectedProfileId: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SELECTED_PROFILE_ID] ?: 0L }

    override val selectedGroupId: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SELECTED_GROUP_ID] ?: 0L }

    // One snapshot, one decision: the group wins if both are somehow set.
    override val connectionTarget: Flow<ConnectionTarget> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs ->
            val group = prefs[Keys.SELECTED_GROUP_ID] ?: 0L
            val node = prefs[Keys.SELECTED_PROFILE_ID] ?: 0L
            when {
                group != 0L -> ConnectionTarget.Group(group)
                node != 0L -> ConnectionTarget.Node(node)
                else -> ConnectionTarget.None
            }
        }

    override suspend fun setSelectedProfileId(id: Long) {
        dataStore.edit {
            it[Keys.SELECTED_PROFILE_ID] = id
            // One connection target at a time; see the interface.
            if (id != 0L) it.remove(Keys.SELECTED_GROUP_ID)
        }
    }

    override suspend fun setSelectedGroupId(id: Long) {
        dataStore.edit {
            it[Keys.SELECTED_GROUP_ID] = id
            if (id != 0L) it.remove(Keys.SELECTED_PROFILE_ID)
        }
    }

    override val subscriptionLastSuccessAt: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SUB_LAST_SUCCESS] ?: 0L }

    override val subscriptionLastFailureAt: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SUB_LAST_FAILURE] ?: 0L }

    override val subscriptionLastError: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SUB_LAST_ERROR] }

    override suspend fun recordSubscriptionSuccess(atEpochMs: Long) {
        dataStore.edit {
            it[Keys.SUB_LAST_SUCCESS] = atEpochMs
            it.remove(Keys.SUB_LAST_ERROR)
        }
    }

    override suspend fun recordSubscriptionFailure(atEpochMs: Long, reason: String) {
        dataStore.edit {
            it[Keys.SUB_LAST_FAILURE] = atEpochMs
            it[Keys.SUB_LAST_ERROR] = reason
        }
    }
}
