package com.bellinicine.core.storage

import android.content.Context
import com.bellinicine.core.model.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class ProjectSummary(
    val id: String,
    val name: String,
    val lastModified: Long,
    val compositionCount: Int
)

/**
 * Handles persistent project storage, serialization, autosave, and recovery.
 */
object ProjectRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun saveProject(context: Context, project: Project): File {
        val dir = File(context.filesDir, "projects").apply { mkdirs() }
        val file = File(dir, "${project.id}.json")
        val content = json.encodeToString(project)
        file.writeText(content)
        return file
    }

    fun loadProject(context: Context, projectId: String): Project? {
        val dir = File(context.filesDir, "projects")
        val file = File(dir, "$projectId.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<Project>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun listProjects(context: Context): List<String> {
        val dir = File(context.filesDir, "projects")
        return dir.listFiles { f -> f.extension == "json" }?.map { it.nameWithoutExtension } ?: emptyList()
    }

    fun getProjectSummaries(context: Context): List<ProjectSummary> {
        val dir = File(context.filesDir, "projects")
        val files = dir.listFiles { f -> f.extension == "json" && f.name != "autosave.json" } ?: return emptyList()
        return files.mapNotNull { f ->
            try {
                val proj = json.decodeFromString<Project>(f.readText())
                ProjectSummary(
                    id = proj.id,
                    name = proj.name,
                    lastModified = f.lastModified(),
                    compositionCount = proj.compositions.size
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.lastModified }
    }

    fun deleteProject(context: Context, projectId: String): Boolean {
        val dir = File(context.filesDir, "projects")
        val file = File(dir, "$projectId.json")
        return if (file.exists()) file.delete() else false
    }

    // --- AUTOSAVE & CRASH RECOVERY ---

    fun autosaveProject(context: Context, project: Project): File {
        val dir = File(context.filesDir, "projects").apply { mkdirs() }
        val file = File(dir, "autosave.json")
        file.writeText(json.encodeToString(project))
        return file
    }

    fun hasAutosave(context: Context): Boolean {
        val file = File(File(context.filesDir, "projects"), "autosave.json")
        return file.exists() && file.length() > 0
    }

    fun loadAutosave(context: Context): Project? {
        val file = File(File(context.filesDir, "projects"), "autosave.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<Project>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun clearAutosave(context: Context) {
        val file = File(File(context.filesDir, "projects"), "autosave.json")
        if (file.exists()) file.delete()
    }
}
