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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.bellinicine.core.engine.EvaluatedLayer
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.LayerData
import com.bellinicine.ui.theme.AccentPrimary
import com.bellinicine.ui.theme.BgDark
import com.bellinicine.ui.theme.BorderDark

@Composable
fun CompositionCanvas(
    composition: Composition,
    evaluatedLayers: List<EvaluatedLayer>,
    selectedLayerId: String?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(0.45f) }
    var panOffset by remember { mutableStateOf(Offset(0f, 0f)) }
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

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

            // Draw composition workspace
            withTransform({
                translate(centerViewport.x, centerViewport.y)
                scale(scale, scale, Offset.Zero)
                // Center composition at (0, 0)
                translate(-composition.width / 2f, -composition.height / 2f)
            }) {
                // 1. Draw AE Transparency Checkerboard Grid
                val compSize = Size(composition.width.toFloat(), composition.height.toFloat())
                drawRect(
                    color = Color(composition.backgroundColor),
                    topLeft = Offset.Zero,
                    size = compSize
                )

                // 2. Render Evaluated Layers
                for (evalLayer in evaluatedLayers) {
                    val layer = evalLayer.layer
                    val transform = evalLayer.transform
                    val alpha = (transform.opacity / 100f).coerceIn(0f, 1f)

                    withTransform({
                        translate(transform.position.x, transform.position.y)
                        rotate(transform.rotationZ)
                        scale(transform.scale.x / 100f, transform.scale.y / 100f, Offset.Zero)
                        translate(-transform.anchorPoint.x, -transform.anchorPoint.y)
                    }) {
                        when (val data = layer.data) {
                            is LayerData.Solid -> {
                                drawRect(
                                    color = Color(data.color).copy(alpha = alpha),
                                    topLeft = Offset.Zero,
                                    size = Size(data.width.toFloat(), data.height.toFloat())
                                )
                            }
                            is LayerData.Text -> {
                                val measured = textMeasurer.measure(
                                    text = data.text,
                                    style = TextStyle(
                                        fontSize = data.fontSize.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(data.fontColor).copy(alpha = alpha)
                                    )
                                )
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset.Zero
                                )
                            }
                            is LayerData.Shape -> {
                                drawCircle(
                                    color = Color(data.fillColor).copy(alpha = alpha),
                                    radius = 120f,
                                    center = Offset(120f, 120f)
                                )
                            }
                            else -> {}
                        }

                        // 3. Draw Transform Bounding Box & Gizmo for Selected Layer
                        if (layer.id == selectedLayerId) {
                            val w = when (val d = layer.data) {
                                is LayerData.Solid -> d.width.toFloat()
                                is LayerData.Text -> 500f
                                else -> 240f
                            }
                            val h = when (val d = layer.data) {
                                is LayerData.Solid -> d.height.toFloat()
                                is LayerData.Text -> 100f
                                else -> 240f
                            }

                            // Bounding outline
                            drawRect(
                                color = AccentPrimary,
                                topLeft = Offset.Zero,
                                size = Size(w, h),
                                style = Stroke(
                                    width = 3f / scale,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / scale, 6f / scale))
                                )
                            )

                            // 8-point corner handles
                            val handleRadius = 6f / scale
                            listOf(
                                Offset(0f, 0f), Offset(w / 2f, 0f), Offset(w, 0f),
                                Offset(0f, h / 2f), Offset(w, h / 2f),
                                Offset(0f, h), Offset(w / 2f, h), Offset(w, h)
                            ).forEach { pt ->
                                drawCircle(color = AccentPrimary, radius = handleRadius, center = pt)
                            }

                            // Center Anchor point crosshair
                            val ax = transform.anchorPoint.x
                            val ay = transform.anchorPoint.y
                            val arm = 14f / scale
                            drawLine(AccentPrimary, Offset(ax - arm, ay), Offset(ax + arm, ay), strokeWidth = 2f / scale)
                            drawLine(AccentPrimary, Offset(ax, ay - arm), Offset(ax, ay + arm), strokeWidth = 2f / scale)
                            drawCircle(AccentPrimary, radius = 5f / scale, center = Offset(ax, ay), style = Stroke(2f / scale))
                        }
                    }
                }

                // 4. Composition Outer Boundary Border
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
