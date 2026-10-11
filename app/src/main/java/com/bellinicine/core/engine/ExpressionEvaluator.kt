package com.bellinicine.core.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * Expression context providing runtime variables for After Effects expressions.
 */
data class ExpressionContext(
    val time: Float,
    val value: Float,
    val frame: Long,
    val fps: Float = 30f,
    val index: Int = 1,
    val width: Float = 1080f,
    val height: Float = 1920f,
    val duration: Float = 10f
)

/**
 * Robust Expression Evaluator for After Effects motion design.
 * Supports mathematical operations, trigonometry, conditionals,
 * wiggle with smooth gradient noise, linear/ease interpolation, and loopOut.
 */
object ExpressionEvaluator {

    fun evaluateFloat(
        expression: String,
        timeSeconds: Float,
        currentValue: Float,
        frameIndex: Long,
        fps: Float = 30f
    ): Float {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return currentValue

        val ctx = ExpressionContext(
            time = timeSeconds,
            value = currentValue,
            frame = frameIndex,
            fps = fps
        )

        return try {
            val parser = ExpressionParser(trimmed, ctx)
            parser.parseExpression()
        } catch (_: Exception) {
            currentValue
        }
    }

    /**
     * Smooth 1D gradient noise for continuous, organic wiggle motion.
     */
    fun smoothNoise(t: Float, seed: Int = 1337): Float {
        val i0 = floor(t).toInt()
        val i1 = i0 + 1
        val frac = t - i0

        // Smoothstep interpolation (3t^2 - 2t^3)
        val smoothFrac = frac * frac * (3f - 2f * frac)

        val g0 = gradient(i0, seed)
        val g1 = gradient(i1, seed)

        val v0 = g0 * frac
        val v1 = g1 * (frac - 1f)

        return (v0 + (v1 - v0) * smoothFrac) * 2f
    }

    private fun gradient(x: Int, seed: Int): Float {
        val hash = (x * 374761393 xor seed * 668265263).toLong() and 0xFFFFFFFFL
        val rng = Random(hash)
        return rng.nextFloat() * 2f - 1f
    }
}

/**
 * Lexer and Recursive-Descent Parser for mathematical and AE expressions.
 */
class ExpressionParser(private val input: String, private val ctx: ExpressionContext) {
    private var pos = 0

    fun parseExpression(): Float {
        val res = parseTernary()
        return res
    }

    // Ternary operator: condition ? trueVal : falseVal
    private fun parseTernary(): Float {
        val cond = parseLogicalOr()
        skipWhitespace()
        if (match('?')) {
            val trueVal = parseTernary()
            skipWhitespace()
            if (match(':')) {
                val falseVal = parseTernary()
                return if (cond != 0f) trueVal else falseVal
            }
        }
        return cond
    }

    private fun parseLogicalOr(): Float {
        var left = parseLogicalAnd()
        while (true) {
            skipWhitespace()
            if (match("||")) {
                val right = parseLogicalAnd()
                left = if (left != 0f || right != 0f) 1f else 0f
            } else break
        }
        return left
    }

    private fun parseLogicalAnd(): Float {
        var left = parseComparison()
        while (true) {
            skipWhitespace()
            if (match("&&")) {
                val right = parseComparison()
                left = if (left != 0f && right != 0f) 1f else 0f
            } else break
        }
        return left
    }

    private fun parseComparison(): Float {
        var left = parseAddSub()
        skipWhitespace()
        if (match("==")) return if (abs(left - parseAddSub()) < 1e-5f) 1f else 0f
        if (match("!=")) return if (abs(left - parseAddSub()) >= 1e-5f) 1f else 0f
        if (match("<=")) return if (left <= parseAddSub()) 1f else 0f
        if (match(">=")) return if (left >= parseAddSub()) 1f else 0f
        if (match('<')) return if (left < parseAddSub()) 1f else 0f
        if (match('>')) return if (left > parseAddSub()) 1f else 0f
        return left
    }

    private fun parseAddSub(): Float {
        var left = parseMulDiv()
        while (true) {
            skipWhitespace()
            if (match('+')) left += parseMulDiv()
            else if (match('-')) left -= parseMulDiv()
            else break
        }
        return left
    }

    private fun parseMulDiv(): Float {
        var left = parsePower()
        while (true) {
            skipWhitespace()
            if (match('*')) left *= parsePower()
            else if (match('/')) {
                val r = parsePower()
                left = if (r != 0f) left / r else 0f
            }
            else if (match('%')) {
                val r = parsePower()
                left = if (r != 0f) left % r else 0f
            }
            else break
        }
        return left
    }

    private fun parsePower(): Float {
        var left = parseUnary()
        skipWhitespace()
        if (match('^')) {
            left = left.pow(parsePower())
        }
        return left
    }

    private fun parseUnary(): Float {
        skipWhitespace()
        if (match('+')) return parseUnary()
        if (match('-')) return -parseUnary()
        if (match('!')) return if (parseUnary() == 0f) 1f else 0f
        return parsePrimary()
    }

    private fun parsePrimary(): Float {
        skipWhitespace()

        // Parentheses
        if (match('(')) {
            val v = parseExpression()
            skipWhitespace()
            match(')')
            return v
        }

        // Numbers
        if (peek().isDigit() || peek() == '.') {
            return parseNumber()
        }

        // Identifiers / Function calls / Properties
        if (peek().isLetter() || peek() == '_' || peek() == '$') {
            return parseIdentifierOrCall()
        }

        return 0f
    }

