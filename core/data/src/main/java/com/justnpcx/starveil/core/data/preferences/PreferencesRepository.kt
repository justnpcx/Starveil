package com.justnpcx.starveil.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.justnpcx.starveil.core.model.KillSwitchMode
import com.justnpcx.starveil.core.model.LeakCheckRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/** Theme selection. Stored as a String; unknown stored values fall back to SYSTEM. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

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
    val speedTestUrl: Flow<String>

    /** Currently selected node; 0 = none chosen yet. */
    val selectedProfileId: Flow<Long>

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
    suspend fun setKillSwitchMode(mode: KillSwitchMode)
    suspend fun recordLeakCheck(record: LeakCheckRecord)
    suspend fun setSpeedTestUrl(url: String)
    suspend fun setSelectedProfileId(id: Long)

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
        val KILL_SWITCH_MODE = stringPreferencesKey("kill_switch_mode")
        val LEAK_CHECK_AT = longPreferencesKey("leak_check_at")
        val LEAK_CHECK_LEAKING = booleanPreferencesKey("leak_check_leaking")
        val SPEED_TEST_URL = stringPreferencesKey("speed_test_url")
        val SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")
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

    override val killSwitchMode: Flow<KillSwitchMode> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> KillSwitchMode.fromStorage(prefs[Keys.KILL_SWITCH_MODE]) }

    override val lastLeakCheck: Flow<LeakCheckRecord> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs ->
            LeakCheckRecord(
                checkedAtEpochMs = prefs[Keys.LEAK_CHECK_AT] ?: 0L,
                leaking = prefs[Keys.LEAK_CHECK_LEAKING] ?: false,
            )
        }

    override val speedTestUrl: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SPEED_TEST_URL] ?: "http://cp.cloudflare.com/" }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    override suspend fun setKillSwitchMode(mode: KillSwitchMode) {
        dataStore.edit { it[Keys.KILL_SWITCH_MODE] = mode.name }
    }

    override suspend fun recordLeakCheck(record: LeakCheckRecord) {
        dataStore.edit {
            it[Keys.LEAK_CHECK_AT] = record.checkedAtEpochMs
            it[Keys.LEAK_CHECK_LEAKING] = record.leaking
        }
    }

    override suspend fun setSpeedTestUrl(url: String) {
        dataStore.edit { it[Keys.SPEED_TEST_URL] = url }
    }

    override val selectedProfileId: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SELECTED_PROFILE_ID] ?: 0L }

    override suspend fun setSelectedProfileId(id: Long) {
        dataStore.edit { it[Keys.SELECTED_PROFILE_ID] = id }
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
