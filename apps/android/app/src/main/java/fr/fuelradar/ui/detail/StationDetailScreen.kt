package fr.fuelradar.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import fr.fuelradar.BuildConfig
import fr.fuelradar.data.DeptIndex
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.prefs.Filters
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPriceDelta
import fr.fuelradar.domain.formatPriceEuro
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.domain.isStale
import fr.fuelradar.domain.timeAgo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationDetailScreen(stationId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = ServiceLocator.stations
    val favStore = ServiceLocator.favorites
    val favorites by favStore.ids.collectAsStateWithLifecycle(emptySet())
    val filters by ServiceLocator.filters.filters.collectAsStateWithLifecycle(Filters())
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(
        fr.fuelradar.data.prefs.AppSettings(),
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val station by produceState<Station?>(initialValue = null, stationId) {
        var s = repo.findCached(stationId)
        if (s == null) {
            val loc = ServiceLocator.filters.filters.first().userLocation
            if (loc != null) {
                repo.nearby(loc.lat, loc.lng, 60.0)
                s = repo.findCached(stationId)
            }
        }
        value = s
    }
    val history by produceState<Map<String, List<List<Double>>>>(emptyMap(), station) {
        val st = station
        value = if (st != null) {
            repo.deptHistory(DeptIndex.getDepartment(st.cp))[stationId.toString()] ?: emptyMap()
        } else {
            emptyMap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(station?.brand ?: "Station") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    val st = station
                    if (st != null) {
                        IconButton(onClick = { shareStation(context, st, filters.fuel) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Partager")
                        }
                    }
                    val fav = favorites.contains(stationId)
                    IconButton(onClick = { scope.launch { favStore.toggle(stationId) } }) {
                        Icon(
                            imageVector = if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (fav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val st = station
        if (st == null) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Station introuvable", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header mini-map with a station pin.
            if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
                val cam = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(st.lat, st.lng), 14f)
                }
                GoogleMap(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    cameraPositionState = cam,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        scrollGesturesEnabled = false,
                        zoomGesturesEnabled = false,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false,
                    ),
                ) {
                    Marker(state = MarkerState(position = LatLng(st.lat, st.lng)))
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("${st.addr}, ${st.cp} ${st.city}", style = MaterialTheme.typography.bodyLarge)
                filters.userLocation?.let {
                    val d = haversineKm(it.lat, it.lng, st.lat, st.lng)
                    Text(
                        "À ${formatDistance(d)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = {
                    val uri = Uri.parse("geo:${st.lat},${st.lng}?q=${st.lat},${st.lng}(${Uri.encode(st.brand ?: st.city)})")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Filled.Place, contentDescription = null)
                Text("  Itinéraire")
            }

            // Availability chips.
            if (st.h24 == true || !st.services.isNullOrEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (st.h24 == true) {
                        AssistChip(
                            onClick = {},
                            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                            label = { Text("Ouvert 24h/24") },
                        )
                    }
                    st.services.orEmpty().forEach { svc ->
                        AssistChip(onClick = {}, label = { Text(svc) })
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            FuelType.entries.forEach { fuel ->
                val price = st.fuels[fuel.code] ?: return@forEach
                val series = history[fuel.code].orEmpty().map { it.getOrElse(1) { 0.0 } }
                val delta = trendDelta(series)
                val selected = fuel == filters.fuel
                val stale = settings.staleWarning && isStale(price.d)
                val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(bg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            fuel.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            formatPriceEuro(price.p),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (stale) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = "Données anciennes",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.height(14.dp),
                                )
                            }
                            Text(
                                "  ${timeAgo(price.d)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (delta != null) {
                            Text(
                                "7 j : ${formatPriceDelta(delta)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    if (series.size >= 2) {
                        TrendBars(
                            values = series.takeLast(14),
                            modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 4.dp),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            Column(modifier = Modifier.height(16.dp)) {}
        }
    }
}

private fun shareStation(context: android.content.Context, st: Station, fuel: FuelType) {
    val price = st.fuels[fuel.code]?.p
    val priceLine = if (price != null) "${fuel.label} : ${formatPriceEuro(price)}\n" else ""
    val text = buildString {
        append(st.brand ?: "Station")
        append("\n")
        append("${st.addr}, ${st.cp} ${st.city}\n")
        append(priceLine)
        append("https://www.google.com/maps/search/?api=1&query=${st.lat},${st.lng}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager la station")) }
}

private fun trendDelta(series: List<Double>): Double? {
    if (series.size < 2) return null
    val last = series.last()
    val prev = series[(series.size - 8).coerceAtLeast(0)]
    return last - prev
}

@Composable
private fun TrendBars(values: List<Double>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val n = values.size
        val gap = size.width * 0.02f
        val barWidth = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val norm = ((v - min) / range).toFloat()
            val h = size.height * (0.15f + 0.85f * norm)
            val x = i * (barWidth + gap)
            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
            )
        }
    }
}
