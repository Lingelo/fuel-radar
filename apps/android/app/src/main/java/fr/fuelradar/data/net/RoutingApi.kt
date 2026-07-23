package fr.fuelradar.data.net

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Free, keyless routing via OSRM (router.project-osrm.org). The full URL (with the
 * coordinates in the path) is passed through @Url, like GeocodeApi.
 */
interface RoutingApi {
    @GET
    suspend fun route(@Url url: String): OsrmResponse
}

@Serializable
data class OsrmResponse(
    val code: String = "",
    val routes: List<OsrmRoute> = emptyList(),
)

@Serializable
data class OsrmRoute(
    val geometry: OsrmGeometry = OsrmGeometry(),
    /** Metres. */
    val distance: Double = 0.0,
    /** Seconds. */
    val duration: Double = 0.0,
)

@Serializable
data class OsrmGeometry(
    /** GeoJSON coordinate pairs, each [lng, lat]. */
    val coordinates: List<List<Double>> = emptyList(),
)
