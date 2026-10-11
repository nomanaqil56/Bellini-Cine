package com.bellinicine.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.bellinicine.core.engine.CompositionEvaluator
import com.bellinicine.core.model.Effect
import com.bellinicine.core.model.EffectCategory
import com.bellinicine.core.model.EffectParameter
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic effect processor executing typed visual effects with keyframe interpolation.
 */
object EffectProcessor {

    fun hasProcessorFor(effectId: String): Boolean {
        return when (effectId) {
            EffectRegistry.ID_FAST_BLUR,
            EffectRegistry.ID_BOX_BLUR,
            EffectRegistry.ID_DIRECTIONAL_BLUR,
            EffectRegistry.ID_SHARPEN,
            EffectRegistry.ID_GLOW,
            EffectRegistry.ID_BRIGHTNESS_CONTRAST,
            EffectRegistry.ID_EXPOSURE,
            EffectRegistry.ID_LEVELS,
            EffectRegistry.ID_GAMMA,
            EffectRegistry.ID_INVERT,
            EffectRegistry.ID_HUE_SATURATION,
            EffectRegistry.ID_TINT,
            EffectRegistry.ID_COLOR_BALANCE,
            EffectRegistry.ID_GRAYSCALE,
            EffectRegistry.ID_POSTERIZE,
            EffectRegistry.ID_MOSAIC,
            EffectRegistry.ID_NOISE,
            EffectRegistry.ID_VIGNETTE,
            EffectRegistry.ID_DROP_SHADOW,
            EffectRegistry.ID_SOLID_COMPOSITE -> true
            else -> false
        }
    }

    fun applyEffects(
        sourceBitmap: Bitmap,
        effects: List<Effect>,
        frame: Long,
        fps: Float
    ): Bitmap {
        var currentBitmap = sourceBitmap
        val seconds = (frame.toFloat() / fps.coerceAtLeast(1f))

        for (effect in effects) {
            if (!effect.enabled) continue

            val result = processSingleEffect(currentBitmap, effect, frame, seconds)
            if (result !== currentBitmap && currentBitmap !== sourceBitmap) {
                currentBitmap.recycle()
            }
            currentBitmap = result
        }

        return currentBitmap
    }

