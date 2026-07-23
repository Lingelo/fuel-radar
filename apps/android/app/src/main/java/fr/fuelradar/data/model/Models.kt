package fr.fuelradar.data.model

import kotlinx.serialization.Serializable

/**
 * Data models mirroring the web types in apps/web/src/types/index.ts.
 * The JSON is the shared, stable contract served by GitHub Pages.
 */

@Serializable
data class FuelPrice(
    /** Price in euros. */
    val p: Double,
    /** Last update date (YYYY-MM-DD). */
    val d: String,
)

@Serializable
data class Station(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val addr: String = "",
    val city: String = "",
    val cp: String = "",
    val brand: String? = null,
    /** Open 24/7 via automatic dispenser. */
    val h24: Boolean? = null,
    val services: List<String>? = null,
    /** Fuel code ("Gazole", "SP95", ...) -> price. */
    val fuels: Map<String, FuelPrice> = emptyMap(),
)

@Serializable
data class MetaData(
    val lastUpdate: String,
)

/** [minLat, maxLat, minLng, maxLng] per department code. */
typealias DeptBbox = Map<String, List<Double>>

/** Per-station history: stationId -> fuel -> list of [epoch, price]. */
typealias StationHistoryData = Map<String, Map<String, List<List<Double>>>>

/** National daily averages: fuel -> list of [epoch, price]. */
@Serializable
data class NationalHistory(
    val fuels: Map<String, List<List<Double>>> = emptyMap(),
    val updated: String? = null,
)

/** Per-country daily averages: scope ("ALL"/"FR"/"ES"/"PT") -> fuel -> [epoch, price]. */
@Serializable
data class CountriesHistory(
    val countries: Map<String, Map<String, List<List<Double>>>> = emptyMap(),
    val updated: String? = null,
)

/**
 * Fuel types in display order (mirror of FUEL_TYPES / FUEL_LABELS). `code` is
 * the JSON/data key; `label` is the user-facing name (E10 shows as SP95-E10).
 */
enum class FuelType(val code: String, val label: String) {
    GAZOLE("Gazole", "Gazole"),
    SP95("SP95", "SP95"),
    E10("E10", "SP95-E10"),
    SP98("SP98", "SP98"),
    E85("E85", "E85"),
    GPLC("GPLc", "GPLc");

    companion object {
        fun fromCode(code: String): FuelType? = entries.firstOrNull { it.code == code }
    }
}
