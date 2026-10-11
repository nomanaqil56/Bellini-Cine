package com.bellinicine.ui.grapheditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bellinicine.core.engine.GraphEditorMath
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.KeyframeInterpolation
import com.bellinicine.core.model.Layer
import com.bellinicine.ui.theme.AccentPrimary
import com.bellinicine.ui.theme.BorderDark
import com.bellinicine.ui.theme.KeyframeAmber
import com.bellinicine.ui.theme.PanelDark
import com.bellinicine.ui.theme.PanelHeader
import com.bellinicine.ui.theme.PlayheadColor
import com.bellinicine.ui.theme.TextMuted
import com.bellinicine.ui.theme.TextSecondary

@Composable
fun GraphEditorView(
    composition: Composition,
    selectedLayer: Layer?,
    currentFrame: Long,
    isSpeedGraph: Boolean,
    onToggleSpeedGraph: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect keyframes from selected layer (e.g. rotation or opacity)
    val floatKeyframes: List<Keyframe<Float>> = remember(selectedLayer) {
        selectedLayer?.transform?.rotation?.keyframes?.ifEmpty { null }
            ?: selectedLayer?.transform?.opacity?.keyframes?.ifEmpty { null }
            ?: listOf(
                Keyframe(0L, 0f, KeyframeInterpolation.BEZIER),
                Keyframe(30L, 100f, KeyframeInterpolation.BEZIER),
                Keyframe(60L, 0f, KeyframeInterpolation.BEZIER)
            )
    }

    val graphPoints = remember(floatKeyframes, isSpeedGraph) {
        GraphEditorMath.sampleGraph(
            keyframes = floatKeyframes,
            fps = composition.frameRate,
            startFrame = 0L,
            endFrame = composition.durationFrames,
            stepFrames = 1
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelDark)
    ) {
        // Graph Editor Toolbar
        Surface(
            color = PanelHeader,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = if (isSpeedGraph) "Speed Graph (Velocity px/s)" else "Value Graph",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPrimary
                )

                Row {
                    FilledTonalButton(
                        onClick = { onToggleSpeedGraph(true) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSpeedGraph) AccentPrimary else BorderDark
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Speed", fontSize = 10.sp, color = Color.White)
                    }

                    FilledTonalButton(
                        onClick = { onToggleSpeedGraph(false) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (!isSpeedGraph) AccentPrimary else BorderDark
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(28.dp).padding(start = 6.dp)
                    ) {
                        Text("Value", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }

        // Graph Canvas Curve Display
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(PanelDark)
        ) {
            if (graphPoints.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height

            // Calculate min/max range for auto-scaling
            val values = graphPoints.map { if (isSpeedGraph) it.speed else it.value }
            val minVal = values.minOrNull() ?: 0f
            val maxVal = (values.maxOrNull() ?: 100f).coerceAtLeast(minVal + 1f)
            val valRange = maxVal - minVal

            val totalFrames = composition.durationFrames.toFloat().coerceAtLeast(1f)

            // 1. Draw Background Grid Lines
            val gridLines = 5
            for (i in 0..gridLines) {
                val y = h * (i / gridLines.toFloat())
                drawLine(
                    color = BorderDark,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            // 2. Draw Spline Curve
            val path = Path()
            graphPoints.forEachIndexed { idx, pt ->
                val x = (pt.frame / totalFrames) * w
                val v = if (isSpeedGraph) pt.speed else pt.value
                val y = h - ((v - minVal) / valRange) * (h * 0.8f) - (h * 0.1f)

                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = AccentPrimary, style = Stroke(width = 3.dp.toPx()))

            // 3. Draw Keyframe Circles
            floatKeyframes.forEach { kf ->
                val kfPt = graphPoints.firstOrNull { it.frame == kf.frame }
                if (kfPt != null) {
                    val kfX = (kf.frame / totalFrames) * w
                    val v = if (isSpeedGraph) kfPt.speed else kfPt.value
                    val kfY = h - ((v - minVal) / valRange) * (h * 0.8f) - (h * 0.1f)

                    drawCircle(color = KeyframeAmber, radius = 6.dp.toPx(), center = Offset(kfX, kfY))
                    drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = Offset(kfX, kfY))
                }
            }

            // 4. Draw Current Playhead Line
            val playheadX = (currentFrame / totalFrames) * w
            drawLine(
                color = PlayheadColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, h),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
