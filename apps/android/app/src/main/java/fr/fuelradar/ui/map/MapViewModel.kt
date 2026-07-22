package fr.fuelradar.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.prefs.Filters
import fr.fuelradar.domain.Coords
import fr.fuelradar.domain.priceBounds
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
    val filters: Filters = Filters(),
    val center: Coords = Coords(48.8566, 2.3522),
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

    init {
        viewModelScope.launch {
            filtersStore.filters.collect { f ->
                _state.value = _state.value.copy(filters = f)
                load(lastCenter.lat, lastCenter.lng)
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            val hit = geocoder.search(q).firstOrNull() ?: return@launch
            _target.value = Coords(hit.lat, hit.lng)
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

    fun load(lat: Double, lng: Double) {
        lastCenter = Coords(lat, lng)
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val filters = filtersStore.filters.first()
            val fuelCode = filters.fuel.code
            val stations = repo.nearby(lat, lng, filters.radiusKm.toDouble())
                .filter { filters.brands.isEmpty() || (it.brand != null && filters.brands.contains(it.brand)) }
                .filter { !filters.openH24Only || it.h24 == true }
            val items = stations.map { StationClusterItem(it, it.fuels[fuelCode]?.p) }
            val prices = items.mapNotNull { it.price }
            val (pMin, pMax) = priceBounds(prices)
            _state.value = _state.value.copy(
                loading = false, items = items, pMin = pMin, pMax = pMax,
                center = Coords(lat, lng),
            )
        }
    }
}
