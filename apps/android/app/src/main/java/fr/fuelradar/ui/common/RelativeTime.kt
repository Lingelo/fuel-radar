package fr.fuelradar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.fuelradar.R
import fr.fuelradar.domain.parseEpochMillis

/** Localized relative time ("il y a X h" / "X h ago" / …) — mirror of timeAgo. */
@Composable
fun relativeTime(isoOrYmd: String, nowMillis: Long = System.currentTimeMillis()): String {
    val t = parseEpochMillis(isoOrYmd) ?: return isoOrYmd
    val mins = ((nowMillis - t) / 60_000).coerceAtLeast(0)
    return when {
        mins < 1 -> stringResource(R.string.time_now)
        mins < 60 -> stringResource(R.string.time_min, mins.toInt())
        mins < 24 * 60 -> stringResource(R.string.time_h, (mins / 60).toInt())
        else -> stringResource(R.string.time_d, (mins / (24 * 60)).toInt())
    }
}
