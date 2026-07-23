package fr.fuelradar.data

import fr.fuelradar.data.model.DeptBbox
import kotlin.math.PI
import kotlin.math.cos

/** Axis-aligned bounding box in degrees. */
data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

/**
 * Pure geo helpers mirroring apps/web/src/lib/department.ts and deptIndex.ts.
 * No Android dependencies, so this is unit-testable on the JVM.
 */
object DeptIndex {

    /** Convert a 5-digit French postal code to its department code. */
    fun getDepartment(cp: String): String {
        if (cp.startsWith("97")) return cp.take(3)
        if (cp.startsWith("20")) {
            val num = cp.toIntOrNull() ?: 0
            return if (num < 20200) "2A" else "2B"
        }
        return cp.take(2)
    }

    /** Approx square covering a circle of [radiusKm] around a coordinate. */
    fun boundingBox(lat: Double, lng: Double, radiusKm: Double): BoundingBox {
        val dLat = radiusKm / 111.0
        val dLng = radiusKm / (111.0 * cos(lat * PI / 180.0))
        return BoundingBox(
            minLat = lat - dLat,
            maxLat = lat + dLat,
            minLng = lng - dLng,
            maxLng = lng + dLng,
        )
    }

    /** Department codes whose station bbox overlaps [target]. */
    fun findOverlappingDepts(index: DeptBbox, target: BoundingBox): List<String> {
        val out = mutableListOf<String>()
        for ((dept, bb) in index) {
            if (bb.size < 4) continue
            val minLat = bb[0]
            val maxLat = bb[1]
            val minLng = bb[2]
            val maxLng = bb[3]
            val overlaps =
                maxLat >= target.minLat &&
                    minLat <= target.maxLat &&
                    maxLng >= target.minLng &&
                    minLng <= target.maxLng
            if (overlaps) out.add(dept)
        }
        return out
    }

    /** Department codes whose bbox overlaps a circle of [radiusKm] around a point. */
    fun deptsAround(index: DeptBbox, lat: Double, lng: Double, radiusKm: Double): List<String> =
        findOverlappingDepts(index, boundingBox(lat, lng, radiusKm))
}
