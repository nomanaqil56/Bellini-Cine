package com.bellinicine.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Vec2(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(factor: Float) = Vec2(x * factor, y * factor)
}

@Serializable
data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(factor: Float) = Vec3(x * factor, y * factor, z * factor)
}

@Serializable
data class Transform(
    val anchorPoint: AnimatedProperty<Vec3> = AnimatedProperty("Anchor Point", Vec3(0f, 0f, 0f)),
    val position: AnimatedProperty<Vec3> = AnimatedProperty("Position", Vec3(0f, 0f, 0f)),
    val scale: AnimatedProperty<Vec3> = AnimatedProperty("Scale", Vec3(100f, 100f, 100f)),
    val rotation: AnimatedProperty<Float> = AnimatedProperty("Rotation", 0f), // 2D rotation (Z axis)
    val rotationX: AnimatedProperty<Float> = AnimatedProperty("X Rotation", 0f),
    val rotationY: AnimatedProperty<Float> = AnimatedProperty("Y Rotation", 0f),
    val rotationZ: AnimatedProperty<Float> = AnimatedProperty("Z Rotation", 0f),
    val orientation: AnimatedProperty<Vec3> = AnimatedProperty("Orientation", Vec3(0f, 0f, 0f)),
    val opacity: AnimatedProperty<Float> = AnimatedProperty("Opacity", 100f) // 0..100%
)
