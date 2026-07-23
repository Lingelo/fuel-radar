package fr.fuelradar.ui.trends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.fuelradar.R
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.domain.formatPrice
import fr.fuelradar.ui.common.relativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Web-parity fuel palette (apps/web TrendsScreen FUEL_COLORS). */
private val FUEL_COLORS = mapOf(
    "Gazole" to Color(0xFFEA580C),
    "E10" to Color(0xFF0D9488),
    "SP95" to Color(0xFF16A34A),
    "SP98" to Color(0xFF2563EB),
    "E85" to Color(0xFF7C3AED),
    "GPLc" to Color(0xFF525252),
)

/** A single [epoch ms, price] sample. */
private typealias Point = List<Double>

data class TrendsUiState(
    val loading: Boolean = true,
    val periodDays: Int = 90,
    val scope: String = "FR",
    val activeFuels: Set<String> = setOf("Gazole", "SP95", "E10", "SP98"),
    val seriesFull: Map<String, List<Point>> = emptyMap(),
    val updated: String? = null,
)

class TrendsViewModel : ViewModel() {
    private val repo = ServiceLocator.stations

    private var national: Map<String, List<Point>> = emptyMap()
    private var nationalUpdated: String? = null
    private var countries: Map<String, Map<String, List<Point>>> = emptyMap()
    private var countriesUpdated: String? = null

    private val _state = MutableStateFlow(TrendsUiState())
    val state: StateFlow<TrendsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.nationalHistory()?.let { national = it.fuels; nationalUpdated = it.updated }
            repo.countriesHistory()?.let { countries = it.countries; countriesUpdated = it.updated }
            recompute(_state.value.copy(loading = false))
        }
    }

    fun setScope(scope: String) = recompute(_state.value.copy(scope = scope))

    fun setPeriod(days: Int) = recompute(_state.value.copy(periodDays = days))

    fun toggleFuel(fuel: String) {
        val next = _state.value.activeFuels.toMutableSet()
        if (!next.add(fuel)) next.remove(fuel)
        recompute(_state.value.copy(activeFuels = next))
    }

    /** France keeps its deep archive-backed series; other scopes only exist in
     *  the day-by-day accumulator file (mirror of the web selection logic). */
    private fun seriesFor(scope: String): Map<String, List<Point>> = when (scope) {
        "FR" -> national.ifEmpty { countries["FR"] ?: emptyMap() }
        else -> countries[scope] ?: emptyMap()
    }

    private fun updatedFor(scope: String): String? =
        if (scope == "FR") nationalUpdated ?: countriesUpdated else countriesUpdated

    private fun recompute(base: TrendsUiState) {
        _state.value = base.copy(
            seriesFull = seriesFor(base.scope),
            updated = updatedFor(base.scope),
        )
    }
}

private fun latestPrice(points: List<Point>): Double? = points.lastOrNull()?.getOrNull(1)

/** Year-over-year % change (web yearChange). */
private fun yearChange(points: List<Point>): Double? {
    if (points.size < 2) return null
    val cutoff = System.currentTimeMillis() - 365L * 86_400_000L
    val past = points.firstOrNull { it.getOrElse(0) { 0.0 } >= cutoff }?.getOrNull(1)
        ?: points.first().getOrNull(1) ?: return null
    val last = points.last().getOrNull(1) ?: return null
    if (past == 0.0) return null
    return (last - past) / past * 100.0
}

