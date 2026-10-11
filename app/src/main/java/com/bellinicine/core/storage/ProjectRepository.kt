package com.bellinicine.core.storage

import android.content.Context
import com.bellinicine.core.model.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

data class ProjectSummary(
    val id: String,
    val name: String,
    val lastModified: Long,
    val compositionCount: Int,
    val schemaVersion: Int
)

/**
 * Handles atomic persistent project storage, schema versioning,
 * automatic crash recovery (.bak backups), and project migrations.
 */
object ProjectRepository {

    const val CURRENT_SCHEMA_VERSION = 1

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Atomically writes project data to disk via a temporary file and creates a .bak backup.
     */
    fun saveProject(context: Context, project: Project): File {
        val dir = File(context.filesDir, "projects").apply { mkdirs() }
        val file = File(dir, "${project.id}.json")
        saveProjectToFile(file, project)
        return file
    }

    /**
     * Core atomic file writer with crash-safe .tmp staging and .bak backup copy.
     */
    fun saveProjectToFile(file: File, project: Project) {
        val parentDir = file.parentFile ?: File(".")
        if (!parentDir.exists()) parentDir.mkdirs()

        val tmpFile = File(parentDir, "${file.name}.tmp")
        val bakFile = File(parentDir, "${file.name}.bak")

        val versionedProject = if (project.version <= 0) project.copy(version = CURRENT_SCHEMA_VERSION) else project
        val content = json.encodeToString(versionedProject)

        // 1. Write fully to temporary file and flush
        FileOutputStream(tmpFile).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }

        // 2. Backup existing project file
        if (file.exists() && file.length() > 0) {
            try {
                file.copyTo(bakFile, overwrite = true)
            } catch (_: Exception) {}
        }

        // 3. Atomically replace target file
        val renamed = tmpFile.renameTo(file)
        if (!renamed) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
    }

    fun loadProject(context: Context, projectId: String): Project? {
        val dir = File(context.filesDir, "projects")
        val file = File(dir, "$projectId.json")
        return loadProjectFromFile(file)
    }

    /**
     * Loads a project from file with automatic recovery from .bak if the primary file is corrupted.
     */
    fun loadProjectFromFile(file: File): Project? {
        if (!file.exists()) {
            val bak = File(file.parentFile, "${file.name}.bak")
            if (bak.exists()) return decodeAndMigrate(bak)
            return null
        }

        val primary = decodeAndMigrate(file)
        if (primary != null) return primary

        // Fallback to backup if primary failed to decode (e.g. corruption during abrupt shutdown)
        val bakFile = File(file.parentFile, "${file.name}.bak")
        if (bakFile.exists()) {
            val recovered = decodeAndMigrate(bakFile)
            if (recovered != null) return recovered
        }

        return null
    }

    private fun decodeAndMigrate(file: File): Project? {
        return try {
            val text = file.readText(Charsets.UTF_8)
            val project = json.decodeFromString<Project>(text)
            migrateIfNeeded(project)
        } catch (_: Exception) {
            null
        }
    }

    private fun migrateIfNeeded(project: Project): Project {
        if (project.version >= CURRENT_SCHEMA_VERSION) {
            return project
        }
        // Run migration pipeline if needed for legacy schema versions
        return project.copy(version = CURRENT_SCHEMA_VERSION)
    }

    fun listProjects(context: Context): List<String> {
        val dir = File(context.filesDir, "projects")
        return dir.listFiles { f -> f.extension == "json" && !f.name.startsWith("autosave") }
            ?.map { it.nameWithoutExtension } ?: emptyList()
    }

    fun getProjectSummaries(context: Context): List<ProjectSummary> {
        val dir = File(context.filesDir, "projects")
        val files = dir.listFiles { f -> f.extension == "json" && !f.name.startsWith("autosave") } ?: return emptyList()
        return files.mapNotNull { f ->
            try {
                val proj = loadProjectFromFile(f) ?: return@mapNotNull null
                ProjectSummary(
                    id = proj.id,
                    name = proj.name,
                    lastModified = f.lastModified(),
                    compositionCount = proj.compositions.size,
                    schemaVersion = proj.version
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.lastModified }
    }

    fun deleteProject(context: Context, projectId: String): Boolean {
        val dir = File(context.filesDir, "projects")
        val file = File(dir, "$projectId.json")
        val bakFile = File(dir, "$projectId.json.bak")
        if (bakFile.exists()) bakFile.delete()
        return if (file.exists()) file.delete() else false
    }

    // --- AUTOSAVE & CRASH RECOVERY ---

    fun autosaveProject(context: Context, project: Project): File {
        val dir = File(context.filesDir, "projects").apply { mkdirs() }
        val file = File(dir, "autosave.json")
        saveProjectToFile(file, project)
        return file
    }

    fun hasAutosave(context: Context): Boolean {
        val file = File(File(context.filesDir, "projects"), "autosave.json")
        val bak = File(File(context.filesDir, "projects"), "autosave.json.bak")
        return (file.exists() && file.length() > 0) || (bak.exists() && bak.length() > 0)
    }

    fun loadAutosave(context: Context): Project? {
        val file = File(File(context.filesDir, "projects"), "autosave.json")
        return loadProjectFromFile(file)
    }

    fun clearAutosave(context: Context) {
        val dir = File(context.filesDir, "projects")
        val file = File(dir, "autosave.json")
        val bak = File(dir, "autosave.json.bak")
        if (file.exists()) file.delete()
        if (bak.exists()) bak.delete()
    }
}
