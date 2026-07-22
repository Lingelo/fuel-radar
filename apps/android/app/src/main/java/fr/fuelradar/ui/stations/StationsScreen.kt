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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.prefs.SortMode
import androidx.compose.ui.res.stringResource
import fr.fuelradar.R
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.prefs.AppSettings
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPrice
import fr.fuelradar.domain.formatPriceEuro
import fr.fuelradar.domain.isStale
import fr.fuelradar.domain.priceColor
import fr.fuelradar.domain.timeAgo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    onOpenStation: (Long) -> Unit,
    onOpenMap: () -> Unit = {},
    viewModel: StationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var showFilters by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) viewModel.onLocated(loc.latitude, loc.longitude)
                }
            }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) fetchLocation() }
    val onLocateClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocation()
        } else {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

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
                    Row {
                        IconButton(onClick = onLocateClick) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = stringResource(R.string.locate_me),
                            )
                        }
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.filters))
                        }
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
                    val updated = d?.let { stringResource(R.string.updated, timeAgo(it)) }
                    StationCard(
                        row = row,
                        priceColor = color,
                        cheapest = row.station.id == state.cheapestId,
                        favorite = state.favorites.contains(row.station.id),
                        selectedFuel = state.filters.fuel,
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StationCard(
    row: StationRow,
    priceColor: Color,
    cheapest: Boolean,
    favorite: Boolean,
    selectedFuel: FuelType,
    stale: Boolean,
    updatedLabel: String?,
    onToggleFavorite: () -> Unit,
    onViewMap: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (cheapest) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (cheapest) 3.dp else 1.dp),
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.LocalGasStation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    row.station.brand ?: stringResource(R.string.station_fallback),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        " ${formatDistance(row.distanceKm)} • ${row.station.addr.ifBlank { row.station.city }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        val others = FuelType.entries
                            .filter { it != selectedFuel && row.station.fuels.containsKey(it.code) }
                            .take(3)
                        if (others.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                others.forEach { ft ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            "${ft.label} ${formatPrice(row.station.fuels[ft.code]!!.p)} €",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        row.price?.let {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    formatPrice(it),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = priceColor,
                                )
                                Text(
                                    " €",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = priceColor,
                                )
                            }
                        }
                        Text(
                            selectedFuel.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (stale) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = stringResource(R.string.stale_data),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            updatedLabel ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stale) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = if (stale) 4.dp else 0.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = if (favorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onViewMap) {
                            Text(
                                stringResource(R.string.view_on_map),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Map,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (cheapest) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(bottomStart = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.cheapest),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
