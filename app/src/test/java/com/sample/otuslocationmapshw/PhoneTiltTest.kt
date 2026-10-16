package com.sample.otuslocationmapshw

import com.sample.otuslocationmapshw.camera.PhoneTilt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTiltTest {
    @Test
    fun uprightPortraitAndLandscapeNeedNoHint() {
        assertFalse(PhoneTilt.needsAlignment(0.0, 9.81, 0.0))
        assertFalse(PhoneTilt.needsAlignment(9.81, 0.0, 0.0))
    }

    @Test
    fun phoneLyingOnEitherSideNeedsAlignment() {
        assertTrue(PhoneTilt.needsAlignment(0.0, 0.0, 9.81))
        assertTrue(PhoneTilt.needsAlignment(0.0, 0.0, -9.81))
    }

    @Test
    fun thresholdIsIndependentOfAccelerationMagnitudeAndDirection() {
        assertFalse(PhoneTilt.needsAlignment(0.0, 9.6, 1.0))
        assertTrue(PhoneTilt.needsAlignment(0.0, 9.0, 3.0))
        assertTrue(PhoneTilt.needsAlignment(0.0, -90.0, -30.0))
    }

    @Test
    fun invalidReadingsDoNotShowSpuriousHint() {
        assertFalse(PhoneTilt.needsAlignment(0.0, 0.0, 0.0))
        assertFalse(PhoneTilt.needsAlignment(Double.NaN, 1.0, 0.0))
        assertFalse(PhoneTilt.needsAlignment(Double.POSITIVE_INFINITY, 1.0, 0.0))
    }
}
