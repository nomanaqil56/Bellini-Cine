package com.bellinicine.ui.grapheditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.bellinicine.ui.theme.TextPrimary
import com.bellinicine.ui.theme.TextSecondary
import kotlin.math.hypot

@Composable
fun GraphEditorView(
    composition: Composition,
    selectedLayer: Layer?,
    currentFrame: Long,
    isSpeedGraph: Boolean,
    onToggleSpeedGraph: (Boolean) -> Unit,
    onUpdateKeyframe: (Long, Float, KeyframeInterpolation) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedKfIndex by remember { mutableStateOf(0) }

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
        // Graph Editor Toolbar with Easing Controls
        Surface(
            color = PanelHeader,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSpeedGraph) "Speed Graph" else "Value Graph",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )

                    // Mode Toggles
                    FilledTonalButton(
                        onClick = { onToggleSpeedGraph(true) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSpeedGraph) AccentPrimary else BorderDark
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(26.dp).padding(start = 8.dp)
                    ) {
                        Text("Speed", fontSize = 9.sp, color = Color.White)
                    }

                    FilledTonalButton(
                        onClick = { onToggleSpeedGraph(false) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (!isSpeedGraph) AccentPrimary else BorderDark
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(26.dp).padding(start = 4.dp)
                    ) {
                        Text("Value", fontSize = 9.sp, color = Color.White)
                    }
                }

                // Interactive Interpolation Easing Buttons
                Row {
                    FilledTonalButton(
                        onClick = {
                            if (floatKeyframes.isNotEmpty()) {
                                val kf = floatKeyframes[selectedKfIndex.coerceIn(0, floatKeyframes.lastIndex)]
                                onUpdateKeyframe(kf.frame, kf.value, KeyframeInterpolation.LINEAR)
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = BorderDark),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Linear", fontSize = 9.sp, color = TextPrimary)
                    }

                    FilledTonalButton(
                        onClick = {
                            if (floatKeyframes.isNotEmpty()) {
                                val kf = floatKeyframes[selectedKfIndex.coerceIn(0, floatKeyframes.lastIndex)]
                                onUpdateKeyframe(kf.frame, kf.value, KeyframeInterpolation.BEZIER)
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentPrimary),
                        modifier = Modifier.height(26.dp).padding(start = 4.dp)
                    ) {
                        Text("Easy Ease", fontSize = 9.sp, color = Color.White)
                    }

                    FilledTonalButton(
                        onClick = {
                            if (floatKeyframes.isNotEmpty()) {
                                val kf = floatKeyframes[selectedKfIndex.coerceIn(0, floatKeyframes.lastIndex)]
                                onUpdateKeyframe(kf.frame, kf.value, KeyframeInterpolation.HOLD)
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = BorderDark),
                        modifier = Modifier.height(26.dp).padding(start = 4.dp)
                    ) {
                        Text("Hold", fontSize = 9.sp, color = TextPrimary)
                    }
                }
            }
        }

        // Selected Keyframe Readout
        if (floatKeyframes.isNotEmpty()) {
            val activeKf = floatKeyframes[selectedKfIndex.coerceIn(0, floatKeyframes.lastIndex)]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelDark)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Keyframe at frame ${activeKf.frame} • Value: ${activeKf.value.toInt()} • Mode: ${activeKf.interpolation.name}",
                    fontSize = 10.sp,
                    color = KeyframeAmber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Drag diamond nodes to edit curves",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }

        // Interactive Graph Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(PanelDark)
                .pointerInput(floatKeyframes) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val totalFrames = composition.durationFrames.toFloat().coerceAtLeast(1f)
                        val values = graphPoints.map { if (isSpeedGraph) it.speed else it.value }
                        val minVal = values.minOrNull() ?: 0f
                        val maxVal = (values.maxOrNull() ?: 100f).coerceAtLeast(minVal + 1f)
                        val valRange = maxVal - minVal

                        // Check which keyframe was tapped
                        floatKeyframes.forEachIndexed { idx, kf ->
                            val kfX = (kf.frame / totalFrames) * w
                            val v = kf.value
                            val kfY = h - ((v - minVal) / valRange) * (h * 0.8f) - (h * 0.1f)
                            if (hypot(tapOffset.x - kfX, tapOffset.y - kfY) < 40f) {
                                selectedKfIndex = idx
                            }
                        }
                    }
                }
                .pointerInput(floatKeyframes, selectedKfIndex) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (floatKeyframes.isNotEmpty()) {
                            val idx = selectedKfIndex.coerceIn(0, floatKeyframes.lastIndex)
                            val kf = floatKeyframes[idx]
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val totalFrames = composition.durationFrames.toFloat().coerceAtLeast(1f)

                            val dFrame = (dragAmount.x / w * totalFrames).toLong()
                            val newFrame = (kf.frame + dFrame).coerceIn(0L, composition.durationFrames)
                            val dVal = -dragAmount.y / h * 100f
                            val newVal = kf.value + dVal

                            onUpdateKeyframe(newFrame, newVal, kf.interpolation)
                        }
                    }
                }
        ) {
            if (graphPoints.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height

            val values = graphPoints.map { if (isSpeedGraph) it.speed else it.value }
            val minVal = values.minOrNull() ?: 0f
            val maxVal = (values.maxOrNull() ?: 100f).coerceAtLeast(minVal + 1f)
            val valRange = maxVal - minVal
            val totalFrames = composition.durationFrames.toFloat().coerceAtLeast(1f)

            // 1. Grid Lines
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

            // 2. Continuous Spline Curve
            val path = Path()
            graphPoints.forEachIndexed { idx, pt ->
                val x = (pt.frame / totalFrames) * w
                val v = if (isSpeedGraph) pt.speed else pt.value
                val y = h - ((v - minVal) / valRange) * (h * 0.8f) - (h * 0.1f)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = AccentPrimary, style = Stroke(width = 3.dp.toPx()))

            // 3. Interactive Keyframe Diamonds
            floatKeyframes.forEachIndexed { idx, kf ->
                val kfX = (kf.frame / totalFrames) * w
                val v = kf.value
                val kfY = h - ((v - minVal) / valRange) * (h * 0.8f) - (h * 0.1f)
                val isSelected = idx == selectedKfIndex

                drawCircle(
                    color = if (isSelected) Color.White else KeyframeAmber,
                    radius = (if (isSelected) 8.dp else 6.dp).toPx(),
                    center = Offset(kfX, kfY)
                )
                drawCircle(
                    color = if (isSelected) AccentPrimary else Color.Black,
                    radius = (if (isSelected) 4.dp else 3.dp).toPx(),
                    center = Offset(kfX, kfY)
                )
            }

            // 4. Playhead Line
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
