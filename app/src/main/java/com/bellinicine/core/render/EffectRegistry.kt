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
import com.bellinicine.core.model.AnimatedProperty
import com.bellinicine.core.model.Effect
import com.bellinicine.core.model.EffectCategory
import com.bellinicine.core.model.EffectParameter
import kotlin.math.max
import kotlin.math.min

/**
 * Definition and metadata for an available visual effect.
 */
data class EffectDefinition(
    val effectId: String,
    val displayName: String,
    val category: EffectCategory,
    val defaultParameters: List<EffectParameter>,
    val isSupported: Boolean = true
)

/**
 * Central registry of typed After Effects-style visual effects.
 */
object EffectRegistry {

    const val ID_FAST_BLUR = "com.bellinicine.effects.fast_blur"
    const val ID_BOX_BLUR = "com.bellinicine.effects.box_blur"
    const val ID_DIRECTIONAL_BLUR = "com.bellinicine.effects.directional_blur"
    const val ID_SHARPEN = "com.bellinicine.effects.sharpen"
    const val ID_GLOW = "com.bellinicine.effects.glow"
    const val ID_BRIGHTNESS_CONTRAST = "com.bellinicine.effects.brightness_contrast"
    const val ID_EXPOSURE = "com.bellinicine.effects.exposure"
    const val ID_LEVELS = "com.bellinicine.effects.levels"
    const val ID_GAMMA = "com.bellinicine.effects.gamma"
    const val ID_TINT = "com.bellinicine.effects.tint"
    const val ID_INVERT = "com.bellinicine.effects.invert"
    const val ID_HUE_SATURATION = "com.bellinicine.effects.hue_saturation"
    const val ID_COLOR_BALANCE = "com.bellinicine.effects.color_balance"
    const val ID_GRAYSCALE = "com.bellinicine.effects.grayscale"
    const val ID_POSTERIZE = "com.bellinicine.effects.posterize"
    const val ID_MOSAIC = "com.bellinicine.effects.mosaic"
    const val ID_NOISE = "com.bellinicine.effects.noise"
    const val ID_VIGNETTE = "com.bellinicine.effects.vignette"
    const val ID_DROP_SHADOW = "com.bellinicine.effects.drop_shadow"
    const val ID_SOLID_COMPOSITE = "com.bellinicine.effects.solid_composite"

    private val registry = mutableMapOf<String, EffectDefinition>()

