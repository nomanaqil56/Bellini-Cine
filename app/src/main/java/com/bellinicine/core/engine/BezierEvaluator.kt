package com.bellinicine.core.engine

import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.KeyframeInterpolation
import com.bellinicine.core.model.Vec2
import com.bellinicine.core.model.Vec3
import kotlin.math.abs

/**
 * High-performance Bezier curve solver for keyframe interpolation,
 * matching After Effects cubic spline velocity and influence math.
 */
object BezierEvaluator {

    /**
     * Evaluates a float value between two keyframes at frame [t].
     */
    fun evaluateFloat(
        kf1: Keyframe<Float>,
        kf2: Keyframe<Float>,
        currentFrame: Long
    ): Float {
        if (currentFrame <= kf1.frame) return kf1.value
        if (currentFrame >= kf2.frame) return kf2.value

        val totalFrames = (kf2.frame - kf1.frame).toFloat()
        if (totalFrames <= 0f) return kf1.value

        val progress = (currentFrame - kf1.frame) / totalFrames

        return when (kf1.interpolation) {
            KeyframeInterpolation.HOLD -> kf1.value
            KeyframeInterpolation.LINEAR -> {
                kf1.value + (kf2.value - kf1.value) * progress
            }
            KeyframeInterpolation.BEZIER,
            KeyframeInterpolation.AUTO_BEZIER,
            KeyframeInterpolation.CONTINUOUS_BEZIER -> {
                val deltaVal = kf2.value - kf1.value
                val p1x = (kf1.outTangent.influence / 100f).coerceIn(0.001f, 0.999f)
                val p2x = 1f - (kf2.inTangent.influence / 100f).coerceIn(0.001f, 0.999f)

                // Temporal velocity-to-displacement curve calculation
                val p1y = if (abs(deltaVal) > 1e-4f) {
                    val speed = when (kf1.interpolation) {
                        KeyframeInterpolation.AUTO_BEZIER -> deltaVal / totalFrames * 0.5f
                        else -> kf1.outTangent.speed
                    }
                    (speed * totalFrames * p1x / deltaVal).coerceIn(-3f, 4f)
                } else 0f

                val p2y = if (abs(deltaVal) > 1e-4f) {
                    val speed = when (kf2.interpolation) {
                        KeyframeInterpolation.AUTO_BEZIER -> deltaVal / totalFrames * 0.5f
                        else -> kf2.inTangent.speed
                    }
                    1f - (speed * totalFrames * (1f - p2x) / deltaVal).coerceIn(-3f, 4f)
                } else 1f

                // Temporal curve easing
                val t = solveCubicBezierT(progress, p1x, p2x)
                val easedProgress = cubicBezier1D(0f, p1y, p2y, 1f, t)

                kf1.value + deltaVal * easedProgress
            }
        }
    }

    /**
     * Evaluates a 3D vector between two keyframes at frame [t].
     */
    fun evaluateVec3(
        kf1: Keyframe<Vec3>,
        kf2: Keyframe<Vec3>,
        currentFrame: Long
    ): Vec3 {
        val x = evaluateFloat(
            Keyframe(kf1.frame, kf1.value.x, kf1.interpolation, kf1.inTangent, kf1.outTangent),
            Keyframe(kf2.frame, kf2.value.x, kf2.interpolation, kf2.inTangent, kf2.outTangent),
            currentFrame
        )
        val y = evaluateFloat(
            Keyframe(kf1.frame, kf1.value.y, kf1.interpolation, kf1.inTangent, kf1.outTangent),
            Keyframe(kf2.frame, kf2.value.y, kf2.interpolation, kf2.inTangent, kf2.outTangent),
            currentFrame
        )
        val z = evaluateFloat(
            Keyframe(kf1.frame, kf1.value.z, kf1.interpolation, kf1.inTangent, kf1.outTangent),
            Keyframe(kf2.frame, kf2.value.z, kf2.interpolation, kf2.inTangent, kf2.outTangent),
            currentFrame
        )
        return Vec3(x, y, z)
    }

    /**
     * 1D cubic Bezier calculation: B(t) = (1-t)^3*P0 + 3(1-t)^2*t*P1 + 3(1-t)*t^2*P2 + t^3*P3
     */
    fun cubicBezier1D(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val u = 1f - t
        val tt = t * t
        val uu = u * u
        val uuu = uu * u
        val ttt = tt * t

        return uuu * p0 + 3f * uu * t * p1 + 3f * u * tt * p2 + ttt * p3
    }

    /**
     * Newton-Raphson numerical solver to find parameter [t] given Bezier X coordinate.
     */
    fun solveCubicBezierT(xTarget: Float, x1: Float, x2: Float, epsilon: Float = 1e-4f): Float {
        var t = xTarget
        for (i in 0 until 8) {
            val currentX = cubicBezier1D(0f, x1, x2, 1f, t) - xTarget
            if (abs(currentX) < epsilon) return t

            // Derivative dB/dt
            val u = 1f - t
            val dX = 3f * u * u * x1 + 6f * u * t * (x2 - x1) + 3f * t * t * (1f - x2)
            if (abs(dX) < 1e-6f) break

            t -= currentX / dX
            t = t.coerceIn(0f, 1f)
        }
        return t
    }
}
