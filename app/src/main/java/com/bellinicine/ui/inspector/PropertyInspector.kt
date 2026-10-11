package com.bellinicine.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bellinicine.core.model.BlendMode
import com.bellinicine.core.model.EffectCategory
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.TrackMatteType
import com.bellinicine.ui.theme.AccentPrimary
import com.bellinicine.ui.theme.KeyframeAmber
import com.bellinicine.ui.theme.PanelDark
import com.bellinicine.ui.theme.PanelHeader
import com.bellinicine.ui.theme.TextMuted
import com.bellinicine.ui.theme.TextPrimary
import com.bellinicine.ui.theme.TextSecondary

@Composable
fun PropertyInspector(
    selectedLayer: Layer?,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateRotation: (Float) -> Unit,
    onUpdatePosition: (Float, Float) -> Unit,
    onUpdateScale: (Float, Float) -> Unit,
    onUpdateBlendMode: (BlendMode) -> Unit,
    onUpdateTrackMatte: (TrackMatteType) -> Unit,
    onToggleKeyframing: (String) -> Unit,
    onAddEffect: (String, EffectCategory) -> Unit,
    onRemoveEffect: (String) -> Unit,
    onToggleEffect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedLayer == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(PanelDark)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No Layer Selected", color = TextMuted, fontSize = 14.sp)
            Text("Tap a layer in the timeline or canvas to inspect and edit properties.", color = TextMuted, fontSize = 11.sp)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelDark)
            .verticalScroll(rememberScrollState())
    ) {
        // Layer Header
        Surface(color = PanelHeader, modifier = Modifier.fillMaxWidth().height(42.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(selectedLayer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text(selectedLayer.type.name, fontSize = 10.sp, color = AccentPrimary)
            }
        }

        // Blend Mode & Track Matte Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelDark),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Blend Mode", fontSize = 12.sp, color = TextSecondary)
                BlendModeDropdown(
                    currentMode = selectedLayer.blendMode,
                    onSelect = onUpdateBlendMode
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Track Matte", fontSize = 12.sp, color = TextSecondary)
                TrackMatteDropdown(
                    currentMatte = selectedLayer.trackMatteType,
                    onSelect = onUpdateTrackMatte
                )
            }
        }

        // Section: Transform Properties
        Text(
            text = "TRANSFORM CONTROLS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
        )

        // Opacity
        val currentOpacity = selectedLayer.transform.opacity.defaultValue
        PropertyRow(
            name = "Opacity",
            valueText = "${currentOpacity.toInt()}%",
            isKeyframed = selectedLayer.transform.opacity.hasKeyframes(),
            onToggleStopwatch = { onToggleKeyframing("opacity") }
        ) {
            Slider(
                value = currentOpacity,
                onValueChange = { onUpdateOpacity(it) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
            )
        }

        // Rotation
        val currentRotation = selectedLayer.transform.rotation.defaultValue
        PropertyRow(
            name = "Rotation",
            valueText = "${currentRotation.toInt()}°",
            isKeyframed = selectedLayer.transform.rotation.hasKeyframes(),
            onToggleStopwatch = { onToggleKeyframing("rotation") }
        ) {
            Slider(
                value = currentRotation,
                onValueChange = { onUpdateRotation(it) },
                valueRange = -360f..360f,
                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
            )
        }

        // Position X & Y
        val pos = selectedLayer.transform.position.defaultValue
        PropertyRow(
            name = "Position X/Y",
            valueText = "X: ${pos.x.toInt()}  Y: ${pos.y.toInt()}",
            isKeyframed = selectedLayer.transform.position.hasKeyframes(),
            onToggleStopwatch = { onToggleKeyframing("position") }
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(
                    value = pos.x,
                    onValueChange = { onUpdatePosition(it, pos.y) },
                    valueRange = 0f..1080f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                )
                Slider(
                    value = pos.y,
                    onValueChange = { onUpdatePosition(pos.x, it) },
                    valueRange = 0f..1920f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                )
            }
        }

        // Scale
        val scale = selectedLayer.transform.scale.defaultValue
        PropertyRow(
            name = "Scale",
            valueText = "${scale.x.toInt()}%",
            isKeyframed = selectedLayer.transform.scale.hasKeyframes(),
            onToggleStopwatch = { onToggleKeyframing("scale") }
        ) {
            Slider(
                value = scale.x,
                onValueChange = { onUpdateScale(it, it) },
                valueRange = 0f..300f,
                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
            )
        }

        // Section: Visual Effects & Filters
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)
        ) {
            Text(
                text = "APPLIED EFFECTS (${selectedLayer.effects.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )

            AddEffectMenu(onAddEffect = onAddEffect)
        }

        if (selectedLayer.effects.isEmpty()) {
            Text(
                text = "No effects applied. Tap '+ Add Effect' above.",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            selectedLayer.effects.forEach { effect ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelHeader),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Checkbox(
                            checked = effect.enabled,
                            onCheckedChange = { onToggleEffect(effect.id) },
                            colors = CheckboxDefaults.colors(checkedColor = AccentPrimary),
                            modifier = Modifier.size(24.dp)
                        )
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                        Text(effect.name, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveEffect(effect.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(
    name: String,
    valueText: String,
    isKeyframed: Boolean,
    onToggleStopwatch: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleStopwatch, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Stopwatch",
                        tint = if (isKeyframed) KeyframeAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(name, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
            }
            Text(valueText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        content?.invoke()
    }
}

@Composable
private fun BlendModeDropdown(currentMode: BlendMode, onSelect: (BlendMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = currentMode.displayName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AccentPrimary,
            modifier = Modifier.clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(
                BlendMode.NORMAL,
                BlendMode.MULTIPLY,
                BlendMode.SCREEN,
                BlendMode.OVERLAY,
                BlendMode.ADD,
                BlendMode.COLOR_DODGE,
                BlendMode.DIFFERENCE,
                BlendMode.EXCLUSION
            ).forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TrackMatteDropdown(currentMatte: TrackMatteType, onSelect: (TrackMatteType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = currentMatte.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AccentPrimary,
            modifier = Modifier.clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TrackMatteType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AddEffectMenu(onAddEffect: (String, EffectCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentPrimary),
            modifier = Modifier.height(28.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
            Text("Add Effect", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(start = 2.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Gaussian Blur") },
                onClick = {
                    onAddEffect("Gaussian Blur", EffectCategory.BLUR_AND_SHARPEN)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Glow & Bloom") },
                onClick = {
                    onAddEffect("Glow & Bloom", EffectCategory.STYLIZE)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Color Curves / Tint") },
                onClick = {
                    onAddEffect("Color Curves", EffectCategory.COLOR_CORRECTION)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Turbulent Displace") },
                onClick = {
                    onAddEffect("Turbulent Displace", EffectCategory.DISTORT)
                    expanded = false
                }
            )
        }
    }
}
