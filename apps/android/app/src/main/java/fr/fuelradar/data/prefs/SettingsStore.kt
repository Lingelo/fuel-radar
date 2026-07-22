package fr.fuelradar.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

data class AppSettings(
    val startupTab: String = "map",
    val staleWarning: Boolean = true,
)

/** App-level preferences (mirror of the web Settings screen). */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            startupTab = p[STARTUP] ?: "map",
            staleWarning = p[STALE] ?: true,
        )
    }

    suspend fun setStartupTab(tab: String) = store.edit { it[STARTUP] = tab }

    suspend fun setStaleWarning(enabled: Boolean) = store.edit { it[STALE] = enabled }

    private companion object {
        val STARTUP = stringPreferencesKey("startup_tab")
        val STALE = booleanPreferencesKey("stale_warning")
    }
}
