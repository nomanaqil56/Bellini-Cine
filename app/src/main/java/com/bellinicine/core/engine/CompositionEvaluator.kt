package com.bellinicine.core.engine

import com.bellinicine.core.model.AnimatedProperty
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.Vec3
import kotlin.math.cos
import kotlin.math.sin

data class EvaluatedTransform(
    val anchorPoint: Vec3,
    val position: Vec3,
    val scale: Vec3,
    val rotationZ: Float,
    val opacity: Float // 0..100%
)

data class EvaluatedLayer(
    val layer: Layer,
    val transform: EvaluatedTransform,
    val worldMatrix: FloatArray // 4x4 matrix
)

/**
 * Evaluates the full composition state at a given frame, resolving keyframes,
 * expressions, and hierarchical parent-child transforms into world coordinate matrices.
 */
object CompositionEvaluator {

    fun evaluateComposition(composition: Composition, frame: Long): List<EvaluatedLayer> {
        val seconds = composition.frameToSeconds(frame)
        val hasSoloLayers = composition.layers.any { it.isSolo }

        // Filter active layers according to visibility, solo, and in/out points
        val activeLayers = composition.layers.filter { layer ->
            val soloMatch = !hasSoloLayers || layer.isSolo
            layer.isActiveAtFrame(frame) && soloMatch
        }

        val layerMap = composition.layers.associateBy { it.id }
        val evaluatedTransforms = mutableMapOf<String, EvaluatedTransform>()
        val worldMatrices = mutableMapOf<String, FloatArray>()

        // 1. Evaluate local transforms for all layers
        for (layer in composition.layers) {
            val anchor = evaluateVec3Property(layer.transform.anchorPoint, frame, seconds)
            val pos = evaluateVec3Property(layer.transform.position, frame, seconds)
            val scale = evaluateVec3Property(layer.transform.scale, frame, seconds)
            val rot = evaluateFloatProperty(layer.transform.rotation, frame, seconds)
            val opac = evaluateFloatProperty(layer.transform.opacity, frame, seconds).coerceIn(0f, 100f)

            evaluatedTransforms[layer.id] = EvaluatedTransform(
                anchorPoint = anchor,
                position = pos,
                scale = scale,
                rotationZ = rot,
                opacity = opac
            )
        }

        // 2. Cascade parent transforms into 4x4 world matrices
        fun computeWorldMatrix(layerId: String): FloatArray {
            worldMatrices[layerId]?.let { return it }

            val layer = layerMap[layerId] ?: return createIdentityMatrix()
            val eval = evaluatedTransforms[layerId] ?: return createIdentityMatrix()

            val localMatrix = createTransformMatrix(eval)

            val finalMatrix = if (layer.parentLayerId != null && layer.parentLayerId != layerId) {
                val parentMat = computeWorldMatrix(layer.parentLayerId)
                multiplyMatrices(parentMat, localMatrix)
            } else {
                localMatrix
            }

            worldMatrices[layerId] = finalMatrix
            return finalMatrix
        }

        for (layer in composition.layers) {
            computeWorldMatrix(layer.id)
        }

        // Return evaluated active layers in composition stacking order (bottom to top)
        return activeLayers.map { layer ->
            EvaluatedLayer(
                layer = layer,
                transform = evaluatedTransforms[layer.id]!!,
                worldMatrix = worldMatrices[layer.id]!!
            )
        }
    }

    fun evaluateFloatProperty(prop: AnimatedProperty<Float>, frame: Long, seconds: Float = 0f): Float {
        var value = if (prop.hasKeyframes()) {
            val kfs = prop.keyframes.sortedBy { it.frame }
            val first = kfs.first()
            val last = kfs.last()

            if (frame <= first.frame) first.value
            else if (frame >= last.frame) last.value
            else {
                val idx = kfs.indexOfLast { it.frame <= frame }
                val k1 = kfs[idx]
                val k2 = kfs[idx + 1]
                BezierEvaluator.evaluateFloat(k1, k2, frame)
            }
        } else {
            prop.defaultValue
        }

        if (!prop.expression.isNullOrBlank()) {
            value = ExpressionEvaluator.evaluateFloat(prop.expression, seconds, value, frame)
        }

        return value
    }

    fun evaluateVec3Property(prop: AnimatedProperty<Vec3>, frame: Long, seconds: Float = 0f): Vec3 {
        var value = if (prop.hasKeyframes()) {
            val kfs = prop.keyframes.sortedBy { it.frame }
            val first = kfs.first()
            val last = kfs.last()

            if (frame <= first.frame) first.value
            else if (frame >= last.frame) last.value
            else {
                val idx = kfs.indexOfLast { it.frame <= frame }
                val k1 = kfs[idx]
                val k2 = kfs[idx + 1]
                BezierEvaluator.evaluateVec3(k1, k2, frame)
            }
        } else {
            prop.defaultValue
        }

        if (!prop.expression.isNullOrBlank()) {
            val x = ExpressionEvaluator.evaluateFloat(prop.expression, seconds, value.x, frame)
            val y = ExpressionEvaluator.evaluateFloat(prop.expression, seconds, value.y, frame)
            value = Vec3(x, y, value.z)
        }

        return value
    }

    fun createIdentityMatrix(): FloatArray {
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun createTransformMatrix(eval: EvaluatedTransform): FloatArray {
        // Translation * Rotation * Scale * -AnchorPoint
        val rad = Math.toRadians(eval.rotationZ.toDouble()).toFloat()
        val cos = cos(rad)
        val sin = sin(rad)
        val sx = eval.scale.x / 100f
        val sy = eval.scale.y / 100f

        val ax = eval.anchorPoint.x
        val ay = eval.anchorPoint.y
        val px = eval.position.x
        val py = eval.position.y

        val m00 = cos * sx
        val m01 = -sin * sy
        val m10 = sin * sx
        val m11 = cos * sy

        val tx = px - (m00 * ax + m01 * ay)
        val ty = py - (m10 * ax + m11 * ay)

        return floatArrayOf(
            m00, m10, 0f, 0f,
            m01, m11, 0f, 0f,
            0f,  0f,  1f, 0f,
            tx,  ty,  0f, 1f
        )
    }

    private fun multiplyMatrices(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[k * 4 + row] * b[col * 4 + k]
                }
                result[col * 4 + row] = sum
            }
        }
        return result
    }
}
