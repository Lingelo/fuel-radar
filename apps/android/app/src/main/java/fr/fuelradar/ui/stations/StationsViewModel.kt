package fr.fuelradar.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.geo.AddressResult
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.prefs.Filters
import fr.fuelradar.data.prefs.SortMode
import fr.fuelradar.domain.Coords
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.domain.priceBounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StationRow(
    val station: Station,
    val distanceKm: Double,
    val price: Double?,
)

data class StationsUiState(
    val loading: Boolean = true,
    val center: Coords = Coords(48.8566, 2.3522),
    val locationLabel: String = "Paris",
    val query: String = "",
    val rows: List<StationRow> = emptyList(),
    val pMin: Double = 0.0,
    val pMax: Double = 1.0,
    val cheapestId: Long? = null,
    val filters: Filters = Filters(),
    val favorites: Set<Long> = emptySet(),
    val suggestions: List<AddressResult> = emptyList(),
    /** True when a route is active: the list shows stations along the trip,
     *  ordered by distance from the start (not by price). */
    val routeActive: Boolean = false,
)

class StationsViewModel : ViewModel() {

    private val repo = ServiceLocator.stations
    private val geocoder = ServiceLocator.geocoder
    private val favStore = ServiceLocator.favorites
    private val filtersStore = ServiceLocator.filters
    private val routeSession = ServiceLocator.routeSession

    private val _state = MutableStateFlow(StationsUiState())
    val state: StateFlow<StationsUiState> = _state.asStateFlow()

    private var lastLabel: String? = null

    init {
        viewModelScope.launch {
            // The list is a mode-aware view of the same data as the map: when a
            // route is active it shows the stations along the trip; otherwise the
            // stations around the searched/located position.
            combine(filtersStore.filters, routeSession.state) { f, r -> f to r }
                .collect { (f, r) ->
                    _state.value = _state.value.copy(filters = f)
                    // Keep the search field in sync with the shared address label.
                    if (f.searchLabel != null && f.searchLabel != lastLabel) {
                        lastLabel = f.searchLabel
                        _state.value = _state.value.copy(query = f.searchLabel)
                    }
                    if (r.active && r.hasRoute) showRoute(r) else reload()
                }
        }
        viewModelScope.launch {
            favStore.ids.collect { ids ->
                _state.value = _state.value.copy(favorites = ids)
            }
        }
    }

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _state.value = _state.value.copy(suggestions = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.value = _state.value.copy(suggestions = geocoder.search(q))
        }
    }

    fun selectSuggestion(hit: AddressResult) {
        val label = listOf(hit.postcode, hit.city).filter { it.isNotBlank() }
            .joinToString(" ").ifBlank { hit.label }
        _state.value = _state.value.copy(query = label, suggestions = emptyList())
        viewModelScope.launch { filtersStore.setLocation(hit.lat, hit.lng, label) }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            val hit = geocoder.search(q).firstOrNull() ?: return@launch
            selectSuggestion(hit)
        }
    }

    fun setLocation(lat: Double, lng: Double, label: String?) {
        viewModelScope.launch { filtersStore.setLocation(lat, lng, label) }
    }

    /** "View on map": recenter on the station AND flag it to be highlighted. */
    fun focusOnMap(station: Station) {
        viewModelScope.launch {
            filtersStore.setLocation(station.lat, station.lng, "${station.cp} ${station.city}")
            filtersStore.setFocusStation(station.id)
        }
    }

    /** Device geolocation resolved -> reverse-geocode a label and share it. */
    fun onLocated(lat: Double, lng: Double) {
        viewModelScope.launch {
            val r = geocoder.reverse(lat, lng)
            val label = r?.let {
                listOf(it.postcode, it.city).filter { s -> s.isNotBlank() }.joinToString(" ")
            }?.ifBlank { null }
            filtersStore.setLocation(lat, lng, label)
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { favStore.toggle(id) }
    }

    fun setFuel(fuel: fr.fuelradar.data.model.FuelType) {
        viewModelScope.launch { filtersStore.setFuel(fuel) }
    }

    fun setSort(sort: SortMode) {
        viewModelScope.launch { filtersStore.setSort(sort) }
    }

    fun applyFilters(filters: Filters) {
        viewModelScope.launch { filtersStore.apply(filters) }
    }

    private suspend fun reload() {
        val s = _state.value
        _state.value = s.copy(loading = true)
        val center = s.filters.userLocation ?: Coords(48.8566, 2.3522)
        val label = s.filters.searchLabel ?: "Paris"
        val fuel = s.filters.fuel
        val all = repo.nearby(center.lat, center.lng, s.filters.radiusKm.toDouble())
        val rows = all
            .asSequence()
            .filter { s.filters.brands.isEmpty() || (it.brand != null && s.filters.brands.contains(it.brand)) }
            .filter { !s.filters.openH24Only || it.h24 == true }
            .map { st ->
                StationRow(
                    station = st,
                    distanceKm = haversineKm(center.lat, center.lng, st.lat, st.lng),
                    price = fuel.priceIn(st.fuels),
                )
            }
            .filter { it.price != null }
            // Same as the map: only stations within the radius of the address/position.
            .filter { it.distanceKm <= s.filters.radiusKm.toDouble() }
            .toList()
        val sorted = when (s.filters.sort) {
            SortMode.PRICE -> rows.sortedBy { it.price }
            SortMode.DISTANCE -> rows.sortedBy { it.distanceKm }
        }
        val prices = sorted.mapNotNull { it.price }
        val (pMin, pMax) = priceBounds(prices)
        val cheapestId = rows.minByOrNull { it.price ?: Double.MAX_VALUE }?.station?.id
        _state.value = _state.value.copy(
            loading = false,
            rows = sorted,
            pMin = pMin,
            pMax = pMax,
            cheapestId = cheapestId,
            center = center,
            locationLabel = label,
            routeActive = false,
        )
    }

    /** Route mode: show the stations along the trip, already sorted by progression
     *  from the start (nearest first) — a route is about distance, not price. */
    private fun showRoute(r: fr.fuelradar.data.route.RouteState) {
        _state.value = _state.value.copy(
            loading = false,
            routeActive = true,
            rows = r.stations.map { StationRow(it.station, it.distanceKm ?: 0.0, it.price) },
            pMin = r.pMin,
            pMax = r.pMax,
            cheapestId = r.cheapestId,
            locationLabel = r.startLabel.ifBlank { _state.value.locationLabel },
        )
    }
}
