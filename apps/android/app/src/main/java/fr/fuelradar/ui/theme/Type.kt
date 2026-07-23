package fr.fuelradar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.fuelradar.R

/** Inter (variable font) — matches the web app's typography. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun interFont(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(
    interFont(FontWeight.Normal),
    interFont(FontWeight.Medium),
    interFont(FontWeight.SemiBold),
    interFont(FontWeight.Bold),
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Inter),
    displayMedium = base.displayMedium.copy(fontFamily = Inter),
    displaySmall = base.displaySmall.copy(fontFamily = Inter),
    headlineLarge = base.headlineLarge.copy(fontFamily = Inter),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter),
    headlineSmall = base.headlineSmall.copy(fontFamily = Inter),
    titleLarge = base.titleLarge.copy(fontFamily = Inter),
    titleMedium = base.titleMedium.copy(fontFamily = Inter),
    titleSmall = base.titleSmall.copy(fontFamily = Inter),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter),
    labelMedium = base.labelMedium.copy(fontFamily = Inter),
    labelSmall = base.labelSmall.copy(fontFamily = Inter),
)

/** Bold tabular price style (web `display-price`). */
val PriceDisplayStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
)
