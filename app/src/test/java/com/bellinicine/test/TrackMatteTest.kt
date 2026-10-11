package com.bellinicine.test

import com.bellinicine.core.render.CompositorPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pixel-level numerical automated tests for isolated Track Matte compositing (Finding A).
 * Verifies Alpha, Alpha Inverted, BT.709 Luminance, and Luma Inverted modes.
 */
class TrackMatteTest {

    private fun makeArgb(a: Int, r: Int, g: Int, b: Int): Int {
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun getA(pix: Int): Int = (pix ushr 24) and 0xff
    private fun getR(pix: Int): Int = (pix ushr 16) and 0xff
    private fun getG(pix: Int): Int = (pix ushr 8) and 0xff
    private fun getB(pix: Int): Int = pix and 0xff

    @Test
    fun testAlphaMatteOpaquePreservesTarget() {
        // Red target pixel: A=255, R=255, G=0, B=0
        val target = intArrayOf(makeArgb(255, 255, 0, 0))
        // Fully opaque matte pixel: A=255
        val matte = intArrayOf(makeArgb(255, 0, 255, 0))

        CompositorPipeline.applyAlphaMattePixels(target, matte, 1, inverted = false)

        assertEquals("Alpha should be fully preserved (255)", 255, getA(target[0]))
        assertEquals("Red channel should be preserved (255)", 255, getR(target[0]))
        assertEquals("Green should be 0", 0, getG(target[0]))
    }

    @Test
    fun testAlphaMatteTransparentRemovesTarget() {
        val target = intArrayOf(makeArgb(255, 255, 0, 0))
        // Transparent matte pixel: A=0
        val matte = intArrayOf(makeArgb(0, 0, 255, 0))

        CompositorPipeline.applyAlphaMattePixels(target, matte, 1, inverted = false)

        assertEquals("Alpha should be 0", 0, getA(target[0]))
        assertEquals("Red channel should be 0", 0, getR(target[0]))
    }

    @Test
    fun testAlphaInvertedMatte() {
        // Transparent matte with inverted alpha should preserve target!
        val target1 = intArrayOf(makeArgb(255, 255, 0, 0))
        val matteTransparent = intArrayOf(makeArgb(0, 255, 255, 255))
        CompositorPipeline.applyAlphaMattePixels(target1, matteTransparent, 1, inverted = true)
        assertEquals("Alpha should be preserved with inverted transparent matte", 255, getA(target1[0]))

        // Opaque matte with inverted alpha should clip target to 0!
        val target2 = intArrayOf(makeArgb(255, 255, 0, 0))
        val matteOpaque = intArrayOf(makeArgb(255, 255, 255, 255))
        CompositorPipeline.applyAlphaMattePixels(target2, matteOpaque, 1, inverted = true)
        assertEquals("Alpha should be 0 with inverted opaque matte", 0, getA(target2[0]))
    }

    @Test
    fun testLumaMattePureWhiteAndBlack() {
        // Pure White Matte (Luma = 1.0)
        val targetWhite = intArrayOf(makeArgb(255, 0, 255, 0))
        val matteWhite = intArrayOf(makeArgb(255, 255, 255, 255))
        CompositorPipeline.applyLumaMattePixels(targetWhite, matteWhite, 1, inverted = false)
        assertEquals("Luma matte over pure white should preserve alpha (255)", 255, getA(targetWhite[0]))
        assertEquals("Green channel should remain 255", 255, getG(targetWhite[0]))

        // Pure Black Matte (Luma = 0.0)
        val targetBlack = intArrayOf(makeArgb(255, 0, 255, 0))
        val matteBlack = intArrayOf(makeArgb(255, 0, 0, 0))
        CompositorPipeline.applyLumaMattePixels(targetBlack, matteBlack, 1, inverted = false)
        assertEquals("Luma matte over pure black should reduce alpha to 0", 0, getA(targetBlack[0]))
    }

    @Test
    fun testLumaMatteBt709Coefficients() {
        // BT.709: Y = 0.2126 * R + 0.7152 * G + 0.0722 * B
        val targetR = intArrayOf(makeArgb(255, 255, 255, 255))
        val mattePureRed = intArrayOf(makeArgb(255, 255, 0, 0))
        CompositorPipeline.applyLumaMattePixels(targetR, mattePureRed, 1, inverted = false)
        val expectedRedAlpha = (255 * 0.2126f).toInt()
        assertTrue(
            "Pure Red luma should be ~${expectedRedAlpha}, got ${getA(targetR[0])}",
            abs(getA(targetR[0]) - expectedRedAlpha) <= 2
        )

        val targetG = intArrayOf(makeArgb(255, 255, 255, 255))
        val mattePureGreen = intArrayOf(makeArgb(255, 0, 255, 0))
        CompositorPipeline.applyLumaMattePixels(targetG, mattePureGreen, 1, inverted = false)
        val expectedGreenAlpha = (255 * 0.7152f).toInt()
        assertTrue(
            "Pure Green luma should be ~${expectedGreenAlpha}, got ${getA(targetG[0])}",
            abs(getA(targetG[0]) - expectedGreenAlpha) <= 2
        )

        val targetB = intArrayOf(makeArgb(255, 255, 255, 255))
        val mattePureBlue = intArrayOf(makeArgb(255, 0, 0, 255))
        CompositorPipeline.applyLumaMattePixels(targetB, mattePureBlue, 1, inverted = false)
        val expectedBlueAlpha = (255 * 0.0722f).toInt()
        assertTrue(
            "Pure Blue luma should be ~${expectedBlueAlpha}, got ${getA(targetB[0])}",
            abs(getA(targetB[0]) - expectedBlueAlpha) <= 2
        )
    }

    @Test
    fun testLumaInvertedMatte() {
        // Pure White Matte with Luma Inverted -> Alpha becomes 0
        val targetWhite = intArrayOf(makeArgb(255, 200, 100, 50))
        val matteWhite = intArrayOf(makeArgb(255, 255, 255, 255))
        CompositorPipeline.applyLumaMattePixels(targetWhite, matteWhite, 1, inverted = true)
        assertEquals("Inverted luma on pure white should be 0 alpha", 0, getA(targetWhite[0]))

        // Pure Black Matte with Luma Inverted -> Alpha remains 255
        val targetBlack = intArrayOf(makeArgb(255, 200, 100, 50))
        val matteBlack = intArrayOf(makeArgb(255, 0, 0, 0))
        CompositorPipeline.applyLumaMattePixels(targetBlack, matteBlack, 1, inverted = true)
        assertEquals("Inverted luma on pure black should preserve 255 alpha", 255, getA(targetBlack[0]))
    }
}
