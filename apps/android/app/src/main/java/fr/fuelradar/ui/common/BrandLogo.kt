package fr.fuelradar.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Brand → canonical slug. To show a real logo, drop a file named
 * `brand_<slug>.png` (or .webp/.xml) into app/src/main/res/drawable/.
 * Missing files fall back to a colored monogram.
 */
private val BRAND_SLUGS = linkedMapOf(
    "totalenergies" to "total", "total" to "total",
    "leclerc" to "leclerc",
    "intermarché" to "intermarche", "intermarche" to "intermarche",
    "système u" to "systemeu", "systeme u" to "systemeu", "super u" to "systemeu",
    "carrefour" to "carrefour", "auchan" to "auchan",
    "bp" to "bp", "shell" to "shell", "esso" to "esso",
    "casino" to "casino", "avia" to "avia", "repsol" to "repsol",
    "cepsa" to "cepsa", "moeve" to "moeve", "galp" to "galp",
    "prio" to "prio", "ballenoil" to "ballenoil",
    "plenergy" to "plenoil", "plenoil" to "plenoil",
)

private val BRAND_COLORS = linkedMapOf(
    "total" to Color(0xFFE3001B),
    "leclerc" to Color(0xFF0066B3),
    "intermarche" to Color(0xFFE2001A),
    "systemeu" to Color(0xFFE2001A),
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
    "plenoil" to Color(0xFFE2001A),
)

private val PALETTE = listOf(
    Color(0xFF006A60), Color(0xFF006399), Color(0xFF006B2F),
    Color(0xFF8E24AA), Color(0xFFB8860B), Color(0xFFC62828),
)

private fun slugFor(brand: String?): String? {
    val b = brand?.lowercase() ?: return null
    return BRAND_SLUGS.entries.firstOrNull { b.contains(it.key) }?.value
}

private fun brandColor(brand: String?): Color {
    val slug = slugFor(brand)
    BRAND_COLORS[slug]?.let { return it }
    val b = brand?.lowercase().orEmpty()
    return if (b.isEmpty()) PALETTE[0] else PALETTE[b.hashCode().absoluteValue % PALETTE.size]
}

@Composable
fun BrandLogo(brand: String?, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val slug = slugFor(brand)
    val resId = slug?.let {
        ctx.resources.getIdentifier("brand_$it", "drawable", ctx.packageName)
    } ?: 0

    if (resId != 0) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(resId),
                contentDescription = brand,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size).padding(size * 0.14f),
            )
        }
        return
    }

    // Fallback: colored monogram.
    Box(
        modifier = modifier.size(size).background(brandColor(brand), RoundedCornerShape(10.dp)),
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
