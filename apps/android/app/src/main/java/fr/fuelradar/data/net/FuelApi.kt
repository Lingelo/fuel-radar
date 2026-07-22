package fr.fuelradar.data.net

import fr.fuelradar.data.model.DeptBbox
import fr.fuelradar.data.model.MetaData
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.model.StationHistoryData
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Static JSON data served by GitHub Pages under BuildConfig.DATA_BASE_URL.
 * Same files the web app consumes — no backend involved.
 */
interface FuelApi {
    @GET("meta.json")
    suspend fun meta(): MetaData

    @GET("dept-bbox.json")
    suspend fun deptBbox(): DeptBbox

    @GET("departments/{dept}.json")
    suspend fun department(@Path("dept") dept: String): List<Station>

    @GET("history/{dept}.json")
    suspend fun deptHistory(@Path("dept") dept: String): StationHistoryData
}
