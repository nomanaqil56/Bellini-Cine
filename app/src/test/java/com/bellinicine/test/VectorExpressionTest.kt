package com.bellinicine.test

import com.bellinicine.core.engine.ExpressionEvaluator
import com.bellinicine.core.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for multi-dimensional vector expressions, array indexing, and loopOut (Finding H).
 */
class VectorExpressionTest {

    @Test
    fun testVectorLiteralExpression() {
        val initial = Vec3(100f, 200f, 0f)
        val expr = "[value[0] + 50, value[1] * 2, 99]"

        val result = ExpressionEvaluator.evaluateVec3(expr, 1f, initial, 30L)

        assertEquals(150f, result.x, 0.001f)
        assertEquals(400f, result.y, 0.001f)
        assertEquals(99f, result.z, 0.001f)
    }

    @Test
    fun testVectorWiggleDistinctAxes() {
        val initial = Vec3(500f, 500f, 0f)
        val expr = "wiggle(5, 50)"

        val res1 = ExpressionEvaluator.evaluateVec3(expr, 1.25f, initial, 37L)
        val res2 = ExpressionEvaluator.evaluateVec3(expr, 1.25f, initial, 37L)

        // Deterministic: same time gives same result
        assertEquals(res1.x, res2.x, 0.001f)
        assertEquals(res1.y, res2.y, 0.001f)

        // Multi-axis: x and y wiggle with different seeds, so they should not be identical!
        assertNotEquals("X and Y wiggle should use distinct seeds", res1.x, res1.y)
    }

    @Test
    fun testLoopOutCycleAndPingPong() {
        // Test loopOut("cycle")
        val valBefore = ExpressionEvaluator.evaluateFloat("loopOut(\"cycle\")", 1f, 100f, 30L, keyframeSpan = 2f, keyframeStart = 0f)
        assertEquals("Before loop duration ends, value is unchanged", 100f, valBefore, 0.001f)

        // After loop duration (at t=3.0, delta=1.0 into 2.0s loop)
        val valAfterCycle = ExpressionEvaluator.evaluateFloat("loopOut(\"cycle\")", 3f, 100f, 90L, keyframeSpan = 2f, keyframeStart = 0f)
        // delta=1.0, factor=0.5 -> 50.0
        assertEquals(50f, valAfterCycle, 0.001f)

        // After loop duration with pingpong
        val valAfterPingPong = ExpressionEvaluator.evaluateFloat("loopOut(\"pingpong\")", 3f, 100f, 90L, keyframeSpan = 2f, keyframeStart = 0f)
        assertEquals(50f, valAfterPingPong, 0.001f)
    }
}
