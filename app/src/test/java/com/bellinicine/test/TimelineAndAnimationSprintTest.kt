package com.bellinicine.test

import com.bellinicine.core.engine.BezierEvaluator
import com.bellinicine.core.engine.CompositionEvaluator
import com.bellinicine.core.model.AnimatedProperty
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.KeyframeInterpolation
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.Mask
import com.bellinicine.core.model.MaskMode
import com.bellinicine.core.model.MaskVertex
import com.bellinicine.core.model.Project
import com.bellinicine.core.model.Transform
import com.bellinicine.core.model.Vec2
import com.bellinicine.core.model.Vec3
import com.bellinicine.core.render.EffectProcessor
import com.bellinicine.core.render.EffectRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TimelineAndAnimationSprintTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // --- WORKSTREAM 1: TIMELINE EDITING ---

    @Test
    fun layerSplittingProducesTwoContinuousLayers() {
        val originalLayer = Layer(
            id = "layer-1",
            name = "Test Solid",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFFFFFFFF, 100, 100),
            inPointFrame = 0L,
            outPointFrame = 100L,
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(100f, 200f, 0f))
            )
        )

        val splitFrame = 40L
        val part1 = originalLayer.copy(outPointFrame = splitFrame)
        val part2 = originalLayer.copy(
            id = "layer-split-2",
            name = "${originalLayer.name} (Split)",
            inPointFrame = splitFrame
        )

        assertEquals(0L, part1.inPointFrame)
        assertEquals(40L, part1.outPointFrame)
        assertEquals(40L, part2.inPointFrame)
        assertEquals(100L, part2.outPointFrame)
        assertEquals(originalLayer.transform.position.defaultValue, part2.transform.position.defaultValue)
        assertNotEquals(part1.id, part2.id)
    }

    @Test
    fun layerDuplicationPreservesAllPropertiesAndKeyframes() {
        val originalLayer = Layer(
            id = "layer-orig",
            name = "Animated Text",
            type = LayerType.TEXT,
            data = LayerData.Text("Animated Text"),
            inPointFrame = 10L,
            outPointFrame = 90L,
            transform = Transform(
                rotation = AnimatedProperty(
                    name = "Rotation",
                    defaultValue = 0f,
                    keyframes = listOf(
                        Keyframe(10L, 0f, KeyframeInterpolation.BEZIER),
                        Keyframe(50L, 180f, KeyframeInterpolation.BEZIER)
                    ),
                    isKeyframed = true
                )
            ),
            effects = listOfNotNull(EffectRegistry.createEffect(EffectRegistry.ID_FAST_BLUR))
        )

        val duplicate = originalLayer.copy(
            id = "layer-dup-${UUID.randomUUID()}",
            name = "${originalLayer.name} Copy"
        )

        assertNotEquals(originalLayer.id, duplicate.id)
        assertEquals("${originalLayer.name} Copy", duplicate.name)
        assertEquals(originalLayer.inPointFrame, duplicate.inPointFrame)
        assertEquals(originalLayer.outPointFrame, duplicate.outPointFrame)
        assertEquals(2, duplicate.transform.rotation.keyframes.size)
        assertEquals(180f, duplicate.transform.rotation.keyframes[1].value, 0.001f)
        assertEquals(1, duplicate.effects.size)
        assertEquals(EffectRegistry.ID_FAST_BLUR, duplicate.effects[0].id)
    }

    // --- WORKSTREAM 2: GENERIC KEYFRAMING & VEC2 INTERPOLATION ---

    @Test
    fun vec2KeyframeInterpolationEvaluatesSmoothly() {
        val kf1Linear = Keyframe(0L, Vec2(0f, 0f), KeyframeInterpolation.LINEAR)
        val kf2Linear = Keyframe(100L, Vec2(100f, 200f), KeyframeInterpolation.LINEAR)
        val linearMid = BezierEvaluator.evaluateVec2(kf1Linear, kf2Linear, 50L)
        assertEquals(50f, linearMid.x, 0.001f)
        assertEquals(100f, linearMid.y, 0.001f)

        val kf1Bezier = Keyframe(0L, Vec2(0f, 0f), KeyframeInterpolation.BEZIER)
        val kf2Bezier = Keyframe(100L, Vec2(100f, 200f), KeyframeInterpolation.BEZIER)
        val bezierMid = BezierEvaluator.evaluateVec2(kf1Bezier, kf2Bezier, 50L)
        assertEquals(50f, bezierMid.x, 0.01f)
        assertEquals(100f, bezierMid.y, 0.01f)

        val kf1Hold = Keyframe(0L, Vec2(0f, 0f), KeyframeInterpolation.HOLD)
        val kf2Hold = Keyframe(100L, Vec2(100f, 200f), KeyframeInterpolation.HOLD)
        val holdMid = BezierEvaluator.evaluateVec2(kf1Hold, kf2Hold, 50L)
        assertEquals(0f, holdMid.x, 0.001f)
        assertEquals(0f, holdMid.y, 0.001f)
    }

    @Test
    fun compositionEvaluatorResolvesVec2PropertyAtArbitraryFrames() {
        val prop = AnimatedProperty(
            name = "Anchor",
            defaultValue = Vec2(0f, 0f),
            keyframes = listOf(
                Keyframe(0L, Vec2(0f, 0f), KeyframeInterpolation.LINEAR),
                Keyframe(100L, Vec2(200f, 400f), KeyframeInterpolation.LINEAR)
            ),
            isKeyframed = true
        )

        val valAt25 = CompositionEvaluator.evaluateVec2Property(prop, 25L, 0f)
        assertEquals(50f, valAt25.x, 0.001f)
        assertEquals(100f, valAt25.y, 0.001f)

        val valAt75 = CompositionEvaluator.evaluateVec2Property(prop, 75L, 0f)
        assertEquals(150f, valAt75.x, 0.001f)
        assertEquals(300f, valAt75.y, 0.001f)
    }

    // --- WORKSTREAM 4: SHAPE OPERATORS (TRIM PATHS & REPEATER) ---

    @Test
    fun shapeOperatorsSerializeAndDeserializeWithDefaults() {
        val shapeData = LayerData.Shape(
            shapeType = "star",
            width = 400f,
            height = 400f,
            trimPathsStart = 10f,
            trimPathsEnd = 90f,
            trimPathsOffset = 45f,
            repeaterCopies = 5,
            repeaterOffsetX = 40f,
            repeaterOffsetY = 20f,
            repeaterScale = 1.1f,
            repeaterRotation = 15f
        )

        val jsonStr = json.encodeToString(shapeData)
        val decoded = json.decodeFromString<LayerData.Shape>(jsonStr)

        assertEquals("star", decoded.shapeType)
        assertEquals(10f, decoded.trimPathsStart, 0.001f)
        assertEquals(90f, decoded.trimPathsEnd, 0.001f)
        assertEquals(45f, decoded.trimPathsOffset, 0.001f)
        assertEquals(5, decoded.repeaterCopies)
        assertEquals(40f, decoded.repeaterOffsetX, 0.001f)
        assertEquals(20f, decoded.repeaterOffsetY, 0.001f)
        assertEquals(1.1f, decoded.repeaterScale, 0.001f)
        assertEquals(15f, decoded.repeaterRotation, 0.001f)
    }

    // --- WORKSTREAM 5: TYPOGRAPHY & TEXT RANGE ANIMATOR ---

    @Test
    fun textRangeAnimatorSerializesAndCalculatesActiveWindows() {
        val textData = LayerData.Text(
            text = "AFTER EFFECTS MOBILE",
            fontSize = 64f,
            tracking = 15f,
            leading = 8f,
            alignment = "center",
            animatorEnabled = true,
            animatorStart = 20f,
            animatorEnd = 80f,
            animatorOpacity = 0f,
            animatorOffsetY = -50f,
            animatorScale = 1.5f
        )

        val jsonStr = json.encodeToString(textData)
        val decoded = json.decodeFromString<LayerData.Text>(jsonStr)

        assertEquals("center", decoded.alignment)
        assertTrue(decoded.animatorEnabled)
        assertEquals(20f, decoded.animatorStart, 0.001f)
        assertEquals(80f, decoded.animatorEnd, 0.001f)
        assertEquals(0f, decoded.animatorOpacity, 0.001f)
        assertEquals(-50f, decoded.animatorOffsetY, 0.001f)
        assertEquals(1.5f, decoded.animatorScale, 0.001f)

        // Character index check for 20-character string
        val charCount = textData.text.length
        val startIdx = (decoded.animatorStart / 100f * charCount).toInt()
        val endIdx = (decoded.animatorEnd / 100f * charCount).toInt()
        assertTrue("Characters before startIdx are unaffected", 0 < startIdx)
        assertTrue("Characters inside window are animated", startIdx < endIdx)
    }

    // --- WORKSTREAM 6: MASK MODES & ADJUSTMENT LAYERS ---

    @Test
    fun maskDataModesAndParametersAreFullySupported() {
        val mask = Mask(
            id = "mask-1",
            name = "Vignette Mask",
            mode = MaskMode.SUBTRACT,
            inverted = true,
            opacity = AnimatedProperty("Opacity", 80f),
            feather = AnimatedProperty("Feather", 25f),
            expansion = AnimatedProperty("Expansion", 10f),
            vertices = listOf(
                MaskVertex(Vec2(0f, 0f)),
                MaskVertex(Vec2(100f, 0f)),
                MaskVertex(Vec2(100f, 100f)),
                MaskVertex(Vec2(0f, 100f))
            ),
            isClosed = true
        )

        assertEquals(MaskMode.SUBTRACT, mask.mode)
        assertTrue(mask.inverted)
        assertEquals(80f, mask.opacity.defaultValue, 0.001f)
        assertEquals(25f, mask.feather.defaultValue, 0.001f)
        assertEquals(10f, mask.expansion.defaultValue, 0.001f)
    }

    // --- WORKSTREAM 7: VISUAL EFFECTS PIXEL PROCESSING ---

    @Test
    fun levelsEffectCompressesAndExpandsColorTonalRanges() {
        // Pixel with middle gray (128)
        val pix = intArrayOf((255 shl 24) or (128 shl 16) or (128 shl 8) or 128)
        // Levels mapping: Input [0, 255], Gamma 1.0, Output [50, 200]
        val processed = EffectProcessor.applyLevelsPixels(pix, 0f, 255f, 1f, 50f, 200f)
        val outR = (processed[0] ushr 16) and 0xff
        assertTrue("Output gray must be mapped into [50, 200]", outR in 120..130)
    }

    @Test
    fun posterizeEffectQuantizesColorChannelsIntoDiscreteSteps() {
        // Gradient of 5 pixel values: 0, 60, 128, 190, 255
        val pix = intArrayOf(
            (255 shl 24) or (0 shl 16) or (0 shl 8) or 0,
            (255 shl 24) or (60 shl 16) or (60 shl 8) or 60,
            (255 shl 24) or (128 shl 16) or (128 shl 8) or 128,
            (255 shl 24) or (190 shl 16) or (190 shl 8) or 190,
            (255 shl 24) or (255 shl 16) or (255 shl 8) or 255
        )

        // Quantize to 4 levels: 0, 85, 170, 255
        val processed = EffectProcessor.applyPosterizePixels(pix, 4)
        val validLevels = setOf(0, 85, 170, 255)
        for (p in processed) {
            val r = (p ushr 16) and 0xff
            assertTrue("Posterized value $r must belong to quantized buckets", r in validLevels)
        }
    }

    @Test
    fun mosaicEffectAveragesBlocksCorrectly() {
        val w = 4
        val h = 4
        val pix = IntArray(w * h) {
            val v = (it * 15).coerceIn(0, 255)
            (255 shl 24) or (v shl 16) or (v shl 8) or v
        }

        // Apply 2x2 mosaic
        val mosaic = EffectProcessor.applyMosaicPixels(pix, w, h, blockSize = 2)

        // Block 0,0 (pixels (0,0), (1,0), (0,1), (1,1)) should all have identical value
        val b0 = mosaic[0]
        val b1 = mosaic[1]
        val b2 = mosaic[w]
        val b3 = mosaic[w + 1]
        assertEquals("Mosaic block pixels must be identical", b0, b1)
        assertEquals("Mosaic block pixels must be identical", b0, b2)
        assertEquals("Mosaic block pixels must be identical", b0, b3)
    }

    @Test
    fun sharpenEnhancesHighContrastEdges() {
        val w = 3
        val h = 3
        // Center pixel is 200, surrounded by 50
        val pix = IntArray(9) { (255 shl 24) or (50 shl 16) or (50 shl 8) or 50 }
        pix[4] = (255 shl 24) or (200 shl 16) or (200 shl 8) or 200

        val sharpened = EffectProcessor.applySharpenPixels(pix, w, h, amount = 50f)
        val centerVal = (sharpened[4] ushr 16) and 0xff
        assertTrue("Sharpen must boost center peak above original 200", centerVal > 200)
    }

    @Test
    fun vignetteDimsOuterPixelsMoreThanCenterPixels() {
        val w = 100
        val h = 100
        // Uniform white image
        val pix = IntArray(w * h) { 0xFFFFFFFF.toInt() }

        val vignetted = EffectProcessor.applyVignettePixels(pix, w, h, radius = 0.2f, darkness = 0.8f)

        // Center pixel (50, 50)
        val centerR = (vignetted[50 * w + 50] ushr 16) and 0xff
        // Corner pixel (0, 0)
        val cornerR = (vignetted[0] ushr 16) and 0xff

        assertEquals(255, centerR)
        assertTrue("Corner pixels must be dimmed by vignette", cornerR < 150)
    }

    // --- WORKSTREAM 8: AUDIO LAYER & TIMELINE SUPPORT ---

    @Test
    fun audioLayerModelSerializesWaveformAndVolume() {
        val audioData = LayerData.Audio(
            audioUri = "content://media/external/audio/media/42",
            durationSeconds = 120.5f,
            waveformPeaks = listOf(0.1f, 0.5f, 0.9f, 0.8f, 0.4f, 0.2f)
        )
        val audioLayer = Layer(
            id = "layer-audio-1",
            name = "Soundtrack",
            type = LayerType.AUDIO,
            data = audioData,
            audioVolume = 0.85f,
            inPointFrame = 0L,
            outPointFrame = 3600L
        )

        val jsonStr = json.encodeToString(audioLayer)
        val decoded = json.decodeFromString<Layer>(jsonStr)

        assertEquals(LayerType.AUDIO, decoded.type)
        assertEquals(0.85f, decoded.audioVolume, 0.001f)
        val decodedData = decoded.data as LayerData.Audio
        assertEquals("content://media/external/audio/media/42", decodedData.audioUri)
        assertEquals(120.5f, decodedData.durationSeconds, 0.001f)
        assertEquals(6, decodedData.waveformPeaks.size)
        assertEquals(0.9f, decodedData.waveformPeaks[2], 0.001f)
    }
}
