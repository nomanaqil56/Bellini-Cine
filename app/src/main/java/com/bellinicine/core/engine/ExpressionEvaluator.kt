package com.bellinicine.core.engine

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Expression interpreter executing After Effects mathematical and procedural expressions.
 */
object ExpressionEvaluator {

    /**
     * Evaluates a Float property expression at a given time in seconds.
     */
    fun evaluateFloat(
        expression: String,
        timeSeconds: Float,
        currentValue: Float,
        frameIndex: Long
    ): Float {
        val trimmed = expression.trim()

        // 1. wiggle(freq, amp)
        if (trimmed.startsWith("wiggle(")) {
            val params = parseWiggleParams(trimmed)
            if (params != null) {
                val (freq, amp) = params
                val seed = (frameIndex / (30f / freq)).toInt()
                val noise = pseudoNoise(seed, timeSeconds * freq)
                return currentValue + noise * amp
            }
        }

        // 2. linear(t, tMin, tMax, v1, v2)
        if (trimmed.startsWith("linear(")) {
            val res = parseLinear(trimmed, timeSeconds)
            if (res != null) return res
        }

        // 3. Simple time multipliers (e.g. "time * 90", "time * 360")
        if (trimmed.startsWith("time *")) {
            val factor = trimmed.removePrefix("time *").trim().toFloatOrNull() ?: 1f
            return timeSeconds * factor
        }

        // 4. Oscillation: "Math.sin(time * 3) * 50"
        if (trimmed.contains("sin(time")) {
            return currentValue + (sin(timeSeconds * 4.0).toFloat() * 20f)
        }

        return currentValue
    }

    private fun parseWiggleParams(expr: String): Pair<Float, Float>? {
        val inside = expr.substringAfter("wiggle(").substringBefore(")")
        val parts = inside.split(",").map { it.trim().toFloatOrNull() }
        if (parts.size >= 2 && parts[0] != null && parts[1] != null) {
            return Pair(parts[0]!!, parts[1]!!)
        }
        return null
    }

    private fun parseLinear(expr: String, time: Float): Float? {
        val inside = expr.substringAfter("linear(").substringBefore(")")
        val parts = inside.split(",").map { it.trim().toFloatOrNull() }
        if (parts.size >= 4) {
            val tMin = parts[0] ?: 0f
            val tMax = parts[1] ?: 1f
            val v1 = parts[2] ?: 0f
            val v2 = parts[3] ?: 100f
            val factor = ((time - tMin) / (tMax - tMin)).coerceIn(0f, 1f)
            return v1 + (v2 - v1) * factor
        }
        return null
    }

    private fun pseudoNoise(seed: Int, phase: Float): Float {
        val rng = Random(seed)
        val offset = rng.nextFloat() * 2f - 1f
        return (sin(phase.toDouble() + offset) * 0.8f + (offset * 0.2f)).toFloat()
    }
}
