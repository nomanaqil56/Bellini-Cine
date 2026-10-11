package com.bellinicine.core.engine

import com.bellinicine.core.model.Vec3
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
    val duration: Float = 10f,
    val vectorValues: FloatArray? = null,
    val keyframeSpan: Float = 0f,
    val keyframeStart: Float = 0f,
    val seedOffset: Int = 0
)

/**
 * Robust Expression Evaluator for After Effects motion design.
 * Supports mathematical operations, trigonometry, conditionals,
 * multi-dimensional vector expressions [x, y, z], array indexing value[0],
 * organic smooth noise wiggle, linear/ease interpolation, and loopOut("cycle" / "pingpong").
 */
object ExpressionEvaluator {

    fun evaluateFloat(
        expression: String,
        timeSeconds: Float,
        currentValue: Float,
        frameIndex: Long,
        fps: Float = 30f,
        vectorValues: FloatArray? = null,
        keyframeSpan: Float = 0f,
        keyframeStart: Float = 0f,
        seedOffset: Int = 0
    ): Float {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return currentValue

        val ctx = ExpressionContext(
            time = timeSeconds,
            value = currentValue,
            frame = frameIndex,
            fps = fps,
            vectorValues = vectorValues,
            keyframeSpan = keyframeSpan,
            keyframeStart = keyframeStart,
            seedOffset = seedOffset
        )

        return try {
            val parser = ExpressionParser(trimmed, ctx)
            parser.parseExpression()
        } catch (_: Exception) {
            currentValue
        }
    }

    fun evaluateVec3(
        expression: String,
        timeSeconds: Float,
        currentValue: Vec3,
        frameIndex: Long,
        fps: Float = 30f,
        keyframeSpan: Float = 0f,
        keyframeStart: Float = 0f
    ): Vec3 {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return currentValue

        val vecArray = floatArrayOf(currentValue.x, currentValue.y, currentValue.z)

        // 1. Vector literal expression e.g. [value[0] + 50, value[1] * 2, 0]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            val parts = splitTopLevel(inner, ',')
            val x = if (parts.isNotEmpty()) evaluateFloat(parts[0], timeSeconds, currentValue.x, frameIndex, fps, vecArray, keyframeSpan, keyframeStart, 0) else currentValue.x
            val y = if (parts.size >= 2) evaluateFloat(parts[1], timeSeconds, currentValue.y, frameIndex, fps, vecArray, keyframeSpan, keyframeStart, 1000) else currentValue.y
            val z = if (parts.size >= 3) evaluateFloat(parts[2], timeSeconds, currentValue.z, frameIndex, fps, vecArray, keyframeSpan, keyframeStart, 2000) else currentValue.z
            return Vec3(x, y, z)
        }

        // 2. Vector wiggle e.g. wiggle(5, 30)
        if (trimmed.lowercase().startsWith("wiggle(")) {
            val x = evaluateFloat(trimmed, timeSeconds, currentValue.x, frameIndex, fps, vecArray, keyframeSpan, keyframeStart, 0)
            val y = evaluateFloat(trimmed, timeSeconds, currentValue.y, frameIndex, fps, vecArray, keyframeSpan, keyframeStart, 1000)
            val z = evaluateFloat(trimmed, timeSeconds, currentValue.z, frameIndex, fps, vecArray, keyframeSpan, keyframeStart, 2000)
            return Vec3(x, y, z)
        }

        // 3. Uniform scalar expression
        val uniform = evaluateFloat(trimmed, timeSeconds, currentValue.x, frameIndex, fps, vecArray, keyframeSpan, keyframeStart)
        return Vec3(uniform, uniform, currentValue.z)
    }

    private fun splitTopLevel(str: String, delimiter: Char): List<String> {
        val list = mutableListOf<String>()
        var depthParen = 0
        var depthBracket = 0
        var start = 0
        for (i in str.indices) {
            when (str[i]) {
                '(' -> depthParen++
                ')' -> if (depthParen > 0) depthParen--
                '[' -> depthBracket++
                ']' -> if (depthBracket > 0) depthBracket--
                delimiter -> {
                    if (depthParen == 0 && depthBracket == 0) {
                        list.add(str.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
        }
        if (start < str.length) {
            list.add(str.substring(start).trim())
        }
        return list
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
    private var recursionDepth = 0

    fun parseExpression(): Float {
        if (recursionDepth++ > 100) return ctx.value
        val res = parseTernary()
        recursionDepth--
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
        var left = parseUnary()
        while (true) {
            skipWhitespace()
            if (match('*')) left *= parseUnary()
            else if (match('/')) {
                val right = parseUnary()
                left = if (right != 0f) left / right else 0f
            } else if (match('%')) {
                val right = parseUnary()
                left = if (right != 0f) left % right else 0f
            } else break
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
        if (match('(')) {
            val expr = parseExpression()
            skipWhitespace()
            match(')')
            return expr
        }

        if (peek().isDigit() || peek() == '.') {
            return parseNumber()
        }

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
            var strArg = ""
            skipWhitespace()
            if (!match(')')) {
                while (true) {
                    skipWhitespace()
                    if (peek() == '"' || peek() == '\'') {
                        val quote = peek()
                        pos++
                        val sStart = pos
                        while (pos < input.length && peek() != quote) pos++
                        strArg = input.substring(sStart, pos)
                        if (pos < input.length) pos++ // skip quote
                        args.add(0f)
                    } else {
                        args.add(parseExpression())
                    }
                    skipWhitespace()
                    if (match(',')) continue
                    if (match(')')) break
                    break
                }
            }
            return executeFunction(name, args, strArg)
        }

        // Variables and array indexing
        return resolveVariable(name)
    }

    private fun resolveVariable(name: String): Float {
        val lower = name.lowercase().replace(" ", "")

        // Vector indexing e.g. value[0], value[1], value[2]
        if (lower.startsWith("value[") && lower.endsWith("]")) {
            val idx = lower.removePrefix("value[").removeSuffix("]").toIntOrNull() ?: 0
            return ctx.vectorValues?.getOrNull(idx) ?: ctx.value
        }

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

    private fun executeFunction(name: String, args: List<Float>, strArg: String): Float {
        val fn = name.lowercase().removePrefix("math.")
        return when (fn) {
            // After Effects Wiggle: wiggle(freq, amp)
            "wiggle" -> {
                val freq = args.getOrElse(0) { 1f }
                val amp = args.getOrElse(1) { 50f }
                val seed = 1337 + ctx.seedOffset
                val timeOffset = ctx.seedOffset * 19.73f
                val noise = ExpressionEvaluator.smoothNoise((ctx.time + timeOffset) * freq, seed)
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
                val mode = if (strArg.isNotBlank()) strArg.lowercase() else "cycle"
                val loopDuration = if (ctx.keyframeSpan > 0f) ctx.keyframeSpan else max(1f, ctx.duration)
                val startTime = ctx.keyframeStart
                val delta = ctx.time - (startTime + loopDuration)

                if (delta <= 0f) {
                    ctx.value
                } else {
                    when (mode) {
                        "pingpong" -> {
                            val cycles = (delta / loopDuration).toInt()
                            val rem = delta % loopDuration
                            val factor = if (cycles % 2 == 0) (1f - (rem / loopDuration)) else (rem / loopDuration)
                            ctx.value * factor
                        }
                        else -> { // cycle
                            val rem = delta % loopDuration
                            val factor = rem / loopDuration
                            ctx.value * factor
                        }
                    }
                }
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
