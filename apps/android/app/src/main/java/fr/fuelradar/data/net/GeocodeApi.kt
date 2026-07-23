package fr.fuelradar.data.net

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Free, keyless geocoders (mirror of apps/web/src/lib/geocode.ts):
 * api-adresse.data.gouv.fr (France) and photon.komoot.io (ES/PT).
 * Full URLs are passed via @Url so one Retrofit instance serves both hosts.
 */
interface GeocodeApi {
    @GET
    suspend fun ban(@Url url: String): BanResponse

    @GET
    suspend fun photon(@Url url: String): PhotonResponse
}

@Serializable
data class Geometry(val coordinates: List<Double> = emptyList())

@Serializable
data class BanResponse(val features: List<BanFeature> = emptyList())

@Serializable
data class BanFeature(val geometry: Geometry, val properties: BanProps)

@Serializable
data class BanProps(
    val label: String = "",
    val city: String = "",
    val postcode: String = "",
    val score: Double? = null,
)

@Serializable
data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@Serializable
data class PhotonFeature(val geometry: Geometry, val properties: PhotonProps)

@Serializable
data class PhotonProps(
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val postcode: String? = null,
    val countrycode: String? = null,
    val state: String? = null,
    val country: String? = null,
)
