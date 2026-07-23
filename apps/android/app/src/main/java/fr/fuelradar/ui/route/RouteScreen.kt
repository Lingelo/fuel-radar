package fr.fuelradar.ui.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import fr.fuelradar.BuildConfig
import fr.fuelradar.R
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPrice
import fr.fuelradar.domain.priceColor
import fr.fuelradar.ui.common.AddressSearchBar
import fr.fuelradar.ui.common.StationCard

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun RouteScreen(
    onBack: () -> Unit,
    onOpenStation: (Long) -> Unit,
    viewModel: RouteViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by ServiceLocator.favorites.ids.collectAsStateWithLifecycle(emptySet())
    val hasRoute = state.routePoints.size >= 2

    // Auto-collapse the input panel and dismiss the keyboard once a route is
    // computed, so the map gets full height (and the camera fits the whole route).
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var panelExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(hasRoute) {
        if (hasRoute) {
            panelExpanded = false
            focus.clearFocus()
        }
    }

    val summary = if (hasRoute) {
        stringResource(
            R.string.route_summary,
            formatDistance(state.distanceKm),
            state.durationMin,
            state.stations.size,
        )
    } else {
        ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
            }
            Text(
                stringResource(R.string.route_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (hasRoute) {
                IconButton(onClick = { panelExpanded = !panelExpanded }) {
                    Icon(
                        if (panelExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = { viewModel.toggleList() }) {
                    if (state.showList) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.route_show_map))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.route_show_list))
                    }
                }
            }
        }

        if (panelExpanded) {
            // Start / end address inputs (same pill look as the map search).
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FieldLabel(stringResource(R.string.route_start))
                SearchFieldPill {
                    AddressSearchBar(
                        query = state.startQuery,
                        suggestions = state.startSuggestions,
                        onQueryChange = viewModel::onStartQueryChange,
                        onSelect = viewModel::selectStart,
                        onSearch = {},
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.useMyLocationAsStart() }) {
                                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.locate_me))
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                FieldLabel(stringResource(R.string.route_end))
                SearchFieldPill {
                    AddressSearchBar(
                        query = state.endQuery,
                        suggestions = state.endSuggestions,
                        onQueryChange = viewModel::onEndQueryChange,
                        onSelect = viewModel::selectEnd,
                        onSearch = {},
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }

            // Corridor distance + fuel pills.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.route_corridor),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(1, 2, 5).forEach { km ->
                    FilterChip(
                        selected = state.corridorKm == km,
                        onClick = { viewModel.setCorridor(km) },
                        label = { Text("$km km") },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FuelType.entries.forEach { fuel ->
                    FilterChip(
                        selected = state.fuel == fuel,
                        onClick = { viewModel.setFuel(fuel) },
                        label = { Text(fuel.label) },
                    )
                }
            }

            if (hasRoute) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else if (hasRoute) {
            // Collapsed: compact bar (tap to expand) so the map stays large.
            Surface(
                onClick = { panelExpanded = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        "${state.startQuery} → ${state.endQuery}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Content.
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.error -> CenteredMessage(stringResource(R.string.route_error))
                !hasRoute && !state.loading -> CenteredMessage(stringResource(R.string.route_hint))
                state.showList -> RouteList(state, favorites, onOpenStation, viewModel)
                else -> RouteMap(state, onOpenStation)
            }
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun RouteMap(state: RouteUiState, onOpenStation: (Long) -> Unit) {
    if (BuildConfig.MAPS_API_KEY.isBlank()) {
        CenteredMessage(stringResource(R.string.map_unavailable))
        return
    }
    // Center on the departure whenever the map is shown (this composable is
    // recreated on every map/list switch, so the camera re-centers on the start).
    val startPoint = state.routePoints.firstOrNull() ?: state.start
    val cameraPositionState = rememberCameraPositionState {
        if (startPoint != null) {
            position = CameraPosition.fromLatLngZoom(LatLng(startPoint.lat, startPoint.lng), 11f)
        }
    }
    val routeColor = MaterialTheme.colorScheme.primary

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
        ),
    ) {
        if (state.routePoints.size >= 2) {
            Polyline(
                points = state.routePoints.map { LatLng(it.lat, it.lng) },
                color = routeColor,
                width = 12f,
            )
            // Start / end markers.
            EndpointMarker(state.routePoints.first(), MaterialTheme.colorScheme.tertiary)
            EndpointMarker(state.routePoints.last(), MaterialTheme.colorScheme.error)
        }
        state.stations.forEach { row ->
            key(row.station.id) {
                val markerState = rememberMarkerState(
                    key = row.station.id.toString(),
                    position = LatLng(row.station.lat, row.station.lng),
                )
                val label = row.price?.let { "${formatPrice(it)} €" }
                val color = row.price?.let { priceColor(it, state.pMin, state.pMax) } ?: Color.Gray
                MarkerComposable(
                    keys = arrayOf(row.station.id),
                    state = markerState,
                    onClick = { onOpenStation(row.station.id); true },
                ) {
                    RoutePricePin(label, color)
                }
            }
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun EndpointMarker(point: fr.fuelradar.domain.Coords, color: Color) {
    val markerState = rememberMarkerState(position = LatLng(point.lat, point.lng))
    MarkerComposable(keys = arrayOf(point.lat, point.lng), state = markerState) {
        Box(
            modifier = Modifier.size(18.dp).padding(2.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(50),
                color = color,
                border = BorderStroke(3.dp, Color.White),
                content = {},
            )
        }
    }
}

@Composable
private fun RoutePricePin(label: String?, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, Color.White),
    ) {
        Text(
            text = label ?: "—",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun RouteList(
    state: RouteUiState,
    favorites: Set<Long>,
    onOpenStation: (Long) -> Unit,
    viewModel: RouteViewModel,
) {
    if (state.stations.isEmpty()) {
        CenteredMessage(stringResource(R.string.route_no_result))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.stations, key = { it.station.id }) { row ->
            val color = row.price?.let { priceColor(it, state.pMin, state.pMax) }
                ?: MaterialTheme.colorScheme.onSurfaceVariant
            StationCard(
                station = row.station,
                price = row.price,
                distanceKm = row.distanceKm,
                priceColor = color,
                selectedFuel = state.fuel,
                favorite = favorites.contains(row.station.id),
                cheapest = row.station.id == state.cheapestId,
                onToggleFavorite = { viewModel.toggleFavorite(row.station.id) },
                onViewMap = { viewModel.showMap() },
                onClick = { onOpenStation(row.station.id) },
            )
        }
    }
}

@Composable
private fun SearchFieldPill(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) { content() }
}
