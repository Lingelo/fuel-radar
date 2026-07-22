package fr.fuelradar.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.fuelradar.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.domain.formatPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val FUEL_COLORS = mapOf(
    "Gazole" to Color(0xFF006A60),
    "SP95" to Color(0xFF006399),
    "E10" to Color(0xFF006B2F),
    "SP98" to Color(0xFFB8860B),
    "E85" to Color(0xFF2E7D32),
    "GPLc" to Color(0xFF8E24AA),
)

data class FuelKpi(val latest: Double, val yoyPct: Double?)

data class TrendsUiState(
    val loading: Boolean = true,
    val periodDays: Int = 30,
    val scope: String = "ALL",
    val series: Map<String, List<Double>> = emptyMap(),
    val kpis: Map<String, FuelKpi> = emptyMap(),
    val hiddenFuels: Set<String> = emptySet(),
)

class TrendsViewModel : ViewModel() {
    private val repo = ServiceLocator.stations

    // scope ("ALL"/"FR"/"ES"/"PT") -> fuel -> full price series
    private var byScope: Map<String, Map<String, List<Double>>> = emptyMap()

    private val _state = MutableStateFlow(TrendsUiState())
    val state: StateFlow<TrendsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val countries = repo.countriesHistory()
            byScope = countries?.countries?.mapValues { (_, fuels) ->
                fuels.mapValues { (_, points) -> points.map { it.getOrElse(1) { 0.0 } } }
            } ?: emptyMap()
            if (byScope.isEmpty()) {
                val nat = repo.nationalHistory()
                val frSeries = nat?.fuels?.mapValues { (_, p) -> p.map { it.getOrElse(1) { 0.0 } } }
                    ?: emptyMap()
                byScope = mapOf("ALL" to frSeries, "FR" to frSeries)
            }
            apply(_state.value.scope, _state.value.periodDays, _state.value.hiddenFuels)
        }
    }

    fun setPeriod(days: Int) = apply(_state.value.scope, days, _state.value.hiddenFuels)

    fun setScope(scope: String) = apply(scope, _state.value.periodDays, _state.value.hiddenFuels)

    fun toggleFuel(fuel: String) {
        val hidden = _state.value.hiddenFuels.toMutableSet()
        if (!hidden.add(fuel)) hidden.remove(fuel)
        apply(_state.value.scope, _state.value.periodDays, hidden)
    }

    private fun apply(scope: String, days: Int, hidden: Set<String>) {
        val full = byScope[scope] ?: emptyMap()
        val series = full.mapValues { (_, v) -> v.takeLast(days) }
        // KPI: latest price + year-over-year change from the full series.
        val kpis = full.mapValues { (_, v) ->
            val latest = v.lastOrNull() ?: 0.0
            val yearAgo = if (v.size > 365) v[v.size - 366] else v.firstOrNull()
            val yoy = yearAgo?.takeIf { it > 0 }?.let { (latest - it) / it * 100.0 }
            FuelKpi(latest, yoy)
        }
        _state.value = TrendsUiState(
            loading = false,
            periodDays = days,
            scope = scope,
            series = series.filterKeys { it !in hidden },
            kpis = kpis,
            hiddenFuels = hidden,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendsScreen(viewModel: TrendsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.trends_title), style = MaterialTheme.typography.headlineSmall)

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "ALL" to R.string.scope_all,
                "FR" to R.string.scope_fr,
                "ES" to R.string.scope_es,
                "PT" to R.string.scope_pt,
            ).forEach { (code, labelRes) ->
                FilterChip(
                    selected = state.scope == code,
                    onClick = { viewModel.setScope(code) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(30 to R.string.period_30, 90 to R.string.period_90, 365 to R.string.period_365)
                .forEach { (days, labelRes) ->
                    FilterChip(
                        selected = state.periodDays == days,
                        onClick = { viewModel.setPeriod(days) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        if (state.series.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        LineChart(
            series = state.series,
            modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp),
        )

        // KPI cards double as a clickable legend (tap to show/hide a fuel).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            state.kpis.entries
                .sortedBy { FuelType.fromCode(it.key)?.ordinal ?: 99 }
                .forEach { (fuel, kpi) ->
                    val hidden = fuel in state.hiddenFuels
                    val color = FUEL_COLORS[fuel] ?: MaterialTheme.colorScheme.primary
                    Surface(
                        onClick = { viewModel.toggleFuel(fuel) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                            .copy(alpha = if (hidden) 0.4f else 1f),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                                Text(
                                    "  ${FuelType.fromCode(fuel)?.label ?: fuel}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Text(
                                "${formatPrice(kpi.latest)} €",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                            kpi.yoyPct?.let {
                                val sign = if (it > 0) "+" else ""
                                Text(
                                    "$sign${String.format("%.1f", it)} %/an",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (it > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun LineChart(series: Map<String, List<Double>>, modifier: Modifier = Modifier) {
    val all = series.values.flatten()
    if (all.isEmpty()) return
    val min = all.min()
    val max = all.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    Canvas(modifier = modifier) {
        series.forEach { (fuel, values) ->
            if (values.size < 2) return@forEach
            val color = FUEL_COLORS[fuel] ?: Color.Gray
            val stepX = size.width / (values.size - 1)
            var prev: Offset? = null
            values.forEachIndexed { i, v ->
                val norm = ((v - min) / range).toFloat()
                val y = size.height * (1f - norm)
                val point = Offset(i * stepX, y)
                prev?.let { drawLine(color, it, point, strokeWidth = 3f) }
                prev = point
            }
        }
    }
}
