package fr.fuelradar.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.fuelradar.data.model.FuelFamily
import fr.fuelradar.data.model.FuelType

/**
 * Two-level fuel picker: a row of families (Essence / Diesel / GPL) then a row of
 * the fuels in the selected family. The shown family follows the selected fuel;
 * tapping a family just reveals its fuels (selection happens on the second row).
 */
@Composable
fun FuelSelector(
    selected: FuelType,
    onSelect: (FuelType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var family by remember(selected) { mutableStateOf(selected.family) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FuelFamily.entries.forEach { fam ->
                FilterChip(
                    selected = family == fam,
                    onClick = { family = fam },
                    label = { Text(fam.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FuelType.entries.filter { it.family == family }.forEach { ft ->
                FilterChip(
                    selected = selected == ft,
                    onClick = { onSelect(ft) },
                    label = { Text(ft.label) },
                )
            }
        }
    }
}