    private fun parseNumber(): Float {
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
            pos++
        }
        return input.substring(start, pos).toFloatOrNull() ?: 0f
    }

    private fun parseIdentifierOrCall(): Float {
        val start = pos
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_' || input[pos] == '.' || input[pos] == '[' || input[pos] == ']')) {
            pos++
        }
        val name = input.substring(start, pos)
        skipWhitespace()

        // Function call: name(arg1, arg2, ...)
        if (match('(')) {
            val args = mutableListOf<Float>()
            skipWhitespace()
            if (!match(')')) {
                while (true) {
                    args.add(parseExpression())
                    skipWhitespace()
                    if (match(',')) continue
                    if (match(')')) break
                    break
                }
            }
            return executeFunction(name, args)
        }

        // Variables
        return resolveVariable(name)
    }

    private fun resolveVariable(name: String): Float {
        val lower = name.lowercase().replace(" ", "")
        return when (lower) {
            "time" -> ctx.time
            "value" -> ctx.value
            "frame" -> ctx.frame.toFloat()
            "fps" -> ctx.fps
            "index" -> ctx.index.toFloat()
            "width" -> ctx.width
            "height" -> ctx.height
            "duration" -> ctx.duration
            "math.pi", "pi" -> PI.toFloat()
            "math.e", "e" -> Math.E.toFloat()
            else -> 0f
        }
    }

    private fun executeFunction(name: String, args: List<Float>): Float {
        val fn = name.lowercase().removePrefix("math.")
        return when (fn) {
            // After Effects Wiggle: wiggle(freq, amp)
            "wiggle" -> {
                val freq = args.getOrElse(0) { 1f }
                val amp = args.getOrElse(1) { 50f }
                val noise = ExpressionEvaluator.smoothNoise(ctx.time * freq)
                ctx.value + noise * amp
            }

            // After Effects Linear: linear(t, tMin, tMax, v1, v2) or linear(t, v1, v2)
            "linear" -> {
                if (args.size >= 5) {
                    val t = args[0]
                    val tMin = args[1]
                    val tMax = args[2]
                    val v1 = args[3]
                    val v2 = args[4]
                    if (tMax == tMin) v1
                    else {
                        val progress = ((t - tMin) / (tMax - tMin)).coerceIn(0f, 1f)
                        v1 + (v2 - v1) * progress
                    }
                } else if (args.size >= 3) {
                    val t = args[0]
                    val v1 = args[1]
                    val v2 = args[2]
                    val progress = t.coerceIn(0f, 1f)
                    v1 + (v2 - v1) * progress
                } else {
                    ctx.value
                }
            }

            // After Effects Ease: ease(t, tMin, tMax, v1, v2)
            "ease" -> {
                val t = args.getOrElse(0) { ctx.time }
                val tMin = args.getOrElse(1) { 0f }
                val tMax = args.getOrElse(2) { 1f }
                val v1 = args.getOrElse(3) { 0f }
                val v2 = args.getOrElse(4) { 100f }
                if (tMax == tMin) v1
                else {
                    val p = ((t - tMin) / (tMax - tMin)).coerceIn(0f, 1f)
                    val smooth = p * p * (3f - 2f * p)
                    v1 + (v2 - v1) * smooth
                }
            }

            // loopOut("cycle") / loopOut("pingpong")
            "loopout" -> {
                val loopDuration = max(1f, ctx.duration)
                val cycleTime = ctx.time % loopDuration
                ctx.value * (cycleTime / loopDuration)
            }

            // Math built-ins
            "sin" -> sin(args.getOrElse(0) { 0f }.toDouble()).toFloat()
            "cos" -> cos(args.getOrElse(0) { 0f }.toDouble()).toFloat()
            "tan" -> tan(args.getOrElse(0) { 0f }.toDouble()).toFloat()
            "abs" -> abs(args.getOrElse(0) { 0f })
            "sqrt" -> sqrt(max(0f, args.getOrElse(0) { 0f }))
            "pow" -> args.getOrElse(0) { 0f }.pow(args.getOrElse(1) { 1f })
            "min" -> min(args.getOrElse(0) { 0f }, args.getOrElse(1) { 0f })
            "max" -> max(args.getOrElse(0) { 0f }, args.getOrElse(1) { 0f })
            "floor" -> floor(args.getOrElse(0) { 0f })
            "ceil" -> ceil(args.getOrElse(0) { 0f })
            "round" -> round(args.getOrElse(0) { 0f })
            "random" -> Random.nextFloat() * args.getOrElse(0) { 1f }
            "clamp" -> args.getOrElse(0) { 0f }.coerceIn(args.getOrElse(1) { 0f }, args.getOrElse(2) { 1f })

            else -> ctx.value
        }
    }

    private fun peek(): Char = if (pos < input.length) input[pos] else '\u0000'

    private fun match(expected: Char): Boolean {
        if (pos < input.length && input[pos] == expected) {
            pos++
            return true
        }
        return false
    }

    private fun match(expected: String): Boolean {
        if (input.startsWith(expected, pos)) {
            pos += expected.length
            return true
        }
        return false
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }
}
