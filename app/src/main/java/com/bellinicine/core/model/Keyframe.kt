package com.bellinicine.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class KeyframeInterpolation {
    LINEAR,
    BEZIER,
    AUTO_BEZIER,
    CONTINUOUS_BEZIER,
    HOLD
}

@Serializable
data class KeyframeTangent(
    val speed: Float = 0f,
    val influence: Float = 33.33f // Percentage 0..100
)

@Serializable
data class Keyframe<T>(
    val frame: Long,
    val value: T,
    val interpolation: KeyframeInterpolation = KeyframeInterpolation.BEZIER,
    val inTangent: KeyframeTangent = KeyframeTangent(),
    val outTangent: KeyframeTangent = KeyframeTangent()
)

@Serializable
data class AnimatedProperty<T>(
    val name: String,
    val defaultValue: T,
    val keyframes: List<Keyframe<T>> = emptyList(),
    val expression: String? = null,
    val isKeyframed: Boolean = false
) {
    fun hasKeyframes(): Boolean = isKeyframed && keyframes.isNotEmpty()
}
