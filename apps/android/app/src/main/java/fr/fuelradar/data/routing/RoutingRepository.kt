package fr.fuelradar.data.routing

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import fr.fuelradar.data.DeptIndex
import fr.fuelradar.data.StationRepository
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.net.RoutingApi
import fr.fuelradar.domain.Coords
import fr.fuelradar.domain.haversineKm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A computed driving route between two points. */
data class RouteResult(
    val points: List<Coords>,
    val distanceKm: Double,
    val durationMin: Int,
)

/**
 * Driving routes via OSRM + selection of stations along the route. Network errors
 * degrade to null/empty (same policy as StationRepository) so the UI never crashes.
 */
class RoutingRepository(
    private val api: RoutingApi,
    private val stations: StationRepository,
) {

    suspend fun route(start: Coords, end: Coords): RouteResult? = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append("https://router.project-osrm.org/route/v1/driving/")
                append("${start.lng},${start.lat};${end.lng},${end.lat}")
                append("?overview=full&geometries=geojson")
            }
            val r = api.route(url).routes.firstOrNull() ?: return@runCatching null
            val pts = r.geometry.coordinates.mapNotNull {
                if (it.size >= 2) Coords(it[1], it[0]) else null
            }
            if (pts.size < 2) null
            else RouteResult(pts, r.distance / 1000.0, (r.duration / 60.0).roundToInt())
        }.getOrNull()
    }

    /**
     * Stations within [corridorKm] of the route [polyline] that sell [fuelCode],
     * cheapest first, capped at [max].
     */
    suspend fun alongRoute(
        polyline: List<Coords>,
        corridorKm: Double,
        fuelCode: String,
        max: Int,
    ): List<Station> = withContext(Dispatchers.IO) {
        if (polyline.size < 2) return@withContext emptyList()
        val index = stations.deptBbox() ?: return@withContext emptyList()

        // Union of departments overlapping the corridor along the whole route.
        val depts = LinkedHashSet<String>()
        val step = maxOf(1, polyline.size / 120)
        var i = 0
        while (i < polyline.size) {
            val p = polyline[i]
            depts += DeptIndex.deptsAround(index, p.lat, p.lng, corridorKm + 2.0)
            i += step
        }
        polyline.last().let { depts += DeptIndex.deptsAround(index, it.lat, it.lng, corridorKm + 2.0) }

        // Decimate the polyline for the point-on-path test to bound cost on long routes.
        val full = polyline.map { LatLng(it.lat, it.lng) }
        val testRoute = if (full.size > 400) {
            val k = full.size / 400 + 1
            full.filterIndexed { idx, _ -> idx % k == 0 || idx == full.lastIndex }
        } else {
            full
        }
        val corridorM = corridorKm * 1000.0

        val inCorridor = stations.stationsForDepts(depts.toList())
            .filter { it.fuels.containsKey(fuelCode) }
            .filter { PolyUtil.isLocationOnPath(LatLng(it.lat, it.lng), testRoute, false, corridorM) }

        // Spread the cap ALONG the route instead of taking the globally cheapest:
        // bucket stations by their progress on the route and keep the cheapest of
        // each bucket. Otherwise a whole cheap country (e.g. Spain on a FR→PT trip)
        // would fill all the slots and hide the rest of the journey.
        val lastIdx = testRoute.size - 1
        val buckets = arrayOfNulls<Station>(max)
        val bucketPrice = DoubleArray(max) { Double.MAX_VALUE }
        for (st in inCorridor) {
            val price = st.fuels[fuelCode]?.p ?: continue
            var nearest = 0
            var nearestKm = Double.MAX_VALUE
            for (idx in testRoute.indices) {
                val d = haversineKm(st.lat, st.lng, testRoute[idx].latitude, testRoute[idx].longitude)
                if (d < nearestKm) { nearestKm = d; nearest = idx }
            }
            val b = if (lastIdx <= 0) 0
            else (nearest.toDouble() / lastIdx * (max - 1)).toInt().coerceIn(0, max - 1)
            if (price < bucketPrice[b]) {
                bucketPrice[b] = price
                buckets[b] = st
            }
        }
        buckets.filterNotNull().sortedBy { it.fuels[fuelCode]?.p ?: Double.MAX_VALUE }
    }
}
