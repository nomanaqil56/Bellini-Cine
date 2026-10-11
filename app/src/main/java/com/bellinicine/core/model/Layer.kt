package com.bellinicine.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class LayerType {
    FOOTAGE,
    SOLID,
    TEXT,
    SHAPE,
    NULL_OBJECT,
    ADJUSTMENT,
    CAMERA,
    LIGHT,
    PRECOMP
}

@Serializable
sealed class LayerData {
    @Serializable
    data class Solid(val color: Long, val width: Int, val height: Int) : LayerData()

    @Serializable
    data class Footage(val assetId: String, val mediaUri: String, val naturalWidth: Int, val naturalHeight: Int) : LayerData()

    @Serializable
    data class Text(
        val text: String,
        val fontSize: Float = 48f,
        val fontColor: Long = 0xFFFFFFFF,
        val fontFamily: String = "sans-serif",
        val tracking: Float = 0f,
        val leading: Float = 0f
    ) : LayerData()

    @Serializable
    data class Shape(
        val shapeType: String = "Rectangle", // "Rectangle", "Ellipse", "Star", "Polygon"
        val width: Float = 400f,
        val height: Float = 400f,
        val cornerRadius: Float = 0f,
        val points: Int = 5,
        val innerRadiusRatio: Float = 0.45f,
        val fillColor: Long = 0xFF7C4DFF,
        val strokeColor: Long = 0xFFFFFFFF,
        val strokeWidth: Float = 4f
    ) : LayerData()

    @Serializable
    object NullObject : LayerData()

    @Serializable
    object Adjustment : LayerData()

    @Serializable
    data class Camera(val focalLength: Float = 50f, val zoom: Float = 1000f) : LayerData()

    @Serializable
    data class Light(val lightType: String = "Point", val lightColor: Long = 0xFFFFFFFF, val intensity: Float = 100f) : LayerData()

    @Serializable
    data class Precomp(val compositionId: String) : LayerData()
}

@Serializable
data class Layer(
    val id: String,
    val name: String,
    val type: LayerType,
    val data: LayerData,
    val inPointFrame: Long = 0L,
    val outPointFrame: Long = 300L,
    val startTimeFrame: Long = 0L,
    val timeStretch: Float = 100f, // percentage
    val isVisible: Boolean = true,
    val isAudioMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isSolo: Boolean = false,
    val isShy: Boolean = false,
    val is3D: Boolean = false,
    val motionBlur: Boolean = false,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val trackMatteType: TrackMatteType = TrackMatteType.NONE,
    val parentLayerId: String? = null,
    val transform: Transform = Transform(),
    val masks: List<Mask> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val labelColorIndex: Int = 0
) {
    fun isActiveAtFrame(frame: Long): Boolean {
        return isVisible && frame >= inPointFrame && frame < outPointFrame
    }
}
