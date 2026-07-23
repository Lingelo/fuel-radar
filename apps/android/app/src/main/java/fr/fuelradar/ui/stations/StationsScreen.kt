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
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.material3.LocalContentColor
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
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
import fr.fuelradar.ui.common.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    onOpenStation: (Long) -> Unit,
    onOpenMap: () -> Unit = {},
    onOpenRoute: () -> Unit = {},
    viewModel: StationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var showFilters by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationGranted = fr.fuelradar.ui.common.rememberLocationGranted()
    fun fetchLocation() {
        if (!fr.fuelradar.ui.common.hasFineLocation(context)) return
        runCatching {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        viewModel.onLocated(loc.latitude, loc.longitude)
                    } else {
                        fused.lastLocation.addOnSuccessListener { last ->
                            if (last != null) viewModel.onLocated(last.latitude, last.longitude)
                        }
                    }
                }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationGranted.value = granted
        if (granted) fetchLocation()
    }
    val onLocateClick = {
        if (locationGranted.value) fetchLocation()
        else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
                        IconButton(onClick = onOpenRoute) {
                            Icon(
                                Icons.Filled.Route,
                                contentDescription = stringResource(R.string.route_title),
                            )
                        }
                        IconButton(onClick = onLocateClick) {
                            Icon(
                                if (locationGranted.value) Icons.Filled.MyLocation
                                else Icons.Filled.LocationDisabled,
                                contentDescription = stringResource(R.string.locate_me),
                                tint = if (locationGranted.value) LocalContentColor.current
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.filters))
                        }
                    }
                },
            )

            fr.fuelradar.ui.common.FuelSelector(
                selected = state.filters.fuel,
                onSelect = { viewModel.setFuel(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            if (state.routeActive) {
                // Route mode: the list is ordered by distance from the start, not
                // price — make that explicit instead of showing the sort chips.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "${stringResource(R.string.route_title)} · ${stringResource(R.string.sort_distance)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            } else {
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
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.station.id }) { row ->
                    val color = row.price?.let { priceColor(it, state.pMin, state.pMax) }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant
                    val d = state.filters.fuel.dateIn(row.station.fuels)
                    val stale = settings.staleWarning && d != null && isStale(d)
                    val updated = d?.let { stringResource(R.string.updated, relativeTime(it)) }
                    fr.fuelradar.ui.common.StationCard(
                        station = row.station,
                        price = row.price,
                        distanceKm = row.distanceKm,
                        priceColor = color,
                        cheapest = row.station.id == state.cheapestId,
                        favorite = state.favorites.contains(row.station.id),
                        selectedFuel = state.filters.fuel,
                        stale = stale,
                        updatedLabel = updated,
                        onToggleFavorite = { viewModel.toggleFavorite(row.station.id) },
                        onViewMap = {
                            viewModel.focusOnMap(row.station)
                            onOpenMap()
                        },
                        onClick = { onOpenStation(row.station.id) },
                    )
                }
            }
        }
    }
}
