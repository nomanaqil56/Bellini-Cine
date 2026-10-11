package com.bellinicine.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.ui.theme.AccentPrimary
import com.bellinicine.ui.theme.BorderDark
import com.bellinicine.ui.theme.KeyframeAmber
import com.bellinicine.ui.theme.PanelDark
import com.bellinicine.ui.theme.PanelHeader
import com.bellinicine.ui.theme.PlayheadColor
import com.bellinicine.ui.theme.TextMuted
import com.bellinicine.ui.theme.TextPrimary
import com.bellinicine.ui.theme.TextSecondary

@Composable
fun TimelineView(
    composition: Composition,
    currentFrame: Long,
    isPlaying: Boolean,
    selectedLayerId: String?,
    onSeek: (Long) -> Unit,
    onStepFrame: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onAddSolid: () -> Unit,
    onAddText: () -> Unit,
    onSplitLayer: (String) -> Unit = {},
    onDuplicateLayer: (String) -> Unit = {},
    onDeleteLayer: (String) -> Unit = {},
    onReorderLayer: (String, Boolean) -> Unit = { _, _ -> },
    onTrimInPoint: (String, Long) -> Unit = { _, _ -> },
    onTrimOutPoint: (String, Long) -> Unit = { _, _ -> },
    onMoveLayerTime: (String, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val pixelsPerFrame = 6f
    val timelineWidthDp = (composition.durationFrames * pixelsPerFrame / 2.5f).dp.coerceAtLeast(800.dp)
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelDark)
    ) {
        // 1. Transport Bar & Timecode Header with Editing Actions
        TransportBar(
            currentFrame = currentFrame,
            fps = composition.frameRate,
            isPlaying = isPlaying,
            selectedLayerId = selectedLayerId,
            onTogglePlay = onTogglePlay,
            onStepFrame = onStepFrame,
            onAddSolid = onAddSolid,
            onAddText = onAddText,
            onSplitLayer = { selectedLayerId?.let { onSplitLayer(it) } },
            onDuplicateLayer = { selectedLayerId?.let { onDuplicateLayer(it) } },
            onDeleteLayer = { selectedLayerId?.let { onDeleteLayer(it) } },
            onReorderLayer = { moveUp -> selectedLayerId?.let { onReorderLayer(it, moveUp) } }
        )

        // 2. Multi-track Timeline Area
        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Left Column: Layer Headers (Fixed width, vertical scroll)
            LayerHeaderColumn(
                layers = composition.layers,
                selectedLayerId = selectedLayerId,
                onSelectLayer = onSelectLayer,
                onToggleVisibility = onToggleVisibility,
                onToggleSolo = onToggleSolo,
                onToggleLock = onToggleLock,
                modifier = Modifier.width(180.dp)
            )

            // Right Column: Time Ruler & Track Bars (Horizontally scrollable)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                Column(modifier = Modifier.width(timelineWidthDp)) {
                    // Time Ruler
                    TimeRuler(
                        totalFrames = composition.durationFrames,
                        currentFrame = currentFrame,
                        pixelsPerFrame = pixelsPerFrame,
                        onSeek = onSeek,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )

                    // Layer Tracks
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        composition.layers.forEach { layer ->
                            LayerTrackBar(
                                layer = layer,
                                isSelected = layer.id == selectedLayerId,
                                pixelsPerFrame = pixelsPerFrame,
                                onSelect = { onSelectLayer(layer.id) },
                                onTrimInPoint = onTrimInPoint,
                                onTrimOutPoint = onTrimOutPoint,
                                onMoveLayerTime = onMoveLayerTime,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            )
                        }
                    }
                }

                // Draggable Current Time Indicator (Playhead)
                PlayheadIndicator(
                    currentFrame = currentFrame,
                    pixelsPerFrame = pixelsPerFrame,
                    onSeek = onSeek
                )
            }
        }
    }
}

