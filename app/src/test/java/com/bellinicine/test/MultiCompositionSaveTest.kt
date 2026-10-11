package com.bellinicine.test

import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.FootageAsset
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.Project
import com.bellinicine.core.storage.ProjectRepository
import com.bellinicine.ui.workspace.WorkspaceViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MultiCompositionSaveTest {

    @Test
    fun projectWithThreeCompositionsPreservesAllCompositionsOnSaveAndLoad() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bellini_multi_comp_${System.currentTimeMillis()}").apply { mkdirs() }
        val projectFile = File(tempDir, "multi_comp_project.json")

        val layer1 = Layer(
            id = "layer-c1-1",
            name = "Comp1 Layer",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFFFF0000, 1080, 1920)
        )
        val comp1 = Composition(
            id = "comp-1",
            name = "Opening Scene",
            width = 1080,
            height = 1920,
            layers = listOf(layer1)
        )

        val layer2 = Layer(
            id = "layer-c2-1",
            name = "Comp2 Title",
            type = LayerType.TEXT,
            data = LayerData.Text("Scene Two", 48f)
        )
        val comp2 = Composition(
            id = "comp-2",
            name = "Middle Action",
            width = 1920,
            height = 1080,
            layers = listOf(layer2)
        )

        val layer3 = Layer(
            id = "layer-c3-1",
            name = "Comp3 Star",
            type = LayerType.SHAPE,
            data = LayerData.Shape("Star", 300f, 300f)
        )
        val comp3 = Composition(
            id = "comp-3",
            name = "Credits Roll",
            width = 1080,
            height = 1080,
            layers = listOf(layer3)
        )

        val asset = FootageAsset(
            id = "asset-1",
            name = "Background.mp4",
            uri = "content://media/external/video/media/42",
            width = 1920,
            height = 1080,
            durationSeconds = 12.5f,
            frameRate = 30f,
            mediaType = "video"
        )

        val originalProject = Project(
            id = "project-multi-comp",
            name = "Feature Film Multi-Comp",
            compositions = listOf(comp1, comp2, comp3),
            assets = listOf(asset),
            activeCompositionId = comp2.id,
            version = 1
        )

        // 1. Save Project to File
        ProjectRepository.saveProjectToFile(projectFile, originalProject)
        assertTrue("Project file must exist on disk", projectFile.exists())

        // 2. Load Project from File
        val loadedProject = ProjectRepository.loadProjectFromFile(projectFile)
        assertNotNull("Loaded project must not be null", loadedProject)
        assertEquals(originalProject.id, loadedProject?.id)
        assertEquals(originalProject.name, loadedProject?.name)
        assertEquals("Must preserve all 3 compositions", 3, loadedProject?.compositions?.size)

        // 3. Verify each composition and layer details
        val loadedC1 = loadedProject?.compositions?.find { it.id == "comp-1" }
        assertNotNull(loadedC1)
        assertEquals("Opening Scene", loadedC1?.name)
        assertEquals(1, loadedC1?.layers?.size)
        assertEquals("Comp1 Layer", loadedC1?.layers?.first()?.name)

        val loadedC2 = loadedProject?.compositions?.find { it.id == "comp-2" }
        assertNotNull(loadedC2)
        assertEquals("Middle Action", loadedC2?.name)
        assertEquals(1920, loadedC2?.width)
        assertEquals("Comp2 Title", loadedC2?.layers?.first()?.name)

        val loadedC3 = loadedProject?.compositions?.find { it.id == "comp-3" }
        assertNotNull(loadedC3)
        assertEquals("Credits Roll", loadedC3?.name)
        assertEquals(1080, loadedC3?.width)
        assertEquals(1080, loadedC3?.height)
        assertEquals("Comp3 Star", loadedC3?.layers?.first()?.name)

        // 4. Verify assets preserved
        assertEquals(1, loadedProject?.assets?.size)
        assertEquals("content://media/external/video/media/42", loadedProject?.assets?.first()?.uri)
        assertEquals("comp-2", loadedProject?.activeCompositionId)

        tempDir.deleteRecursively()
    }

    @Test
    fun updateActiveCompositionInProjectPreservesOtherCompositions() {
        val viewModel = WorkspaceViewModel()

        val c1 = viewModel.uiState.value.activeComposition
        val c2 = viewModel.addCompositionToProject("Comp Two")
        val c3 = viewModel.addCompositionToProject("Comp Three")

        val projWithThree = viewModel.uiState.value.project
        assertEquals(3, projWithThree.compositions.size)

        // Mutate layer in active composition
        val updatedComp = c1.copy(name = "Renamed Main Comp")
        val updatedProj = viewModel.updateActiveCompositionInProject(projWithThree, updatedComp)

        assertEquals("Must still have 3 compositions after updating active comp", 3, updatedProj.compositions.size)
        assertEquals("Renamed Main Comp", updatedProj.compositions.find { it.id == c1.id }?.name)
        assertEquals("Comp Two", updatedProj.compositions.find { it.id == c2.id }?.name)
        assertEquals("Comp Three", updatedProj.compositions.find { it.id == c3.id }?.name)
    }

    @Test
    fun failedSaveCannotReplaceValidProjectWithCorruptedData() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bellini_corrupt_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val projectFile = File(tempDir, "safe_project.json")

        val validComp = Composition(id = "valid-c", name = "Valid Comp", layers = emptyList())
        val validProj = Project(id = "proj-corruption-test", name = "Safe Project", compositions = listOf(validComp), version = 1)

        ProjectRepository.saveProjectToFile(projectFile, validProj)
        assertTrue(projectFile.exists())

        // Corrupt the primary project file
        projectFile.writeText("INVALID CORRUPTED JSON {{{")

        // Load project from file: should recover from .bak
        val recovered = ProjectRepository.loadProjectFromFile(projectFile)
        assertNotNull("Should recover valid project from backup .bak file", recovered)
        assertEquals(validProj.id, recovered?.id)
        assertEquals("Safe Project", recovered?.name)

        tempDir.deleteRecursively()
    }
}
