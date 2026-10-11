package com.bellinicine.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bellinicine.core.engine.CompositionEvaluator
import com.bellinicine.core.engine.EvaluatedLayer
import com.bellinicine.core.model.AnimatedProperty
import com.bellinicine.core.model.BlendMode
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Effect
import com.bellinicine.core.model.EffectCategory
import com.bellinicine.core.model.EffectParameter
import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.KeyframeInterpolation
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.Project
import com.bellinicine.core.model.TrackMatteType
import com.bellinicine.core.render.CompositorPipeline
import android.content.Context
import android.net.Uri
import com.bellinicine.core.model.FootageAsset
import com.bellinicine.core.render.EffectRegistry
import com.bellinicine.core.render.VideoFrameDecoder
import com.bellinicine.core.storage.ProjectRepository
import com.bellinicine.core.storage.ProjectSummary
import com.bellinicine.core.model.Transform
import com.bellinicine.core.model.Vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class WorkspaceTab {
    TIMELINE,
    GRAPH_EDITOR,
    EFFECTS_LIBRARY,
    PROJECT_ASSETS,
    RENDER_QUEUE
}

data class WorkspaceUiState(
    val project: Project,
    val activeComposition: Composition,
    val currentFrame: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedLayerId: String? = null,
    val selectedPropertyPath: String = "transform.position",
    val activeTab: WorkspaceTab = WorkspaceTab.TIMELINE,
    val isSpeedGraph: Boolean = true,
    val evaluatedLayers: List<EvaluatedLayer> = emptyList(),
    val isLooping: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showExportDialog: Boolean = false,
    val exportProgress: Float = 0f,
    val isExporting: Boolean = false,
    val exportResultPath: String? = null,
    val showProjectDialog: Boolean = false,
    val projectList: List<ProjectSummary> = emptyList(),
    val hasAutosave: Boolean = false,
    val statusMessage: String? = null
)

class WorkspaceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private val undoStack = mutableListOf<Composition>()
    private val redoStack = mutableListOf<Composition>()

    init {
        CompositorPipeline.compositionProvider = { compId ->
            _uiState.value.project.compositions.firstOrNull { it.id == compId }
        }
        reevaluateCurrentFrame()
    }

    private fun createInitialState(): WorkspaceUiState {
        val textLayer = Layer(
            id = "layer-text-1",
            name = "Title: Bellini Cine",
            type = LayerType.TEXT,
            data = LayerData.Text("BELLINI CINE", fontSize = 68f, fontColor = 0xFFFFFFFF),
            inPointFrame = 0L,
            outPointFrame = 300L,
            transform = Transform(
                position = AnimatedProperty(
                    name = "Position",
                    defaultValue = Vec3(540f, 600f, 0f),
                    keyframes = listOf(
                        Keyframe(0L, Vec3(540f, 400f, 0f), KeyframeInterpolation.BEZIER),
                        Keyframe(45L, Vec3(540f, 600f, 0f), KeyframeInterpolation.BEZIER)
                    ),
                    isKeyframed = true
                ),
                scale = AnimatedProperty(
                    name = "Scale",
                    defaultValue = Vec3(100f, 100f, 100f),
                    keyframes = listOf(
                        Keyframe(0L, Vec3(0f, 0f, 100f), KeyframeInterpolation.BEZIER),
                        Keyframe(45L, Vec3(100f, 100f, 100f), KeyframeInterpolation.BEZIER)
                    ),
                    isKeyframed = true
                ),
                opacity = AnimatedProperty(
                    name = "Opacity",
                    defaultValue = 100f,
                    keyframes = listOf(
                        Keyframe(0L, 0f, KeyframeInterpolation.BEZIER),
                        Keyframe(30L, 100f, KeyframeInterpolation.BEZIER)
                    ),
                    isKeyframed = true
                )
            ),
            labelColorIndex = 4
        )

        val starShapeLayer = Layer(
            id = "layer-star-vector",
            name = "Star Vector Shape",
            type = LayerType.SHAPE,
            data = LayerData.Shape(
                shapeType = "Star",
                width = 320f,
                height = 320f,
                points = 5,
                fillColor = 0xFFFFD740,
                strokeColor = 0xFFFFFFFF,
                strokeWidth = 6f
            ),
            inPointFrame = 15L,
            outPointFrame = 270L,
            transform = Transform(
                anchorPoint = AnimatedProperty("Anchor Point", Vec3(160f, 160f, 0f)),
                position = AnimatedProperty("Position", Vec3(540f, 1100f, 0f)),
                rotation = AnimatedProperty(
                    name = "Rotation",
                    defaultValue = 0f,
                    keyframes = listOf(
                        Keyframe(15L, -180f, KeyframeInterpolation.BEZIER),
                        Keyframe(60L, 0f, KeyframeInterpolation.BEZIER)
                    ),
                    isKeyframed = true
                ),
                scale = AnimatedProperty(
                    name = "Scale",
                    defaultValue = Vec3(100f, 100f, 100f),
                    keyframes = listOf(
                        Keyframe(15L, Vec3(10f, 10f, 100f), KeyframeInterpolation.BEZIER),
                        Keyframe(60L, Vec3(100f, 100f, 100f), KeyframeInterpolation.BEZIER)
                    ),
                    isKeyframed = true
                )
            ),
            labelColorIndex = 2
        )

        val backgroundSolid = Layer(
            id = "layer-bg-solid",
            name = "Deep Zinc Background",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFF16161D, width = 1080, height = 1920),
            inPointFrame = 0L,
            outPointFrame = 300L,
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(540f, 960f, 0f)),
                anchorPoint = AnimatedProperty("Anchor Point", Vec3(540f, 960f, 0f))
            ),
            isLocked = true,
            labelColorIndex = 0
        )

        val comp = Composition(
            id = "comp-main",
            name = "Main Composition",
            width = 1080,
            height = 1920,
            frameRate = 30f,
            durationFrames = 150L,
            layers = listOf(backgroundSolid, starShapeLayer, textLayer)
        )

        val project = Project(
            id = "proj-1",
            name = "Bellini Cine Project",
            compositions = listOf(comp),
            activeCompositionId = comp.id
        )

        return WorkspaceUiState(
            project = project,
            activeComposition = comp,
            currentFrame = 0L,
            selectedLayerId = textLayer.id
        )
    }

    // --- COMPOSITION & PROJECT STATE MANAGEMENT ---

    fun updateActiveCompositionInProject(
        currentProject: Project,
        updatedComp: Composition
    ): Project {
        val existingComps = currentProject.compositions
        val updatedComps = if (existingComps.any { it.id == updatedComp.id }) {
            existingComps.map { if (it.id == updatedComp.id) updatedComp else it }
        } else {
            existingComps + updatedComp
        }
        return currentProject.copy(
            compositions = updatedComps,
            activeCompositionId = updatedComp.id
        )
    }

    private val autosaveMutex = Mutex()
    private var autosaveContext: Context? = null
    private var autosaveJob: Job? = null

    fun setAutosaveContext(context: Context) {
        this.autosaveContext = context.applicationContext
    }

    /**
     * Executes non-blocking serialized autosave on Dispatchers.IO.
     * Guarantees that the newest project state is saved and concurrent writes are synchronized.
     */
    suspend fun performSerializedAutosave(context: Context, project: Project): Boolean = withContext(Dispatchers.IO) {
        autosaveMutex.withLock {
            try {
                ProjectRepository.autosaveProject(context, project)
                true
            } catch (e: Exception) {
                android.util.Log.e("WorkspaceViewModel", "Autosave write failed: ${e.message}")
                false
            }
        }
    }

    fun scheduleAutosave(delayMs: Long = 1200L) {
        val ctx = autosaveContext ?: return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(delayMs)
            val currentProj = _uiState.value.project
            val projToSave = updateActiveCompositionInProject(currentProj, _uiState.value.activeComposition)
            performSerializedAutosave(ctx, projToSave)
        }
    }

    private fun commitCompositionUpdate(
        updatedComp: Composition,
        selectedLayerId: String? = _uiState.value.selectedLayerId
    ) {
        _uiState.update { state ->
            val updatedProj = updateActiveCompositionInProject(state.project, updatedComp)
            state.copy(
                project = updatedProj,
                activeComposition = updatedComp,
                selectedLayerId = selectedLayerId
            )
        }
        reevaluateCurrentFrame()
        scheduleAutosave()
    }

    // --- UNDO / REDO HISTORY ---

    private fun pushHistoryState() {
        undoStack.add(_uiState.value.activeComposition)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
        updateHistoryFlags()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(_uiState.value.activeComposition)
            commitCompositionUpdate(prev)
            updateHistoryFlags()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(_uiState.value.activeComposition)
            commitCompositionUpdate(next)
            updateHistoryFlags()
        }
    }

    private fun updateHistoryFlags() {
        _uiState.update {
            it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
        }
    }

    // --- TWO-WAY LAYER MUTATIONS ---

    private fun mutateSelectedLayer(transform: (Layer) -> Layer) {
        val selectedId = _uiState.value.selectedLayerId ?: return
        pushHistoryState()
        val updatedLayers = _uiState.value.activeComposition.layers.map { layer ->
            if (layer.id == selectedId) transform(layer) else layer
        }
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp)
    }

    fun updateLayerOpacity(opacity: Float) {
        mutateSelectedLayer { layer ->
            val cur = layer.transform.opacity
            val updated = if (cur.isKeyframed) {
                val newKfs = addOrReplaceKeyframe(cur.keyframes, _uiState.value.currentFrame, opacity)
                cur.copy(keyframes = newKfs)
            } else {
                cur.copy(defaultValue = opacity)
            }
            layer.copy(transform = layer.transform.copy(opacity = updated))
        }
    }

    fun updateLayerRotation(rotation: Float) {
        mutateSelectedLayer { layer ->
            val cur = layer.transform.rotation
            val updated = if (cur.isKeyframed) {
                val newKfs = addOrReplaceKeyframe(cur.keyframes, _uiState.value.currentFrame, rotation)
                cur.copy(keyframes = newKfs)
            } else {
                cur.copy(defaultValue = rotation)
            }
            layer.copy(transform = layer.transform.copy(rotation = updated))
        }
    }

    fun updateLayerPosition(x: Float, y: Float) {
        mutateSelectedLayer { layer ->
            val cur = layer.transform.position
            val newVal = Vec3(x, y, cur.defaultValue.z)
            val updated = if (cur.isKeyframed) {
                val newKfs = addOrReplaceKeyframe(cur.keyframes, _uiState.value.currentFrame, newVal)
                cur.copy(keyframes = newKfs)
            } else {
                cur.copy(defaultValue = newVal)
            }
            layer.copy(transform = layer.transform.copy(position = updated))
        }
    }

    fun updateLayerScale(sx: Float, sy: Float) {
        mutateSelectedLayer { layer ->
            val cur = layer.transform.scale
            val newVal = Vec3(sx, sy, cur.defaultValue.z)
            val updated = if (cur.isKeyframed) {
                val newKfs = addOrReplaceKeyframe(cur.keyframes, _uiState.value.currentFrame, newVal)
                cur.copy(keyframes = newKfs)
            } else {
                cur.copy(defaultValue = newVal)
            }
            layer.copy(transform = layer.transform.copy(scale = updated))
        }
    }

    fun updateLayerAnchorPoint(ax: Float, ay: Float) {
        mutateSelectedLayer { layer ->
            val cur = layer.transform.anchorPoint
            val newVal = Vec3(ax, ay, cur.defaultValue.z)
            val updated = cur.copy(defaultValue = newVal)
            layer.copy(transform = layer.transform.copy(anchorPoint = updated))
        }
    }

    fun updateLayerBlendMode(blendMode: BlendMode) {
        mutateSelectedLayer { layer ->
            layer.copy(blendMode = blendMode)
        }
    }

    fun updateLayerTrackMatte(trackMatteType: TrackMatteType) {
        mutateSelectedLayer { layer ->
            layer.copy(trackMatteType = trackMatteType)
        }
    }

    fun togglePropertyKeyframing(propName: String) {
        mutateSelectedLayer { layer ->
            when (propName.lowercase()) {
                "opacity" -> {
                    val p = layer.transform.opacity
                    val nextKeyframed = !p.isKeyframed
                    val kfs = if (nextKeyframed) {
                        listOf(Keyframe(_uiState.value.currentFrame, p.defaultValue))
                    } else emptyList()
                    layer.copy(transform = layer.transform.copy(opacity = p.copy(isKeyframed = nextKeyframed, keyframes = kfs)))
                }
                "rotation" -> {
                    val p = layer.transform.rotation
                    val nextKeyframed = !p.isKeyframed
                    val kfs = if (nextKeyframed) {
                        listOf(Keyframe(_uiState.value.currentFrame, p.defaultValue))
                    } else emptyList()
                    layer.copy(transform = layer.transform.copy(rotation = p.copy(isKeyframed = nextKeyframed, keyframes = kfs)))
                }
                "position" -> {
                    val p = layer.transform.position
                    val nextKeyframed = !p.isKeyframed
                    val kfs = if (nextKeyframed) {
                        listOf(Keyframe(_uiState.value.currentFrame, p.defaultValue))
                    } else emptyList()
                    layer.copy(transform = layer.transform.copy(position = p.copy(isKeyframed = nextKeyframed, keyframes = kfs)))
                }
                "scale" -> {
                    val p = layer.transform.scale
                    val nextKeyframed = !p.isKeyframed
                    val kfs = if (nextKeyframed) {
                        listOf(Keyframe(_uiState.value.currentFrame, p.defaultValue))
                    } else emptyList()
                    layer.copy(transform = layer.transform.copy(scale = p.copy(isKeyframed = nextKeyframed, keyframes = kfs)))
                }
                else -> layer
            }
        }
    }

    private fun <T> addOrReplaceKeyframe(existing: List<Keyframe<T>>, frame: Long, value: T): List<Keyframe<T>> {
        val withoutCurrent = existing.filter { it.frame != frame }
        return (withoutCurrent + Keyframe(frame, value)).sortedBy { it.frame }
    }

    // --- EFFECTS MANAGEMENT ---

    fun addEffectToSelectedLayer(effectId: String) {
        val effect = EffectRegistry.createEffect(effectId)
        if (effect == null) {
            setStatusMessage("Unsupported or unregistered effect: $effectId")
            return
        }
        mutateSelectedLayer { layer ->
            layer.copy(effects = layer.effects + effect)
        }
    }

    fun addEffectToSelectedLayer(name: String, category: EffectCategory) {
        val match = EffectRegistry.getAll().firstOrNull { it.displayName.equals(name, ignoreCase = true) || it.effectId == name }
        val id = match?.effectId ?: effectIdForCategory(category, name)
        if (id == null) {
            setStatusMessage("Unsupported or unregistered effect: $name")
            return
        }
        addEffectToSelectedLayer(id)
    }

    private fun effectIdForCategory(category: EffectCategory, name: String): String? {
        return when {
            name.contains("blur", ignoreCase = true) -> EffectRegistry.ID_FAST_BLUR
            name.contains("glow", ignoreCase = true) -> EffectRegistry.ID_GLOW
            name.contains("brightness", ignoreCase = true) || name.contains("contrast", ignoreCase = true) -> EffectRegistry.ID_BRIGHTNESS_CONTRAST
            name.contains("tint", ignoreCase = true) -> EffectRegistry.ID_TINT
            name.contains("invert", ignoreCase = true) -> EffectRegistry.ID_INVERT
            name.contains("hue", ignoreCase = true) || name.contains("sat", ignoreCase = true) -> EffectRegistry.ID_HUE_SATURATION
            name.contains("solid", ignoreCase = true) -> EffectRegistry.ID_SOLID_COMPOSITE
            else -> null
        }
    }

    fun removeEffectFromSelectedLayer(effectId: String) {
        mutateSelectedLayer { layer ->
            layer.copy(effects = layer.effects.filter { it.id != effectId })
        }
    }

    fun toggleEffectEnabled(effectId: String) {
        mutateSelectedLayer { layer ->
            layer.copy(effects = layer.effects.map { eff ->
                if (eff.id == effectId) eff.copy(enabled = !eff.enabled) else eff
            })
        }
    }

    fun updateEffectSlider(effectId: String, paramName: String, value: Float) {
        mutateSelectedLayer { layer ->
            val updated = layer.effects.map { eff ->
                if (eff.id == effectId) {
                    val updatedParams = eff.parameters.map { param ->
                        when {
                            param.name.equals(paramName, ignoreCase = true) && param is EffectParameter.Slider ->
                                param.copy(property = param.property.copy(defaultValue = value))
                            param.name.equals(paramName, ignoreCase = true) && param is EffectParameter.Angle ->
                                param.copy(property = param.property.copy(defaultValue = value))
                            else -> param
                        }
                    }
                    eff.copy(parameters = updatedParams)
                } else eff
            }
            layer.copy(effects = updated)
        }
    }

    fun updateEffectCheckbox(effectId: String, paramName: String, value: Boolean) {
        mutateSelectedLayer { layer ->
            val updated = layer.effects.map { eff ->
                if (eff.id == effectId) {
                    val updatedParams = eff.parameters.map { param ->
                        if (param.name.equals(paramName, ignoreCase = true) && param is EffectParameter.Checkbox) {
                            param.copy(value = value)
                        } else param
                    }
                    eff.copy(parameters = updatedParams)
                } else eff
            }
            layer.copy(effects = updated)
        }
    }

    fun reorderEffect(effectIndex: Int, moveUp: Boolean) {
        mutateSelectedLayer { layer ->
            val effects = layer.effects.toMutableList()
            val targetIndex = if (moveUp) effectIndex - 1 else effectIndex + 1
            if (effectIndex in effects.indices && targetIndex in effects.indices) {
                val temp = effects[effectIndex]
                effects[effectIndex] = effects[targetIndex]
                effects[targetIndex] = temp
                layer.copy(effects = effects)
            } else layer
        }
    }

    // --- TIMELINE PLAYBACK & CONTROLS ---

    fun seekToFrame(frame: Long) {
        val total = _uiState.value.activeComposition.durationFrames
        val clamped = frame.coerceIn(0L, total)
        _uiState.update { it.copy(currentFrame = clamped) }
        reevaluateCurrentFrame()
    }

    fun stepFrame(delta: Long) {
        seekToFrame(_uiState.value.currentFrame + delta)
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) pause() else play()
    }

    fun play() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true) }
        val fps = _uiState.value.activeComposition.frameRate
        val frameDurationMs = (1000f / fps).toLong()

        playbackJob = viewModelScope.launch {
            while (isActive && _uiState.value.isPlaying) {
                val nextFrame = _uiState.value.currentFrame + 1
                val total = _uiState.value.activeComposition.durationFrames
                if (nextFrame >= total) {
                    if (_uiState.value.isLooping) {
                        seekToFrame(0L)
                    } else {
                        pause()
                        break
                    }
                } else {
                    seekToFrame(nextFrame)
                }
                delay(frameDurationMs)
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun selectLayer(layerId: String?) {
        _uiState.update { it.copy(selectedLayerId = layerId) }
    }

    fun setActiveTab(tab: WorkspaceTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun toggleSpeedGraph(isSpeed: Boolean) {
        _uiState.update { it.copy(isSpeedGraph = isSpeed) }
    }

    fun addSolidLayer(name: String, color: Long, width: Int = 1080, height: Int = 1920) {
        pushHistoryState()
        val newLayer = Layer(
            id = "layer-${UUID.randomUUID()}",
            name = name,
            type = LayerType.SOLID,
            data = LayerData.Solid(color, width, height),
            inPointFrame = 0L,
            outPointFrame = _uiState.value.activeComposition.durationFrames,
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(width / 2f, height / 2f, 0f)),
                anchorPoint = AnimatedProperty("Anchor Point", Vec3(width / 2f, height / 2f, 0f))
            )
        )
        val updatedLayers = _uiState.value.activeComposition.layers + newLayer
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp, newLayer.id)
    }

    fun addTextLayer(text: String) {
        pushHistoryState()
        val newLayer = Layer(
            id = "layer-${UUID.randomUUID()}",
            name = "Text: $text",
            type = LayerType.TEXT,
            data = LayerData.Text(text, fontSize = 56f),
            inPointFrame = 0L,
            outPointFrame = _uiState.value.activeComposition.durationFrames,
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(540f, 960f, 0f)),
                anchorPoint = AnimatedProperty("Anchor Point", Vec3(0f, 0f, 0f))
            )
        )
        val updatedLayers = _uiState.value.activeComposition.layers + newLayer
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp, newLayer.id)
    }

    fun addShapeLayer(shapeType: String) {
        pushHistoryState()
        val newLayer = Layer(
            id = "layer-${UUID.randomUUID()}",
            name = "$shapeType Layer",
            type = LayerType.SHAPE,
            data = LayerData.Shape(shapeType = shapeType, width = 360f, height = 360f),
            inPointFrame = 0L,
            outPointFrame = _uiState.value.activeComposition.durationFrames,
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(540f, 960f, 0f)),
                anchorPoint = AnimatedProperty("Anchor Point", Vec3(180f, 180f, 0f))
            )
        )
        val updatedLayers = _uiState.value.activeComposition.layers + newLayer
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp, newLayer.id)
    }

    fun toggleLayerVisibility(layerId: String) {
        pushHistoryState()
        val updatedLayers = _uiState.value.activeComposition.layers.map { layer ->
            if (layer.id == layerId) layer.copy(isVisible = !layer.isVisible) else layer
        }
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp)
    }

    fun toggleLayerSolo(layerId: String) {
        val updatedLayers = _uiState.value.activeComposition.layers.map { layer ->
            if (layer.id == layerId) layer.copy(isSolo = !layer.isSolo) else layer
        }
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp)
    }

    fun toggleLayerLock(layerId: String) {
        val updatedLayers = _uiState.value.activeComposition.layers.map { layer ->
            if (layer.id == layerId) layer.copy(isLocked = !layer.isLocked) else layer
        }
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp)
    }

    fun showExportDialog(show: Boolean) {
        _uiState.update { it.copy(showExportDialog = show) }
    }

    fun setExportProgress(progress: Float, isExporting: Boolean, resultPath: String? = null) {
        _uiState.update {
            it.copy(
                exportProgress = progress,
                isExporting = isExporting,
                exportResultPath = resultPath
            )
        }
    }

    fun setStatusMessage(msg: String?) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    private fun reevaluateCurrentFrame() {
        val eval = CompositionEvaluator.evaluateComposition(
            _uiState.value.activeComposition,
            _uiState.value.currentFrame
        )
        _uiState.update { it.copy(evaluatedLayers = eval) }
    }

    // --- TIMELINE EDITING & LAYER OPERATIONS ---

    fun splitLayerAtPlayhead(layerId: String) {
        val comp = _uiState.value.activeComposition
        val frame = _uiState.value.currentFrame
        val layer = comp.layers.firstOrNull { it.id == layerId } ?: return
        if (frame <= layer.inPointFrame || frame >= layer.outPointFrame) return

        pushHistoryState()
        val firstPart = layer.copy(outPointFrame = frame)
        val secondPart = layer.copy(
            id = "layer-${UUID.randomUUID()}",
            name = "${layer.name} (Split)",
            inPointFrame = frame
        )

        val newLayers = mutableListOf<Layer>()
        comp.layers.forEach { l ->
            if (l.id == layerId) {
                newLayers.add(firstPart)
                newLayers.add(secondPart)
            } else {
                newLayers.add(l)
            }
        }
        val updatedComp = comp.copy(layers = newLayers)
        commitCompositionUpdate(updatedComp, secondPart.id)
    }

    fun duplicateLayer(layerId: String) {
        val comp = _uiState.value.activeComposition
        val layer = comp.layers.firstOrNull { it.id == layerId } ?: return
        pushHistoryState()

        val copy = layer.copy(
            id = "layer-${UUID.randomUUID()}",
            name = "${layer.name} Copy"
        )
        val newLayers = mutableListOf<Layer>()
        comp.layers.forEach { l ->
            newLayers.add(l)
            if (l.id == layerId) {
                newLayers.add(copy)
            }
        }
        val updatedComp = comp.copy(layers = newLayers)
        commitCompositionUpdate(updatedComp, copy.id)
    }

    fun deleteLayer(layerId: String) {
        val comp = _uiState.value.activeComposition
        if (comp.layers.none { it.id == layerId }) return
        pushHistoryState()

        val newLayers = comp.layers.filter { it.id != layerId }
        val newSelected = newLayers.lastOrNull()?.id
        val updatedComp = comp.copy(layers = newLayers)
        commitCompositionUpdate(updatedComp, newSelected)
    }

    fun reorderLayer(layerId: String, moveUp: Boolean) {
        val comp = _uiState.value.activeComposition
        val idx = comp.layers.indexOfFirst { it.id == layerId }
        if (idx == -1) return

        val targetIdx = if (moveUp) idx + 1 else idx - 1
        if (targetIdx !in comp.layers.indices) return

        pushHistoryState()
        val mutableLayers = comp.layers.toMutableList()
        val temp = mutableLayers[idx]
        mutableLayers[idx] = mutableLayers[targetIdx]
        mutableLayers[targetIdx] = temp

        val updatedComp = comp.copy(layers = mutableLayers)
        commitCompositionUpdate(updatedComp)
    }

    fun trimLayerInPoint(layerId: String, newInPoint: Long) {
        val comp = _uiState.value.activeComposition
        val layer = comp.layers.firstOrNull { it.id == layerId } ?: return
        val clamped = newInPoint.coerceIn(0L, layer.outPointFrame - 1)

        val updatedLayers = comp.layers.map {
            if (it.id == layerId) it.copy(inPointFrame = clamped) else it
        }
        val updatedComp = comp.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp)
    }

    fun trimLayerOutPoint(layerId: String, newOutPoint: Long) {
        val comp = _uiState.value.activeComposition
        val layer = comp.layers.firstOrNull { it.id == layerId } ?: return
        val clamped = newOutPoint.coerceIn(layer.inPointFrame + 1, comp.durationFrames)

        val updatedLayers = comp.layers.map {
            if (it.id == layerId) it.copy(outPointFrame = clamped) else it
        }
        val updatedComp = comp.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp)
    }

    fun moveLayerTime(layerId: String, deltaFrames: Long) {
        val comp = _uiState.value.activeComposition
        val layer = comp.layers.firstOrNull { it.id == layerId } ?: return
        val duration = layer.outPointFrame - layer.inPointFrame
        val newIn = (layer.inPointFrame + deltaFrames).coerceAtLeast(0L)
        val newOut = newIn + duration

        val updatedLayers = comp.layers.map {
            if (it.id == layerId) it.copy(inPointFrame = newIn, outPointFrame = newOut) else it
        }
        val updatedComp = comp.copy(layers = updatedLayers)
        commitCompositionUpdate(updatedComp)
    }

    // --- GRAPH EDITOR & KEYFRAME EDITING ---

    fun updateKeyframe(frame: Long, value: Float, interpolation: KeyframeInterpolation) {
        mutateSelectedLayer { layer ->
            val cur = layer.transform.rotation
            if (cur.hasKeyframes()) {
                val updatedKfs = cur.keyframes.map { kf ->
                    if (kf.frame == frame) kf.copy(value = value, interpolation = interpolation) else kf
                }.sortedBy { it.frame }
                layer.copy(transform = layer.transform.copy(rotation = cur.copy(keyframes = updatedKfs)))
            } else {
                val op = layer.transform.opacity
                val updatedKfs = op.keyframes.map { kf ->
                    if (kf.frame == frame) kf.copy(value = value, interpolation = interpolation) else kf
                }.sortedBy { it.frame }
                layer.copy(transform = layer.transform.copy(opacity = op.copy(keyframes = updatedKfs)))
            }
        }
    }

    // --- PROJECT PERSISTENCE & AUTOSAVE ---

    fun showProjectDialog(show: Boolean) {
        _uiState.update { it.copy(showProjectDialog = show) }
    }

    fun refreshProjects(context: Context) {
        val summaries = ProjectRepository.getProjectSummaries(context)
        val hasAuto = ProjectRepository.hasAutosave(context)
        _uiState.update { it.copy(projectList = summaries, hasAutosave = hasAuto) }
    }

    fun saveCurrentProject(context: Context, customName: String? = null): File {
        val currentProj = _uiState.value.project
        val updatedProj = updateActiveCompositionInProject(currentProj, _uiState.value.activeComposition).copy(
            name = customName ?: currentProj.name
        )
        val file = ProjectRepository.saveProject(context, updatedProj)
        _uiState.update { it.copy(project = updatedProj, statusMessage = "Project '${updatedProj.name}' saved") }
        refreshProjects(context)
        return file
    }

    fun loadProject(context: Context, projectId: String): Boolean {
        val proj = ProjectRepository.loadProject(context, projectId) ?: return false
        val activeComp = proj.compositions.firstOrNull { it.id == proj.activeCompositionId }
            ?: proj.compositions.firstOrNull() ?: return false

        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            it.copy(
                project = proj,
                activeComposition = activeComp,
                currentFrame = 0L,
                selectedLayerId = activeComp.layers.firstOrNull()?.id,
                statusMessage = "Loaded project '${proj.name}'"
            )
        }
        reevaluateCurrentFrame()
        updateHistoryFlags()
        return true
    }

    fun createNewProject(
        name: String,
        width: Int = 1080,
        height: Int = 1920,
        fps: Float = 30f,
        durationFrames: Long = 150L
    ) {
        val newComp = Composition(
            id = "comp-${UUID.randomUUID()}",
            name = "Main Comp",
            width = width,
            height = height,
            frameRate = fps,
            durationFrames = durationFrames,
            layers = emptyList()
        )
        val newProj = Project(
            id = "proj-${UUID.randomUUID()}",
            name = name,
            compositions = listOf(newComp),
            activeCompositionId = newComp.id
        )
        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            it.copy(
                project = newProj,
                activeComposition = newComp,
                currentFrame = 0L,
                selectedLayerId = null,
                statusMessage = "Created new project '$name'"
            )
        }
        reevaluateCurrentFrame()
        updateHistoryFlags()
    }

    fun restoreAutosave(context: Context): Boolean {
        val autoProj = ProjectRepository.loadAutosave(context) ?: return false
        val comp = autoProj.compositions.firstOrNull { it.id == autoProj.activeCompositionId }
            ?: autoProj.compositions.firstOrNull() ?: return false
        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            it.copy(
                project = autoProj,
                activeComposition = comp,
                currentFrame = 0L,
                selectedLayerId = comp.layers.firstOrNull()?.id,
                statusMessage = "Restored autosave successfully"
            )
        }
        reevaluateCurrentFrame()
        updateHistoryFlags()
        return true
    }

    /**
     * Non-blocking immediate autosave for lifecycle callbacks (ON_PAUSE, ON_STOP, dispose).
     * Cancels any pending delayed debounced save and serializes file persistence off the main thread.
     */
    fun triggerAutosave(context: Context): Job {
        val currentProj = _uiState.value.project
        val projToSave = updateActiveCompositionInProject(currentProj, _uiState.value.activeComposition)
        autosaveJob?.cancel()
        return viewModelScope.launch(Dispatchers.IO) {
            performSerializedAutosave(context, projToSave)
        }
    }

    /**
     * Synchronous autosave for test environments or synchronous teardown checkpoints.
     */
    fun triggerAutosaveSync(context: Context): Boolean {
        val currentProj = _uiState.value.project
        val projToSave = updateActiveCompositionInProject(currentProj, _uiState.value.activeComposition)
        return try {
            ProjectRepository.autosaveProject(context, projToSave)
            true
        } catch (e: Exception) {
            android.util.Log.e("WorkspaceViewModel", "Synchronous autosave failed: ${e.message}")
            false
        }
    }

    // --- MULTI-COMPOSITION WORKFLOWS ---

    fun switchActiveComposition(compId: String) {
        val proj = _uiState.value.project
        val targetComp = proj.compositions.firstOrNull { it.id == compId } ?: return
        val updatedProj = updateActiveCompositionInProject(proj, _uiState.value.activeComposition).copy(
            activeCompositionId = compId
        )
        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            it.copy(
                project = updatedProj,
                activeComposition = targetComp,
                currentFrame = 0L,
                selectedLayerId = targetComp.layers.firstOrNull()?.id,
                statusMessage = "Switched to composition '${targetComp.name}'"
            )
        }
        reevaluateCurrentFrame()
        updateHistoryFlags()
        scheduleAutosave()
    }

    fun addCompositionToProject(
        name: String,
        width: Int = 1080,
        height: Int = 1920,
        fps: Float = 30f,
        durationFrames: Long = 150L
    ): Composition {
        val newComp = Composition(
            id = "comp-${UUID.randomUUID()}",
            name = name,
            width = width,
            height = height,
            frameRate = fps,
            durationFrames = durationFrames,
            layers = emptyList()
        )
        val proj = _uiState.value.project
        val updatedProj = updateActiveCompositionInProject(proj, _uiState.value.activeComposition).let {
            it.copy(compositions = it.compositions + newComp)
        }
        _uiState.update { it.copy(project = updatedProj, statusMessage = "Created composition '$name'") }
        scheduleAutosave()
        return newComp
    }

    // --- MEDIA BIN & FOOTAGE ASSET MANAGEMENT ---

    fun importMediaAsset(context: Context, uriString: String, displayName: String? = null) {
        val meta = VideoFrameDecoder.getMetadata(context, uriString)
        val name = displayName ?: Uri.parse(uriString).lastPathSegment ?: "Media Asset"
        val asset = FootageAsset(
            id = "asset-${UUID.randomUUID()}",
            name = name,
            uri = uriString,
            width = meta?.width ?: 1920,
            height = meta?.height ?: 1080,
            durationSeconds = if (meta != null && meta.durationUs > 0) meta.durationUs / 1_000_000f else 0f,
            frameRate = meta?.frameRate ?: 30f,
            mediaType = if (meta?.isVideo == true) "video" else "image"
        )
        val currentProj = _uiState.value.project
        val updatedProj = currentProj.copy(assets = currentProj.assets + asset)
        _uiState.update { it.copy(project = updatedProj, statusMessage = "Imported '${asset.name}'") }
        scheduleAutosave()
    }

    fun addFootageLayerFromAsset(assetId: String) {
        val asset = _uiState.value.project.assets.firstOrNull { it.id == assetId } ?: return
        pushHistoryState()
        val comp = _uiState.value.activeComposition
        val newLayer = Layer(
            id = "layer-${UUID.randomUUID()}",
            name = asset.name,
            type = LayerType.FOOTAGE,
            data = LayerData.Footage(
                assetId = asset.id,
                mediaUri = asset.uri,
                naturalWidth = asset.width,
                naturalHeight = asset.height
            ),
            inPointFrame = 0L,
            outPointFrame = if (asset.durationSeconds > 0) (asset.durationSeconds * comp.frameRate).toLong().coerceAtMost(comp.durationFrames) else comp.durationFrames,
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(comp.width / 2f, comp.height / 2f, 0f)),
                anchorPoint = AnimatedProperty("Anchor Point", Vec3(asset.width / 2f, asset.height / 2f, 0f))
            )
        )
        val updatedComp = comp.copy(layers = comp.layers + newLayer)
        commitCompositionUpdate(updatedComp, newLayer.id)
    }

    fun relinkMediaAsset(assetId: String, newUri: String) {
        val currentProj = _uiState.value.project
        val updatedAssets = currentProj.assets.map { asset ->
            if (asset.id == assetId) asset.copy(uri = newUri) else asset
        }
        val updatedComps = currentProj.compositions.map { comp ->
            val updatedLayers = comp.layers.map { layer ->
                if (layer.data is LayerData.Footage && layer.data.assetId == assetId) {
                    layer.copy(data = layer.data.copy(mediaUri = newUri))
                } else layer
            }
            comp.copy(layers = updatedLayers)
        }
        val activeComp = updatedComps.firstOrNull { it.id == _uiState.value.activeComposition.id }
            ?: _uiState.value.activeComposition
        val updatedProj = currentProj.copy(assets = updatedAssets, compositions = updatedComps)
        _uiState.update {
            it.copy(project = updatedProj, activeComposition = activeComp, statusMessage = "Asset relinked")
        }
        reevaluateCurrentFrame()
        scheduleAutosave()
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
