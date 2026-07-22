package fr.fuelradar.data.geo

import fr.fuelradar.data.net.GeocodeApi
import fr.fuelradar.data.net.PhotonFeature
import java.net.URLEncoder
import java.text.Normalizer

/** A geocoding hit, mirror of AddressResult in apps/web/src/lib/geocode.ts. */
data class AddressResult(
    val label: String,
    val city: String,
    val postcode: String,
    val lat: Double,
    val lng: Double,
    /** BAN relevance (0..1), France only. */
    val score: Double? = null,
)

/**
 * Forward/reverse geocoding over the same free keyless services as the web:
 * France via BAN (api-adresse.data.gouv.fr), ES/PT via photon.komoot.io.
 */
class Geocoder(private val api: GeocodeApi) {

    suspend fun search(query: String, limit: Int = 8): List<AddressResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val fr = searchFrance(q, limit)
        val iberia = searchIberia(q)
        if (iberia.isEmpty()) return fr
        val qn = norm(q)
        val exactIberia = iberia.any { norm(it.city) == qn }
        val bestFrScore = fr.firstOrNull()?.score ?: 0.0
        return if (exactIberia || bestFrScore < 0.8) iberia + fr else fr + iberia
    }

    suspend fun reverse(lat: Double, lng: Double): AddressResult? {
        runCatching {
            val url = "https://api-adresse.data.gouv.fr/reverse/?lat=$lat&lon=$lng&limit=1"
            val f = api.ban(url).features.firstOrNull()
            if (f != null) {
                return AddressResult(
                    label = f.properties.label,
                    city = f.properties.city,
                    postcode = f.properties.postcode,
                    lng = f.geometry.coordinates.getOrElse(0) { lng },
                    lat = f.geometry.coordinates.getOrElse(1) { lat },
                )
            }
        }
        return runCatching {
            val url = "https://photon.komoot.io/reverse?lat=$lat&lon=$lng&lang=fr"
            api.photon(url).features.firstOrNull()?.let(::photonToResult)
        }.getOrNull()
    }

    private suspend fun searchFrance(query: String, limit: Int): List<AddressResult> = runCatching {
        val url = "https://api-adresse.data.gouv.fr/search/?q=${enc(query)}&limit=$limit"
        api.ban(url).features.map {
            AddressResult(
                label = it.properties.label,
                city = it.properties.city,
                postcode = it.properties.postcode,
                score = it.properties.score,
                lng = it.geometry.coordinates.getOrElse(0) { 0.0 },
                lat = it.geometry.coordinates.getOrElse(1) { 0.0 },
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun searchIberia(query: String, limit: Int = 4): List<AddressResult> = runCatching {
        val url = "https://photon.komoot.io/api/?q=${enc(query)}&limit=${limit * 3}&lang=fr"
        val out = mutableListOf<AddressResult>()
        val seen = mutableSetOf<String>()
        for (f in api.photon(url).features) {
            val cc = (f.properties.countrycode ?: "").uppercase()
            if (cc != "ES" && cc != "PT") continue
            val r = photonToResult(f) ?: continue
            if (!seen.add(r.label)) continue
            out.add(r)
            if (out.size >= limit) break
        }
        out
    }.getOrDefault(emptyList())

    private fun photonToResult(f: PhotonFeature): AddressResult? {
        val p = f.properties
        val street = listOfNotNull(p.street, p.housenumber).joinToString(" ").ifBlank { null }
        val name = p.name ?: street
        val city = p.city ?: p.name ?: ""
        if (name == null && city.isBlank()) return null
        val parts = mutableListOf<String>()
        for (part in listOf(name, city, p.state, p.country)) {
            if (!part.isNullOrBlank() && !parts.contains(part)) parts.add(part)
        }
        return AddressResult(
            label = parts.joinToString(", "),
            city = city.ifBlank { name ?: "" },
            postcode = p.postcode ?: "",
            lng = f.geometry.coordinates.getOrElse(0) { 0.0 },
            lat = f.geometry.coordinates.getOrElse(1) { 0.0 },
        )
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .trim()
}
