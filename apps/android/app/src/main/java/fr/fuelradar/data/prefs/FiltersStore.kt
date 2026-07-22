package fr.fuelradar.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.fuelradar.data.model.FuelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.filtersDataStore: DataStore<Preferences> by preferencesDataStore("filters")

enum class SortMode { PRICE, DISTANCE }

/** User filters (mirror of the web localStorage FiltersContext). */
data class Filters(
    val fuel: FuelType = FuelType.GAZOLE,
    val radiusKm: Int = 10,
    val sort: SortMode = SortMode.PRICE,
    val brands: Set<String> = emptySet(),
)

class FiltersStore(context: Context) {

    private val store = context.applicationContext.filtersDataStore

    val filters: Flow<Filters> = store.data.map { p ->
        Filters(
            fuel = p[FUEL]?.let { FuelType.fromCode(it) } ?: FuelType.GAZOLE,
            radiusKm = p[RADIUS] ?: 10,
            sort = p[SORT]?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: SortMode.PRICE,
            brands = p[BRANDS] ?: emptySet(),
        )
    }

    suspend fun setFuel(fuel: FuelType) = store.edit { it[FUEL] = fuel.code }

    suspend fun setRadius(km: Int) = store.edit { it[RADIUS] = km }

    suspend fun setSort(sort: SortMode) = store.edit { it[SORT] = sort.name }

    suspend fun toggleBrand(brand: String) {
        store.edit { prefs ->
            val current = prefs[BRANDS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(brand)) current.remove(brand)
            prefs[BRANDS] = current
        }
    }

    private companion object {
        val FUEL = stringPreferencesKey("fuel")
        val RADIUS = intPreferencesKey("radius_km")
        val SORT = stringPreferencesKey("sort")
        val BRANDS = stringSetPreferencesKey("brands")
    }
}
