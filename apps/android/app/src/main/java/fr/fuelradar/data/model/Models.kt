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

/** High-level fuel family, for the two-level filter (family → fuel). */
enum class FuelFamily(val label: String) {
    ESSENCE("Essence"),
    DIESEL("Diesel"),
    GPL("GPL"),
}

/**
 * Fuel types in display order. A type maps to one or MORE data codes: "Sans-plomb
 * 95" covers both `SP95` and `E10` (SP95-E10) because they are the same 95-octane
 * petrol labelled differently across countries (France uses E10, Spain/Portugal
 * SP95). Matching all codes is what makes a fuel choice work on a cross-border trip.
 */
enum class FuelType(
    val codes: List<String>,
    val label: String,
    val family: FuelFamily,
) {
    GAZOLE(listOf("Gazole"), "Gazole", FuelFamily.DIESEL),
    SP95(listOf("SP95", "E10"), "Sans-plomb 95", FuelFamily.ESSENCE),
    SP98(listOf("SP98"), "SP98", FuelFamily.ESSENCE),
    E85(listOf("E85"), "E85", FuelFamily.ESSENCE),
    GPLC(listOf("GPLc"), "GPLc", FuelFamily.GPL);

    /** Primary code — used for persistence and history lookups. */
    val code: String get() = codes.first()

    /** Price for this fuel in a station's [fuels] map, trying every code. */
    fun priceIn(fuels: Map<String, FuelPrice>): Double? =
        codes.firstNotNullOfOrNull { fuels[it]?.p }

    /** Last-update date for this fuel, trying every code. */
    fun dateIn(fuels: Map<String, FuelPrice>): String? =
        codes.firstNotNullOfOrNull { fuels[it]?.d }

    /** True if the station sells this fuel under any of its codes. */
    fun availableIn(fuels: Map<String, FuelPrice>): Boolean =
        codes.any { fuels.containsKey(it) }

    /** Value from a by-code map (e.g. a history series), trying every code. */
    fun <T> seriesIn(byCode: Map<String, T>): T? = codes.firstNotNullOfOrNull { byCode[it] }

    companion object {
        fun fromCode(code: String): FuelType? = entries.firstOrNull { code in it.codes }
    }
}
