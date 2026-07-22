package fr.fuelradar.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.Station
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
)

class MapViewModel : ViewModel() {
    private val repo = ServiceLocator.stations
    private val filtersStore = ServiceLocator.filters

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    init {
        // Initial load around Paris; camera moves can trigger reload later.
        load(48.8566, 2.3522, 25.0)
    }

    fun load(lat: Double, lng: Double, radiusKm: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val fuelCode = filtersStore.filters.first().fuel.code
            val stations = repo.nearby(lat, lng, radiusKm)
            val items = stations.map { StationClusterItem(it, it.fuels[fuelCode]?.p) }
            val prices = items.mapNotNull { it.price }
            val (pMin, pMax) = priceBounds(prices)
            _state.value = MapUiState(loading = false, items = items, pMin = pMin, pMax = pMax)
        }
    }
}
