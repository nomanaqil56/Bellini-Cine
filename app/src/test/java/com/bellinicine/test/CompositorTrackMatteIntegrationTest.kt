package com.bellinicine.test

import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.TrackMatteType
import com.bellinicine.core.render.CompositorPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun compositionEvaluatesHiddenMatteLayerForActiveTarget() {
        // In After Effects, when a layer is designated as a track matte, its standalone
        // visibility eyeball is turned off, but it MUST still be evaluated to composite the target layer!
        val matteLayer = Layer(
            id = "layer-matte",
            name = "Matte Circle",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFFFFFFFF, 100, 100),
            isVisible = false // Standalone visibility eye toggled off
        )

        val targetLayer = Layer(
            id = "layer-target",
            name = "Target Fill",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFFFF0000, 100, 100),
            trackMatteType = TrackMatteType.ALPHA,
            isVisible = true
        )

        val comp = Composition(
            id = "comp-matte-test",
            name = "Matte Comp",
            layers = listOf(matteLayer, targetLayer)
        )

        val evaluated = com.bellinicine.core.engine.CompositionEvaluator.evaluateComposition(comp, 0L)
        assertEquals("Both matte and target layers must be evaluated for track matte compositing", 2, evaluated.size)
        assertEquals("layer-matte", evaluated[0].layer.id)
        assertEquals("layer-target", evaluated[1].layer.id)
    }

    @Test
    fun endToEndCompositionRendersTrackMatteCorrectly() {
        val w = 50
        val h = 50
        // Target: 50x50 pure red
        val targetPixels = IntArray(w * h) { makeArgb(255, 255, 0, 0) }
        // Matte: 50x50 white box with transparent border
        val mattePixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x in 10..39 && y in 10..39) makeArgb(255, 255, 255, 255) else 0
        }

        CompositorPipeline.applyAlphaMattePixels(targetPixels, mattePixels, targetPixels.size, inverted = false)

        // Center pixel should be red with alpha = 255
        val centerPix = targetPixels[(h / 2) * w + (w / 2)]
        val centerA = getA(centerPix)
        val centerR = getR(centerPix)
        assertEquals("Alpha channel of matted target within matte box should be fully opaque", 255, centerA)
        assertEquals("Red channel of matted target should be 255", 255, centerR)

        // Corner pixel outside matte should have alpha = 0
        val cornerPix = targetPixels[0]
        assertEquals("Alpha channel of matted target outside matte box should be 0", 0, getA(cornerPix))
    }
}

