package fr.fuelradar.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.fuelradar.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberMarkerState
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPriceEuro
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.ui.common.BrandLogo
import com.google.maps.android.compose.rememberCameraPositionState
import fr.fuelradar.BuildConfig
import fr.fuelradar.domain.formatPrice
import fr.fuelradar.domain.priceColor
import fr.fuelradar.ui.common.FilterSheet

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
    var showFilters by remember { mutableStateOf(false) }
    var sheetExpanded by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        // Start zoomed on the (persisted) location; the collector recenters
        // when a location is set/changed.
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 12f)
    }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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

    LaunchedEffect(target) {
        target?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 13f),
            )
            viewModel.consumeTarget()
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val c = cameraPositionState.position.target
            viewModel.load(c.latitude, c.longitude)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
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
            if (state.hasLocation) {
                Circle(
                    center = LatLng(state.center.lat, state.center.lng),
                    radius = state.filters.radiusKm * 1000.0,
                    strokeColor = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3f,
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                )
            }
            // Pulsing highlight around the cheapest station (a real animated
            // overlay — reliable, unlike animating a rasterized marker).
            val cheapest = state.items.firstOrNull { it.station.id == state.cheapestId }
            if (cheapest != null) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val radius by pulse.animateFloat(
                    initialValue = 250f,
                    targetValue = 1100f,
                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
                    label = "radius",
                )
                val alpha by pulse.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
                    label = "alpha",
                )
                Circle(
                    center = LatLng(cheapest.station.lat, cheapest.station.lng),
                    radius = radius.toDouble(),
                    fillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                    strokeColor = Color.Transparent,
                    strokeWidth = 0f,
                )
            }
            // Individual price pins — no clustering (like the web). Capped for perf.
            state.items.take(MAX_PINS).forEach { item ->
                val markerState = rememberMarkerState(
                    key = item.station.id.toString(),
                    position = LatLng(item.station.lat, item.station.lng),
                )
                MarkerComposable(
                    keys = arrayOf(item.station.id),
                    state = markerState,
                    onClick = {
                        onOpenStation(item.station.id)
                        true
                    },
                ) {
                    PricePin(
                        priceLabel = item.price?.let { "${formatPrice(it)} €" },
                        color = item.price?.let { priceColor(it, state.pMin, state.pMax) } ?: Color.Gray,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                fr.fuelradar.ui.common.AddressSearchBar(
                    query = state.query,
                    suggestions = state.suggestions,
                    onQueryChange = viewModel::onQueryChange,
                    onSelect = viewModel::selectSuggestion,
                    onSearch = viewModel::search,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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
            }
            // Quick fuel pills (mirror of the web map).
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fr.fuelradar.data.model.FuelType.entries.forEach { ft ->
                    androidx.compose.material3.ElevatedFilterChip(
                        selected = state.filters.fuel == ft,
                        onClick = { viewModel.applyFilters(state.filters.copy(fuel = ft)) },
                        label = { Text(ft.label) },
                        colors = androidx.compose.material3.FilterChipDefaults.elevatedFilterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        }

        val sheetStations = state.items.filter { it.price != null }.sortedBy { it.price!! }

        // Bottom station sheet — collapsed carousel or expanded vertical list.
        if (sheetStations.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(if (sheetExpanded) Modifier.fillMaxHeight(0.55f) else Modifier),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (sheetExpanded) Modifier.fillMaxHeight() else Modifier)
                        .padding(top = 10.dp, bottom = 12.dp),
                ) {
                    // Handle + count — tap to expand/collapse.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sheetExpanded = !sheetExpanded }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dy ->
                                    if (dy < -6f) sheetExpanded = true
                                    else if (dy > 6f) sheetExpanded = false
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                        Text(
                            "${sheetStations.size} stations",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp),
                        )
                    }
                    if (sheetExpanded) {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sheetStations, key = { it.station.id }) { item ->
                                MapStationRow(
                                    item = item,
                                    distanceKm = haversineKm(
                                        state.center.lat, state.center.lng,
                                        item.station.lat, item.station.lng,
                                    ),
                                    cheapest = item.station.id == state.cheapestId,
                                    color = item.price?.let { priceColor(it, state.pMin, state.pMax) }
                                        ?: MaterialTheme.colorScheme.onSurface,
                                    onClick = {
                                        viewModel.goTo(item.station.lat, item.station.lng)
                                        sheetExpanded = false
                                    },
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(sheetStations.take(20), key = { it.station.id }) { item ->
                                MapStationCard(
                                    item = item,
                                    distanceKm = haversineKm(
                                        state.center.lat, state.center.lng,
                                        item.station.lat, item.station.lng,
                                    ),
                                    color = item.price?.let { priceColor(it, state.pMin, state.pMax) }
                                        ?: MaterialTheme.colorScheme.onSurface,
                                    onClick = { viewModel.goTo(item.station.lat, item.station.lng) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapStationCard(
    item: StationClusterItem,
    distanceKm: Double,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(230.dp).height(72.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLogo(item.station.brand, size = 36.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    item.station.brand ?: item.station.city,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    formatDistance(distanceKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            item.price?.let {
                Text(
                    formatPriceEuro(it),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLogo(item.station.brand, size = 36.dp)
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
private fun PricePin(priceLabel: String?, color: Color, bounce: Boolean = false) {
    val scale = if (bounce) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "bounce")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(600),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "scale",
        ).value
    } else {
        1f
    }
    if (priceLabel == null) {
        Box(modifier = Modifier.scale(scale).padding(2.dp).background(color, RoundedCornerShape(8.dp)).border(2.dp, Color.White, RoundedCornerShape(8.dp)).padding(6.dp))
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
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ClusterPin(priceLabel: String?, color: Color, count: Int) {
    Box(contentAlignment = Alignment.TopEnd) {
        Surface(
            color = color,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
        ) {
            Text(
                text = priceLabel ?: "$count",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Surface(
            color = Color(0xFF161D1B),
            shape = RoundedCornerShape(9.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
            modifier = Modifier.padding(top = 0.dp),
        ) {
            Text(
                text = "$count",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
