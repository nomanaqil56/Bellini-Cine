package com.bellinicine.test

import com.bellinicine.core.engine.ExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExpressionEvaluatorTest {

    @Test
    fun testBasicArithmeticOperations() {
        val result = ExpressionEvaluator.evaluateFloat(
            expression = "10 + 20 * 2",
            timeSeconds = 0f,
            currentValue = 0f,
            frameIndex = 0L
        )
        assertEquals(50f, result, 0.001f)
    }

    @Test
    fun testVariablesTimeAndValue() {
        val result = ExpressionEvaluator.evaluateFloat(
            expression = "time * 2 + value",
            timeSeconds = 3.5f,
            currentValue = 10f,
            frameIndex = 105L
        )
        // 3.5 * 2 + 10 = 17
        assertEquals(17f, result, 0.001f)
    }

    @Test
    fun testTrigonometricFunctions() {
        val sinResult = ExpressionEvaluator.evaluateFloat(
            expression = "sin(0)",
            timeSeconds = 0f,
            currentValue = 0f,
            frameIndex = 0L
        )
        assertEquals(0f, sinResult, 0.001f)

        val cosResult = ExpressionEvaluator.evaluateFloat(
            expression = "cos(0)",
            timeSeconds = 0f,
            currentValue = 0f,
            frameIndex = 0L
        )
        assertEquals(1f, cosResult, 0.001f)
    }

    @Test
    fun testLinearInterpolation() {
        // 5-parameter linear: linear(t, tMin, tMax, v1, v2)
        val mid = ExpressionEvaluator.evaluateFloat(
            expression = "linear(time, 0, 10, 100, 200)",
            timeSeconds = 5f,
            currentValue = 0f,
            frameIndex = 150L
        )
        assertEquals(150f, mid, 0.001f)

        // Clamping past max
        val clamped = ExpressionEvaluator.evaluateFloat(
            expression = "linear(time, 0, 10, 100, 200)",
            timeSeconds = 15f,
            currentValue = 0f,
            frameIndex = 450L
        )
        assertEquals(200f, clamped, 0.001f)
    }

    @Test
    fun testEaseInterpolation() {
        // ease(t, tMin, tMax, v1, v2)
        val easeMid = ExpressionEvaluator.evaluateFloat(
            expression = "ease(time, 0, 1, 0, 100)",
            timeSeconds = 0.5f,
            currentValue = 0f,
            frameIndex = 15L
        )
        // S-curve at 0.5 is exactly 0.5 * 100 = 50
        assertEquals(50f, easeMid, 0.01f)
    }

    @Test
    fun testWiggleReturnsWithinBounds() {
        val wiggled = ExpressionEvaluator.evaluateFloat(
            expression = "wiggle(2, 50)",
            timeSeconds = 1.0f,
            currentValue = 100f,
            frameIndex = 30L
        )
        // Value should be within 100 - 100 and 100 + 100
        assertTrue("Wiggle value should stay near amplitude: $wiggled", abs(wiggled - 100f) <= 100f)
    }

    @Test
    fun testTernaryConditional() {
        val trueBranch = ExpressionEvaluator.evaluateFloat(
            expression = "time > 2 ? 100 : 50",
            timeSeconds = 3f,
            currentValue = 0f,
            frameIndex = 90L
        )
        assertEquals(100f, trueBranch, 0.001f)

        val falseBranch = ExpressionEvaluator.evaluateFloat(
            expression = "time > 2 ? 100 : 50",
            timeSeconds = 1f,
            currentValue = 0f,
            frameIndex = 30L
        )
        assertEquals(50f, falseBranch, 0.001f)
    }
}
