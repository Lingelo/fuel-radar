package fr.fuelradar.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import fr.fuelradar.BuildConfig
import fr.fuelradar.R
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPrice
import fr.fuelradar.domain.formatPriceEuro
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.domain.priceColor
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.geo.AddressResult
import fr.fuelradar.data.route.RouteState
import fr.fuelradar.ui.common.AddressSearchBar
import fr.fuelradar.ui.common.BrandLogo
import fr.fuelradar.ui.common.FuelSelector
import fr.fuelradar.ui.common.hasFineLocation
import fr.fuelradar.ui.common.rememberLocationGranted
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private const val MAX_PINS = 150

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(
    onOpenStation: (Long) -> Unit,
    viewModel: MapViewModel = viewModel(),
) {
    if (BuildConfig.MAPS_API_KEY.isBlank()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.map_unavailable), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.map_key_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val route by viewModel.routeState.collectAsStateWithLifecycle()
    val routeInput by viewModel.routeInput.collectAsStateWithLifecycle()
    // Station flagged by "view on map": bounce its pin, then release after a moment.
    val focusId by ServiceLocator.filters.focusStationId.collectAsStateWithLifecycle(null)
    LaunchedEffect(focusId) {
        if (focusId != null) {
            delay(4500)
            ServiceLocator.filters.setFocusStation(null)
        }
    }
    var showFilters by remember { mutableStateOf(false) }
    // Cold-start framing (no saved location yet): a wide Western-Europe view
    // covering France, Spain and Portugal. Once a location is known the camera
    // jumps to it (see the target effect below).
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(43.0, -3.0), 4.6f)
    }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationGranted = rememberLocationGranted()
    fun fetchLocation() {
        if (!hasFineLocation(context)) return
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
    // On first launch with no saved location, ask for / use the device location.
    LaunchedEffect(Unit) {
        if (ServiceLocator.filters.filters.first().userLocation == null) {
            if (locationGranted.value) fetchLocation()
            else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    if (showFilters) {
        FilterSheetHost(state, viewModel) { showFilters = false }
    }

    // Recenter the camera whenever a search / locate / "view on map" resolves.
    // Station loading is driven solely by the filters collector in the VM (the
    // station set is always anchored on userLocation), so there is deliberately
    // no camera-driven reload here — that previously raced with the filters
    // update and left stale pins from the previous location.
    // The first recenter (persisted location on launch) jumps instantly so the
    // map doesn't visibly fly from the default Paris position each start; later
    // recenters (search / locate) animate.
    var firstCenter by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        target?.let {
            val update = CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 12f)
            if (firstCenter) {
                firstCenter = false
                cameraPositionState.move(update)
            } else {
                cameraPositionState.animate(update)
            }
            viewModel.consumeTarget()
        }
    }

    // In route mode, frame the WHOLE trip (start → end) so the user sees stations
    // across every country crossed, not just around the start.
    LaunchedEffect(route.routePoints) {
        if (route.routePoints.size >= 2) {
            val b = com.google.android.gms.maps.model.LatLngBounds.builder()
            route.routePoints.forEach { b.include(LatLng(it.lat, it.lng)) }
            runCatching {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(b.build(), 120))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            // Native blue "my location" dot so the user sees where they are (#7).
            properties = MapProperties(isMyLocationEnabled = locationGranted.value),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                indoorLevelPickerEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
            ),
        ) {
            if (route.active && route.hasRoute) {
                // Route mode: the trip line + the stations selected along it.
                Polyline(
                    points = route.routePoints.map { LatLng(it.lat, it.lng) },
                    color = MaterialTheme.colorScheme.primary,
                    width = 12f,
                )
                // #6: a pulse traveling from start to end, looping — a moving
                // dot along the polyline, cheap to animate (native Circle).
                val pulse = rememberInfiniteTransition(label = "routePulse")
                val t by pulse.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(2600, easing = LinearEasing),
                        RepeatMode.Restart,
                    ),
                    label = "t",
                )
                val pts = route.routePoints
                val idx = (t * (pts.size - 1)).toInt().coerceIn(0, pts.size - 1)
                Circle(
                    center = LatLng(pts[idx].lat, pts[idx].lng),
                    radius = 3500.0,
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    strokeColor = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4f,
                )
                route.stations.take(MAX_PINS).forEach { rs ->
                    key(rs.station.id) {
                        val ms = rememberMarkerState(
                            key = rs.station.id.toString(),
                            position = LatLng(rs.station.lat, rs.station.lng),
                        )
                        val lbl = rs.price?.let { "${formatPrice(it)} €" }
                        val c = rs.price?.let { priceColor(it, route.pMin, route.pMax) } ?: Color.Gray
                        MarkerComposable(
                            keys = arrayOf(rs.station.id),
                            state = ms,
                            onClick = { onOpenStation(rs.station.id); true },
                        ) { PricePin(lbl, c) }
                    }
                }
            } else {
            // Search-radius circle around the current location.
            Circle(
                center = LatLng(state.center.lat, state.center.lng),
                radius = state.radiusKm * 1000.0,
                strokeColor = MaterialTheme.colorScheme.primary,
                strokeWidth = 3f,
                fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            )
            // Individual price pins (no clustering). The cheapest one bounces.
            // Each marker is wrapped in key(station.id) so Compose disposes the
            // markers that leave the set when the location/radius changes —
            // otherwise MarkerComposable leaves ghost pins from the old search.
            state.items.take(MAX_PINS).forEach { item ->
                key(item.station.id) {
                    val markerState = rememberMarkerState(
                        key = item.station.id.toString(),
                        position = LatLng(item.station.lat, item.station.lng),
                    )
                    val label = item.price?.let { "${formatPrice(it)} €" }
                    val color = item.price?.let { priceColor(it, state.pMin, state.pMax) } ?: Color.Gray
                    if (item.station.id == state.cheapestId || item.station.id == focusId) {
                        val transition = rememberInfiniteTransition(label = "bounce")
                        val scale by transition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.22f,
                            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                            label = "scale",
                        )
                        // Quantized scale in the marker keys forces re-rasterization
                        // each step, so the pin visibly bounces. The padding keeps the
                        // rasterized bounds large enough so the scaled pill isn't clipped.
                        MarkerComposable(
                            keys = arrayOf(item.station.id, (scale * 12).toInt()),
                            state = markerState,
                            onClick = { onOpenStation(item.station.id); true },
                        ) {
                            Box(modifier = Modifier.padding(8.dp)) {
                                PricePin(label, color, scale = scale)
                            }
                        }
                    } else {
                        MarkerComposable(
                            keys = arrayOf(item.station.id),
                            state = markerState,
                            onClick = { onOpenStation(item.station.id); true },
                        ) {
                            PricePin(label, color)
                        }
                    }
                }
            }
            }
        }

        // Search + fuel pills overlay.
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter),
        ) {
            if (route.active) {
                RouteInputPanel(
                    route = route,
                    input = routeInput,
                    onStartQuery = viewModel::onRouteStartQueryChange,
                    onEndQuery = viewModel::onRouteEndQueryChange,
                    onSelectStart = viewModel::selectRouteStart,
                    onSelectEnd = viewModel::selectRouteEnd,
                    onExit = viewModel::exitRouteMode,
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    AddressSearchBar(
                        query = state.query,
                        suggestions = state.suggestions,
                        onQueryChange = viewModel::onQueryChange,
                        onSelect = viewModel::selectSuggestion,
                        onSearch = viewModel::search,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { viewModel.enterRouteMode() }) {
                                    Icon(Icons.Filled.Route, contentDescription = stringResource(R.string.route_title))
                                }
                                IconButton(onClick = onLocateClick) {
                                    Icon(
                                        if (locationGranted.value) Icons.Filled.MyLocation
                                        else Icons.Filled.LocationDisabled,
                                        contentDescription = stringResource(R.string.locate_me),
                                        tint = if (locationGranted.value) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error,
                                    )
                                }
                                IconButton(onClick = { showFilters = true }) {
                                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.filters))
                                }
                            }
                        },
                    )
                }
            }
            FuelSelector(
                selected = state.filters.fuel,
                onSelect = { viewModel.applyFilters(state.filters.copy(fuel = it)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

    }
}

@Composable
private fun StationSheet(
    stations: List<StationClusterItem>,
    center: fr.fuelradar.domain.Coords,
    cheapestId: Long?,
    pMin: Double,
    pMax: Double,
    onOpen: (StationClusterItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val closedPx = with(density) { 72.dp.toPx() }
    val basePx = with(density) { 240.dp.toPx() }
    val openPx = with(density) { (config.screenHeightDp * 0.6f).dp.toPx() }
    val heightAnim = remember { Animatable(basePx) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { heightAnim.value.toDp() }),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                scope.launch {
                                    heightAnim.snapTo(
                                        (heightAnim.value - dy).coerceIn(closedPx, openPx),
                                    )
                                }
                            },
                            onDragEnd = {
                                val target = listOf(closedPx, basePx, openPx)
                                    .minByOrNull { abs(it - heightAnim.value) } ?: basePx
                                scope.launch { heightAnim.animateTo(target) }
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                )
                Text(
                    "${stations.size} stations",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(stations, key = { it.station.id }) { item ->
                    MapStationRow(
                        item = item,
                        distanceKm = haversineKm(center.lat, center.lng, item.station.lat, item.station.lng),
                        cheapest = item.station.id == cheapestId,
                        color = item.price?.let { priceColor(it, pMin, pMax) }
                            ?: MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            onOpen(item)
                            scope.launch { heightAnim.animateTo(basePx) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MapStationRow(
    item: StationClusterItem,
    distanceKm: Double,
    cheapest: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLogo(item.station.brand, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    item.station.brand ?: item.station.city,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    "${formatDistance(distanceKm)} · ${item.station.cp} ${item.station.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (cheapest) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            stringResource(R.string.cheapest),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            item.price?.let {
                Text(
                    formatPriceEuro(it),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (cheapest) MaterialTheme.colorScheme.tertiary else color,
                )
            }
        }
    }
}

@Composable
private fun PricePin(priceLabel: String?, color: Color, scale: Float = 1f) {
    if (priceLabel == null) {
        Box(
            modifier = Modifier
                .scale(scale)
                .padding(2.dp)
                .background(color, RoundedCornerShape(8.dp))
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                .padding(6.dp),
        )
        return
    }
    Surface(
        modifier = Modifier.scale(scale),
        color = color,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
    ) {
        Text(
            text = priceLabel,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FilterSheetHost(
    state: MapUiState,
    viewModel: MapViewModel,
    onDismiss: () -> Unit,
) {
    fr.fuelradar.ui.common.FilterSheet(
        current = state.filters,
        onDismiss = onDismiss,
        onApply = { viewModel.applyFilters(it); onDismiss() },
    )
}

/** Route-mode overlay: start/end address inputs + trip summary, on the map. */
@Composable
private fun RouteInputPanel(
    route: RouteState,
    input: RouteInputState,
    onStartQuery: (String) -> Unit,
    onEndQuery: (String) -> Unit,
    onSelectStart: (AddressResult) -> Unit,
    onSelectEnd: (AddressResult) -> Unit,
    onExit: () -> Unit,
) {
    // Collapse the inputs once a route is computed so the map gets full height;
    // the header chevron re-opens them (mirror of the old route screen).
    var expanded by remember { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    LaunchedEffect(route.hasRoute) {
        if (route.hasRoute) {
            expanded = false
            focus.clearFocus()
        }
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Route,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.route_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (route.hasRoute) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
            if (expanded) {
                Text(
                    stringResource(R.string.route_start),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                AddressSearchBar(
                    query = input.startQuery,
                    suggestions = input.startSuggestions,
                    onQueryChange = onStartQuery,
                    onSelect = onSelectStart,
                    onSearch = {},
                )
                Text(
                    stringResource(R.string.route_end),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
                AddressSearchBar(
                    query = input.endQuery,
                    suggestions = input.endSuggestions,
                    onQueryChange = onEndQuery,
                    onSelect = onSelectEnd,
                    onSearch = {},
                )
            }
            if (route.error) {
                Text(
                    stringResource(R.string.route_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            } else if (route.hasRoute) {
                Text(
                    stringResource(
                        R.string.route_summary,
                        formatDistance(route.distanceKm),
                        route.durationMin,
                        route.stations.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                )
            }
        }
    }
}
