package fr.fuelradar.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.fuelradar.R
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.prefs.AppSettings
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.domain.isStale
import fr.fuelradar.domain.priceBounds
import fr.fuelradar.domain.priceColor
import fr.fuelradar.ui.common.StationCard
import fr.fuelradar.ui.common.relativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FavRow(
    val station: Station,
    val price: Double?,
    val distanceKm: Double?,
)

data class FavoritesUiState(
    val loading: Boolean = true,
    val count: Int = 0,
    val rows: List<FavRow> = emptyList(),
    val fuel: FuelType = FuelType.GAZOLE,
    val pMin: Double = 0.0,
    val pMax: Double = 1.0,
)

class FavoritesViewModel : ViewModel() {
    private val repo = ServiceLocator.stations
    private val favStore = ServiceLocator.favorites
    private val filtersStore = ServiceLocator.filters

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(favStore.ids, filtersStore.filters) { ids, f -> ids to f }.collect { (ids, f) ->
                if (ids.isEmpty()) {
                    _state.value = FavoritesUiState(loading = false, count = 0, fuel = f.fuel)
                    return@collect
                }
                _state.value = _state.value.copy(loading = true, count = ids.size, fuel = f.fuel)
                val loc = f.userLocation
                // Warm the cache with departments around the user so out-of-cache
                // favorites resolve (mirror of the web behavior).
                if (loc != null) {
                    repo.nearby(loc.lat, loc.lng, maxOf(f.radiusKm, 50).toDouble())
                }
                val rows = ids.mapNotNull { repo.findCached(it) }.map { st ->
                    FavRow(
                        station = st,
                        price = st.fuels[f.fuel.code]?.p,
                        distanceKm = loc?.let { haversineKm(it.lat, it.lng, st.lat, st.lng) },
                    )
                }.let { list ->
                    if (loc != null) list.sortedBy { it.distanceKm ?: Double.MAX_VALUE } else list
                }
                val (pMin, pMax) = priceBounds(rows.mapNotNull { it.price })
                _state.value = FavoritesUiState(
                    loading = false, count = ids.size, rows = rows,
                    fuel = f.fuel, pMin = pMin, pMax = pMax,
                )
            }
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { favStore.toggle(id) }
    }

    fun setLocation(lat: Double, lng: Double, label: String?) {
        viewModelScope.launch { filtersStore.setLocation(lat, lng, label) }
    }
}

@Composable
fun FavoritesScreen(
    onOpenStation: (Long) -> Unit,
    onOpenMap: () -> Unit = {},
    viewModel: FavoritesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(AppSettings())

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: title + count.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.favorites_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.stations_count, state.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            state.count == 0 && !state.loading -> EmptyFavorites()
            state.count > 0 && state.rows.isEmpty() && !state.loading -> LoadingOutside()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.station.id }) { row ->
                    val color = row.price?.let { priceColor(it, state.pMin, state.pMax) }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant
                    val d = row.station.fuels[state.fuel.code]?.d
                    val stale = settings.staleWarning && d != null && isStale(d)
                    val updated = d?.let { stringResource(R.string.updated, relativeTime(it)) }
                    StationCard(
                        station = row.station,
                        price = row.price,
                        distanceKm = row.distanceKm,
                        priceColor = color,
                        selectedFuel = state.fuel,
                        favorite = true,
                        stale = stale,
                        updatedLabel = updated,
                        onToggleFavorite = { viewModel.toggleFavorite(row.station.id) },
                        onViewMap = {
                            viewModel.setLocation(
                                row.station.lat,
                                row.station.lng,
                                "${row.station.cp} ${row.station.city}",
                            )
                            onOpenMap()
                        },
                        onClick = { onOpenStation(row.station.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFavorites() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.StarBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.no_favorites),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.no_favorites_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingOutside() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.fav_loading_outside),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
