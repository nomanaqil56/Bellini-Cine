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
    const val ID_GLOW = "com.bellinicine.effects.glow"
    const val ID_BRIGHTNESS_CONTRAST = "com.bellinicine.effects.brightness_contrast"
    const val ID_TINT = "com.bellinicine.effects.tint"
    const val ID_INVERT = "com.bellinicine.effects.invert"
    const val ID_HUE_SATURATION = "com.bellinicine.effects.hue_saturation"
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
