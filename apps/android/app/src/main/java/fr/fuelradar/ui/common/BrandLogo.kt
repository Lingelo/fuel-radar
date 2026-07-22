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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

/**
 * Brand → logo domain (Clearbit logo API). Fuzzy, case-insensitive substring
 * match so data variants ("E.Leclerc", "TOTAL", "Carrefour Market") still hit.
 */
private val BRAND_DOMAINS = linkedMapOf(
    "totalenergies" to "totalenergies.com",
    "total" to "totalenergies.com",
    "leclerc" to "e-leclerc.com",
    "intermarché" to "intermarche.com",
    "intermarche" to "intermarche.com",
    "système u" to "magasins-u.com",
    "systeme u" to "magasins-u.com",
    "super u" to "magasins-u.com",
    "carrefour" to "carrefour.fr",
    "auchan" to "auchan.fr",
    "bp" to "bp.com",
    "shell" to "shell.com",
    "esso" to "esso.fr",
    "casino" to "casino.fr",
    "avia" to "avia.fr",
    "repsol" to "repsol.com",
    "cepsa" to "cepsa.com",
    "moeve" to "moeve.com",
    "galp" to "galp.com",
    "prio" to "prio.pt",
    "ballenoil" to "ballenoil.es",
    "plenergy" to "plenoil.com",
    "plenoil" to "plenoil.com",
)

private fun brandLogoUrl(brand: String?): String? {
    if (brand.isNullOrBlank()) return null
    val b = brand.lowercase()
    val domain = BRAND_DOMAINS.entries.firstOrNull { b.contains(it.key) }?.value ?: return null
    // Google favicon service — reliable brand mark by domain (Clearbit's logo
    // API was shut down). 128px is crisp enough at avatar sizes.
    return "https://www.google.com/s2/favicons?sz=128&domain=$domain"
}

@Composable
fun BrandLogo(brand: String?, size: Dp, modifier: Modifier = Modifier) {
    val url = brandLogoUrl(brand)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            Icon(
                Icons.Filled.LocalGasStation,
                contentDescription = brand,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
        if (url == null) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = brand,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size).padding(4.dp).clip(RoundedCornerShape(6.dp)),
                loading = { fallback() },
                error = { fallback() },
            )
        }
    }
}
