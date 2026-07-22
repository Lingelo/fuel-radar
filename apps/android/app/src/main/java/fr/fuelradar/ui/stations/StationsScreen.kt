package fr.fuelradar.ui.stations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.fuelradar.ui.common.FilterSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.prefs.SortMode
import androidx.compose.ui.res.stringResource
import fr.fuelradar.R
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.prefs.AppSettings
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPriceEuro
import fr.fuelradar.domain.isStale
import fr.fuelradar.domain.priceColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    onOpenStation: (Long) -> Unit,
    viewModel: StationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var showFilters by remember { mutableStateOf(false) }

    if (showFilters) {
        FilterSheet(
            current = state.filters,
            onDismiss = { showFilters = false },
            onApply = { viewModel.applyFilters(it); showFilters = false },
        )
    }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            fr.fuelradar.ui.common.AddressSearchBar(
                query = state.query,
                suggestions = state.suggestions,
                onQueryChange = viewModel::onQueryChange,
                onSelect = viewModel::selectSuggestion,
                onSearch = viewModel::search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                trailingIcon = {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.filters))
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FuelType.entries.forEach { fuel ->
                    FilterChip(
                        selected = state.filters.fuel == fuel,
                        onClick = { viewModel.setFuel(fuel) },
                        label = { Text(fuel.label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.filters.sort == mode,
                        onClick = { viewModel.setSort(mode) },
                        label = { Text(stringResource(if (mode == SortMode.PRICE) R.string.sort_price else R.string.sort_distance)) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.station.id }) { row ->
                    val color = row.price?.let { priceColor(it, state.pMin, state.pMax) }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant
                    val d = row.station.fuels[state.filters.fuel.code]?.d
                    val stale = settings.staleWarning && d != null && isStale(d)
                    StationCard(
                        row = row,
                        priceColor = color,
                        cheapest = row.station.id == state.cheapestId,
                        favorite = state.favorites.contains(row.station.id),
                        fuelLabel = state.filters.fuel.label,
                        stale = stale,
                        onToggleFavorite = { viewModel.toggleFavorite(row.station.id) },
                        onClick = { onOpenStation(row.station.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StationCard(
    row: StationRow,
    priceColor: Color,
    cheapest: Boolean,
    favorite: Boolean,
    fuelLabel: String,
    stale: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand avatar (initial).
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (row.station.brand ?: row.station.city).take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.station.brand ?: "Station",
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
                        text = " ${formatDistance(row.distanceKm)} · ${row.station.cp} ${row.station.city}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (cheapest) {
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = " " + stringResource(R.string.cheapest),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                row.price?.let {
                    Text(
                        text = formatPriceEuro(it),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = priceColor,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stale) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = stringResource(R.string.stale_data),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(fuelLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite),
                    tint = if (favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
