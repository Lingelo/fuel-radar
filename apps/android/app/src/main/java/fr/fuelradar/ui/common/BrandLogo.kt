package fr.fuelradar.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import kotlin.math.absoluteValue

/** Brand → representative color (approximate brand colors). */
private val BRAND_COLORS = linkedMapOf(
    "totalenergies" to Color(0xFFE3001B),
    "total" to Color(0xFFE3001B),
    "leclerc" to Color(0xFF0066B3),
    "intermarché" to Color(0xFFE2001A),
    "intermarche" to Color(0xFFE2001A),
    "système u" to Color(0xFFE2001A),
    "systeme u" to Color(0xFFE2001A),
    "super u" to Color(0xFFE2001A),
    "carrefour" to Color(0xFF004E9E),
    "auchan" to Color(0xFFE2001A),
    "bp" to Color(0xFF009900),
    "shell" to Color(0xFFDD1D21),
    "esso" to Color(0xFF1D4F91),
    "casino" to Color(0xFF00954C),
    "avia" to Color(0xFFE2001A),
    "repsol" to Color(0xFFF29100),
    "cepsa" to Color(0xFF009639),
    "moeve" to Color(0xFF00A19A),
    "galp" to Color(0xFFEF7D00),
    "prio" to Color(0xFF8BC63F),
    "ballenoil" to Color(0xFF1B3A6B),
    "plenergy" to Color(0xFFE2001A),
    "plenoil" to Color(0xFFE2001A),
)

/** Brand → Simple Icons slug (open-source CC0 SVG logos). Missing brands
 *  fall back to a colored monogram. */
private val SIMPLE_ICON_SLUGS = linkedMapOf(
    "totalenergies" to "totalenergies",
    "total" to "totalenergies",
    "shell" to "shell",
    "bp" to "bp",
    "repsol" to "repsol",
    "galp" to "galp",
    "cepsa" to "cepsa",
    "carrefour" to "carrefour",
    "auchan" to "auchan",
    "moeve" to "moeve",
)

private val PALETTE = listOf(
    Color(0xFF006A60), Color(0xFF006399), Color(0xFF006B2F),
    Color(0xFF8E24AA), Color(0xFFB8860B), Color(0xFFC62828),
)

private fun brandColor(brand: String?): Color {
    if (brand.isNullOrBlank()) return PALETTE[0]
    val b = brand.lowercase()
    BRAND_COLORS.entries.firstOrNull { b.contains(it.key) }?.let { return it.value }
    return PALETTE[(b.hashCode().absoluteValue) % PALETTE.size]
}

private fun slugFor(brand: String?): String? {
    val b = brand?.lowercase() ?: return null
    return SIMPLE_ICON_SLUGS.entries.firstOrNull { b.contains(it.key) }?.value
}

private fun Color.toHex(): String = String.format("%06X", 0xFFFFFF and toArgb())

@Composable
fun BrandLogo(brand: String?, size: Dp, modifier: Modifier = Modifier) {
    val color = brandColor(brand)
    val slug = slugFor(brand)
    val monogram: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(size).background(color, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val letter = brand?.trim()?.take(1)?.uppercase().orEmpty()
            if (letter.isEmpty()) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f),
                )
            } else {
                Text(
                    letter,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = TextUnit(size.value * 0.42f, TextUnitType.Sp),
                )
            }
        }
    }

    if (slug == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { monogram() }
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = "https://cdn.simpleicons.org/$slug/${color.toHex()}",
            contentDescription = brand,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).padding(size * 0.2f),
            loading = { monogram() },
            error = { monogram() },
        )
    }
}
