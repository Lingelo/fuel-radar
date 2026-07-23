package fr.fuelradar.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.fuelradar.R
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.prefs.Filters
import fr.fuelradar.data.prefs.SortMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    current: Filters,
    onDismiss: () -> Unit,
    onApply: (Filters) -> Unit,
) {
    var fuel by remember(current) { mutableStateOf(current.fuel) }
    var radius by remember(current) { mutableStateOf(current.radiusKm) }
    var sort by remember(current) { mutableStateOf(current.sort) }
    var h24 by remember(current) { mutableStateOf(current.openH24Only) }
    var brands by remember(current) { mutableStateOf(current.brands) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.filters), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = {
                    fuel = FuelType.GAZOLE; radius = 10; sort = SortMode.PRICE
                    h24 = false; brands = emptySet()
                }) { Text(stringResource(R.string.reset)) }
            }

            Text(stringResource(R.string.fuel), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelType.entries.forEach { ft ->
                    FilterChip(
                        selected = fuel == ft,
                        onClick = { fuel = ft },
                        label = { Text(ft.label) },
                    )
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { m ->
                    FilterChip(
                        selected = sort == m,
                        onClick = { sort = m },
                        label = { Text(stringResource(if (m == SortMode.PRICE) R.string.sort_price else R.string.sort_distance)) },
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.radius), style = MaterialTheme.typography.titleMedium)
                Text("$radius km", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = radius.coerceIn(1, 30).toFloat(),
                onValueChange = { radius = it.toInt() },
                valueRange = 1f..30f,
                steps = 28,
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.h24_only), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = h24, onCheckedChange = { h24 = it })
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.brands), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    brands = if (brands.size == KNOWN_BRANDS.size) emptySet() else KNOWN_BRANDS.toSet()
                }) { Text(stringResource(if (brands.size == KNOWN_BRANDS.size) R.string.deselect_all else R.string.select_all)) }
            }
            Text(
                if (brands.isEmpty()) stringResource(R.string.all_brands) else stringResource(R.string.brands_selected, brands.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KNOWN_BRANDS.forEach { b ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = brands.contains(b),
                        onCheckedChange = { checked ->
                            brands = if (checked) brands + b else brands - b
                        },
                    )
                    Text(b, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Button(
                onClick = {
                    onApply(
                        current.copy(
                            fuel = fuel,
                            radiusKm = radius,
                            sort = sort,
                            openH24Only = h24,
                            brands = brands,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.apply), fontWeight = FontWeight.Bold) }

            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
