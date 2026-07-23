package fr.fuelradar.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore("favorites")

/**
 * Persists favorite station ids (mirror of the web localStorage FavoritesContext).
 */
class FavoritesStore(context: Context) {

    private val store = context.applicationContext.favoritesDataStore

    val ids: Flow<Set<Long>> = store.data.map { prefs ->
        prefs[KEY]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun toggle(id: Long) {
        store.edit { prefs ->
            val current = prefs[KEY]?.toMutableSet() ?: mutableSetOf()
            val key = id.toString()
            if (!current.add(key)) current.remove(key)
            prefs[KEY] = current
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("favorite_ids")
    }
}
