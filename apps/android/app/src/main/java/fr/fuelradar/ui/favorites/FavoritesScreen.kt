package fr.fuelradar.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import fr.fuelradar.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.Station
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPriceEuro
import fr.fuelradar.domain.haversineKm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FavRow(
    val station: Station,
    val price: Double?,
    val distanceKm: Double?,
    val fuelLabel: String,
)

data class FavoritesUiState(
    val loading: Boolean = true,
    val rows: List<FavRow> = emptyList(),
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
                    _state.value = FavoritesUiState(loading = false, rows = emptyList())
                    return@collect
                }
                _state.value = _state.value.copy(loading = true)
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
                        fuelLabel = f.fuel.label,
                    )
                }.let { list ->
                    if (loc != null) list.sortedBy { it.distanceKm ?: Double.MAX_VALUE } else list
                }
                _state.value = FavoritesUiState(loading = false, rows = rows)
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    onOpenStation: (Long) -> Unit,
    viewModel: FavoritesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.loading && state.rows.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.no_favorites), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.no_favorites_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.rows, key = { it.station.id }) { row ->
            ElevatedCard(
                onClick = { onOpenStation(row.station.id) },
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (row.station.brand ?: row.station.city).take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            row.station.brand ?: "Station",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                " " + (row.distanceKm?.let { "${formatDistance(it)} · " } ?: "") +
                                    "${row.station.cp} ${row.station.city}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    row.price?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatPriceEuro(it),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(row.fuelLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
