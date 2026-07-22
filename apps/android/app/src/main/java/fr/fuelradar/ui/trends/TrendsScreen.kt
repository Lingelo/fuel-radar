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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.model.FuelType
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

data class TrendsUiState(
    val loading: Boolean = true,
    val periodDays: Int = 30,
    val series: Map<String, List<Double>> = emptyMap(),
)

class TrendsViewModel : ViewModel() {
    private val repo = ServiceLocator.stations
    private var full: Map<String, List<Double>> = emptyMap()

    private val _state = MutableStateFlow(TrendsUiState())
    val state: StateFlow<TrendsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val nat = repo.nationalHistory()
            full = nat?.fuels?.mapValues { (_, points) -> points.map { it.getOrElse(1) { 0.0 } } }
                ?: emptyMap()
            applyPeriod(_state.value.periodDays)
        }
    }

    fun setPeriod(days: Int) = applyPeriod(days)

    private fun applyPeriod(days: Int) {
        _state.value = TrendsUiState(
            loading = false,
            periodDays = days,
            series = full.mapValues { (_, v) -> v.takeLast(days) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendsScreen(viewModel: TrendsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tendances nationales", style = MaterialTheme.typography.headlineSmall)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(30 to "30 j", 90 to "90 j", 365 to "1 an").forEach { (days, label) ->
                FilterChip(
                    selected = state.periodDays == days,
                    onClick = { viewModel.setPeriod(days) },
                    label = { Text(label) },
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
                Text("Historique indisponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        LineChart(
            series = state.series,
            modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.series.keys.forEach { fuel ->
                val color = FUEL_COLORS[fuel] ?: MaterialTheme.colorScheme.primary
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = color, shape = CircleShape, modifier = Modifier.size(12.dp)) {}
                    Text(
                        "  ${FuelType.fromCode(fuel)?.label ?: fuel}",
                        style = MaterialTheme.typography.labelMedium,
                    )
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
