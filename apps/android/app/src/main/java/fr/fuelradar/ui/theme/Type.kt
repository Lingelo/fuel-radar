package fr.fuelradar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Base typography. Inter font family + a dedicated tabular "price-display"
// style are wired in a later unit; defaults keep U3 self-contained.
val AppTypography = Typography()

// Placeholder for the price display style (Stitch DESIGN.md: 22sp / 700).
val PriceDisplayStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
)
