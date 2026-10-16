package com.sample.otuslocationmapshw.camera

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

internal object PhoneTilt {
    fun needsAlignment(x: Double, y: Double, z: Double): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!magnitude.isFinite() || magnitude == 0.0) return false
        val degrees = Math.toDegrees(asin((abs(z) / magnitude).coerceIn(0.0, 1.0)))
        return degrees > 15.0
    }
}
