package fr.fuelradar

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import fr.fuelradar.data.ServiceLocator

class FuelRadarApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    // Enable SVG decoding (Simple Icons brand logos are SVG).
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
}
