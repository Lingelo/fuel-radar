package fr.fuelradar.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.geo.AddressResult
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.prefs.Filters
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

/** A station rendered as a clusterable map marker. */
data class StationClusterItem(
    val station: Station,
    val price: Double?,
) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(station.lat, station.lng)
    override fun getTitle(): String = station.brand ?: station.city
    override fun getSnippet(): String = "${station.cp} ${station.city}"
    override fun getZIndex(): Float = 0f
}

data class MapUiState(
    val loading: Boolean = true,
    val items: List<StationClusterItem> = emptyList(),
    val pMin: Double = 0.0,
    val pMax: Double = 1.0,
    val query: String = "",
    val suggestions: List<AddressResult> = emptyList(),
    val filters: Filters = Filters(),
    val center: Coords = Coords(48.8566, 2.3522),
    /** Radius actually applied to [items] in the last completed load. The circle
     *  is drawn from this (not filters.radiusKm) so it always matches the pins. */
    val radiusKm: Int = 10,
    val hasLocation: Boolean = false,
    val cheapestId: Long? = null,
)

class MapViewModel : ViewModel() {
    private val repo = ServiceLocator.stations
    private val geocoder = ServiceLocator.geocoder
    private val filtersStore = ServiceLocator.filters

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    /** Set when a search resolves; the screen animates the camera here. */
    private val _target = MutableStateFlow<Coords?>(null)
    val target: StateFlow<Coords?> = _target.asStateFlow()

    private var lastCenter = Coords(48.8566, 2.3522)
    private var lastUserLoc: Coords? = null
    private var lastLabel: String? = null

    init {
        viewModelScope.launch {
            filtersStore.filters.collect { f ->
                _state.value = _state.value.copy(filters = f)
                // Keep the search field in sync with the shared address label.
                if (f.searchLabel != null && f.searchLabel != lastLabel) {
                    lastLabel = f.searchLabel
                    _state.value = _state.value.copy(query = f.searchLabel)
                }
                // Any shared location change (search/locate from any screen, incl.
                // "view on map") recenters the camera.
                val loc = f.userLocation
                if (loc != null && loc != lastUserLoc) {
                    lastUserLoc = loc
                    _target.value = loc
                }
                // Reload around the new location (fallback: last camera center).
                val c = loc ?: lastCenter
                load(c.lat, c.lng)
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
            val results = geocoder.search(q)
            _state.value = _state.value.copy(suggestions = results)
        }
    }

    fun selectSuggestion(hit: AddressResult) {
        val label = listOf(hit.postcode, hit.city).filter { it.isNotBlank() }
            .joinToString(" ").ifBlank { hit.label }
        _state.value = _state.value.copy(query = label, suggestions = emptyList())
        viewModelScope.launch {
            filtersStore.setLocation(hit.lat, hit.lng, label)
            _target.value = Coords(hit.lat, hit.lng)
        }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            val hit = geocoder.search(q).firstOrNull() ?: return@launch
            selectSuggestion(hit)
        }
    }

    /** Called when device geolocation resolves — persist it and recenter. */
    fun onLocated(lat: Double, lng: Double) {
        viewModelScope.launch {
            filtersStore.setLocation(lat, lng, null)
            _target.value = Coords(lat, lng)
        }
    }

    fun consumeTarget() {
        _target.value = null
    }

    /** Move the camera to a coordinate (used by search results and "locate me"). */
    fun goTo(lat: Double, lng: Double) {
        _target.value = Coords(lat, lng)
    }

    fun applyFilters(filters: Filters) {
        viewModelScope.launch { filtersStore.apply(filters) }
    }

    /** [lat]/[lng] is the current camera target (browse mode fallback). The
     *  circle + station set are anchored to the searched/located point when set. */
    fun load(lat: Double, lng: Double) {
        lastCenter = Coords(lat, lng)
        viewModelScope.launch {
            val filters = filtersStore.filters.first()
            // Always anchored on a location and always radius-limited, so the
            // map stays consistent with the Stations list.
            val anchor = filters.userLocation ?: Coords(48.8566, 2.3522)
            val r = filters.radiusKm.toDouble()
            _state.value = _state.value.copy(loading = true)
            val fuelCode = filters.fuel.code
            val stations = repo.nearby(anchor.lat, anchor.lng, r)
                .filter { haversineKm(anchor.lat, anchor.lng, it.lat, it.lng) <= r }
                .filter { it.fuels.containsKey(fuelCode) } // only stations selling the selected fuel
                .filter { filters.brands.isEmpty() || (it.brand != null && filters.brands.contains(it.brand)) }
                .filter { !filters.openH24Only || it.h24 == true }
            val items = stations.map { StationClusterItem(it, it.fuels[fuelCode]?.p) }
            val prices = items.mapNotNull { it.price }
            val (pMin, pMax) = priceBounds(prices)
            val cheapestId = items.filter { it.price != null }
                .minByOrNull { it.price!! }?.station?.id
            _state.value = _state.value.copy(
                loading = false, items = items, pMin = pMin, pMax = pMax,
                center = anchor,
                radiusKm = filters.radiusKm,
                hasLocation = true,
                cheapestId = cheapestId,
            )
        }
    }
}
