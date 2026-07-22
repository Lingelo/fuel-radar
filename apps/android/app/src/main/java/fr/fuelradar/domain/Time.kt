package fr.fuelradar.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Time helpers mirroring apps/web/src/lib/data.ts (timeAgo / isStale).
 * minSdk 26 has java.time natively.
 */

fun parseEpochMillis(isoOrYmd: String): Long? {
    val zone = ZoneId.systemDefault()
    return runCatching {
        // Full ISO datetime first (meta.lastUpdate), then plain YYYY-MM-DD.
        LocalDateTime.parse(isoOrYmd.removeSuffix("Z"))
            .atZone(zone).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDate.parse(isoOrYmd).atStartOfDay(zone).toInstant().toEpochMilli()
    }.getOrNull()
}

fun timeAgo(isoOrYmd: String, nowMillis: Long = System.currentTimeMillis()): String {
    val t = parseEpochMillis(isoOrYmd) ?: return isoOrYmd
    val mins = ((nowMillis - t) / 60_000).coerceAtLeast(0)
    return when {
        mins < 1 -> "à l'instant"
        mins < 60 -> "il y a $mins min"
        mins < 24 * 60 -> "il y a ${mins / 60} h"
        mins < 7 * 24 * 60 -> "il y a ${mins / (24 * 60)} j"
        else -> runCatching {
            LocalDate.ofInstant(java.time.Instant.ofEpochMilli(t), ZoneId.systemDefault()).toString()
        }.getOrDefault(isoOrYmd)
    }
}

/** True if [updateDate] is older than [thresholdHours] (default 72h). */
fun isStale(updateDate: String, thresholdHours: Long = 72, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val t = parseEpochMillis(updateDate) ?: return false
    return ChronoUnit.MILLIS.between(java.time.Instant.ofEpochMilli(t), java.time.Instant.ofEpochMilli(nowMillis)) >
        thresholdHours * 60 * 60 * 1000
}
