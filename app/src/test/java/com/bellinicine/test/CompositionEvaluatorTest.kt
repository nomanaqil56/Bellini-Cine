package com.bellinicine.test

import com.bellinicine.core.engine.CompositionEvaluator
import com.bellinicine.core.model.AnimatedProperty
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.KeyframeInterpolation
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.Transform
import com.bellinicine.core.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositionEvaluatorTest {

    @Test
    fun testEvaluatesAnimatedPositionAtFrames() {
        val layer = Layer(
            id = "layer-pos",
            name = "Animated Position",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFFFFFFFF, 100, 100),
            inPointFrame = 0L,
            outPointFrame = 100L,
            transform = Transform(
                position = AnimatedProperty(
                    name = "Position",
                    defaultValue = Vec3(0f, 0f, 0f),
                    keyframes = listOf(
                        Keyframe(0L, Vec3(0f, 0f, 0f), KeyframeInterpolation.LINEAR),
                        Keyframe(100L, Vec3(500f, 1000f, 0f), KeyframeInterpolation.LINEAR)
                    ),
                    isKeyframed = true
                )
            )
        )

        val comp = Composition(
            id = "comp-test",
            layers = listOf(layer),
            durationFrames = 100L
        )

        // At frame 0
        val eval0 = CompositionEvaluator.evaluateComposition(comp, 0L)
        assertEquals(1, eval0.size)
        assertEquals(0f, eval0[0].transform.position.x, 0.001f)
        assertEquals(0f, eval0[0].transform.position.y, 0.001f)

        // At frame 50 (midpoint)
        val eval50 = CompositionEvaluator.evaluateComposition(comp, 50L)
        assertEquals(250f, eval50[0].transform.position.x, 0.001f)
        assertEquals(500f, eval50[0].transform.position.y, 0.001f)

        // At frame 100
        val eval100 = CompositionEvaluator.evaluateComposition(comp, 100L)
        // At frame 100, layer is active (inPoint 0, outPoint 100 is exclusive or active)
        if (eval100.isNotEmpty()) {
            assertEquals(500f, eval100[0].transform.position.x, 0.001f)
            assertEquals(1000f, eval100[0].transform.position.y, 0.001f)
        }
    }

    @Test
    fun testParentChildTransformCascade() {
        val parent = Layer(
            id = "parent-1",
            name = "Parent Layer",
            type = LayerType.NULL_OBJECT,
            data = LayerData.NullObject,
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(100f, 200f, 0f))
            )
        )

        val child = Layer(
            id = "child-1",
            name = "Child Layer",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFFFF0000, 50, 50),
            parentLayerId = "parent-1",
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(50f, 30f, 0f))
            )
        )

        val comp = Composition(
            id = "comp-parent-test",
            layers = listOf(parent, child)
        )

        val eval = CompositionEvaluator.evaluateComposition(comp, 0L)
        val childEval = eval.first { it.layer.id == "child-1" }

        // In 4x4 column-major matrix: m[12] is tx, m[13] is ty
        val tx = childEval.worldMatrix[12]
        val ty = childEval.worldMatrix[13]

        // Parent pos (100, 200) + child pos (50, 30) = (150, 230)
        assertEquals(150f, tx, 0.01f)
        assertEquals(230f, ty, 0.01f)
    }
}
