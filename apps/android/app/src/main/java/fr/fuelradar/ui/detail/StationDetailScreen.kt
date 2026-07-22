package fr.fuelradar.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import fr.fuelradar.data.DeptIndex
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.model.Station
import fr.fuelradar.domain.formatPriceDelta
import fr.fuelradar.domain.formatPriceEuro
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(stationId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = ServiceLocator.stations
    val favStore = ServiceLocator.favorites
    val favorites by favStore.ids.collectAsStateWithLifecycle(emptySet())
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val station by produceState<Station?>(initialValue = null, stationId) {
        value = repo.findCached(stationId)
    }
    val history by produceState<Map<String, List<List<Double>>>>(emptyMap(), station) {
        val st = station
        value = if (st != null) {
            val dept = DeptIndex.getDepartment(st.cp)
            repo.deptHistory(dept)[stationId.toString()] ?: emptyMap()
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
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${st.addr}, ${st.cp} ${st.city}", style = MaterialTheme.typography.bodyLarge)

            Button(
                onClick = {
                    val uri = Uri.parse("geo:${st.lat},${st.lng}?q=${st.lat},${st.lng}(${Uri.encode(st.brand ?: st.city)})")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
            ) {
                Icon(Icons.Filled.Place, contentDescription = null)
                Text("  Itinéraire")
            }

            HorizontalDivider()

            FuelType.entries.forEach { fuel ->
                val price = st.fuels[fuel.code] ?: return@forEach
                val series = history[fuel.code].orEmpty().map { it.getOrElse(1) { 0.0 } }
                val delta = trendDelta(series)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(fuel.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatPriceEuro(price.p),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "MàJ ${price.d}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** Price change between the last point and ~7 daily points earlier. */
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
            val h = (size.height * (0.15f + 0.85f * norm))
            val x = i * (barWidth + gap)
            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
            )
        }
    }
}