    init {
        register(
            EffectDefinition(
                effectId = ID_FAST_BLUR,
                displayName = "Fast Gaussian Blur",
                category = EffectCategory.BLUR_AND_SHARPEN,
                defaultParameters = listOf(
                    EffectParameter.Slider("Radius", AnimatedProperty("Radius", 15f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_BOX_BLUR,
                displayName = "Box Blur",
                category = EffectCategory.BLUR_AND_SHARPEN,
                defaultParameters = listOf(
                    EffectParameter.Slider("Radius", AnimatedProperty("Radius", 10f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_DIRECTIONAL_BLUR,
                displayName = "Directional Blur",
                category = EffectCategory.BLUR_AND_SHARPEN,
                defaultParameters = listOf(
                    EffectParameter.Angle("Direction", AnimatedProperty("Direction", 0f)),
                    EffectParameter.Slider("Length", AnimatedProperty("Length", 20f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_SHARPEN,
                displayName = "Sharpen",
                category = EffectCategory.BLUR_AND_SHARPEN,
                defaultParameters = listOf(
                    EffectParameter.Slider("Sharpen Amount", AnimatedProperty("Sharpen Amount", 25f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_GLOW,
                displayName = "Glow & Bloom",
                category = EffectCategory.STYLIZE,
                defaultParameters = listOf(
                    EffectParameter.Slider("Threshold", AnimatedProperty("Threshold", 50f), min = 0f, max = 100f),
                    EffectParameter.Slider("Radius", AnimatedProperty("Radius", 20f), min = 0f, max = 100f),
                    EffectParameter.Slider("Intensity", AnimatedProperty("Intensity", 80f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_BRIGHTNESS_CONTRAST,
                displayName = "Brightness & Contrast",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Slider("Brightness", AnimatedProperty("Brightness", 0f), min = -100f, max = 100f),
                    EffectParameter.Slider("Contrast", AnimatedProperty("Contrast", 0f), min = -100f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_EXPOSURE,
                displayName = "Exposure",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Slider("Exposure", AnimatedProperty("Exposure", 0f), min = -5f, max = 5f),
                    EffectParameter.Slider("Offset", AnimatedProperty("Offset", 0f), min = -1f, max = 1f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_LEVELS,
                displayName = "Levels",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Slider("Input Black", AnimatedProperty("Input Black", 0f), min = 0f, max = 255f),
                    EffectParameter.Slider("Input White", AnimatedProperty("Input White", 255f), min = 0f, max = 255f),
                    EffectParameter.Slider("Gamma", AnimatedProperty("Gamma", 1f), min = 0.1f, max = 5f),
                    EffectParameter.Slider("Output Black", AnimatedProperty("Output Black", 0f), min = 0f, max = 255f),
                    EffectParameter.Slider("Output White", AnimatedProperty("Output White", 255f), min = 0f, max = 255f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_GAMMA,
                displayName = "Gamma Correction",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Slider("Gamma", AnimatedProperty("Gamma", 1.0f), min = 0.1f, max = 4.0f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_TINT,
                displayName = "Tint",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.ColorParam("Map Black To", AnimatedProperty("Map Black To", 0xFF000000L)),
                    EffectParameter.ColorParam("Map White To", AnimatedProperty("Map White To", 0xFFFFFFFFL)),
                    EffectParameter.Slider("Amount to Tint", AnimatedProperty("Amount to Tint", 100f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_INVERT,
                displayName = "Invert",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Checkbox("Invert RGB", true),
                    EffectParameter.Checkbox("Invert Alpha", false)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_HUE_SATURATION,
                displayName = "Hue / Saturation",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Angle("Master Hue", AnimatedProperty("Master Hue", 0f)),
                    EffectParameter.Slider("Master Saturation", AnimatedProperty("Master Saturation", 0f), min = -100f, max = 100f),
                    EffectParameter.Slider("Master Lightness", AnimatedProperty("Master Lightness", 0f), min = -100f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_COLOR_BALANCE,
                displayName = "Color Balance",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Slider("Red Balance", AnimatedProperty("Red Balance", 0f), min = -100f, max = 100f),
                    EffectParameter.Slider("Green Balance", AnimatedProperty("Green Balance", 0f), min = -100f, max = 100f),
                    EffectParameter.Slider("Blue Balance", AnimatedProperty("Blue Balance", 0f), min = -100f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_GRAYSCALE,
                displayName = "Grayscale",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Slider("Mix", AnimatedProperty("Mix", 100f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_POSTERIZE,
                displayName = "Posterize",
                category = EffectCategory.COLOR_CORRECTION,
                defaultParameters = listOf(
                    EffectParameter.Slider("Levels", AnimatedProperty("Levels", 6f), min = 2f, max = 32f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_MOSAIC,
                displayName = "Mosaic / Pixelate",
                category = EffectCategory.STYLIZE,
                defaultParameters = listOf(
                    EffectParameter.Slider("Block Size", AnimatedProperty("Block Size", 10f), min = 2f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_NOISE,
                displayName = "Noise",
                category = EffectCategory.STYLIZE,
                defaultParameters = listOf(
                    EffectParameter.Slider("Amount", AnimatedProperty("Amount", 15f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_VIGNETTE,
                displayName = "Vignette",
                category = EffectCategory.STYLIZE,
                defaultParameters = listOf(
                    EffectParameter.Slider("Radius", AnimatedProperty("Radius", 60f), min = 0f, max = 100f),
                    EffectParameter.Slider("Darkness", AnimatedProperty("Darkness", 80f), min = 0f, max = 100f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_DROP_SHADOW,
                displayName = "Drop Shadow",
                category = EffectCategory.PERSPECTIVE,
                defaultParameters = listOf(
                    EffectParameter.ColorParam("Shadow Color", AnimatedProperty("Shadow Color", 0xFF000000L)),
                    EffectParameter.Slider("Opacity", AnimatedProperty("Opacity", 75f), min = 0f, max = 100f),
                    EffectParameter.Slider("Distance", AnimatedProperty("Distance", 15f), min = 0f, max = 100f),
                    EffectParameter.Angle("Direction", AnimatedProperty("Direction", 45f)),
                    EffectParameter.Slider("Softness", AnimatedProperty("Softness", 10f), min = 0f, max = 50f)
                )
            )
        )
        register(
            EffectDefinition(
                effectId = ID_SOLID_COMPOSITE,
                displayName = "Solid Composite",
                category = EffectCategory.GENERATE,
                defaultParameters = listOf(
                    EffectParameter.ColorParam("Color", AnimatedProperty("Color", 0xFF000000L)),
                    EffectParameter.Slider("Opacity", AnimatedProperty("Opacity", 100f), min = 0f, max = 100f)
                )
            )
        )
    }

    fun register(definition: EffectDefinition) {
        registry[definition.effectId] = definition
    }

    fun get(effectId: String): EffectDefinition? = registry[effectId]

    fun getAll(): List<EffectDefinition> = registry.values.toList()

    fun createEffect(effectId: String, name: String? = null): Effect? {
        val def = registry[effectId] ?: return null
        if (!EffectProcessor.hasProcessorFor(effectId)) return null
        return Effect(
            id = effectId,
            name = name ?: def.displayName,
            category = def.category,
            enabled = true,
            parameters = def.defaultParameters
        )
    }

    fun isSupported(effectId: String): Boolean = registry.containsKey(effectId) && EffectProcessor.hasProcessorFor(effectId)
}
