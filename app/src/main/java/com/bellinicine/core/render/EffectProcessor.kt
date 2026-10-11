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
            EffectRegistry.ID_GLOW,
            EffectRegistry.ID_BRIGHTNESS_CONTRAST,
            EffectRegistry.ID_INVERT,
            EffectRegistry.ID_HUE_SATURATION,
            EffectRegistry.ID_TINT,
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

        val bR = (blackColor ushr 16 and 0xff).toInt()
        val bG = (blackColor ushr 8 and 0xff).toInt()
        val bB = (blackColor and 0xff).toInt()

        val wR = (whiteColor ushr 16 and 0xff).toInt()
        val wG = (whiteColor ushr 8 and 0xff).toInt()
        val wB = (whiteColor and 0xff).toInt()

        for (i in 0 until (w * h)) {
            val p = pix[i]
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

            pix[i] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }

        out.setPixels(pix, 0, w, 0, 0, w, h)
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
}

