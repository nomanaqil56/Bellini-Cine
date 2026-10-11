package com.bellinicine.test

import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.Project
import com.bellinicine.core.storage.ProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutosaveRecoveryTest {

    @Test
    fun multiCompositionAutosavePreservesAllCompositionsAndLayers() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bellini_test_autosave_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val comp1 = Composition(
            id = "comp-alpha",
            name = "Alpha Shot",
            width = 1080,
            height = 1920,
            layers = listOf(
                Layer(
                    id = "layer-1",
                    name = "Red Solid",
                    type = LayerType.SOLID,
                    data = LayerData.Solid(0xFFFF0000, 1080, 1920)
                )
            )
        )

        val comp2 = Composition(
            id = "comp-beta",
            name = "Beta Title",
            width = 1920,
            height = 1080,
            layers = listOf(
                Layer(
                    id = "layer-2",
                    name = "Title Text",
                    type = LayerType.TEXT,
                    data = LayerData.Text("Title Sequence")
                )
            )
        )

        val project = Project(
            id = "proj-autosave-test",
            name = "Autosave Master Project",
            compositions = listOf(comp1, comp2),
            activeCompositionId = "comp-alpha"
        )

        // Autosave the project to directory
        val autosaveFile = ProjectRepository.autosaveProjectToDir(tempDir, project)
        assertTrue("Autosave file must exist", autosaveFile.exists())
        assertTrue("hasAutosaveInDir must report true", ProjectRepository.hasAutosaveInDir(tempDir))

        // Reload via loadAutosaveFromDir
        val restored = ProjectRepository.loadAutosaveFromDir(tempDir)
        assertNotNull("Autosave must be successfully restored", restored)
        assertEquals("Both compositions must survive autosave", 2, restored!!.compositions.size)
        assertEquals("comp-alpha", restored.activeCompositionId)

        val restoredComp1 = restored.compositions.first { it.id == "comp-alpha" }
        assertEquals(1, restoredComp1.layers.size)
        assertEquals("Red Solid", restoredComp1.layers[0].name)

        val restoredComp2 = restored.compositions.first { it.id == "comp-beta" }
        assertEquals(1, restoredComp2.layers.size)
        assertEquals("Title Text", restoredComp2.layers[0].name)

        // Clear autosave
        ProjectRepository.clearAutosaveInDir(tempDir)
        assertTrue("Autosave file should be cleaned up", !autosaveFile.exists())

        tempDir.deleteRecursively()
    }

    @Test
    fun autosaveRecoversFromBakWhenPrimaryCorrupted() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bellini_test_autosave_corrupt_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val project = Project(
            id = "proj-corrupt-test",
            name = "Resilient Project",
            compositions = listOf(Composition(id = "comp-1", name = "Comp 1"))
        )

        // Save twice to establish primary and .bak
        ProjectRepository.autosaveProjectToDir(tempDir, project)
        val projectModified = project.copy(name = "Resilient Project Modified")
        ProjectRepository.autosaveProjectToDir(tempDir, projectModified)

        val primaryFile = File(tempDir, "autosave.json")
        val bakFile = File(tempDir, "autosave.json.bak")

        assertTrue("Primary must exist", primaryFile.exists())
        assertTrue("Backup must exist", bakFile.exists())

        // Corrupt primary file
        primaryFile.writeText("{{corrupted json garbage !!")

        // Load must fall back to .bak
        val recovered = ProjectRepository.loadAutosaveFromDir(tempDir)
        assertNotNull("Corrupted autosave must recover from .bak", recovered)
        assertTrue("Recovered project must be valid", recovered!!.compositions.isNotEmpty())

        tempDir.deleteRecursively()
    }
}
