package fr.fuelradar.data.route

import fr.fuelradar.data.model.Station
import fr.fuelradar.data.prefs.FiltersStore
import fr.fuelradar.data.routing.RoutingRepository
import fr.fuelradar.domain.Coords
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.domain.priceBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A station selected along a route, with its price and distance from the start. */
data class RouteStation(
    val station: Station,
    val price: Double?,
    val distanceKm: Double?,
)

/**
 * Shared route state. The route is a MODE of the map (not a separate screen): the
 * map and the stations list both observe this session so they stay in sync. Lives
 * in [fr.fuelradar.data.ServiceLocator] for the app session (not persisted).
 */
data class RouteState(
    /** True once the user enters route mode (inputs shown even before a full route). */
    val active: Boolean = false,
    val start: Coords? = null,
    val startLabel: String = "",
    val end: Coords? = null,
    val endLabel: String = "",
    val routePoints: List<Coords> = emptyList(),
    /** Stations along the route, sorted by progression from the start (nearest first). */
    val stations: List<RouteStation> = emptyList(),
    val corridorKm: Int = 5,
    val distanceKm: Double = 0.0,
    val durationMin: Int = 0,
    val pMin: Double = 0.0,
    val pMax: Double = 1.0,
    val cheapestId: Long? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
) {
    val hasRoute: Boolean get() = routePoints.size >= 2
}

class RouteSession(
    private val routing: RoutingRepository,
    private val filters: FiltersStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(RouteState())
    val state: StateFlow<RouteState> = _state.asStateFlow()

    private var computeJob: Job? = null

    init {
        // Recompute the station selection when the shared fuel changes (the fuel
        // pills write to the same FiltersStore).
        scope.launch {
            filters.filters.map { it.fuel }.distinctUntilChanged().collect {
                if (_state.value.hasRoute) recomputeStations()
            }
        }
    }

    /** Enter route mode (show the start/end inputs) without a route yet. */
    fun activate() {
        _state.value = _state.value.copy(active = true)
    }

    /** Leave route mode and return the map to "around me". Keeps typed endpoints. */
    fun deactivate() {
        _state.value = _state.value.copy(active = false)
    }

    /** Clear everything (exit + forget the route). */
    fun clear() {
        computeJob?.cancel()
        _state.value = RouteState()
    }

    fun setStart(coords: Coords, label: String) {
        _state.value = _state.value.copy(start = coords, startLabel = label, active = true)
        maybeCompute()
    }

    fun setEnd(coords: Coords, label: String) {
        _state.value = _state.value.copy(end = coords, endLabel = label, active = true)
        maybeCompute()
    }

    fun setCorridor(km: Int) {
        _state.value = _state.value.copy(corridorKm = km)
        if (_state.value.hasRoute) recomputeStations()
    }

    private fun maybeCompute() {
        val s = _state.value.start ?: return
        val e = _state.value.end ?: return
        computeJob?.cancel()
        computeJob = scope.launch {
            _state.value = _state.value.copy(loading = true, error = false)
            val rr = routing.route(s, e)
            if (rr == null) {
                _state.value = _state.value.copy(
                    loading = false, error = true,
                    routePoints = emptyList(), stations = emptyList(),
                )
                return@launch
            }
            val sel = computeStations(rr.points)
            _state.value = _state.value.copy(
                loading = false,
                routePoints = rr.points,
                distanceKm = rr.distanceKm,
                durationMin = rr.durationMin,
                stations = sel.rows,
                pMin = sel.pMin,
                pMax = sel.pMax,
                cheapestId = sel.cheapestId,
            )
        }
    }

    /** Re-filter for the current route when corridor/fuel change (no re-route). */
    private fun recomputeStations() {
        val pts = _state.value.routePoints
        if (pts.size < 2) return
        computeJob?.cancel()
        computeJob = scope.launch {
            _state.value = _state.value.copy(loading = true)
            val sel = computeStations(pts)
            _state.value = _state.value.copy(
                loading = false,
                stations = sel.rows,
                pMin = sel.pMin,
                pMax = sel.pMax,
                cheapestId = sel.cheapestId,
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
        val fuel = filters.filters.first().fuel
        val start = _state.value.start
        val list = routing.alongRoute(points, _state.value.corridorKm.toDouble(), fuel, MAX_STATIONS)
        val rows = list.map { st ->
            RouteStation(
                station = st,
                price = fuel.priceIn(st.fuels),
                distanceKm = start?.let { haversineKm(it.lat, it.lng, st.lat, st.lng) },
            )
        }
        // List view: order by progression from the start (nearest first), because a
        // route is about distance, not price. The cheapest is still highlighted.
        val sorted = rows.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        val (pMin, pMax) = priceBounds(sorted.mapNotNull { it.price })
        val cheapest = sorted.minByOrNull { it.price ?: Double.MAX_VALUE }?.station?.id
        return Selected(sorted, pMin, pMax, cheapest)
    }

    private companion object {
        // High cap: show every station along the trip (sampled evenly only past
        // this many). Matches the map's MAX_PINS so the map shows them all.
        const val MAX_STATIONS = 150
    }
}
