package com.matik.reatube.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityLabelTest {
    @Test
    fun widescreenFramesAreNamedByWidth() {
        // Ведьмак on Rutube is 2:1, so Full HD is 1920×960
        assertEquals("1080p", qualityLabel(1920, 960))
        assertEquals("720p", qualityLabel(1280, 640))
        assertEquals("480p", qualityLabel(856, 424))
        assertEquals("360p", qualityLabel(640, 320))
        assertEquals("240p", qualityLabel(432, 208))
        assertEquals("144p", qualityLabel(256, 128))
    }

    @Test
    fun usualSizesKeepTheirNames() {
        assertEquals("1080p", qualityLabel(1920, 1080))
        assertEquals("720p", qualityLabel(1280, 720))
        assertEquals("480p", qualityLabel(848, 476))
        assertEquals("2160p", qualityLabel(3840, 2160))
    }

    @Test
    fun encoderPaddingAndVerticalVideo() {
        assertEquals("1080p", qualityLabel(1920, 1088))
        assertEquals("720p", qualityLabel(720, 1280))
    }

    @Test
    fun unknownSize() {
        assertEquals("—", qualityLabel(-1, -1))
    }
}
