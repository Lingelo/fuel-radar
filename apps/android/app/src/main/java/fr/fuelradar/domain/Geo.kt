package fr.fuelradar.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A geographic coordinate. */
data class Coords(val lat: Double, val lng: Double)

/** Great-circle distance in km (Haversine) — mirror of apps/web/src/lib/distance.ts. */
fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    fun rad(x: Double) = x * PI / 180.0
    val dLat = rad(lat2 - lat1)
    val dLng = rad(lng2 - lng1)
    val a = sin(dLat / 2).let { it * it } +
        cos(rad(lat1)) * cos(rad(lat2)) * sin(dLng / 2).let { it * it }
    return 2 * r * asin(sqrt(a))
}

fun formatDistance(km: Double): String = when {
    km < 1 -> "${(km * 1000).roundToInt()} m"
    km < 10 -> "${String.format("%.1f", km).replace('.', ',')} km"
    else -> "${km.roundToInt()} km"
}
