package fr.fuelradar.data

import fr.fuelradar.data.model.DeptBbox
import fr.fuelradar.data.model.MetaData
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.model.StationHistoryData
import fr.fuelradar.data.net.FuelApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches station data from the GitHub Pages JSON contract, with in-memory
 * caching per department (mirror of apps/web/src/lib/data.ts). Network errors
 * degrade to empty/null like the web, so the UI never crashes on a bad fetch.
 */
class StationRepository(private val api: FuelApi) {

    private val deptCache = mutableMapOf<String, List<Station>>()
    private val historyCache = mutableMapOf<String, StationHistoryData>()
    @Volatile private var bboxCache: DeptBbox? = null

    suspend fun meta(): MetaData? = withContext(Dispatchers.IO) {
        runCatching { api.meta() }.getOrNull()
    }

    suspend fun deptBbox(): DeptBbox? = withContext(Dispatchers.IO) {
        bboxCache?.let { return@withContext it }
        runCatching { api.deptBbox() }.getOrNull()?.also { bboxCache = it }
    }

    suspend fun department(dept: String): List<Station> = withContext(Dispatchers.IO) {
        deptCache[dept]?.let { return@withContext it }
        val list = runCatching { api.department(dept) }.getOrDefault(emptyList())
        deptCache[dept] = list
        list
    }

    suspend fun stationsForDepts(depts: List<String>): List<Station> =
        depts.flatMap { department(it) }

    /** Stations from all departments whose bbox overlaps a radius around a point. */
    suspend fun nearby(lat: Double, lng: Double, radiusKm: Double): List<Station> {
        val index = deptBbox() ?: return emptyList()
        val depts = DeptIndex.deptsAround(index, lat, lng, radiusKm)
        return stationsForDepts(depts)
    }

    suspend fun deptHistory(dept: String): StationHistoryData = withContext(Dispatchers.IO) {
        historyCache[dept]?.let { return@withContext it }
        val data = runCatching { api.deptHistory(dept) }.getOrDefault(emptyMap())
        historyCache[dept] = data
        data
    }

    fun invalidate() {
        deptCache.clear()
        historyCache.clear()
        bboxCache = null
    }
}
