package com.bellinicine.test

import com.bellinicine.core.engine.BezierEvaluator
import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.KeyframeInterpolation
import com.bellinicine.core.model.KeyframeTangent
import com.bellinicine.core.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test

class BezierEvaluatorTest {

    @Test
    fun testLinearKeyframeInterpolation() {
        val kf1 = Keyframe(0L, 0f, KeyframeInterpolation.LINEAR)
        val kf2 = Keyframe(100L, 200f, KeyframeInterpolation.LINEAR)

        assertEquals(0f, BezierEvaluator.evaluateFloat(kf1, kf2, 0L), 0.001f)
        assertEquals(100f, BezierEvaluator.evaluateFloat(kf1, kf2, 50L), 0.001f)
        assertEquals(200f, BezierEvaluator.evaluateFloat(kf1, kf2, 100L), 0.001f)
    }

    @Test
    fun testHoldKeyframeInterpolation() {
        val kf1 = Keyframe(0L, 100f, KeyframeInterpolation.HOLD)
        val kf2 = Keyframe(50L, 250f, KeyframeInterpolation.LINEAR)

        assertEquals(100f, BezierEvaluator.evaluateFloat(kf1, kf2, 0L), 0.001f)
        assertEquals(100f, BezierEvaluator.evaluateFloat(kf1, kf2, 25L), 0.001f)
        assertEquals(100f, BezierEvaluator.evaluateFloat(kf1, kf2, 49L), 0.001f)
        assertEquals(250f, BezierEvaluator.evaluateFloat(kf1, kf2, 50L), 0.001f)
    }

    @Test
    fun testBezierKeyframeInterpolation() {
        val kf1 = Keyframe(
            frame = 0L,
            value = 0f,
            interpolation = KeyframeInterpolation.BEZIER,
            outTangent = KeyframeTangent(speed = 0f, influence = 33.33f)
        )
        val kf2 = Keyframe(
            frame = 60L,
            value = 100f,
            interpolation = KeyframeInterpolation.BEZIER,
            inTangent = KeyframeTangent(speed = 0f, influence = 33.33f)
        )

        assertEquals(0f, BezierEvaluator.evaluateFloat(kf1, kf2, 0L), 0.01f)
        assertEquals(100f, BezierEvaluator.evaluateFloat(kf1, kf2, 60L), 0.01f)

        // Midpoint should be around 50f for symmetric ease
        val mid = BezierEvaluator.evaluateFloat(kf1, kf2, 30L)
        assertEquals(50f, mid, 2.0f)
    }

    @Test
    fun testVec3Interpolation() {
        val kf1 = Keyframe(0L, Vec3(0f, 100f, 0f), KeyframeInterpolation.LINEAR)
        val kf2 = Keyframe(50L, Vec3(100f, 200f, 50f), KeyframeInterpolation.LINEAR)

        val mid = BezierEvaluator.evaluateVec3(kf1, kf2, 25L)
        assertEquals(50f, mid.x, 0.001f)
        assertEquals(150f, mid.y, 0.001f)
        assertEquals(25f, mid.z, 0.001f)
    }
}
