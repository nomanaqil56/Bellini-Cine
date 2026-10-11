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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.TrackMatteType
import com.bellinicine.ui.theme.AccentPrimary
import com.bellinicine.ui.theme.BorderDark
import com.bellinicine.ui.theme.KeyframeAmber
import com.bellinicine.ui.theme.PanelDark
import com.bellinicine.ui.theme.PanelHeader
import com.bellinicine.ui.theme.TextMuted
import com.bellinicine.ui.theme.TextPrimary
import com.bellinicine.ui.theme.TextSecondary

@Composable
fun PropertyInspector(
    selectedLayer: Layer?,
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
            Text("Tap a layer in the timeline or canvas to inspect properties.", color = TextMuted, fontSize = 11.sp)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelDark)
            .verticalScroll(rememberScrollState())
    ) {
        // Layer Title & Type
        Surface(color = PanelHeader, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(selectedLayer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text(selectedLayer.type.name, fontSize = 10.sp, color = AccentPrimary)
            }
        }

        // Section: Blend Mode & Matte
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelDark),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Blend Mode", fontSize = 12.sp, color = TextSecondary)
                BlendModeDropdown(currentMode = selectedLayer.blendMode)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Track Matte", fontSize = 12.sp, color = TextSecondary)
                Text(selectedLayer.trackMatteType.name, fontSize = 12.sp, color = AccentPrimary)
            }
        }

        // Section: Transform Group
        Text(
            text = "TRANSFORM",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
        )

        // Opacity
        var opacityVal by remember(selectedLayer) { mutableStateOf(selectedLayer.transform.opacity.defaultValue) }
        PropertyRow(
            name = "Opacity",
            valueText = "${opacityVal.toInt()}%",
            isKeyframed = selectedLayer.transform.opacity.hasKeyframes()
        ) {
            Slider(
                value = opacityVal,
                onValueChange = { opacityVal = it },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
            )
        }

        // Rotation
        PropertyRow(
            name = "Rotation",
            valueText = "${selectedLayer.transform.rotation.defaultValue.toInt()}°",
            isKeyframed = selectedLayer.transform.rotation.hasKeyframes()
        )

        // Position
        val pos = selectedLayer.transform.position.defaultValue
        PropertyRow(
            name = "Position",
            valueText = "${pos.x.toInt()}, ${pos.y.toInt()}",
            isKeyframed = selectedLayer.transform.position.hasKeyframes()
        )

        // Scale
        val scl = selectedLayer.transform.scale.defaultValue
        PropertyRow(
            name = "Scale",
            valueText = "${scl.x.toInt()}%, ${scl.y.toInt()}%",
            isKeyframed = selectedLayer.transform.scale.hasKeyframes()
        )

        // Effects Stack
        Text(
            text = "EFFECTS & FILTERS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp)
        )

        listOf("Gaussian Blur", "Glow & Bloom", "Color Wheels / LUT", "Turbulent Displace").forEach { effectName ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(16.dp))
                Text(effectName, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text("fx", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentPrimary)
            }
        }
    }
}

@Composable
private fun PropertyRow(
    name: String,
    valueText: String,
    isKeyframed: Boolean,
    content: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Stopwatch icon
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Stopwatch",
                    tint = if (isKeyframed) KeyframeAmber else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(name, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(start = 6.dp))
            }
            Text(valueText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        content?.invoke()
    }
}

@Composable
private fun BlendModeDropdown(currentMode: BlendMode) {
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
                BlendMode.DIFFERENCE
            ).forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = { expanded = false }
                )
            }
        }
    }
}
