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
import com.bellinicine.core.model.Transform
import com.bellinicine.core.model.Vec3
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val exportResultPath: String? = null
)

class WorkspaceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private val undoStack = mutableListOf<Composition>()
    private val redoStack = mutableListOf<Composition>()

    init {
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
            _uiState.update { it.copy(activeComposition = prev) }
            reevaluateCurrentFrame()
            updateHistoryFlags()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(_uiState.value.activeComposition)
            _uiState.update { it.copy(activeComposition = next) }
            reevaluateCurrentFrame()
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
        _uiState.update { it.copy(activeComposition = updatedComp) }
        reevaluateCurrentFrame()
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

    fun addEffectToSelectedLayer(name: String, category: EffectCategory) {
        mutateSelectedLayer { layer ->
            val effect = Effect(
                id = "effect-${UUID.randomUUID()}",
                name = name,
                category = category,
                parameters = listOf(
                    EffectParameter.Slider("Intensity", AnimatedProperty("Intensity", 50f), min = 0f, max = 100f)
                )
            )
            layer.copy(effects = layer.effects + effect)
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
        _uiState.update { it.copy(activeComposition = updatedComp, selectedLayerId = newLayer.id) }
        reevaluateCurrentFrame()
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
        _uiState.update { it.copy(activeComposition = updatedComp, selectedLayerId = newLayer.id) }
        reevaluateCurrentFrame()
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
        _uiState.update { it.copy(activeComposition = updatedComp, selectedLayerId = newLayer.id) }
        reevaluateCurrentFrame()
    }

    fun toggleLayerVisibility(layerId: String) {
        pushHistoryState()
        val updatedLayers = _uiState.value.activeComposition.layers.map { layer ->
            if (layer.id == layerId) layer.copy(isVisible = !layer.isVisible) else layer
        }
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        _uiState.update { it.copy(activeComposition = updatedComp) }
        reevaluateCurrentFrame()
    }

    fun toggleLayerSolo(layerId: String) {
        val updatedLayers = _uiState.value.activeComposition.layers.map { layer ->
            if (layer.id == layerId) layer.copy(isSolo = !layer.isSolo) else layer
        }
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        _uiState.update { it.copy(activeComposition = updatedComp) }
        reevaluateCurrentFrame()
    }

    fun toggleLayerLock(layerId: String) {
        val updatedLayers = _uiState.value.activeComposition.layers.map { layer ->
            if (layer.id == layerId) layer.copy(isLocked = !layer.isLocked) else layer
        }
        val updatedComp = _uiState.value.activeComposition.copy(layers = updatedLayers)
        _uiState.update { it.copy(activeComposition = updatedComp) }
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

    private fun reevaluateCurrentFrame() {
        val eval = CompositionEvaluator.evaluateComposition(
            _uiState.value.activeComposition,
            _uiState.value.currentFrame
        )
        _uiState.update { it.copy(evaluatedLayers = eval) }
    }
}
