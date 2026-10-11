package com.bellinicine.test

import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Project
import com.bellinicine.core.storage.ProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for crash-resilient atomic project persistence and .bak backup recovery (Finding J).
 */
class AtomicPersistenceTest {

    @Test
    fun testAtomicWriteAndReload() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bellini_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val projFile = File(tempDir, "test_proj.json")

        val proj = Project(
            id = "test-atomic-1",
            name = "Atomic Project",
            compositions = listOf(
                Composition(
                    id = "c1",
                    name = "Comp 1",
                    width = 1920,
                    height = 1080
                )
            ),
            version = 1
        )

        ProjectRepository.saveProjectToFile(projFile, proj)

        assertTrue("Project file must exist", projFile.exists())
        val loaded = ProjectRepository.loadProjectFromFile(projFile)
        assertNotNull("Project must load cleanly", loaded)
        assertEquals("test-atomic-1", loaded!!.id)
        assertEquals(1, loaded.version)

        tempDir.deleteRecursively()
    }

    @Test
    fun testBackupFileCreationAndRecoveryFromCorruptedPrimary() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bellini_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val projFile = File(tempDir, "test_corrupt.json")
        val bakFile = File(tempDir, "test_corrupt.json.bak")

        val projV1 = Project(id = "corrupt-test", name = "Initial Valid Project", version = 1)
        ProjectRepository.saveProjectToFile(projFile, projV1)

        val projV2 = Project(id = "corrupt-test", name = "Second Valid Version", version = 1)
        ProjectRepository.saveProjectToFile(projFile, projV2)

        assertTrue("Backup file .bak must be created on update", bakFile.exists())

        // Simulate file corruption on primary file (e.g. truncated JSON)
        projFile.writeText("{ this is truncated corrupt json ... ")

        // Loading should automatically recover from .bak!
        val recovered = ProjectRepository.loadProjectFromFile(projFile)
        assertNotNull("Must safely recover from .bak backup file", recovered)
        assertEquals("corrupt-test", recovered!!.id)

        tempDir.deleteRecursively()
    }
}