@Composable
private fun TransportBar(
    currentFrame: Long,
    fps: Float,
    isPlaying: Boolean,
    selectedLayerId: String?,
    onTogglePlay: () -> Unit,
    onStepFrame: (Long) -> Unit,
    onAddSolid: () -> Unit,
    onAddText: () -> Unit,
    onSplitLayer: () -> Unit,
    onDuplicateLayer: () -> Unit,
    onDeleteLayer: () -> Unit,
    onReorderLayer: (Boolean) -> Unit
) {
    val totalSeconds = (currentFrame / fps).toInt()
    val frameWithinSecond = (currentFrame % fps).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timecode = String.format("%02d:%02d:%02d:%02d", 0, minutes, seconds, frameWithinSecond)

    Surface(
        color = PanelHeader,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Timecode display
            Text(
                text = timecode,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = AccentPrimary
            )

            // Transport Playback Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onStepFrame(-10) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FastRewind, contentDescription = "-10 frames", tint = TextSecondary)
                }
                IconButton(onClick = { onStepFrame(-1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "-1 frame", tint = TextSecondary)
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(36.dp)
                        .background(AccentPrimary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { onStepFrame(1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "+1 frame", tint = TextSecondary)
                }
                IconButton(onClick = { onStepFrame(10) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FastForward, contentDescription = "+10 frames", tint = TextSecondary)
                }
            }

            // Layer Editing Actions (Split, Duplicate, Delete, Reorder, Add)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedLayerId != null) {
                    IconButton(onClick = onSplitLayer, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ContentCut, contentDescription = "Split at playhead", tint = AccentPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDuplicateLayer, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate layer", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onReorderLayer(true) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move layer up", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onReorderLayer(false) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move layer down", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteLayer, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete layer", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onAddSolid, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Wallpaper, contentDescription = "Add Solid", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onAddText, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.TextFields, contentDescription = "Add Text", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LayerHeaderColumn(
    layers: List<Layer>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(PanelHeader)
            .verticalScroll(rememberScrollState())
    ) {
        // Header space to align with time ruler
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(PanelHeader)
                .padding(start = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Layer Name", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
        }

        layers.forEach { layer ->
            val isSelected = layer.id == selectedLayerId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(if (isSelected) AccentPrimary.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelectLayer(layer.id) }
                    .padding(horizontal = 4.dp)
            ) {
                // Visibility toggle (Eye)
                IconButton(
                    onClick = { onToggleVisibility(layer.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Visibility",
                        tint = if (layer.isVisible) TextPrimary else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Solo toggle (S)
                Text(
                    text = "S",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (layer.isSolo) Color(0xFFFFD600) else TextMuted,
                    modifier = Modifier
                        .clickable { onToggleSolo(layer.id) }
                        .padding(horizontal = 6.dp)
                )

                // Lock toggle
                IconButton(
                    onClick = { onToggleLock(layer.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock",
                        tint = if (layer.isLocked) Color(0xFFFF5252) else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = layer.name,
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeRuler(
    totalFrames: Long,
    currentFrame: Long,
    pixelsPerFrame: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(PanelHeader)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val frame = (change.position.x / pixelsPerFrame).toLong().coerceIn(0L, totalFrames)
                    onSeek(frame)
                }
            }
    ) {
        // Draw frame tick marks
        for (f in 0..totalFrames step 5) {
            val x = f * pixelsPerFrame
            val isMajor = f % 15L == 0L
            val tickHeight = if (isMajor) size.height * 0.6f else size.height * 0.3f
            drawLine(
                color = if (isMajor) TextSecondary else TextMuted,
                start = Offset(x, size.height - tickHeight),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 1.5f else 1f
            )
        }
    }
}

@Composable
private fun LayerTrackBar(
    layer: Layer,
    isSelected: Boolean,
    pixelsPerFrame: Float,
    onSelect: () -> Unit,
    onTrimInPoint: (String, Long) -> Unit,
    onTrimOutPoint: (String, Long) -> Unit,
    onMoveLayerTime: (String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(if (isSelected) BorderDark.copy(alpha = 0.5f) else Color.Transparent)
            .padding(vertical = 4.dp)
    ) {
        val startX = (layer.inPointFrame * pixelsPerFrame).dp
        val barWidth = ((layer.outPointFrame - layer.inPointFrame) * pixelsPerFrame).dp.coerceAtLeast(28.dp)

        // Layer Timeline Bar with left & right trim handles and center mover
        Row(
            modifier = Modifier
                .padding(start = startX)
                .width(barWidth)
                .fillMaxSize()
                .background(
                    color = if (isSelected) AccentPrimary else Color(0xFF333344),
                    shape = RoundedCornerShape(4.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Trim In-Point Handle
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxSize()
                    .background(if (isSelected) Color.White.copy(alpha = 0.35f) else Color.Transparent)
                    .pointerInput(layer.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dFrames = (dragAmount.x / pixelsPerFrame).toLong()
                            if (dFrames != 0L) {
                                onTrimInPoint(layer.id, layer.inPointFrame + dFrames)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(Color.White))
            }

            // Center Movable Track Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelect() }
                    .pointerInput(layer.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dFrames = (dragAmount.x / pixelsPerFrame).toLong()
                            if (dFrames != 0L) {
                                onMoveLayerTime(layer.id, dFrames)
                            }
                        }
                    }
            ) {
                // Display audio waveform for Audio layers
                if (layer.data is LayerData.Audio && layer.data.waveformPeaks.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val peaks = layer.data.waveformPeaks
                        val step = size.width / peaks.size.coerceAtLeast(1)
                        val centerY = size.height / 2f
                        peaks.forEachIndexed { idx, peak ->
                            val h = (peak * centerY * 0.85f).coerceAtLeast(1f)
                            drawLine(
                                color = Color(0xFF00E676).copy(alpha = 0.85f),
                                start = Offset(idx * step, centerY - h),
                                end = Offset(idx * step, centerY + h),
                                strokeWidth = 2.5f
                            )
                        }
                    }
                }

                // Display diamond keyframe markers
                val keyframedProps = listOf(
                    layer.transform.position,
                    layer.transform.scale,
                    layer.transform.rotation,
                    layer.transform.opacity
                ).filter { it.hasKeyframes() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    keyframedProps.forEach { prop ->
                        prop.keyframes.forEach { kf ->
                            val kfX = (kf.frame - layer.inPointFrame) * pixelsPerFrame - 12.dp.toPx()
                            if (kfX >= 0 && kfX <= size.width) {
                                val path = Path().apply {
                                    moveTo(kfX, size.height / 2f - 5f)
                                    lineTo(kfX + 5f, size.height / 2f)
                                    lineTo(kfX, size.height / 2f + 5f)
                                    lineTo(kfX - 5f, size.height / 2f)
                                    close()
                                }
                                drawPath(path, color = KeyframeAmber)
                            }
                        }
                    }
                }
            }

            // Right Trim Out-Point Handle
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxSize()
                    .background(if (isSelected) Color.White.copy(alpha = 0.35f) else Color.Transparent)
                    .pointerInput(layer.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dFrames = (dragAmount.x / pixelsPerFrame).toLong()
                            if (dFrames != 0L) {
                                onTrimOutPoint(layer.id, layer.outPointFrame + dFrames)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(Color.White))
            }
        }
    }
}

@Composable
private fun PlayheadIndicator(
    currentFrame: Long,
    pixelsPerFrame: Float,
    onSeek: (Long) -> Unit
) {
    val playheadX = currentFrame * pixelsPerFrame

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val frame = (change.position.x / pixelsPerFrame).toLong().coerceAtLeast(0L)
                    onSeek(frame)
                }
            }
    ) {
        // Red playhead vertical line
        drawLine(
            color = PlayheadColor,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, size.height),
            strokeWidth = 2.5f
        )

        // Playhead head marker at the top ruler
        val headPath = Path().apply {
            moveTo(playheadX - 7f, 0f)
            lineTo(playheadX + 7f, 0f)
            lineTo(playheadX + 7f, 16f)
            lineTo(playheadX, 24f)
            lineTo(playheadX - 7f, 16f)
            close()
        }
        drawPath(headPath, color = PlayheadColor)
    }
}
