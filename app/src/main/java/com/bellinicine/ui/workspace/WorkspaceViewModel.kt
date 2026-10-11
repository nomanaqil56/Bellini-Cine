package com.bellinicine.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bellinicine.core.engine.CompositionEvaluator
import com.bellinicine.core.engine.EvaluatedLayer
import com.bellinicine.core.model.AnimatedProperty
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Keyframe
import com.bellinicine.core.model.KeyframeInterpolation
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.Project
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
    val isLooping: Boolean = true
)

class WorkspaceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    init {
        reevaluateCurrentFrame()
    }

    private fun createInitialState(): WorkspaceUiState {
        // Create a rich demo composition inspired by After Effects
        val textLayer = Layer(
            id = "layer-text-1",
            name = "Title: Bellini Cine",
            type = LayerType.TEXT,
            data = LayerData.Text("BELLINI CINE", fontSize = 64f, fontColor = 0xFFFFFFFF),
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

        val accentSolidLayer = Layer(
            id = "layer-solid-badge",
            name = "Accent Violet Card",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFF7C4DFF, width = 640, height = 360),
            inPointFrame = 15L,
            outPointFrame = 270L,
            transform = Transform(
                anchorPoint = AnimatedProperty("Anchor Point", Vec3(320f, 180f, 0f)),
                position = AnimatedProperty("Position", Vec3(540f, 1100f, 0f)),
                rotation = AnimatedProperty(
                    name = "Rotation",
                    defaultValue = 0f,
                    keyframes = listOf(
                        Keyframe(15L, -45f, KeyframeInterpolation.BEZIER),
                        Keyframe(60L, 0f, KeyframeInterpolation.BEZIER)
                    ),
                    isKeyframed = true
                ),
                scale = AnimatedProperty(
                    name = "Scale",
                    defaultValue = Vec3(100f, 100f, 100f),
                    keyframes = listOf(
                        Keyframe(15L, Vec3(20f, 20f, 100f), KeyframeInterpolation.BEZIER),
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
            layers = listOf(backgroundSolid, accentSolidLayer, textLayer)
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
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
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

    fun toggleLayerVisibility(layerId: String) {
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

    private fun reevaluateCurrentFrame() {
        val eval = CompositionEvaluator.evaluateComposition(
            _uiState.value.activeComposition,
            _uiState.value.currentFrame
        )
        _uiState.update { it.copy(evaluatedLayers = eval) }
    }
}
