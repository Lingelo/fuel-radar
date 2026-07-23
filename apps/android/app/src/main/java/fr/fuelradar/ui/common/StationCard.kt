package fr.fuelradar.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.fuelradar.R
import fr.fuelradar.data.model.FuelType
import fr.fuelradar.data.model.Station
import fr.fuelradar.domain.formatDistance
import fr.fuelradar.domain.formatPrice

/**
 * Shared station card used by the stations list and favorites (mirror of the web
 * StationCard). Takes primitives so any screen can render it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationCard(
    station: Station,
    price: Double?,
    distanceKm: Double?,
    priceColor: Color,
    selectedFuel: FuelType,
    favorite: Boolean,
    cheapest: Boolean = false,
    stale: Boolean = false,
    updatedLabel: String? = null,
    onToggleFavorite: () -> Unit,
    onViewMap: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (cheapest) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (cheapest) 3.dp else 1.dp),
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandLogo(station.brand, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    station.brand ?: stringResource(R.string.station_fallback),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    val dist = distanceKm?.let { "${formatDistance(it)} • " } ?: ""
                                    Text(
                                        " $dist${station.addr.ifBlank { station.city }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        val others = FuelType.entries
                            .filter { it != selectedFuel && it.availableIn(station.fuels) }
                            .take(3)
                        if (others.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                others.forEach { ft ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            "${ft.label} ${formatPrice(ft.priceIn(station.fuels)!!)} €",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        price?.let {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    formatPrice(it),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = priceColor,
                                )
                                Text(
                                    " €",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = priceColor,
                                )
                            }
                        }
                        Text(
                            selectedFuel.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (stale) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = stringResource(R.string.stale_data),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            updatedLabel ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stale) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = if (stale) 4.dp else 0.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = if (favorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onViewMap) {
                            Text(
                                stringResource(R.string.view_on_map),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Map,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (cheapest) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(bottomStart = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.cheapest),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