private fun shortDate(epoch: Long): String {
    if (epoch <= 0L) return ""
    return runCatching {
        val d = java.time.Instant.ofEpochMilli(epoch)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }.getOrDefault("")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendsScreen(viewModel: TrendsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scopes = listOf(
        "FR" to R.string.scope_fr, "ES" to R.string.scope_es,
        "PT" to R.string.scope_pt, "ALL" to R.string.scope_all,
    )
    val scopeLabel = stringResource(scopes.first { it.first == state.scope }.second)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header: title + subtitle (scope + updated) + period switch.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.trends_title), style = MaterialTheme.typography.headlineSmall)
                val updated = state.updated?.let {
                    " · " + stringResource(R.string.updated, relativeTime(it.take(10)))
                } ?: ""
                Text(
                    stringResource(R.string.trends_subtitle, scopeLabel) + updated,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Scope switch.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            scopes.forEach { (code, labelRes) ->
                Pill(
                    text = stringResource(labelRes),
                    selected = state.scope == code,
                    onClick = { viewModel.setScope(code) },
                )
            }
        }

        // Period switch.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30 to R.string.period_30, 90 to R.string.period_90, 365 to R.string.period_365)
                .forEach { (days, labelRes) ->
                    Pill(
                        text = stringResource(labelRes),
                        selected = state.periodDays == days,
                        onClick = { viewModel.setPeriod(days) },
                    )
                }
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val maxPoints = state.seriesFull.values.maxOfOrNull { it.size } ?: 0
        if (maxPoints < 7) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Info, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp),
                )
                Text(
                    "  " + stringResource(R.string.trends_short_history),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // KPI cards (tap to toggle a fuel on/off in the chart).
        val kpiFuels = FuelType.entries
            .filter { it.seriesIn(state.seriesFull)?.isNotEmpty() == true }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            kpiFuels.forEach { ft ->
                val pts = state.seriesFull[ft.code].orEmpty()
                val active = ft.code in state.activeFuels
                val color = FUEL_COLORS[ft.code] ?: MaterialTheme.colorScheme.primary
                KpiCard(
                    label = ft.label,
                    dotColor = color,
                    price = latestPrice(pts),
                    yoy = yearChange(pts),
                    active = active,
                    onClick = { viewModel.toggleFuel(ft.code) },
                )
            }
        }

        // Chart section.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.trends_evolution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                val chartSeries = FuelType.entries
                    .filter { it.code in state.activeFuels }
                    .mapNotNull { ft ->
                        val cutoff = System.currentTimeMillis() - state.periodDays.toLong() * 86_400_000L
                        val pts = state.seriesFull[ft.code].orEmpty()
                            .filter { it.getOrElse(0) { 0.0 } >= cutoff }
                        if (pts.size >= 2) ft.code to pts else null
                    }
                    .toMap()

                if (chartSeries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.history_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    MultiTrendChart(chartSeries)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.trends_tap_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun KpiCard(
    label: String,
    dotColor: Color,
    price: Double?,
    yoy: Double?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.width(150.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (active) dotColor else dotColor.copy(alpha = 0.35f),
                    shape = CircleShape,
                    modifier = Modifier.size(10.dp),
                ) {}
                Text(
                    "  $label",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                price?.let { "${formatPrice(it)} €" } ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (yoy != null) {
                val pct = "${if (yoy > 0) "+" else ""}${"%.1f".format(yoy)} %"
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(
                        if (yoy < 0) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                        null,
                        tint = if (yoy < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        " " + stringResource(R.string.trends_year_change, pct),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (yoy < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Interactive multi-series line chart with a shared date scrubber. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiTrendChart(series: Map<String, List<Point>>) {
    val allPoints = series.values.flatten()
    val tMin = allPoints.minOf { it[0] }
    val tMax = allPoints.maxOf { it[0] }
    val pMin = allPoints.minOf { it[1] }
    val pMax = allPoints.maxOf { it[1] }
    val tRange = (tMax - tMin).takeIf { it > 0 } ?: 1.0
    val pRange = (pMax - pMin).takeIf { it > 0 } ?: 1.0

    // Shared timeline = epochs of the longest active series.
    val refEpochs = remember(series) {
        (series.values.maxByOrNull { it.size } ?: emptyList()).map { it[0].toLong() }
    }
    var sel by remember(series) { mutableIntStateOf(refEpochs.lastIndex.coerceAtLeast(0)) }
    val selEpoch = refEpochs.getOrElse(sel) { tMax.toLong() }
    val guide = MaterialTheme.colorScheme.outlineVariant
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant

    fun epochToIndex(x: Float, w: Float): Int {
        val epoch = tMin + (x / w).coerceIn(0f, 1f) * tRange
        return refEpochs.indices.minByOrNull { abs(refEpochs[it] - epoch) } ?: 0
    }

    Column {
        // Readout: selected date + per-fuel price at that date.
        Text(
            shortDate(selEpoch),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FuelType.entries.filter { it.code in series }.forEach { ft ->
                val pts = series[ft.code].orEmpty()
                val price = pts.minByOrNull { abs(it[0].toLong() - selEpoch) }?.getOrNull(1)
                val color = FUEL_COLORS[ft.code] ?: MaterialTheme.colorScheme.primary
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = color, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                    Text(
                        "  ${ft.label} ${price?.let { formatPrice(it) } ?: "—"}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Canvas(
            modifier = Modifier.fillMaxWidth().height(220.dp)
                .pointerInput(series) {
                    detectTapGestures { o -> sel = epochToIndex(o.x, size.width.toFloat()) }
                }
                .pointerInput(series) {
                    detectHorizontalDragGestures { change, _ ->
                        sel = epochToIndex(change.position.x, size.width.toFloat())
                    }
                },
        ) {
            val padV = 8f
            val h = size.height - padV * 2
            fun xf(e: Double) = (((e - tMin) / tRange).toFloat()) * size.width
            fun yf(p: Double) = padV + h * (1f - ((p - pMin) / pRange).toFloat())

            series.forEach { (fuel, pts) ->
                val color = FUEL_COLORS[fuel] ?: Color.Gray
                var prev: Offset? = null
                pts.forEach { pt ->
                    val o = Offset(xf(pt[0]), yf(pt[1]))
                    prev?.let { drawLine(color, it, o, strokeWidth = 4f) }
                    prev = o
                }
            }

            // Scrubber + a dot per fuel at the selected date.
            val sx = xf(selEpoch.toDouble())
            drawLine(guide, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 2f)
            series.forEach { (fuel, pts) ->
                val near = pts.minByOrNull { abs(it[0].toLong() - selEpoch) } ?: return@forEach
                val color = FUEL_COLORS[fuel] ?: Color.Gray
                val c = Offset(xf(near[0]), yf(near[1]))
                drawCircle(Color.White, radius = 7f, center = c)
                drawCircle(color, radius = 5f, center = c)
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("min ${formatPrice(pMin)} €", style = MaterialTheme.typography.labelSmall, color = onVariant)
            Text("max ${formatPrice(pMax)} €", style = MaterialTheme.typography.labelSmall, color = onVariant)
        }
    }
}
