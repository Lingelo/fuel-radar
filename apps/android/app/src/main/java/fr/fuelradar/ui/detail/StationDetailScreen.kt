package fr.fuelradar.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import fr.fuelradar.R
import fr.fuelradar.data.DeptIndex
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.model.Station
import fr.fuelradar.data.prefs.Filters
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPrice
import fr.fuelradar.domain.formatPriceDelta
import fr.fuelradar.domain.haversineKm
import fr.fuelradar.domain.priceBounds
import fr.fuelradar.domain.priceColor
import fr.fuelradar.ui.common.BrandLogo
import fr.fuelradar.ui.common.relativeTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationDetailScreen(stationId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = ServiceLocator.stations
    val favStore = ServiceLocator.favorites
    val favorites by favStore.ids.collectAsStateWithLifecycle(emptySet())
    val filters by ServiceLocator.filters.filters.collectAsStateWithLifecycle(Filters())
    val scope = rememberCoroutineScope()

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

    val st = station
    if (st == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.station_not_found), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.close),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp).clickable { onBack() },
            )
        }
        return
    }

    val fav = favorites.contains(stationId)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Map header with overlaid back / share / favorite.
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
                val cam = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(st.lat, st.lng), 15f)
                }
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cam,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false, scrollGesturesEnabled = false,
                        zoomGesturesEnabled = false, tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false, mapToolbarEnabled = false,
                        compassEnabled = false,
                    ),
                ) {
                    Marker(state = MarkerState(position = LatLng(st.lat, st.lng)))
                }
            }
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close), onBack,
                Modifier.align(Alignment.TopStart).padding(12.dp))
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIconButton(Icons.Filled.Share, stringResource(R.string.share), { shareStation(context, st, filters.fuel) })
                RoundIconButton(
                    if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                    stringResource(R.string.favorite),
                    { scope.launch { favStore.toggle(stationId) } },
                    tint = if (fav) MaterialTheme.colorScheme.primary else null,
                )
            }
        }

        // Identity card.
        DetailCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(st.brand, size = 52.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        st.brand ?: stringResource(R.string.station_fallback),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        val dist = filters.userLocation?.let { " • ${formatDistance(haversineKm(it.lat, it.lng, st.lat, st.lng))}" } ?: ""
                        Text(
                            " ${st.addr}, ${st.cp} ${st.city}$dist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(
                onClick = {
                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${st.lat},${st.lng}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.Place, contentDescription = null)
                Text("  " + stringResource(R.string.directions))
            }
        }

        // Prices card — each fuel colored by tier (across this station's fuels).
        val available = FuelType.entries.filter { st.fuels.containsKey(it.code) }
        val ownPrices = available.mapNotNull { st.fuels[it.code]?.p }
        val (pMin, pMax) = priceBounds(ownPrices)
        DetailCard {
            SectionTitle(stringResource(R.string.current_prices))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                available.forEach { fuel ->
                    val fp = st.fuels[fuel.code]!!
                    val selected = fuel == filters.fuel
                    val delta = trendDelta(history[fuel.code])
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                fuel.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${formatPrice(fp.p)} €",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = priceColor(fp.p, pMin, pMax),
                            )
                            if (delta != null && delta != 0.0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (delta < 0) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                                        null,
                                        tint = if (delta < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        " ${formatPriceDelta(delta)}/sem",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (delta < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Text(
                                    relativeTime(fp.d),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Trend card — interactive chart for the selected fuel.
        DetailCard {
            SectionTitle(stringResource(R.string.trend_title, filters.fuel.label))
            val series = history[filters.fuel.code].orEmpty()
                .takeLast(30).map { it.getOrElse(1) { 0.0 } to it.getOrElse(0) { 0.0 } }
            if (series.size < 2) {
                Text(
                    stringResource(R.string.history_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TrendChart(
                    prices = series.map { it.first },
                    epochs = series.map { it.second.toLong() },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Services card.
        if (st.h24 == true || !st.services.isNullOrEmpty()) {
            DetailCard {
                SectionTitle(stringResource(R.string.services))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.h24 == true) {
                        AssistChip(
                            onClick = {},
                            leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                            label = { Text(stringResource(R.string.open_24h)) },
                        )
                    }
                    st.services.orEmpty().forEach { svc ->
                        AssistChip(onClick = {}, label = { Text(svc) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = tint ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TrendChart(prices: List<Double>, epochs: List<Long>, color: Color) {
    var selected by remember(prices) { mutableIntStateOf(prices.lastIndex) }
    val min = prices.min()
    val max = prices.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0

    Column {
        Text(
            "${formatPrice(prices[selected])} €  ·  ${shortDate(epochs.getOrElse(selected) { 0L })}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val guide = MaterialTheme.colorScheme.outlineVariant
        Canvas(
            modifier = Modifier.fillMaxWidth().height(140.dp)
                .pointerInput(prices) {
                    detectTapGestures { o ->
                        selected = ((o.x / size.width) * (prices.size - 1)).roundToInt()
                            .coerceIn(0, prices.lastIndex)
                    }
                }
                .pointerInput(prices) {
                    detectHorizontalDragGestures { change, _ ->
                        selected = ((change.position.x / size.width) * (prices.size - 1)).roundToInt()
                            .coerceIn(0, prices.lastIndex)
                    }
                },
        ) {
            val stepX = size.width / (prices.size - 1)
            fun x(i: Int) = i * stepX
            fun y(v: Double) = size.height * (1f - ((v - min) / range).toFloat())
            // Line.
            var prev: Offset? = null
            prices.forEachIndexed { i, v ->
                val p = Offset(x(i), y(v))
                prev?.let { drawLine(color, it, p, strokeWidth = 4f) }
                prev = p
            }
            // Selected guide + point.
            val sx = x(selected)
            val sy = y(prices[selected])
            drawLine(guide, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 2f)
            drawCircle(Color.White, radius = 9f, center = Offset(sx, sy))
            drawCircle(color, radius = 6f, center = Offset(sx, sy))
        }
    }
}

private fun trendDelta(series: List<List<Double>>?): Double? {
    val s = series ?: return null
    if (s.size < 2) return null
    val last = s.last().getOrElse(1) { 0.0 }
    val prev = s[(s.size - 8).coerceAtLeast(0)].getOrElse(1) { 0.0 }
    return (last - prev).let { Math.round(it * 1000).toDouble() / 1000.0 }
}

private fun shortDate(epoch: Long): String {
    if (epoch <= 0L) return ""
    val millis = if (epoch < 1_000_000_000_000L) epoch * 1000 else epoch
    return runCatching {
        val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }.getOrDefault("")
}

private fun shareStation(context: android.content.Context, st: Station, fuel: FuelType) {
    val price = st.fuels[fuel.code]?.p
    val priceLine = if (price != null) "${fuel.label} : ${formatPrice(price)} €\n" else ""
    val text = buildString {
        append(st.brand ?: "Station"); append("\n")
        append("${st.addr}, ${st.cp} ${st.city}\n")
        append(priceLine)
        append("https://www.google.com/maps/search/?api=1&query=${st.lat},${st.lng}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
