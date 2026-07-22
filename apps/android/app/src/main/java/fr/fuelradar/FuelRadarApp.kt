package fr.fuelradar

import android.app.Application
import fr.fuelradar.data.ServiceLocator

class FuelRadarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
