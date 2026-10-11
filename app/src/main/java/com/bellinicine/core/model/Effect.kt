package com.bellinicine.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EffectCategory {
    BLUR_AND_SHARPEN,
    COLOR_CORRECTION,
    DISTORT,
    GENERATE,
    TRANSITION,
    STYLIZE,
    SIMULATION,
    PERSPECTIVE
}

@Serializable
sealed class EffectParameter {
    abstract val name: String

    @Serializable
    data class Slider(
        override val name: String,
        val property: AnimatedProperty<Float>,
        val min: Float = 0f,
        val max: Float = 100f
    ) : EffectParameter()

    @Serializable
    data class Angle(
        override val name: String,
        val property: AnimatedProperty<Float>
    ) : EffectParameter()

    @Serializable
    data class ColorParam(
        override val name: String,
        val property: AnimatedProperty<Long> // ARGB
    ) : EffectParameter()

    @Serializable
    data class Point(
        override val name: String,
        val property: AnimatedProperty<Vec2>
    ) : EffectParameter()

    @Serializable
    data class Checkbox(
        override val name: String,
        val value: Boolean
    ) : EffectParameter()
}

@Serializable
data class Effect(
    val id: String,
    val name: String,
    val category: EffectCategory,
    val enabled: Boolean = true,
    val parameters: List<EffectParameter> = emptyList()
)