    fun processSingleEffect(
        input: Bitmap,
        effect: Effect,
        frame: Long,
        seconds: Float
    ): Bitmap {
        return when (effect.id) {
            EffectRegistry.ID_FAST_BLUR -> {
                val radius = evaluateSlider(effect, "Radius", frame, seconds, 15f)
                CompositorPipeline.fastBlur(input, radius.toInt().coerceIn(1, 100))
            }

            EffectRegistry.ID_GLOW -> {
                val threshold = evaluateSlider(effect, "Threshold", frame, seconds, 50f) / 100f
                val radius = evaluateSlider(effect, "Radius", frame, seconds, 20f).toInt().coerceIn(1, 60)
                val intensity = evaluateSlider(effect, "Intensity", frame, seconds, 80f) / 100f

                val brightPass = isolateThreshold(input, threshold)
                val glowBmp = CompositorPipeline.fastBlur(brightPass, radius)
                brightPass.recycle()

                val out = input.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(out)
                val paint = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                    alpha = (intensity * 255).toInt().coerceIn(0, 255)
                }
                canvas.drawBitmap(glowBmp, 0f, 0f, paint)
                glowBmp.recycle()
                out
            }

            EffectRegistry.ID_BRIGHTNESS_CONTRAST -> {
                val brightness = evaluateSlider(effect, "Brightness", frame, seconds, 0f)
                val contrast = evaluateSlider(effect, "Contrast", frame, seconds, 0f)

                // Contrast formula: scale = (contrast + 100) / 100
                // Brightness translation
                val scale = ((contrast + 100f) / 100f).coerceAtLeast(0f)
                val translate = (brightness * 2.55f) + 128f * (1f - scale)

                val cm = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                applyColorMatrix(input, cm)
            }

            EffectRegistry.ID_INVERT -> {
                val invertRgb = evaluateCheckbox(effect, "Invert RGB", true)
                val invertAlpha = evaluateCheckbox(effect, "Invert Alpha", false)

                val rScale = if (invertRgb) -1f else 1f
                val rTrans = if (invertRgb) 255f else 0f
                val aScale = if (invertAlpha) -1f else 1f
                val aTrans = if (invertAlpha) 255f else 0f

                val cm = ColorMatrix(floatArrayOf(
                    rScale, 0f, 0f, 0f, rTrans,
                    0f, rScale, 0f, 0f, rTrans,
                    0f, 0f, rScale, 0f, rTrans,
                    0f, 0f, 0f, aScale, aTrans
                ))
                applyColorMatrix(input, cm)
            }

            EffectRegistry.ID_HUE_SATURATION -> {
                val hueAngle = evaluateSlider(effect, "Master Hue", frame, seconds, 0f)
                val sat = evaluateSlider(effect, "Master Saturation", frame, seconds, 0f)
                val lightness = evaluateSlider(effect, "Master Lightness", frame, seconds, 0f)

                val cm = ColorMatrix()

                // 1. Rotate Master Hue if specified
                if (hueAngle != 0f) {
                    val hueMatrix = createHueRotationMatrix(hueAngle)
                    cm.postConcat(hueMatrix)
                }

                // 2. Adjust Master Saturation
                if (sat != 0f) {
                    val satScale = ((sat + 100f) / 100f).coerceAtLeast(0f)
                    val satMatrix = ColorMatrix()
                    satMatrix.setSaturation(satScale)
                    cm.postConcat(satMatrix)
                }

                // 3. Adjust Master Lightness
                if (lightness != 0f) {
                    val lScale = ((lightness + 100f) / 100f).coerceAtLeast(0f)
                    val lCm = ColorMatrix()
                    lCm.setScale(lScale, lScale, lScale, 1f)
                    cm.postConcat(lCm)
                }

                applyColorMatrix(input, cm)
            }

            EffectRegistry.ID_TINT -> {
                val blackColor = evaluateColor(effect, "Map Black To", 0xFF000000L)
                val whiteColor = evaluateColor(effect, "Map White To", 0xFFFFFFFFL)
                val amount = evaluateSlider(effect, "Amount to Tint", frame, seconds, 100f) / 100f
                applyTint(input, blackColor, whiteColor, amount)
            }

            EffectRegistry.ID_SOLID_COMPOSITE -> {
                val solidColor = evaluateColor(effect, "Color", 0xFF000000L)
                val opacity = evaluateSlider(effect, "Opacity", frame, seconds, 100f) / 100f
                val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                val bgPaint = Paint().apply {
                    color = solidColor.toInt()
                    alpha = (opacity * 255).toInt().coerceIn(0, 255)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, input.width.toFloat(), input.height.toFloat(), bgPaint)
                canvas.drawBitmap(input, 0f, 0f, null)
                out
            }

            EffectRegistry.ID_BOX_BLUR -> {
                val radius = evaluateSlider(effect, "Radius", frame, seconds, 10f).toInt().coerceIn(1, 100)
                CompositorPipeline.fastBlur(input, radius)
            }

            EffectRegistry.ID_DIRECTIONAL_BLUR -> {
                val dir = evaluateSlider(effect, "Direction", frame, seconds, 0f)
                val len = evaluateSlider(effect, "Length", frame, seconds, 20f)
                applyDirectionalBlur(input, dir, len)
            }

            EffectRegistry.ID_SHARPEN -> {
                val amount = evaluateSlider(effect, "Sharpen Amount", frame, seconds, 25f)
                applySharpen(input, amount)
            }

            EffectRegistry.ID_EXPOSURE -> {
                val exposure = evaluateSlider(effect, "Exposure", frame, seconds, 0f)
                val offset = evaluateSlider(effect, "Offset", frame, seconds, 0f)
                val scale = Math.pow(2.0, exposure.toDouble()).toFloat()
                val translate = offset * 255f
                val cm = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                applyColorMatrix(input, cm)
            }

            EffectRegistry.ID_LEVELS -> {
                val inB = evaluateSlider(effect, "Input Black", frame, seconds, 0f)
                val inW = evaluateSlider(effect, "Input White", frame, seconds, 255f)
                val gamma = evaluateSlider(effect, "Gamma", frame, seconds, 1f).coerceIn(0.1f, 5f)
                val outB = evaluateSlider(effect, "Output Black", frame, seconds, 0f)
                val outW = evaluateSlider(effect, "Output White", frame, seconds, 255f)
                applyLevels(input, inB, inW, gamma, outB, outW)
            }

            EffectRegistry.ID_GAMMA -> {
                val gamma = evaluateSlider(effect, "Gamma", frame, seconds, 1f).coerceIn(0.1f, 5f)
                applyGamma(input, gamma)
            }

            EffectRegistry.ID_COLOR_BALANCE -> {
                val rBal = evaluateSlider(effect, "Red Balance", frame, seconds, 0f) / 100f
                val gBal = evaluateSlider(effect, "Green Balance", frame, seconds, 0f) / 100f
                val bBal = evaluateSlider(effect, "Blue Balance", frame, seconds, 0f) / 100f
                val cm = ColorMatrix(floatArrayOf(
                    1f + rBal, 0f, 0f, 0f, 0f,
                    0f, 1f + gBal, 0f, 0f, 0f,
                    0f, 0f, 1f + bBal, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                applyColorMatrix(input, cm)
            }

            EffectRegistry.ID_GRAYSCALE -> {
                val mix = evaluateSlider(effect, "Mix", frame, seconds, 100f) / 100f
                val cm = ColorMatrix()
                cm.setSaturation(1f - mix)
                applyColorMatrix(input, cm)
            }

            EffectRegistry.ID_POSTERIZE -> {
                val levels = evaluateSlider(effect, "Levels", frame, seconds, 6f).toInt().coerceIn(2, 32)
                applyPosterize(input, levels)
            }

            EffectRegistry.ID_MOSAIC -> {
                val blockSize = evaluateSlider(effect, "Block Size", frame, seconds, 10f).toInt().coerceIn(2, 100)
                applyMosaic(input, blockSize)
            }

            EffectRegistry.ID_NOISE -> {
                val amount = evaluateSlider(effect, "Amount", frame, seconds, 15f) / 100f
                applyNoise(input, amount)
            }

            EffectRegistry.ID_VIGNETTE -> {
                val radius = evaluateSlider(effect, "Radius", frame, seconds, 60f) / 100f
                val darkness = evaluateSlider(effect, "Darkness", frame, seconds, 80f) / 100f
                applyVignette(input, radius, darkness)
            }

            EffectRegistry.ID_DROP_SHADOW -> {
                val shadowColor = evaluateColor(effect, "Shadow Color", 0xFF000000L)
                val opacity = evaluateSlider(effect, "Opacity", frame, seconds, 75f) / 100f
                val dist = evaluateSlider(effect, "Distance", frame, seconds, 15f)
                val angle = evaluateSlider(effect, "Direction", frame, seconds, 45f)
                val softness = evaluateSlider(effect, "Softness", frame, seconds, 10f)
                applyDropShadow(input, shadowColor, opacity, dist, angle, softness)
            }

            else -> {
                // Unsupported effects are rejected and return input unmodified; never silently become another effect
                android.util.Log.w("EffectProcessor", "Unsupported effect ID '${effect.id}' - returning input unmodified")
                input
            }
        }
    }

    private fun applyColorMatrix(input: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(input, 0f, 0f, paint)
        return out
    }

    fun applyTintPixels(pix: IntArray, blackColor: Long, whiteColor: Long, amount: Float): IntArray {
        val out = pix.copyOf()
        val bR = (blackColor ushr 16 and 0xff).toInt()
        val bG = (blackColor ushr 8 and 0xff).toInt()
        val bB = (blackColor and 0xff).toInt()

        val wR = (whiteColor ushr 16 and 0xff).toInt()
        val wG = (whiteColor ushr 8 and 0xff).toInt()
        val wB = (whiteColor and 0xff).toInt()

        for (i in out.indices) {
            val p = out[i]
            val a = (p ushr 24) and 0xff
            if (a == 0) continue

            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff

            // BT.709 Luminance
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f

            // Map luma: 0 -> blackColor, 1 -> whiteColor
            val tintedR = bR + ((wR - bR) * luma)
            val tintedG = bG + ((wG - bG) * luma)
            val tintedB = bB + ((wB - bB) * luma)

            // Blend with original pixel according to amount
            val finalR = (r * (1f - amount) + tintedR * amount).toInt().coerceIn(0, 255)
            val finalG = (g * (1f - amount) + tintedG * amount).toInt().coerceIn(0, 255)
            val finalB = (b * (1f - amount) + tintedB * amount).toInt().coerceIn(0, 255)

            out[i] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }
        return out
    }

    fun applyInvertPixels(pix: IntArray, invertRgb: Boolean, invertAlpha: Boolean): IntArray {
        val out = pix.copyOf()
        for (i in out.indices) {
            val p = out[i]
            val a = (p ushr 24) and 0xff
            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff

            val finalA = if (invertAlpha) 255 - a else a
            val finalR = if (invertRgb) 255 - r else r
            val finalG = if (invertRgb) 255 - g else g
            val finalB = if (invertRgb) 255 - b else b

            out[i] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }
        return out
    }

    fun applyBrightnessContrastPixels(pix: IntArray, brightness: Float, contrast: Float): IntArray {
        val out = pix.copyOf()
        val scale = ((contrast + 100f) / 100f).coerceAtLeast(0f)
        val translate = (brightness * 2.55f) + 128f * (1f - scale)

        for (i in out.indices) {
            val p = out[i]
            val a = (p ushr 24) and 0xff
            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff

            val finalR = (r * scale + translate).toInt().coerceIn(0, 255)
            val finalG = (g * scale + translate).toInt().coerceIn(0, 255)
            val finalB = (b * scale + translate).toInt().coerceIn(0, 255)

            out[i] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }
        return out
    }

    fun applySolidCompositePixels(pix: IntArray, solidColor: Long, opacity: Float): IntArray {
        val out = pix.copyOf()
        val solidA = ((solidColor ushr 24 and 0xff) * opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        val solidR = (solidColor ushr 16 and 0xff).toInt()
        val solidG = (solidColor ushr 8 and 0xff).toInt()
        val solidB = (solidColor and 0xff).toInt()

        for (i in out.indices) {
            val p = out[i]
            val fgA = (p ushr 24) and 0xff
            val fgR = (p ushr 16) and 0xff
            val fgG = (p ushr 8) and 0xff
            val fgB = p and 0xff

            // Source-Over compositing over solid background
            val alphaFactor = (255 - fgA).toFloat() / 255f
            val finalA = (fgA + solidA * alphaFactor).toInt().coerceIn(0, 255)
            val finalR = (fgR + (solidR * (solidA.toFloat() / 255f) * alphaFactor)).toInt().coerceIn(0, 255)
            val finalG = (fgG + (solidG * (solidA.toFloat() / 255f) * alphaFactor)).toInt().coerceIn(0, 255)
            val finalB = (fgB + (solidB * (solidA.toFloat() / 255f) * alphaFactor)).toInt().coerceIn(0, 255)

            out[i] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }
        return out
    }

    private fun applyTint(
        input: Bitmap,
        blackColor: Long,
        whiteColor: Long,
        amount: Float
    ): Bitmap {
        val w = input.width
        val h = input.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pix = IntArray(w * h)
        input.getPixels(pix, 0, w, 0, 0, w, h)
        val tinted = applyTintPixels(pix, blackColor, whiteColor, amount)
        out.setPixels(tinted, 0, w, 0, 0, w, h)
        return out
    }

    private fun evaluateSlider(
        effect: Effect,
        paramName: String,
        frame: Long,
        seconds: Float,
        default: Float
    ): Float {
        val param = effect.parameters.firstOrNull { it.name.equals(paramName, ignoreCase = true) }
        return when (param) {
            is EffectParameter.Slider -> CompositionEvaluator.evaluateFloatProperty(param.property, frame, seconds)
            is EffectParameter.Angle -> CompositionEvaluator.evaluateFloatProperty(param.property, frame, seconds)
            else -> default
        }
    }

    private fun evaluateCheckbox(
        effect: Effect,
        paramName: String,
        default: Boolean
    ): Boolean {
        val param = effect.parameters.firstOrNull { it.name.equals(paramName, ignoreCase = true) }
        return if (param is EffectParameter.Checkbox) param.value else default
    }

    private fun evaluateColor(
        effect: Effect,
        paramName: String,
        default: Long
    ): Long {
        val param = effect.parameters.firstOrNull { it.name.equals(paramName, ignoreCase = true) }
        return if (param is EffectParameter.ColorParam) param.property.defaultValue else default
    }

    /**
     * Isolates high-luminance pixels exceeding the threshold for bloom/glow passes.
     * Pixels below threshold are suppressed, while pixels above are linearly scaled.
     */
    fun isolateThresholdPixels(pixels: IntArray, thresholdNormalized: Float): IntArray {
        val result = pixels.copyOf()
        val threshold255 = (thresholdNormalized.coerceIn(0f, 1f) * 255f).toInt()

        for (i in result.indices) {
            val pix = result[i]
            val a = (pix ushr 24) and 0xff
            if (a == 0) {
                result[i] = 0
                continue
            }
            val r = (pix ushr 16) and 0xff
            val g = (pix ushr 8) and 0xff
            val b = pix and 0xff

            // ITU-R BT.709 Luminance
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt()
            if (luma <= threshold255) {
                result[i] = 0
            } else {
                val span = (255 - threshold255).coerceAtLeast(1)
                val factor = (luma - threshold255).toFloat() / span.toFloat()
                val glowR = (r * factor).toInt().coerceIn(0, 255)
                val glowG = (g * factor).toInt().coerceIn(0, 255)
                val glowB = (b * factor).toInt().coerceIn(0, 255)
                result[i] = (a shl 24) or (glowR shl 16) or (glowG shl 8) or glowB
            }
        }
        return result
    }

    fun isolateThreshold(input: Bitmap, thresholdNormalized: Float): Bitmap {
        val w = input.width
        val h = input.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        val processed = isolateThresholdPixels(pixels, thresholdNormalized)
        out.setPixels(processed, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Constructs an Euler hue rotation FloatArray in degrees using ITU-R BT.709 coefficients.
     */
    fun createHueRotationMatrixArray(angleDegrees: Float): FloatArray {
        val rad = Math.toRadians(angleDegrees.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()

        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f

        val a00 = lumR + cos * (1f - lumR) - sin * lumR
        val a01 = lumG - cos * lumG - sin * lumG
        val a02 = lumB - cos * lumB + sin * (1f - lumB)

        val a10 = lumR - cos * lumR + sin * 0.143f
        val a11 = lumG + cos * (1f - lumG) + sin * 0.140f
        val a12 = lumB - cos * lumB - sin * 0.283f

        val a20 = lumR - cos * lumR - sin * (1f - lumR)
        val a21 = lumG - cos * lumG + sin * lumG
        val a22 = lumB + cos * (1f - lumB) + sin * lumB

        return floatArrayOf(
            a00, a01, a02, 0f, 0f,
            a10, a11, a12, 0f, 0f,
            a20, a21, a22, 0f, 0f,
            0f,  0f,  0f,  1f, 0f
        )
    }

    /**
     * Constructs an Euler hue rotation ColorMatrix in degrees using ITU-R BT.709 coefficients.
     */
    fun createHueRotationMatrix(angleDegrees: Float): ColorMatrix {
        return ColorMatrix(createHueRotationMatrixArray(angleDegrees))
    }

    fun applyDirectionalBlur(input: Bitmap, dirAngle: Float, length: Float): Bitmap {
        val len = length.toInt().coerceIn(1, 100)
        if (len <= 1) return input
        val w = input.width
        val h = input.height
        val pix = IntArray(w * h)
        input.getPixels(pix, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        val rad = Math.toRadians(dirAngle.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        val halfLen = len / 2

        for (y in 0 until h) {
            for (x in 0 until w) {
                var sumA = 0f
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var count = 0

                for (step in -halfLen..halfLen) {
                    val sx = (x + step * cos).toInt().coerceIn(0, w - 1)
                    val sy = (y + step * sin).toInt().coerceIn(0, h - 1)
                    val p = pix[sy * w + sx]
                    val a = (p ushr 24) and 0xff
                    sumA += a
                    sumR += (p ushr 16) and 0xff
                    sumG += (p ushr 8) and 0xff
                    sumB += p and 0xff
                    count++
                }

                val finalA = (sumA / count).toInt().coerceIn(0, 255)
                val finalR = (sumR / count).toInt().coerceIn(0, 255)
                val finalG = (sumG / count).toInt().coerceIn(0, 255)
                val finalB = (sumB / count).toInt().coerceIn(0, 255)
                out[y * w + x] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }
        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(out, 0, w, 0, 0, w, h)
        return outBitmap
    }

    fun applySharpenPixels(pix: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val out = pix.copyOf()
        val k = (amount / 100f).coerceIn(0f, 3f)
        if (k <= 0f || w < 3 || h < 3) return out

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val center = pix[idx]
                val a = (center ushr 24) and 0xff
                if (a == 0) continue

                val up = pix[(y - 1) * w + x]
                val down = pix[(y + 1) * w + x]
                val left = pix[y * w + (x - 1)]
                val right = pix[y * w + (x + 1)]

                val cR = (center ushr 16) and 0xff
                val cG = (center ushr 8) and 0xff
                val cB = center and 0xff

                val laplacianR = (4 * cR - ((up ushr 16 and 0xff) + (down ushr 16 and 0xff) + (left ushr 16 and 0xff) + (right ushr 16 and 0xff)))
                val laplacianG = (4 * cG - ((up ushr 8 and 0xff) + (down ushr 8 and 0xff) + (left ushr 8 and 0xff) + (right ushr 8 and 0xff)))
                val laplacianB = (4 * cB - ((up and 0xff) + (down and 0xff) + (left and 0xff) + (right and 0xff)))

                val finalR = (cR + k * laplacianR).toInt().coerceIn(0, 255)
                val finalG = (cG + k * laplacianG).toInt().coerceIn(0, 255)
                val finalB = (cB + k * laplacianB).toInt().coerceIn(0, 255)

                out[idx] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }
        return out
    }

    fun applySharpen(input: Bitmap, amount: Float): Bitmap {
        val w = input.width
        val h = input.height
        val pix = IntArray(w * h)
        input.getPixels(pix, 0, w, 0, 0, w, h)
        val sharpened = applySharpenPixels(pix, w, h, amount)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(sharpened, 0, w, 0, 0, w, h)
        return out
    }

    fun applyLevelsPixels(pix: IntArray, inB: Float, inW: Float, gamma: Float, outB: Float, outW: Float): IntArray {
        val lut = IntArray(256)
        val inRange = (inW - inB).coerceAtLeast(1f)
        val outRange = outW - outB
        val invGamma = 1f / gamma.coerceIn(0.1f, 10f)

        for (i in 0..255) {
            val normalized = ((i - inB) / inRange).coerceIn(0f, 1f)
            val gammaCorrected = Math.pow(normalized.toDouble(), invGamma.toDouble()).toFloat()
            lut[i] = (outB + gammaCorrected * outRange).toInt().coerceIn(0, 255)
        }

        val out = pix.copyOf()
        for (i in out.indices) {
            val p = out[i]
            val a = (p ushr 24) and 0xff
            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff
            out[i] = (a shl 24) or (lut[r] shl 16) or (lut[g] shl 8) or lut[b]
        }
        return out
    }

    fun applyLevels(input: Bitmap, inB: Float, inW: Float, gamma: Float, outB: Float, outW: Float): Bitmap {
        val w = input.width
        val h = input.height
        val pix = IntArray(w * h)
        input.getPixels(pix, 0, w, 0, 0, w, h)
        val processed = applyLevelsPixels(pix, inB, inW, gamma, outB, outW)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(processed, 0, w, 0, 0, w, h)
        return out
    }

    fun applyGammaPixels(pix: IntArray, gamma: Float): IntArray {
        return applyLevelsPixels(pix, 0f, 255f, gamma, 0f, 255f)
    }

    fun applyGamma(input: Bitmap, gamma: Float): Bitmap {
        return applyLevels(input, 0f, 255f, gamma, 0f, 255f)
    }

    fun applyPosterizePixels(pix: IntArray, levels: Int): IntArray {
        val lvl = levels.coerceIn(2, 32)
        val lut = IntArray(256)
        val step = 255f / (lvl - 1)
        for (i in 0..255) {
            val bucket = kotlin.math.round((i / 255f) * (lvl - 1))
            lut[i] = (bucket * step).toInt().coerceIn(0, 255)
        }
        val out = pix.copyOf()
        for (i in out.indices) {
            val p = out[i]
            val a = (p ushr 24) and 0xff
            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff
            out[i] = (a shl 24) or (lut[r] shl 16) or (lut[g] shl 8) or lut[b]
        }
        return out
    }

    fun applyPosterize(input: Bitmap, levels: Int): Bitmap {
        val w = input.width
        val h = input.height
        val pix = IntArray(w * h)
        input.getPixels(pix, 0, w, 0, 0, w, h)
        val processed = applyPosterizePixels(pix, levels)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(processed, 0, w, 0, 0, w, h)
        return out
    }

    fun applyMosaicPixels(pix: IntArray, w: Int, h: Int, blockSize: Int): IntArray {
        val b = blockSize.coerceIn(1, 200)
        if (b <= 1) return pix.copyOf()
        val out = IntArray(w * h)

        for (by in 0 until h step b) {
            val blockH = kotlin.math.min(b, h - by)
            for (bx in 0 until w step b) {
                val blockW = kotlin.math.min(b, w - bx)

                var sumA = 0L
                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                var count = 0

                for (y in by until (by + blockH)) {
                    val rowOffset = y * w
                    for (x in bx until (bx + blockW)) {
                        val p = pix[rowOffset + x]
                        sumA += (p ushr 24) and 0xff
                        sumR += (p ushr 16) and 0xff
                        sumG += (p ushr 8) and 0xff
                        sumB += p and 0xff
                        count++
                    }
                }

                val avgA = (sumA / count).toInt().coerceIn(0, 255)
                val avgR = (sumR / count).toInt().coerceIn(0, 255)
                val avgG = (sumG / count).toInt().coerceIn(0, 255)
                val avgB = (sumB / count).toInt().coerceIn(0, 255)
                val blockPixel = (avgA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB

                for (y in by until (by + blockH)) {
                    val rowOffset = y * w
                    for (x in bx until (bx + blockW)) {
                        out[rowOffset + x] = blockPixel
                    }
                }
            }
        }
        return out
    }

    fun applyMosaic(input: Bitmap, blockSize: Int): Bitmap {
        val w = input.width
        val h = input.height
        val pix = IntArray(w * h)
        input.getPixels(pix, 0, w, 0, 0, w, h)
        val processed = applyMosaicPixels(pix, w, h, blockSize)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(processed, 0, w, 0, 0, w, h)
        return out
    }

    fun applyNoisePixels(pix: IntArray, amount: Float, seed: Long = 1337L): IntArray {
        val out = pix.copyOf()
        val rng = java.util.Random(seed)
        val maxOffset = (amount.coerceIn(0f, 1f) * 255f).toInt()
        if (maxOffset <= 0) return out

        for (i in out.indices) {
            val p = out[i]
            val a = (p ushr 24) and 0xff
            if (a == 0) continue

            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff

            val n = rng.nextInt(maxOffset * 2 + 1) - maxOffset
            val finalR = (r + n).coerceIn(0, 255)
            val finalG = (g + n).coerceIn(0, 255)
            val finalB = (b + n).coerceIn(0, 255)

            out[i] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }
        return out
    }

    fun applyNoise(input: Bitmap, amount: Float): Bitmap {
        val w = input.width
        val h = input.height
        val pix = IntArray(w * h)
        input.getPixels(pix, 0, w, 0, 0, w, h)
        val processed = applyNoisePixels(pix, amount)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(processed, 0, w, 0, 0, w, h)
        return out
    }

    fun applyVignettePixels(pix: IntArray, w: Int, h: Int, radius: Float, darkness: Float): IntArray {
        val out = pix.copyOf()
        val cx = w / 2f
        val cy = h / 2f
        val maxDist = kotlin.math.hypot(cx.toDouble(), cy.toDouble()).toFloat().coerceAtLeast(1f)
        val innerDist = (radius.coerceIn(0f, 1f) * maxDist)
        val falloffSpan = (maxDist - innerDist).coerceAtLeast(1f)
        val dark = darkness.coerceIn(0f, 1f)

        for (y in 0 until h) {
            val dy = y - cy
            val rowOffset = y * w
            for (x in 0 until w) {
                val dx = x - cx
                val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > innerDist) {
                    val p = out[rowOffset + x]
                    val a = (p ushr 24) and 0xff
                    if (a == 0) continue

                    val factor = 1f - (dark * ((dist - innerDist) / falloffSpan).coerceIn(0f, 1f))
                    val r = (((p ushr 16 and 0xff)) * factor).toInt().coerceIn(0, 255)
                    val g = (((p ushr 8 and 0xff)) * factor).toInt().coerceIn(0, 255)
                    val b = (((p and 0xff)) * factor).toInt().coerceIn(0, 255)

                    out[rowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return out
    }

    fun applyVignette(input: Bitmap, radius: Float, darkness: Float): Bitmap {
        val w = input.width
        val h = input.height
        val pix = IntArray(w * h)
        input.getPixels(pix, 0, w, 0, 0, w, h)
        val processed = applyVignettePixels(pix, w, h, radius, darkness)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(processed, 0, w, 0, 0, w, h)
        return out
    }

    fun applyDropShadow(
        input: Bitmap,
        shadowColor: Long,
        opacity: Float,
        dist: Float,
        angle: Float,
        softness: Float
    ): Bitmap {
        val w = input.width
        val h = input.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val rad = Math.toRadians(angle.toDouble())
        val dx = (dist * kotlin.math.cos(rad)).toFloat()
        val dy = (dist * kotlin.math.sin(rad)).toFloat()

        val shadowPix = IntArray(w * h)
        input.getPixels(shadowPix, 0, w, 0, 0, w, h)
        val sR = (shadowColor ushr 16 and 0xff).toInt()
        val sG = (shadowColor ushr 8 and 0xff).toInt()
        val sB = (shadowColor and 0xff).toInt()
        val effOpacity = opacity.coerceIn(0f, 1f)

        for (i in shadowPix.indices) {
            val a = (shadowPix[i] ushr 24) and 0xff
            val finalA = (a * effOpacity).toInt().coerceIn(0, 255)
            shadowPix[i] = (finalA shl 24) or (sR shl 16) or (sG shl 8) or sB
        }

        val rawShadow = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        rawShadow.setPixels(shadowPix, 0, w, 0, 0, w, h)

        val soft = softness.toInt().coerceIn(0, 50)
        val shadowToDraw = if (soft > 0) {
            CompositorPipeline.fastBlur(rawShadow, soft)
        } else {
            rawShadow
        }

        canvas.drawBitmap(shadowToDraw, dx, dy, null)
        canvas.drawBitmap(input, 0f, 0f, null)
        return out
    }
}

