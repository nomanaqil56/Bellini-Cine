package com.bellinicine.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Composition(
    val id: String,
    val name: String = "Comp 1",
    val width: Int = 1080,
    val height: Int = 1920, // Default to 9:16 mobile portrait / vertical cinema
    val pixelAspectRatio: Float = 1.0f,
    val frameRate: Float = 30.0f,
    val durationFrames: Long = 300L, // 10 seconds at 30fps
    val backgroundColor: Long = 0xFF000000,
    val layers: List<Layer> = emptyList(),
    val workAreaStartFrame: Long = 0L,
    val workAreaEndFrame: Long = 300L
) {
    val durationSeconds: Float
        get() = durationFrames / frameRate

    fun frameToSeconds(frame: Long): Float = frame / frameRate
    fun secondsToFrame(seconds: Float): Long = (seconds * frameRate).toLong()
}

@Serializable
data class FootageAsset(
    val id: String,
    val name: String,
    val uri: String,
    val width: Int,
    val height: Int,
    val durationSeconds: Float,
    val frameRate: Float,
    val mediaType: String // "video", "image", "audio"
)

@Serializable
data class Project(
    val id: String,
    val name: String = "Untitled Project",
    val compositions: List<Composition> = emptyList(),
    val assets: List<FootageAsset> = emptyList(),
    val activeCompositionId: String? = null,
    val version: Int = 1
)
