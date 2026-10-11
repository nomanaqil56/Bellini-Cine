package com.bellinicine.ui.workspace

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Undo
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellinicine.core.render.ExportConfig
import com.bellinicine.core.render.ExportEngine
import com.bellinicine.core.storage.ProjectRepository
import com.bellinicine.core.storage.ProjectSummary
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val selectedLayer = state.activeComposition.layers.firstOrNull { it.id == state.selectedLayerId }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        com.bellinicine.core.render.VideoFrameDecoder.appContext = context.applicationContext
        viewModel.setAutosaveContext(context.applicationContext)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.triggerAutosave(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.triggerAutosave(context)
        }
    }

    var pendingRelinkAssetId by remember { mutableStateOf<String?>(null) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.importMediaAsset(context, uri.toString())
        }
    }

    val relinkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val assetId = pendingRelinkAssetId
        if (uri != null && assetId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.relinkMediaAsset(assetId, uri.toString())
        }
        pendingRelinkAssetId = null
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    // Project Manager Dialog Button
                    IconButton(
                        onClick = {
                            viewModel.refreshProjects(context)
                            viewModel.showProjectDialog(true)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Project Manager",
                            tint = AccentPrimary
                        )
                    }

                    // Undo button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.canUndo,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) TextPrimary else TextMuted
                        )
                    }

                    // Redo button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = state.canRedo,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) TextPrimary else TextMuted
                        )
                    }

                    // Quick Save Button
                    IconButton(
                        onClick = { viewModel.saveCurrentProject(context) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Project",
                            tint = TextSecondary
                        )
                    }

                    // Export Button
                    Button(
                        onClick = { viewModel.showExportDialog(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.height(32.dp).padding(start = 6.dp, end = 8.dp)
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
                    icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Graph Editor") },
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
                    selectedLayerId = state.selectedLayerId,
                    onUpdatePosition = { x, y -> viewModel.updateLayerPosition(x, y) },
                    onUpdateScale = { sx, sy -> viewModel.updateLayerScale(sx, sy) },
                    onUpdateRotation = { deg -> viewModel.updateLayerRotation(deg) },
                    onUpdateAnchorPoint = { ax, ay -> viewModel.updateLayerAnchorPoint(ax, ay) }
                )
            }

            // Lower Half: Tabbed Workspace Panel
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
                            onAddSolid = { viewModel.addSolidLayer("New Solid", 0xFFE040FB, 500, 500) },
                            onAddText = { viewModel.addTextLayer("Cine Motion") },
                            onSplitLayer = { viewModel.splitLayerAtPlayhead(it) },
                            onDuplicateLayer = { viewModel.duplicateLayer(it) },
                            onDeleteLayer = { viewModel.deleteLayer(it) },
                            onReorderLayer = { id, up -> viewModel.reorderLayer(id, up) },
                            onTrimInPoint = { id, f -> viewModel.trimLayerInPoint(id, f) },
                            onTrimOutPoint = { id, f -> viewModel.trimLayerOutPoint(id, f) },
                            onMoveLayerTime = { id, df -> viewModel.moveLayerTime(id, df) }
                        )
                    }
                    WorkspaceTab.GRAPH_EDITOR -> {
                        GraphEditorView(
                            composition = state.activeComposition,
                            selectedLayer = selectedLayer,
                            currentFrame = state.currentFrame,
                            isSpeedGraph = state.isSpeedGraph,
                            onToggleSpeedGraph = { viewModel.toggleSpeedGraph(it) },
                            onUpdateKeyframe = { f, v, interp -> viewModel.updateKeyframe(f, v, interp) }
                        )
                    }
                    WorkspaceTab.EFFECTS_LIBRARY -> {
                        PropertyInspector(
                            selectedLayer = selectedLayer,
                            onUpdateOpacity = { viewModel.updateLayerOpacity(it) },
                            onUpdateRotation = { viewModel.updateLayerRotation(it) },
                            onUpdatePosition = { x, y -> viewModel.updateLayerPosition(x, y) },
                            onUpdateScale = { sx, sy -> viewModel.updateLayerScale(sx, sy) },
                            onUpdateBlendMode = { viewModel.updateLayerBlendMode(it) },
                            onUpdateTrackMatte = { viewModel.updateLayerTrackMatte(it) },
                            onToggleKeyframing = { viewModel.togglePropertyKeyframing(it) },
                            onAddEffect = { id -> viewModel.addEffectToSelectedLayer(id) },
                            onRemoveEffect = { viewModel.removeEffectFromSelectedLayer(it) },
                            onToggleEffect = { viewModel.toggleEffectEnabled(it) },
                            onUpdateEffectSlider = { id, name, v -> viewModel.updateEffectSlider(id, name, v) },
                            onUpdateEffectCheckbox = { id, name, v -> viewModel.updateEffectCheckbox(id, name, v) },
                            onReorderEffect = { idx, up -> viewModel.reorderEffect(idx, up) }
                        )
                    }
                    WorkspaceTab.PROJECT_ASSETS -> {
                        ProjectMediaBin(
                            assets = state.project.assets,
                            context = context,
                            onImportMedia = {
                                mediaPickerLauncher.launch(arrayOf("image/*", "video/*"))
                            },
                            onAddToComp = { assetId ->
                                viewModel.addFootageLayerFromAsset(assetId)
                            },
                            onRelink = { assetId ->
                                pendingRelinkAssetId = assetId
                                relinkPickerLauncher.launch(arrayOf("image/*", "video/*"))
                            }
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    // Project Manager Dialog
    if (state.showProjectDialog) {
        ProjectManagerDialog(
            projectList = state.projectList,
            hasAutosave = state.hasAutosave,
            onDismiss = { viewModel.showProjectDialog(false) },
            onCreateNew = { name ->
                viewModel.createNewProject(name)
                viewModel.showProjectDialog(false)
            },
            onSave = {
                viewModel.saveCurrentProject(context)
            },
            onLoad = { projId ->
                viewModel.loadProject(context, projId)
                viewModel.showProjectDialog(false)
            },
            onRestoreAutosave = {
                viewModel.restoreAutosave(context)
                viewModel.showProjectDialog(false)
            },
            onDelete = { projId ->
                ProjectRepository.deleteProject(context, projId)
                viewModel.refreshProjects(context)
            }
        )
    }

    // Export Dialog
    if (state.showExportDialog) {
        ExportDialog(
            isExporting = state.isExporting,
            progress = state.exportProgress,
            resultPath = state.exportResultPath,
            onDismiss = {
                if (!state.isExporting) viewModel.showExportDialog(false)
            },
            onStartExport = { config ->
                viewModel.setExportProgress(0f, true)
                coroutineScope.launch {
                    val result = ExportEngine.exportComposition(
                        context = context,
                        composition = state.activeComposition,
                        config = config,
                        onProgress = { p -> viewModel.setExportProgress(p, true) }
                    )
                    when (result) {
                        is com.bellinicine.core.render.ExportResult.Success -> {
                            viewModel.setExportProgress(1f, false, result.filePath)
                            viewModel.setStatusMessage("Export succeeded: ${java.io.File(result.filePath).name} (${result.format})")
                        }
                        is com.bellinicine.core.render.ExportResult.Failure -> {
                            viewModel.setExportProgress(0f, false, null)
                            viewModel.setStatusMessage("Export failed: ${result.errorMessage}")
                        }
                        is com.bellinicine.core.render.ExportResult.Cancelled -> {
                            viewModel.setExportProgress(0f, false, null)
                            viewModel.setStatusMessage("Export was cancelled.")
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ExportDialog(
    isExporting: Boolean,
    progress: Float,
    resultPath: String?,
    onDismiss: () -> Unit,
    onStartExport: (ExportConfig) -> Unit
) {
    var selectedPreset by remember { mutableStateOf("1080p Vertical (9:16)") }
    var selectedFps by remember { mutableStateOf(30f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Composition", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (resultPath != null) {
                    Text("Export Complete!", fontWeight = FontWeight.Bold, color = AccentPrimary)
                    Text("Saved to:\n$resultPath", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                } else if (isExporting) {
                    Text("Rendering frames...", fontSize = 12.sp, color = TextSecondary)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = AccentPrimary
                    )
                    Text("${(progress * 100).toInt()}% completed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                } else {
                    Text("Preset: $selectedPreset", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedPreset = "1080p Vertical (9:16)" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedPreset.contains("Vertical")) AccentPrimary else BorderDark
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("9:16", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { selectedPreset = "1080p Landscape (16:9)" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedPreset.contains("Landscape")) AccentPrimary else BorderDark
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("16:9", fontSize = 11.sp)
                        }
                    }

                    Text("Frame Rate: ${selectedFps.toInt()} fps", fontSize = 12.sp, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            if (resultPath != null || !isExporting) {
                Button(
                    onClick = {
                        if (resultPath != null) {
                            onDismiss()
                        } else {
                            val (w, h) = if (selectedPreset.contains("Vertical")) Pair(1080, 1920) else Pair(1920, 1080)
                            onStartExport(ExportConfig(width = w, height = h, fps = selectedFps))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text(if (resultPath != null) "Done" else "Start Render")
                }
            }
        },
        dismissButton = {
            if (!isExporting) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        containerColor = PanelHeader
    )
}

@Composable
private fun ProjectManagerDialog(
    projectList: List<ProjectSummary>,
    hasAutosave: Boolean,
    onDismiss: () -> Unit,
    onCreateNew: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: (String) -> Unit,
    onRestoreAutosave: () -> Unit,
    onDelete: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = AccentPrimary)
                Text("Project Manager", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                // Top Action Buttons: New, Save, Restore
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onCreateNew("Bellini Project ${System.currentTimeMillis() % 1000}") },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("New", fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                    }

                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = BorderDark),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Save", fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                    }

                    if (hasAutosave) {
                        Button(
                            onClick = onRestoreAutosave,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Recover", fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }

                Text("Saved Projects (${projectList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))

                if (projectList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No saved projects found.", fontSize = 12.sp, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(projectList) { proj ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PanelDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLoad(proj.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(proj.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                        Text(
                                            "${proj.compositionCount} composition(s) • ${dateFormat.format(Date(proj.lastModified))}",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDelete(proj.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = PanelHeader
    )
}

@Composable
private fun ProjectMediaBin(
    assets: List<com.bellinicine.core.model.FootageAsset>,
    context: android.content.Context,
    onImportMedia: () -> Unit,
    onAddToComp: (String) -> Unit,
    onRelink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelDark)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("PROJECT MEDIA BIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Text("${assets.size} imported asset(s)", fontSize = 11.sp, color = TextSecondary)
            }
            Button(
                onClick = onImportMedia,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                Text("Import Media", fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }

        if (assets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(PanelHeader, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.VideoFile, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Media Assets Imported", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                    Text("Tap 'Import Media' above to import videos or images via Android Storage Access Framework.", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(assets) { asset ->
                    val isAvailable = remember(asset.uri) {
                        com.bellinicine.core.render.VideoFrameDecoder.isMediaAvailable(context, asset.uri)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelHeader),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (asset.mediaType == "video") Icons.Default.VideoFile else Icons.Default.Image,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(asset.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                                Text(
                                    "${asset.width}x${asset.height} • ${asset.mediaType.uppercase()}${if (asset.durationSeconds > 0) " • ${String.format(java.util.Locale.US, "%.1fs", asset.durationSeconds)}" else ""}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                if (!isAvailable) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
                                        Text("Media Missing / Unlinked", fontSize = 10.sp, color = Color(0xFFFF5252), modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }

                            if (!isAvailable) {
                                Button(
                                    onClick = { onRelink(asset.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Text("Relink", fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp))
                                }
                            } else {
                                Button(
                                    onClick = { onAddToComp(asset.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Text("Add to Comp", fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
