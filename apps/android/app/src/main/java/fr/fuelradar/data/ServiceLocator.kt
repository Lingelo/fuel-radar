package fr.fuelradar.data

import android.content.Context
import fr.fuelradar.data.geo.Geocoder
import fr.fuelradar.data.net.NetworkModule
import fr.fuelradar.data.prefs.FavoritesStore
import fr.fuelradar.data.prefs.FiltersStore

/**
 * Minimal manual dependency container. Initialized once from the Application;
 * screens read the shared repository/geocoder/stores from here (a DI framework
 * would be over-engineering for this app's size).
 */
object ServiceLocator {
    lateinit var stations: StationRepository
        private set
    lateinit var geocoder: Geocoder
        private set
    lateinit var favorites: FavoritesStore
        private set
    lateinit var filters: FiltersStore
        private set

    fun init(context: Context) {
        val network = NetworkModule(context)
        stations = StationRepository(network.fuelApi)
        geocoder = Geocoder(network.geocodeApi)
        favorites = FavoritesStore(context)
        filters = FiltersStore(context)
    }
}
