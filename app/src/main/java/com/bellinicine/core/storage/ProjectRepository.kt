package com.bellinicine.core.storage

import android.content.Context
import com.bellinicine.core.model.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Handles persistent project storage, serialization, and autosave.
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
}
