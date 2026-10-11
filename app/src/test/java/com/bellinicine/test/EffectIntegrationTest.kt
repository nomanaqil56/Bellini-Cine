package com.bellinicine.test

import com.bellinicine.core.engine.CompositionEvaluator
import com.bellinicine.core.model.AnimatedProperty
import com.bellinicine.core.model.Effect
import com.bellinicine.core.model.EffectCategory
import com.bellinicine.core.model.EffectParameter
import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.KeyframeInterpolation
import com.bellinicine.core.render.EffectProcessor
import com.bellinicine.core.render.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectIntegrationTest {

    @Test
    fun everyRegisteredEffectHasProcessorImplementation() {
        val allDefs = EffectRegistry.getAll()
        assertTrue("Registered effects must not be empty", allDefs.isNotEmpty())

        for (def in allDefs) {
            assertTrue(
                "Effect '${def.displayName}' (${def.effectId}) must have an implementation in EffectProcessor",
                EffectProcessor.hasProcessorFor(def.effectId)
            )
            assertTrue(
                "EffectRegistry.isSupported('${def.effectId}') must return true",
                EffectRegistry.isSupported(def.effectId)
            )
            val created = EffectRegistry.createEffect(def.effectId)
            assertNotNull("createEffect must return an Effect for ${def.effectId}", created)
            assertEquals(def.effectId, created?.id)
            assertEquals(def.displayName, created?.name)
            assertTrue("Default parameters must not be empty", created?.parameters?.isNotEmpty() == true)
        }
    }

    @Test
    fun unsupportedEffectIdIsRejected() {
        assertFalse(EffectProcessor.hasProcessorFor("com.bellinicine.effects.unsupported_fake"))
        assertFalse(EffectRegistry.isSupported("com.bellinicine.effects.unsupported_fake"))
        val created = EffectRegistry.createEffect("com.bellinicine.effects.unsupported_fake")
        assertNull("EffectRegistry.createEffect must reject unsupported effect IDs", created)
    }

    @Test
    fun effectParameterAnimationInterpolatesCorrectlyAcrossFrames() {
        val brightnessProp = AnimatedProperty(
            name = "Brightness",
            defaultValue = 0f,
            keyframes = listOf(
                Keyframe(0L, -50f, KeyframeInterpolation.LINEAR),
                Keyframe(60L, 50f, KeyframeInterpolation.LINEAR)
            ),
            isKeyframed = true
        )

        val valAtFrame0 = CompositionEvaluator.evaluateFloatProperty(brightnessProp, 0L, 0f)
        val valAtFrame30 = CompositionEvaluator.evaluateFloatProperty(brightnessProp, 30L, 1f)
        val valAtFrame60 = CompositionEvaluator.evaluateFloatProperty(brightnessProp, 60L, 2f)

        assertEquals(-50f, valAtFrame0, 0.001f)
        assertEquals(0f, valAtFrame30, 0.001f)
        assertEquals(50f, valAtFrame60, 0.001f)
    }

    @Test
    fun effectEnableDisableAndParameterMutation() {
        val effect = EffectRegistry.createEffect(EffectRegistry.ID_FAST_BLUR)!!
        assertTrue(effect.enabled)

        val disabled = effect.copy(enabled = false)
        assertFalse(disabled.enabled)

        // Mutate Radius parameter
        val updatedParams = effect.parameters.map { param ->
            if (param is EffectParameter.Slider && param.name == "Radius") {
                param.copy(property = param.property.copy(defaultValue = 42f))
            } else param
        }
        val mutatedEffect = effect.copy(parameters = updatedParams)
        val mutatedSlider = mutatedEffect.parameters.filterIsInstance<EffectParameter.Slider>().first { it.name == "Radius" }
        assertEquals(42f, mutatedSlider.property.defaultValue, 0.001f)
    }

    @Test
    fun glowThresholdIsolatesPixelsAboveLuminance() {
        val w = 2
        val h = 1
        // Create 2 pixels: Left is dark (RGB=50,50,50), Right is bright (RGB=220,220,220)
        val darkColor = (255 shl 24) or (50 shl 16) or (50 shl 8) or 50
        val brightColor = (255 shl 24) or (220 shl 16) or (220 shl 8) or 220
        val inputPixels = intArrayOf(darkColor, brightColor)

        // With threshold = 0.5 (128), dark pixel should be zeroed out, bright pixel should remain
        val isolatedPixels = EffectProcessor.isolateThresholdPixels(inputPixels, 0.5f)

        assertEquals("Dark pixel below threshold must be zeroed out", 0, isolatedPixels[0])
        val brightAlpha = (isolatedPixels[1] ushr 24) and 0xff
        assertTrue("Bright pixel above threshold must have non-zero alpha", brightAlpha > 0)
    }

    @Test
    fun hueRotationMatrixTransformsColorChannels() {
        // 0 degrees should produce identity matrix
        val array0 = EffectProcessor.createHueRotationMatrixArray(0f)
        assertEquals(1f, array0[0], 0.001f) // a00 = 1
        assertEquals(0f, array0[1], 0.001f) // a01 = 0
        assertEquals(0f, array0[2], 0.001f) // a02 = 0

        // 180 degrees should produce non-identity transformation
        val array180 = EffectProcessor.createHueRotationMatrixArray(180f)
        assertFalse("180 degree hue rotation must not be identity", array180[0] == 1f && array180[1] == 0f)
    }

    @Test
    fun unknownEffectIsRejectedWithoutBecomingFastBlur() {
        val vm = com.bellinicine.ui.workspace.WorkspaceViewModel()
        vm.addEffectToSelectedLayer("Completely Unknown 3D Particle Flare", EffectCategory.STYLIZE)
        assertEquals("Unsupported or unregistered effect: Completely Unknown 3D Particle Flare", vm.uiState.value.statusMessage)
    }

    @Test
    fun invertEffectInvertsColorAndAlphaChannelsCorrectly() {
        // Red pixel: A=255, R=255, G=0, B=0
        val redPixel = (255 shl 24) or (255 shl 16)
        val invertedRgb = EffectProcessor.applyInvertPixels(intArrayOf(redPixel), invertRgb = true, invertAlpha = false)
        val r = (invertedRgb[0] ushr 16) and 0xff
        val g = (invertedRgb[0] ushr 8) and 0xff
        val b = invertedRgb[0] and 0xff
        val a = (invertedRgb[0] ushr 24) and 0xff
        assertEquals("Red inverted must be 0", 0, r)
        assertEquals("Green inverted must be 255", 255, g)
        assertEquals("Blue inverted must be 255", 255, b)
        assertEquals("Alpha preserved", 255, a)

        // Semi-transparent pixel: A=100
        val semiTransparent = (100 shl 24) or (128 shl 16)
        val invertedAlpha = EffectProcessor.applyInvertPixels(intArrayOf(semiTransparent), invertRgb = false, invertAlpha = true)
        val invA = (invertedAlpha[0] ushr 24) and 0xff
        assertEquals("Alpha inverted (255 - 100) must be 155", 155, invA)
    }

    @Test
    fun tintEffectInterpolatesBetweenMapBlackAndMapWhite() {
        // Pure black pixel (luma 0) and pure white pixel (luma 255)
        val blackPixel = (255 shl 24) or 0
        val whitePixel = (255 shl 24) or (255 shl 16) or (255 shl 8) or 255

        // Map black to Red (0xFFFF0000), white to Blue (0xFF0000FF), amount = 100%
        val mapBlackTo = 0xFFFF0000L
        val mapWhiteTo = 0xFF0000FFL
        val tinted = EffectProcessor.applyTintPixels(intArrayOf(blackPixel, whitePixel), mapBlackTo, mapWhiteTo, 1.0f)

        val tintedBlackR = (tinted[0] ushr 16) and 0xff
        val tintedBlackB = tinted[0] and 0xff
        assertEquals("Black mapped to red must have R=255", 255, tintedBlackR)
        assertEquals("Black mapped to red must have B=0", 0, tintedBlackB)

        val tintedWhiteR = (tinted[1] ushr 16) and 0xff
        val tintedWhiteB = tinted[1] and 0xff
        assertEquals("White mapped to blue must have R=0", 0, tintedWhiteR)
        assertEquals("White mapped to blue must have B=255", 255, tintedWhiteB)
    }

    @Test
    fun brightnessContrastAdjustsDynamicRangeAndLevels() {
        val midGray = (255 shl 24) or (128 shl 16) or (128 shl 8) or 128
        // Increase brightness by +20
        val brightened = EffectProcessor.applyBrightnessContrastPixels(intArrayOf(midGray), brightness = 20f, contrast = 0f)
        val brightR = (brightened[0] ushr 16) and 0xff
        assertTrue("Brightened pixel value must be greater than original 128", brightR > 128)

        // Decrease brightness by -20
        val darkened = EffectProcessor.applyBrightnessContrastPixels(intArrayOf(midGray), brightness = -20f, contrast = 0f)
        val darkR = (darkened[0] ushr 16) and 0xff
        assertTrue("Darkened pixel value must be less than original 128", darkR < 128)
    }

    @Test
    fun solidCompositeBlendsOverSolidBackground() {
        // Transparent pixel (A=0)
        val transparentPix = 0
        // Composite over pure blue solid (0xFF0000FF) with 100% opacity
        val composited = EffectProcessor.applySolidCompositePixels(intArrayOf(transparentPix), solidColor = 0xFF0000FFL, opacity = 1.0f)
        val compA = (composited[0] ushr 24) and 0xff
        val compB = composited[0] and 0xff
        assertEquals("Composited background alpha must be 255", 255, compA)
        assertEquals("Composited background blue must be 255", 255, compB)

        // Fully opaque red pixel (A=255, R=255) over blue solid: should remain pure red
        val opaqueRed = (255 shl 24) or (255 shl 16)
        val overSolid = EffectProcessor.applySolidCompositePixels(intArrayOf(opaqueRed), solidColor = 0xFF0000FFL, opacity = 1.0f)
        val overR = (overSolid[0] ushr 16) and 0xff
        val overB = overSolid[0] and 0xff
        assertEquals("Opaque foreground red must be 255", 255, overR)
        assertEquals("Opaque foreground blue must be 0", 0, overB)
    }
}

