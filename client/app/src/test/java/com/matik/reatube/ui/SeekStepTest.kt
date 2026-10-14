package com.matik.reatube.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekStepTest {
    private val hour = 3_600_000L

    @Test
    fun firstPressesMoveTenSeconds() {
        assertEquals(10_000L, seekStep(0, hour))
        assertEquals(10_000L, seekStep(9, hour))
    }

    @Test
    fun holdingTheKeySpeedsUp() {
        assertEquals(30_000L, seekStep(10, hour))
        assertEquals(60_000L, seekStep(30, hour))
        assertEquals(120_000L, seekStep(60, hour))
    }

    @Test
    fun shortVideosGetSmallerSteps() {
        assertEquals(10_000L, seekStep(60, 120_000)) // 2 min clip: never below 10 s
        assertEquals(20_000L, seekStep(60, 600_000)) // 10 min: at most 1/30 of the video
    }

    @Test
    fun unknownDurationUsesTheSchedule() {
        assertEquals(120_000L, seekStep(60, Long.MIN_VALUE + 1)) // C.TIME_UNSET
    }
}
