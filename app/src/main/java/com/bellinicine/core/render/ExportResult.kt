package com.bellinicine.core.render

/**
 * Typed result representing video export operations.
 * Eliminates silent export failures and provides explicit diagnostics.
 */
sealed class ExportResult {
    data class Success(
        val filePath: String,
        val format: String,
        val width: Int,
        val height: Int,
        val durationFrames: Long,
        val fileSize: Long
    ) : ExportResult()

    data class Failure(
        val errorMessage: String,
        val cause: Throwable? = null
    ) : ExportResult()

    object Cancelled : ExportResult()
}
