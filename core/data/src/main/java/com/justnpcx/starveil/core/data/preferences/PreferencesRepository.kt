package com.justnpcx.starveil.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val killSwitch: Flow<Boolean>
    val speedTestUrl: Flow<String>

    /**
     * Skeleton default true: the Fake kernel is the only working kernel
     * until RealKernelBridge lands. Flip to false when the real kernel is
     * ready — do not delete the switch; it stays as a debug escape hatch.
     */
    val useFakeKernel: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAutoReconnect(enabled: Boolean)
    suspend fun setKillSwitch(enabled: Boolean)
    suspend fun setSpeedTestUrl(url: String)
    suspend fun setUseFakeKernel(useFake: Boolean)
}

class DataStorePreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PreferencesRepository {

    private val dataStore = context.starveilDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val SPEED_TEST_URL = stringPreferencesKey("speed_test_url")
        val USE_FAKE_KERNEL = booleanPreferencesKey("use_fake_kernel")
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

    override val killSwitch: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.KILL_SWITCH] ?: false }

    override val speedTestUrl: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.SPEED_TEST_URL] ?: "http://cp.cloudflare.com/" }

    override val useFakeKernel: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.USE_FAKE_KERNEL] ?: true }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    override suspend fun setKillSwitch(enabled: Boolean) {
        dataStore.edit { it[Keys.KILL_SWITCH] = enabled }
    }

    override suspend fun setSpeedTestUrl(url: String) {
        dataStore.edit { it[Keys.SPEED_TEST_URL] = url }
    }

    override suspend fun setUseFakeKernel(useFake: Boolean) {
        dataStore.edit { it[Keys.USE_FAKE_KERNEL] = useFake }
    }
}
