package com.bellinicine.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.bellinicine.core.engine.EvaluatedLayer
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.render.CompositorPipeline
import com.bellinicine.ui.theme.AccentPrimary
import com.bellinicine.ui.theme.BgDark
import com.bellinicine.ui.theme.BorderDark
import com.bellinicine.ui.theme.KeyframeAmber
import com.bellinicine.ui.theme.WorkAreaBlue
import kotlin.math.atan2

enum class GizmoDragTarget {
    NONE,
    TRANSLATE,
    ROTATE,
    SCALE_CORNER,
    ANCHOR_POINT
}

@Composable
fun CompositionCanvas(
    composition: Composition,
    evaluatedLayers: List<EvaluatedLayer>,
    selectedLayerId: String?,
    onUpdatePosition: (Float, Float) -> Unit = { _, _ -> },
    onUpdateScale: (Float, Float) -> Unit = { _, _ -> },
    onUpdateRotation: (Float) -> Unit = { _ -> },
    onUpdateAnchorPoint: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(0.40f) }
    var panOffset by remember { mutableStateOf(Offset(0f, 0f)) }
    var activeGizmoTarget by remember { mutableStateOf(GizmoDragTarget.NONE) }

    val selectedEval = evaluatedLayers.firstOrNull { it.layer.id == selectedLayerId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .pointerInput(selectedLayerId, scale, panOffset) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        if (selectedEval == null) {
                            activeGizmoTarget = GizmoDragTarget.NONE
                            return@detectDragGestures
                        }

                        val transform = selectedEval.transform
                        val (w, h) = when (val d = selectedEval.layer.data) {
                            is LayerData.Solid -> Pair(d.width.toFloat(), d.height.toFloat())
                            is LayerData.Shape -> Pair(d.width, d.height)
                            is LayerData.Text -> Pair(540f, 120f)
                            is LayerData.Footage -> Pair(d.naturalWidth.toFloat(), d.naturalHeight.toFloat())
                            else -> Pair(200f, 200f)
                        }

                        // Map screen touch to composition space
                        val centerViewport = Offset(size.width / 2f, size.height / 2f) + panOffset
                        val compX = (startOffset.x - centerViewport.x) / scale + composition.width / 2f
                        val compY = (startOffset.y - centerViewport.y) / scale + composition.height / 2f

                        // Inverse transform to layer local space
                        val dx = compX - transform.position.x
                        val dy = compY - transform.position.y
                        val rad = -Math.toRadians(transform.rotationZ.toDouble())
                        val cosR = Math.cos(rad).toFloat()
                        val sinR = Math.sin(rad).toFloat()
                        val unrotX = dx * cosR - dy * sinR
                        val unrotY = dx * sinR + dy * cosR
                        val sx = (transform.scale.x / 100f).coerceAtLeast(0.01f)
                        val sy = (transform.scale.y / 100f).coerceAtLeast(0.01f)
                        val localX = (unrotX / sx) + transform.anchorPoint.x
                        val localY = (unrotY / sy) + transform.anchorPoint.y

                        // Test gizmo handles
                        val touchRadius = 36f / scale
                        val rotHandleLocal = Offset(w / 2f, -32f / scale)
                        val anchorLocal = Offset(transform.anchorPoint.x, transform.anchorPoint.y)

                        activeGizmoTarget = when {
                            kotlin.math.hypot(localX - rotHandleLocal.x, localY - rotHandleLocal.y) < touchRadius ->
                                GizmoDragTarget.ROTATE
                            kotlin.math.hypot(localX - anchorLocal.x, localY - anchorLocal.y) < touchRadius ->
                                GizmoDragTarget.ANCHOR_POINT
                            kotlin.math.hypot(localX - w, localY - h) < touchRadius ||
                            kotlin.math.hypot(localX, localY) < touchRadius ||
                            kotlin.math.hypot(localX - w, localY) < touchRadius ||
                            kotlin.math.hypot(localX, localY - h) < touchRadius ->
                                GizmoDragTarget.SCALE_CORNER
                            localX in -touchRadius..(w + touchRadius) && localY in -touchRadius..(h + touchRadius) ->
                                GizmoDragTarget.TRANSLATE
                            else ->
                                GizmoDragTarget.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (selectedEval != null) {
                            val transform = selectedEval.transform
                            when (activeGizmoTarget) {
                                GizmoDragTarget.TRANSLATE -> {
                                    val compDeltaX = dragAmount.x / scale
                                    val compDeltaY = dragAmount.y / scale
                                    val curPos = transform.position
                                    onUpdatePosition(curPos.x + compDeltaX, curPos.y + compDeltaY)
                                }
                                GizmoDragTarget.ROTATE -> {
                                    val centerViewport = Offset(size.width / 2f, size.height / 2f) + panOffset
                                    val compX = (change.position.x - centerViewport.x) / scale + composition.width / 2f
                                    val compY = (change.position.y - centerViewport.y) / scale + composition.height / 2f
                                    val angleRad = atan2(compY - transform.position.y, compX - transform.position.x)
                                    val deg = Math.toDegrees(angleRad.toDouble()).toFloat() + 90f
                                    onUpdateRotation(deg)
                                }
                                GizmoDragTarget.SCALE_CORNER -> {
                                    val dsx = (dragAmount.x / scale) * 0.5f
                                    val dsy = (dragAmount.y / scale) * 0.5f
                                    val newSx = (transform.scale.x + dsx).coerceIn(5f, 1000f)
                                    val newSy = (transform.scale.y + dsy).coerceIn(5f, 1000f)
                                    onUpdateScale(newSx, newSy)
                                }
                                GizmoDragTarget.ANCHOR_POINT -> {
                                    val dax = dragAmount.x / scale
                                    val day = dragAmount.y / scale
                                    onUpdateAnchorPoint(transform.anchorPoint.x + dax, transform.anchorPoint.y + day)
                                }
                                GizmoDragTarget.NONE -> {
                                    panOffset += dragAmount
                                }
                            }
                        } else {
                            panOffset += dragAmount
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (activeGizmoTarget == GizmoDragTarget.NONE) {
                        scale = (scale * zoom).coerceIn(0.1f, 5.0f)
                        panOffset += pan
                    }
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

                // 3. Curved Spatial Motion Paths (Trajectory)
                if (selectedEval != null && selectedEval.layer.transform.position.hasKeyframes()) {
                    val posKfs = selectedEval.layer.transform.position.keyframes
                    if (posKfs.size >= 2) {
                        val startF = posKfs.first().frame
                        val endF = posKfs.last().frame
                        val trajectoryPath = androidx.compose.ui.graphics.Path()
                        var isFirst = true

                        for (f in startF..endF) {
                            val p = com.bellinicine.core.engine.CompositionEvaluator.evaluateVec3Property(
                                selectedEval.layer.transform.position,
                                f,
                                f / composition.frameRate
                            )
                            val pt = Offset(p.x, p.y)
                            if (isFirst) {
                                trajectoryPath.moveTo(pt.x, pt.y)
                                isFirst = false
                            } else {
                                trajectoryPath.lineTo(pt.x, pt.y)
                            }
                        }

                        drawPath(
                            path = trajectoryPath,
                            color = WorkAreaBlue,
                            style = Stroke(
                                width = 2.5f / scale,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f / scale, 4f / scale))
                            )
                        )

                        posKfs.forEach { kf ->
                            val pt = Offset(kf.value.x, kf.value.y)
                            drawCircle(color = KeyframeAmber, radius = 5.5f / scale, center = pt)
                            drawCircle(color = Color.White, radius = 2.5f / scale, center = pt)
                        }
                    }
                }

                // 4. Interactive Transform Bounding Box & Handles for Selected Layer
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

                        // Outline Bounding Box
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
                        val handleRadius = 6.5f / scale
                        listOf(
                            Offset(0f, 0f), Offset(w / 2f, 0f), Offset(w, 0f),
                            Offset(0f, h / 2f), Offset(w, h / 2f),
                            Offset(0f, h), Offset(w / 2f, h), Offset(w, h)
                        ).forEach { pt ->
                            drawCircle(color = AccentPrimary, radius = handleRadius, center = pt)
                            drawCircle(color = Color.White, radius = handleRadius * 0.5f, center = pt)
                        }

                        // Top Rotation Handle with leader line
                        val rotHandlePos = Offset(w / 2f, -32f / scale)
                        drawLine(AccentPrimary, Offset(w / 2f, 0f), rotHandlePos, strokeWidth = 2f / scale)
                        drawCircle(AccentPrimary, radius = handleRadius * 1.3f, center = rotHandlePos)
                        drawCircle(Color.White, radius = handleRadius * 0.6f, center = rotHandlePos)

                        // Center Anchor Point Crosshair
                        val ax = transform.anchorPoint.x
                        val ay = transform.anchorPoint.y
                        val arm = 18f / scale
                        drawLine(AccentPrimary, Offset(ax - arm, ay), Offset(ax + arm, ay), strokeWidth = 2.5f / scale)
                        drawLine(AccentPrimary, Offset(ax, ay - arm), Offset(ax, ay + arm), strokeWidth = 2.5f / scale)
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
