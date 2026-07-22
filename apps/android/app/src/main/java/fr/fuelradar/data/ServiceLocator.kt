package fr.fuelradar.data

import android.content.Context
import fr.fuelradar.data.geo.Geocoder
import fr.fuelradar.data.net.NetworkModule

/**
 * Minimal manual dependency container. Initialized once from the Application;
 * screens read the shared repository/geocoder from here (a DI framework would
 * be over-engineering for this app's size).
 */
object ServiceLocator {
    lateinit var stations: StationRepository
        private set
    lateinit var geocoder: Geocoder
        private set

    fun init(context: Context) {
        val network = NetworkModule(context)
        stations = StationRepository(network.fuelApi)
        geocoder = Geocoder(network.geocodeApi)
    }
}
