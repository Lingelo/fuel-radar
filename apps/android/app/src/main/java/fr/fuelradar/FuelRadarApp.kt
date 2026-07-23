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

    // Enable SVG decoding (some brand logos are SVG). allowHardware(false) is
    // required because brand logos are drawn inside MarkerComposable, which
    // rasterizes on a software canvas — hardware bitmaps crash there.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .allowHardware(false)
            .build()
}
