package fr.fuelradar.ui.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.geo.AddressResult
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.model.Station
import fr.fuelradar.domain.Coords
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.domain.priceBounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RouteStation(
    val station: Station,
    val price: Double?,
    val distanceKm: Double?,
)

data class RouteUiState(
    val startQuery: String = "",
    val endQuery: String = "",
    val startSuggestions: List<AddressResult> = emptyList(),
    val endSuggestions: List<AddressResult> = emptyList(),
    val start: Coords? = null,
    val end: Coords? = null,
    val routePoints: List<Coords> = emptyList(),
    val stations: List<RouteStation> = emptyList(),
    val fuel: FuelType = FuelType.GAZOLE,
    val corridorKm: Int = 2,
    val distanceKm: Double = 0.0,
    val durationMin: Int = 0,
    val pMin: Double = 0.0,
    val pMax: Double = 1.0,
    val cheapestId: Long? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val showList: Boolean = false,
)

class RouteViewModel : ViewModel() {
    private val geocoder = ServiceLocator.geocoder
    private val routing = ServiceLocator.routing
    private val filtersStore = ServiceLocator.filters

    private val _state = MutableStateFlow(RouteUiState())
    val state: StateFlow<RouteUiState> = _state.asStateFlow()

    private var startJob: Job? = null
    private var endJob: Job? = null
    private var computeJob: Job? = null

    init {
        viewModelScope.launch {
            val f = filtersStore.filters.first()
            _state.value = _state.value.copy(
                fuel = f.fuel,
                start = f.userLocation,
                startQuery = f.searchLabel.orEmpty(),
            )
        }
        // Keep the fuel in sync with the shared filter (fuel pills write to it).
        viewModelScope.launch {
            filtersStore.filters.collect { f ->
                if (f.fuel != _state.value.fuel) {
                    _state.value = _state.value.copy(fuel = f.fuel)
                    recomputeStations()
                }
            }
        }
    }

    fun onStartQueryChange(q: String) {
        _state.value = _state.value.copy(startQuery = q)
        startJob?.cancel()
        if (q.trim().length < 2) {
            _state.value = _state.value.copy(startSuggestions = emptyList())
            return
        }
        startJob = viewModelScope.launch {
            delay(300)
            _state.value = _state.value.copy(startSuggestions = geocoder.search(q))
        }
    }

    fun onEndQueryChange(q: String) {
        _state.value = _state.value.copy(endQuery = q)
        endJob?.cancel()
        if (q.trim().length < 2) {
            _state.value = _state.value.copy(endSuggestions = emptyList())
            return
        }
        endJob = viewModelScope.launch {
            delay(300)
            _state.value = _state.value.copy(endSuggestions = geocoder.search(q))
        }
    }

    fun selectStart(hit: AddressResult) {
        _state.value = _state.value.copy(
            start = Coords(hit.lat, hit.lng),
            startQuery = labelOf(hit),
            startSuggestions = emptyList(),
        )
        maybeCompute()
    }

    fun selectEnd(hit: AddressResult) {
        _state.value = _state.value.copy(
            end = Coords(hit.lat, hit.lng),
            endQuery = labelOf(hit),
            endSuggestions = emptyList(),
        )
        maybeCompute()
    }

    fun useMyLocationAsStart() {
        viewModelScope.launch {
            val f = filtersStore.filters.first()
            val loc = f.userLocation ?: return@launch
            _state.value = _state.value.copy(
                start = loc,
                startQuery = f.searchLabel ?: _state.value.startQuery,
                startSuggestions = emptyList(),
            )
            maybeCompute()
        }
    }

    fun setCorridor(km: Int) {
        _state.value = _state.value.copy(corridorKm = km)
        recomputeStations()
    }

    fun setFuel(fuel: FuelType) {
        viewModelScope.launch { filtersStore.setFuel(fuel) }
    }

    fun toggleList() {
        _state.value = _state.value.copy(showList = !_state.value.showList)
    }

    fun showMap() {
        _state.value = _state.value.copy(showList = false)
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { ServiceLocator.favorites.toggle(id) }
    }

    private fun labelOf(hit: AddressResult): String =
        listOf(hit.postcode, hit.city).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { hit.label }

    private fun maybeCompute() {
        val s = _state.value.start ?: return
        val e = _state.value.end ?: return
        computeJob?.cancel()
        computeJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = false)
            val rr = routing.route(s, e)
            if (rr == null) {
                _state.value = _state.value.copy(
                    loading = false, error = true,
                    routePoints = emptyList(), stations = emptyList(),
                )
                return@launch
            }
            val stations = computeStations(rr.points)
            _state.value = _state.value.copy(
                loading = false,
                routePoints = rr.points,
                distanceKm = rr.distanceKm,
                durationMin = rr.durationMin,
                stations = stations.rows,
                pMin = stations.pMin,
                pMax = stations.pMax,
                cheapestId = stations.cheapestId,
            )
        }
    }

    /** Re-filter stations for the current route when corridor/fuel change (no re-route). */
    private fun recomputeStations() {
        val pts = _state.value.routePoints
        if (pts.size < 2) return
        computeJob?.cancel()
        computeJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val stations = computeStations(pts)
            _state.value = _state.value.copy(
                loading = false,
                stations = stations.rows,
                pMin = stations.pMin,
                pMax = stations.pMax,
                cheapestId = stations.cheapestId,
            )
        }
    }

    private data class Selected(
        val rows: List<RouteStation>,
        val pMin: Double,
        val pMax: Double,
        val cheapestId: Long?,
    )

    private suspend fun computeStations(points: List<Coords>): Selected {
        val fuel = _state.value.fuel
        val start = _state.value.start
        val list = routing.alongRoute(points, _state.value.corridorKm.toDouble(), fuel.code, MAX_STATIONS)
        val rows = list.map { st ->
            RouteStation(
                station = st,
                price = st.fuels[fuel.code]?.p,
                distanceKm = start?.let { haversineKm(it.lat, it.lng, st.lat, st.lng) },
            )
        }
        val (pMin, pMax) = priceBounds(rows.mapNotNull { it.price })
        val cheapest = rows.minByOrNull { it.price ?: Double.MAX_VALUE }?.station?.id
        return Selected(rows, pMin, pMax, cheapest)
    }

    private companion object {
        const val MAX_STATIONS = 50
    }
}
