package fr.fuelradar.domain

import androidx.compose.ui.graphics.Color

/**
 * Price → color mapping mirroring apps/web/src/lib/priceColor.ts
 * (green → yellow → orange → red across robust bounds).
 */

/** Robust min/max (1st/99th percentile) for color scaling. */
fun priceBounds(prices: List<Double>): Pair<Double, Double> {
    if (prices.isEmpty()) return 0.0 to 1.0
    val sorted = prices.sorted()
    val pMin = sorted.getOrElse((sorted.size * 0.01).toInt()) { sorted.first() }
    val pMax = sorted.getOrElse((Math.ceil(sorted.size * 0.99).toInt() - 1).coerceAtLeast(0)) { sorted.last() }
    return pMin to pMax
}

// [pos, hue, saturation%, lightness%]
private val STOPS = listOf(
    doubleArrayOf(0.0, 142.0, 71.0, 40.0),
    doubleArrayOf(0.12, 120.0, 65.0, 42.0),
    doubleArrayOf(0.25, 90.0, 70.0, 44.0),
    doubleArrayOf(0.38, 65.0, 80.0, 46.0),
    doubleArrayOf(0.5, 48.0, 90.0, 48.0),
    doubleArrayOf(0.62, 35.0, 90.0, 48.0),
    doubleArrayOf(0.75, 20.0, 85.0, 48.0),
    doubleArrayOf(0.88, 5.0, 75.0, 45.0),
    doubleArrayOf(1.0, 0.0, 80.0, 30.0),
)

/** Map a price to a color based on [pMin]..[pMax]. */
fun priceColor(price: Double, pMin: Double, pMax: Double): Color {
    if (pMax == pMin) return Color.hsl(142f, 0.71f, 0.45f)
    val t = ((price - pMin) / (pMax - pMin)).coerceIn(0.0, 1.0)
    var i = 0
    while (i < STOPS.size - 2 && STOPS[i + 1][0] < t) i++
    val (pos0, h0, s0, l0) = STOPS[i].let { arrayOf(it[0], it[1], it[2], it[3]) }
    val (pos1, h1, s1, l1) = STOPS[i + 1].let { arrayOf(it[0], it[1], it[2], it[3]) }
    val local = (t - pos0) / (pos1 - pos0)
    val h = (h0 + (h1 - h0) * local).toFloat()
    val s = (s0 + (s1 - s0) * local).toFloat() / 100f
    val l = (l0 + (l1 - l0) * local).toFloat() / 100f
    return Color.hsl(h, s, l)
}

/** Format a price with French decimal comma (2.09 → "2,090"). */
fun formatPrice(price: Double, decimals: Int = 3): String =
    String.format("%.${decimals}f", price).replace('.', ',')

fun formatPriceEuro(price: Double, decimals: Int = 3): String =
    "${formatPrice(price, decimals)} €"

fun formatPriceDelta(delta: Double, decimals: Int = 3): String {
    val sign = if (delta > 0) "+" else ""
    return "$sign${String.format("%.${decimals}f", delta).replace('.', ',')} €"
}
