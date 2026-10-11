package com.bellinicine.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bellinicine.core.engine.EvaluatedLayer
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.render.CompositorPipeline
import com.bellinicine.ui.theme.AccentPrimary
import com.bellinicine.ui.theme.BgDark
import com.bellinicine.ui.theme.BorderDark
import com.bellinicine.ui.theme.KeyframeAmber
import com.bellinicine.ui.theme.WorkAreaBlue

@Composable
fun CompositionCanvas(
    composition: Composition,
    evaluatedLayers: List<EvaluatedLayer>,
    selectedLayerId: String?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(0.40f) }
    var panOffset by remember { mutableStateOf(Offset(0f, 0f)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.1f, 5.0f)
                    panOffset += pan
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerViewport = Offset(size.width / 2f, size.height / 2f) + panOffset
            val compSize = Size(composition.width.toFloat(), composition.height.toFloat())

            withTransform({
                translate(centerViewport.x, centerViewport.y)
                scale(scale, scale, Offset.Zero)
                translate(-composition.width / 2f, -composition.height / 2f)
            }) {
                // 1. Transparency Checkerboard Pattern
                val checkSize = 32f
                val cols = (composition.width / checkSize).toInt() + 1
                val rows = (composition.height / checkSize).toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val color = if ((r + c) % 2 == 0) Color(0xFF1E1E26) else Color(0xFF16161D)
                        drawRect(
                            color = color,
                            topLeft = Offset(c * checkSize, r * checkSize),
                            size = Size(checkSize, checkSize)
                        )
                    }
                }

                // 2. Render Composition Frame using Unified Compositor Pipeline
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    CompositorPipeline.renderEvaluatedLayers(canvas.nativeCanvas, evaluatedLayers)
                    canvas.nativeCanvas.restore()
                }

                // 3. Spatial Motion Paths (Trajectory)
                val selectedEval = evaluatedLayers.firstOrNull { it.layer.id == selectedLayerId }
                if (selectedEval != null && selectedEval.layer.transform.position.hasKeyframes()) {
                    val posKfs = selectedEval.layer.transform.position.keyframes
                    if (posKfs.size >= 2) {
                        for (i in 0 until posKfs.size - 1) {
                            val p1 = Offset(posKfs[i].value.x, posKfs[i].value.y)
                            val p2 = Offset(posKfs[i + 1].value.x, posKfs[i + 1].value.y)
                            drawLine(
                                color = WorkAreaBlue,
                                start = p1,
                                end = p2,
                                strokeWidth = 2f / scale,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f / scale, 4f / scale))
                            )
                            drawCircle(color = KeyframeAmber, radius = 5f / scale, center = p1)
                        }
                        val last = posKfs.last()
                        drawCircle(color = KeyframeAmber, radius = 5f / scale, center = Offset(last.value.x, last.value.y))
                    }
                }

                // 4. Transform Bounding Box Gizmos for Selected Layer
                if (selectedEval != null) {
                    val transform = selectedEval.transform
                    val layer = selectedEval.layer

                    withTransform({
                        translate(transform.position.x, transform.position.y)
                        rotate(transform.rotationZ)
                        scale(transform.scale.x / 100f, transform.scale.y / 100f, Offset.Zero)
                        translate(-transform.anchorPoint.x, -transform.anchorPoint.y)
                    }) {
                        val (w, h) = when (val d = layer.data) {
                            is LayerData.Solid -> Pair(d.width.toFloat(), d.height.toFloat())
                            is LayerData.Shape -> Pair(d.width, d.height)
                            is LayerData.Text -> Pair(540f, 120f)
                            is LayerData.Footage -> Pair(d.naturalWidth.toFloat(), d.naturalHeight.toFloat())
                            else -> Pair(200f, 200f)
                        }

                        // Outline
                        drawRect(
                            color = AccentPrimary,
                            topLeft = Offset.Zero,
                            size = Size(w, h),
                            style = Stroke(
                                width = 2.5f / scale,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / scale, 6f / scale))
                            )
                        )

                        // 8 Corner & Side Handles
                        val handleRadius = 6f / scale
                        listOf(
                            Offset(0f, 0f), Offset(w / 2f, 0f), Offset(w, 0f),
                            Offset(0f, h / 2f), Offset(w, h / 2f),
                            Offset(0f, h), Offset(w / 2f, h), Offset(w, h)
                        ).forEach { pt ->
                            drawCircle(color = AccentPrimary, radius = handleRadius, center = pt)
                            drawCircle(color = Color.White, radius = handleRadius * 0.5f, center = pt)
                        }

                        // Rotation handle at top
                        val rotHandlePos = Offset(w / 2f, -32f / scale)
                        drawLine(AccentPrimary, Offset(w / 2f, 0f), rotHandlePos, strokeWidth = 2f / scale)
                        drawCircle(AccentPrimary, radius = handleRadius * 1.2f, center = rotHandlePos)
                        drawCircle(Color.White, radius = handleRadius * 0.5f, center = rotHandlePos)

                        // Center Anchor Point Crosshair
                        val ax = transform.anchorPoint.x
                        val ay = transform.anchorPoint.y
                        val arm = 16f / scale
                        drawLine(AccentPrimary, Offset(ax - arm, ay), Offset(ax + arm, ay), strokeWidth = 2f / scale)
                        drawLine(AccentPrimary, Offset(ax, ay - arm), Offset(ax, ay + arm), strokeWidth = 2f / scale)
                        drawCircle(AccentPrimary, radius = 6f / scale, center = Offset(ax, ay), style = Stroke(2f / scale))
                    }
                }

                // 5. Outer Composition Boundary
                drawRect(
                    color = BorderDark,
                    topLeft = Offset.Zero,
                    size = compSize,
                    style = Stroke(width = 2f / scale)
                )
            }
        }
    }
}
