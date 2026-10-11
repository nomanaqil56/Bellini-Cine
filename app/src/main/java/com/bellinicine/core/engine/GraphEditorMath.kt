package com.bellinicine.core.engine

import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.Vec2
import kotlin.math.abs

data class GraphPoint(
    val frame: Long,
    val value: Float,
    val speed: Float // units per second
)

data class KeyframeHandle(
    val frame: Long,
    val value: Float,
    val inHandle: Vec2, // offset (dFrame, dValue)
    val outHandle: Vec2,
    val speed: Float
)

/**
 * Mathematical utilities for the Graph Editor (Speed Graph and Value Graph).
 */
object GraphEditorMath {

    /**
     * Samples the value and speed graph between [startFrame] and [endFrame] at [stepFrames] intervals.
     */
    fun sampleGraph(
        keyframes: List<Keyframe<Float>>,
        fps: Float,
        startFrame: Long,
        endFrame: Long,
        stepFrames: Int = 1
    ): List<GraphPoint> {
        if (keyframes.isEmpty()) return emptyList()

        val points = mutableListOf<GraphPoint>()
        val sorted = keyframes.sortedBy { it.frame }
        val deltaSec = (stepFrames / fps).coerceAtLeast(0.001f)

        var lastVal: Float? = null

        for (frame in startFrame..endFrame step stepFrames.toLong()) {
            val valAtT = sampleValueAtFrame(sorted, frame)
            val speed = if (lastVal != null) {
                abs(valAtT - lastVal) / deltaSec
            } else {
                0f
            }
            lastVal = valAtT
            points.add(GraphPoint(frame, valAtT, speed))
        }

        return points
    }

    private fun sampleValueAtFrame(kfs: List<Keyframe<Float>>, frame: Long): Float {
        val first = kfs.first()
        val last = kfs.last()

        if (frame <= first.frame) return first.value
        if (frame >= last.frame) return last.value

        val idx = kfs.indexOfLast { it.frame <= frame }
        val k1 = kfs[idx]
        val k2 = kfs[idx + 1]

        return BezierEvaluator.evaluateFloat(k1, k2, frame)
    }
}
