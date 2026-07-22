package fr.fuelradar.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPriceEuro
import fr.fuelradar.domain.haversineKm
import com.google.maps.android.compose.rememberCameraPositionState
import fr.fuelradar.BuildConfig
import fr.fuelradar.domain.formatPrice
import fr.fuelradar.domain.priceColor
import fr.fuelradar.ui.common.FilterSheet

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
    val cameraPositionState = rememberCameraPositionState {
        // Country-level view of metropolitan France (mirror of the web default).
        position = CameraPosition.fromLatLngZoom(LatLng(46.6, 2.5), 6f)
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
            Clustering(
                items = state.items,
                onClusterItemClick = { item ->
                    onOpenStation(item.station.id)
                    true
                },
                clusterContent = { cluster ->
                    val prices = cluster.items.mapNotNull { it.price }
                    val min = prices.minOrNull()
                    ClusterPin(
                        priceLabel = min?.let { "${formatPrice(it)} €" },
                        color = min?.let { priceColor(it, state.pMin, state.pMax) }
                            ?: MaterialTheme.colorScheme.secondary,
                        count = cluster.size,
                    )
                },
                clusterItemContent = { item ->
                    val color = item.price?.let { priceColor(it, state.pMin, state.pMax) } ?: Color.Gray
                    PricePin(
                        priceLabel = item.price?.let { "${formatPrice(it)} €" },
                        color = color,
                        bounce = item.station.id == state.cheapestId,
                    )
                },
            )
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
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.filters))
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
                    androidx.compose.material3.FilterChip(
                        selected = state.filters.fuel == ft,
                        onClick = { viewModel.applyFilters(state.filters.copy(fuel = ft)) },
                        label = { Text(ft.label) },
                    )
                }
            }
        }

        val sheetStations = state.items.filter { it.price != null }.sortedBy { it.price!! }

        FloatingActionButton(
            onClick = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    fetchLocation()
                } else {
                    permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (sheetStations.isNotEmpty()) 156.dp else 16.dp),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.locate_me))
        }

        // Bottom station carousel (cheapest first) — the map's station list.
        if (sheetStations.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(2.dp),
                            ),
                    )
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
                                cheapest = item.station.id == state.cheapestId,
                                color = item.price?.let { priceColor(it, state.pMin, state.pMax) }
                                    ?: MaterialTheme.colorScheme.onSurface,
                                onClick = { onOpenStation(item.station.id) },
                            )
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
    cheapest: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(230.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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
                )
                if (cheapest) {
                    Text(
                        stringResource(R.string.cheapest),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
