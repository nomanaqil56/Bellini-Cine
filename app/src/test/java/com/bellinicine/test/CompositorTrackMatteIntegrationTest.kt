package com.bellinicine.test

import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.TrackMatteType
import com.bellinicine.core.render.CompositorPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CompositorTrackMatteIntegrationTest {

    private fun makeArgb(a: Int, r: Int, g: Int, b: Int): Int {
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun getA(pix: Int): Int = (pix ushr 24) and 0xff
    private fun getR(pix: Int): Int = (pix ushr 16) and 0xff
    private fun getG(pix: Int): Int = (pix ushr 8) and 0xff
    private fun getB(pix: Int): Int = pix and 0xff

    @Test
    fun alphaMattePixelLevelCoveragePreservesStraightColors() {
        // Red target: A=255, R=255, G=0, B=0
        val target = intArrayOf(makeArgb(255, 255, 0, 0))
        // 50% transparent matte: A=128
        val matte = intArrayOf(makeArgb(128, 0, 255, 0))

        CompositorPipeline.applyAlphaMattePixels(target, matte, 1, inverted = false)

        // Alpha scaled to 50% (approx 128)
        assertTrue(abs(getA(target[0]) - 128) <= 1)
        // Straight RGB must NOT be double-darkened; red remains 255!
        assertEquals("Straight red channel must be preserved without darkening halos", 255, getR(target[0]))
        assertEquals(0, getG(target[0]))
        assertEquals(0, getB(target[0]))
    }

    @Test
    fun alphaInvertedMattePixelLevelCoverage() {
        // Red target: A=255, R=255, G=0, B=0
        val target = intArrayOf(makeArgb(255, 255, 0, 0))
        // 75% opaque matte: A=192 -> Inverted factor = 25% (63)
        val matte = intArrayOf(makeArgb(192, 0, 0, 255))

        CompositorPipeline.applyAlphaMattePixels(target, matte, 1, inverted = true)

        assertTrue(abs(getA(target[0]) - 63) <= 1)
        assertEquals(255, getR(target[0]))
    }

    @Test
    fun lumaMatteBT709PureColors() {
        // Pure green has highest BT.709 luma coefficient (~0.7152)
        // Pure red has ~0.2126
        // Pure blue has ~0.0722
        val targetGreenMatte = intArrayOf(makeArgb(255, 255, 255, 255))
        val matteGreen = intArrayOf(makeArgb(255, 0, 255, 0))
        CompositorPipeline.applyLumaMattePixels(targetGreenMatte, matteGreen, 1, inverted = false)
        val greenLumaAlpha = getA(targetGreenMatte[0])

        val targetRedMatte = intArrayOf(makeArgb(255, 255, 255, 255))
        val matteRed = intArrayOf(makeArgb(255, 255, 0, 0))
        CompositorPipeline.applyLumaMattePixels(targetRedMatte, matteRed, 1, inverted = false)
        val redLumaAlpha = getA(targetRedMatte[0])

        val targetBlueMatte = intArrayOf(makeArgb(255, 255, 255, 255))
        val matteBlue = intArrayOf(makeArgb(255, 0, 0, 255))
        CompositorPipeline.applyLumaMattePixels(targetBlueMatte, matteBlue, 1, inverted = false)
        val blueLumaAlpha = getA(targetBlueMatte[0])

        assertTrue("Green luma ($greenLumaAlpha) must be higher than Red luma ($redLumaAlpha)", greenLumaAlpha > redLumaAlpha)
        assertTrue("Red luma ($redLumaAlpha) must be higher than Blue luma ($blueLumaAlpha)", redLumaAlpha > blueLumaAlpha)
        assertTrue(abs(greenLumaAlpha - (255 * 0.7152f).toInt()) <= 1)
    }

    @Test
    fun lumaInvertedMatte() {
        // Pure white matte: luma = 1.0 -> inverted factor = 0.0 -> target alpha = 0
        val targetWhite = intArrayOf(makeArgb(255, 255, 0, 0))
        val matteWhite = intArrayOf(makeArgb(255, 255, 255, 255))
        CompositorPipeline.applyLumaMattePixels(targetWhite, matteWhite, 1, inverted = true)
        assertEquals(0, getA(targetWhite[0]))

        // Pure black matte: luma = 0.0 -> inverted factor = 1.0 -> target alpha = 255
        val targetBlack = intArrayOf(makeArgb(255, 255, 0, 0))
        val matteBlack = intArrayOf(makeArgb(255, 0, 0, 0))
        CompositorPipeline.applyLumaMattePixels(targetBlack, matteBlack, 1, inverted = true)
        assertEquals(255, getA(targetBlack[0]))
        assertEquals(255, getR(targetBlack[0]))
    }
}
