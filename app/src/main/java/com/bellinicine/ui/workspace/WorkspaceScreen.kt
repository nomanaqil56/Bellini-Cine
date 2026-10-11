package com.bellinicine.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellinicine.ui.grapheditor.GraphEditorView
import com.bellinicine.ui.inspector.PropertyInspector
import com.bellinicine.ui.theme.AccentPrimary
import com.bellinicine.ui.theme.BorderDark
import com.bellinicine.ui.theme.PanelDark
import com.bellinicine.ui.theme.PanelHeader
import com.bellinicine.ui.theme.TextMuted
import com.bellinicine.ui.theme.TextPrimary
import com.bellinicine.ui.theme.TextSecondary
import com.bellinicine.ui.timeline.TimelineView
import com.bellinicine.ui.viewport.CompositionCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val selectedLayer = state.activeComposition.layers.firstOrNull { it.id == state.selectedLayerId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Bellini Cine",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Text(
                            text = " • ${state.activeComposition.name} (${state.activeComposition.width}x${state.activeComposition.height})",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { /* Export modal / Render queue */ },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.height(32.dp).padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Export", fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PanelHeader)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = PanelHeader,
                modifier = Modifier.height(56.dp)
            ) {
                NavigationBarItem(
                    selected = state.activeTab == WorkspaceTab.TIMELINE,
                    onClick = { viewModel.setActiveTab(WorkspaceTab.TIMELINE) },
                    icon = { Icon(Icons.Default.ViewTimeline, contentDescription = "Timeline") },
                    label = { Text("Timeline", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentPrimary,
                        selectedTextColor = AccentPrimary,
                        indicatorColor = PanelDark
                    )
                )

                NavigationBarItem(
                    selected = state.activeTab == WorkspaceTab.GRAPH_EDITOR,
                    onClick = { viewModel.setActiveTab(WorkspaceTab.GRAPH_EDITOR) },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = "Graph Editor") },
                    label = { Text("Graph Editor", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentPrimary,
                        selectedTextColor = AccentPrimary,
                        indicatorColor = PanelDark
                    )
                )

                NavigationBarItem(
                    selected = state.activeTab == WorkspaceTab.EFFECTS_LIBRARY,
                    onClick = { viewModel.setActiveTab(WorkspaceTab.EFFECTS_LIBRARY) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Inspector") },
                    label = { Text("Inspector", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentPrimary,
                        selectedTextColor = AccentPrimary,
                        indicatorColor = PanelDark
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PanelDark)
        ) {
            // Upper Half: Composition Viewer Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CompositionCanvas(
                    composition = state.activeComposition,
                    evaluatedLayers = state.evaluatedLayers,
                    selectedLayerId = state.selectedLayerId
                )
            }

            // Lower Half: Tabbed Workspace Panel (Timeline, Graph Editor, Inspector)
            Surface(
                color = PanelDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (state.activeTab) {
                    WorkspaceTab.TIMELINE -> {
                        TimelineView(
                            composition = state.activeComposition,
                            currentFrame = state.currentFrame,
                            isPlaying = state.isPlaying,
                            selectedLayerId = state.selectedLayerId,
                            onSeek = { viewModel.seekToFrame(it) },
                            onStepFrame = { viewModel.stepFrame(it) },
                            onTogglePlay = { viewModel.togglePlayback() },
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                            onToggleSolo = { viewModel.toggleLayerSolo(it) },
                            onToggleLock = { viewModel.toggleLayerLock(it) },
                            onAddSolid = { viewModel.addSolidLayer("New Solid", 0xFFE040FB, 400, 400) },
                            onAddText = { viewModel.addTextLayer("Cine Motion") }
                        )
                    }
                    WorkspaceTab.GRAPH_EDITOR -> {
                        GraphEditorView(
                            composition = state.activeComposition,
                            selectedLayer = selectedLayer,
                            currentFrame = state.currentFrame,
                            isSpeedGraph = state.isSpeedGraph,
                            onToggleSpeedGraph = { viewModel.toggleSpeedGraph(it) }
                        )
                    }
                    WorkspaceTab.EFFECTS_LIBRARY -> {
                        PropertyInspector(
                            selectedLayer = selectedLayer
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
