package com.bellinicine.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MaskMode {
    NONE,
    ADD,
    SUBTRACT,
    INTERSECT,
    LIGHTEN,
    DARKEN,
    DIFFERENCE
}

@Serializable
data class MaskVertex(
    val point: Vec2,
    val inTangent: Vec2 = Vec2(0f, 0f),
    val outTangent: Vec2 = Vec2(0f, 0f)
)

@Serializable
data class Mask(
    val id: String,
    val name: String = "Mask 1",
    val mode: MaskMode = MaskMode.ADD,
    val inverted: Boolean = false,
    val opacity: AnimatedProperty<Float> = AnimatedProperty("Mask Opacity", 100f),
    val feather: AnimatedProperty<Float> = AnimatedProperty("Mask Feather", 0f),
    val expansion: AnimatedProperty<Float> = AnimatedProperty("Mask Expansion", 0f),
    val vertices: List<MaskVertex> = emptyList(),
    val isClosed: Boolean = true
)

@Serializable
enum class TrackMatteType {
    NONE,
    ALPHA,
    ALPHA_INVERTED,
    LUMA,
    LUMA_INVERTED
}
