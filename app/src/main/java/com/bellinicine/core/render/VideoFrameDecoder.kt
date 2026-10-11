package com.bellinicine.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import java.io.File

/**
 * Metadata descriptor for video and still image assets.
 */
data class MediaMetadata(
    val width: Int,
    val height: Int,
    val durationUs: Long,
    val rotation: Int = 0,
    val frameRate: Float = 30f,
    val isVideo: Boolean = false
)

/**
 * Interface for frame-accurate video and image decoders.
 */
interface VideoFrameProvider {
    fun getFrame(
        mediaUri: String,
        frameNumber: Long,
        fps: Float,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap?

    fun getMetadata(mediaUri: String): MediaMetadata?
    fun clearCache()
    fun release()
}

/**
 * Production-ready Android hardware video frame decoder and bounded LRU cache.
 * Supports Storage Access Framework content:// URIs and standard filesystem paths for
 * MP4, MOV, WebM, MKV, PNG, JPEG, and WebP media.
 */
object VideoFrameDecoder : VideoFrameProvider {

    private const val TAG = "VideoFrameDecoder"
    private const val MAX_CACHE_SIZE_BYTES = 64 * 1024 * 1024 // 64 MB frame cache

    var appContext: Context? = null

    private val frameCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    private val metadataCache = mutableMapOf<String, MediaMetadata>()
    private val retrieverMap = mutableMapOf<String, MediaMetadataRetriever>()

    private val videoExtensions = setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp")

    fun isVideoFile(uriOrPath: String): Boolean {
        val clean = uriOrPath.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase()
        if (videoExtensions.contains(ext)) return true
        if (clean.contains("video", ignoreCase = true)) return true
        return false
    }

    fun isImageFile(uriOrPath: String): Boolean {
        val clean = uriOrPath.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase()
        if (imageExtensions.contains(ext)) return true
        if (clean.contains("image", ignoreCase = true)) return true
        return false
    }

    fun isMediaAvailable(context: Context? = appContext, mediaUri: String): Boolean {
        if (mediaUri.startsWith("content://")) {
            val ctx = context ?: appContext ?: return false
            return try {
                val parsed = Uri.parse(mediaUri)
                ctx.contentResolver.openInputStream(parsed)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
        val file = File(mediaUri)
        return file.exists() && file.isFile
    }

    override fun getMetadata(mediaUri: String): MediaMetadata? {
        return getMetadata(appContext, mediaUri)
    }

    fun getMetadata(context: Context? = appContext, mediaUri: String): MediaMetadata? {
        metadataCache[mediaUri]?.let { return it }
        val ctx = context ?: appContext

        return try {
            val isContent = mediaUri.startsWith("content://")
            val isVid = isVideoFile(mediaUri) || (!isImageFile(mediaUri) && isContent)

            if (isVid) {
                val retriever = getOrCreateRetriever(ctx, mediaUri) ?: return null
                synchronized(retriever) {
                    val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

                    val rawW = widthStr?.toIntOrNull() ?: 1920
                    val rawH = heightStr?.toIntOrNull() ?: 1080
                    val durationMs = durationStr?.toLongOrNull() ?: 0L
                    val rotation = rotationStr?.toIntOrNull() ?: 0

                    val width = if (rotation == 90 || rotation == 270) rawH else rawW
                    val height = if (rotation == 90 || rotation == 270) rawW else rawH

                    val meta = MediaMetadata(
                        width = width,
                        height = height,
                        durationUs = durationMs * 1_000L,
                        rotation = rotation,
                        frameRate = 30f,
                        isVideo = true
                    )
                    metadataCache[mediaUri] = meta
                    meta
                }
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (isContent && ctx != null) {
                    ctx.contentResolver.openInputStream(Uri.parse(mediaUri))?.use { input ->
                        BitmapFactory.decodeStream(input, null, options)
                    }
                } else {
                    val file = File(mediaUri)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath, options)
                    }
                }

                val meta = MediaMetadata(
                    width = if (options.outWidth > 0) options.outWidth else 1920,
                    height = if (options.outHeight > 0) options.outHeight else 1080,
                    durationUs = 0L,
                    rotation = 0,
                    frameRate = 0f,
                    isVideo = false
                )
                metadataCache[mediaUri] = meta
                meta
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read media metadata for $mediaUri: ${e.message}")
            null
        }
    }

    fun getFrameAtTimeUs(
        mediaUri: String,
        timeUs: Long,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? {
        val ctx = appContext
        val isContent = mediaUri.startsWith("content://")
        if (!isContent) {
            val file = File(mediaUri)
            if (!file.exists() || !file.isFile) return null
        }

        val isVideo = isVideoFile(mediaUri) || (!isImageFile(mediaUri) && isContent)
        val cacheKey = if (isVideo) "$mediaUri@$timeUs" else mediaUri

        synchronized(frameCache) {
            frameCache.get(cacheKey)?.let { return it }
        }

        return try {
            if (isVideo) {
                val retriever = getOrCreateRetriever(ctx, mediaUri) ?: return null
                val rawBitmap = synchronized(retriever) {
                    // Note: MediaMetadataRetriever.OPTION_CLOSEST retrieves the nearest keyframe or decoded frame.
                    // Frame-accurate sample-by-sample scrubbing across long GOP/VFR streams is planned via NDK MediaCodec.
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }
                val meta = getMetadata(ctx, mediaUri)
                val bitmap = if (rawBitmap != null && meta != null && meta.rotation != 0) {
                    val m = Matrix().apply { postRotate(meta.rotation.toFloat()) }
                    val rot = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, m, true)
                    if (rot !== rawBitmap) rawBitmap.recycle()
                    rot
                } else {
                    rawBitmap
                }
                if (bitmap != null) {
                    synchronized(frameCache) {
                        frameCache.put(cacheKey, bitmap)
                    }
                }
                bitmap
            } else {
                val bitmap = if (isContent && ctx != null) {
                    ctx.contentResolver.openInputStream(Uri.parse(mediaUri))?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                } else {
                    BitmapFactory.decodeFile(File(mediaUri).absolutePath)
                }
                if (bitmap != null) {
                    synchronized(frameCache) {
                        frameCache.put(cacheKey, bitmap)
                    }
                }
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode frame at $timeUs us for $mediaUri: ${e.message}")
            null
        }
    }

    override fun getFrame(
        mediaUri: String,
        frameNumber: Long,
        fps: Float,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val timeUs = ((frameNumber.toDouble() / fps.coerceAtLeast(1f)) * 1_000_000.0).toLong()
        return getFrameAtTimeUs(mediaUri, timeUs, targetWidth, targetHeight)
    }

    private fun getOrCreateRetriever(context: Context?, mediaUri: String): MediaMetadataRetriever? {
        synchronized(retrieverMap) {
            retrieverMap[mediaUri]?.let { return it }
            return try {
                val retriever = MediaMetadataRetriever()
                if (mediaUri.startsWith("content://") && context != null) {
                    retriever.setDataSource(context, Uri.parse(mediaUri))
                } else {
                    retriever.setDataSource(mediaUri)
                }
                retrieverMap[mediaUri] = retriever
                retriever
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MediaMetadataRetriever for $mediaUri: ${e.message}")
                null
            }
        }
    }

    override fun clearCache() {
        synchronized(frameCache) {
            frameCache.evictAll()
        }
    }

    override fun release() {
        clearCache()
        synchronized(retrieverMap) {
            for (retriever in retrieverMap.values) {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
            retrieverMap.clear()
        }
        metadataCache.clear()
    }
}

/**
 * Calculates deterministic source media timestamp in microseconds based on composition time,
 * source in/out offsets, and time-stretching percentages (e.g. 50% = 2x fast, 200% = 0.5x slow, -100% = reverse).
 */
fun computeSourceMediaTimestampUs(
    compositionFrame: Long,
    compositionFps: Float,
    startTimeFrame: Long,
    timeStretch: Float,
    sourceDurationUs: Long = 0L
): Long {
    val compFpsSafe = compositionFps.coerceAtLeast(1f)
    val compTimeSec = (compositionFrame - startTimeFrame).toDouble() / compFpsSafe

    // Freeze frame
    if (timeStretch == 0f) {
        return 0L
    }

    // Reverse playback support (negative time stretch e.g. -100%)
    if (timeStretch < 0f) {
        val absStretch = -timeStretch / 100.0
        val elapsedSec = compTimeSec / absStretch
        val elapsedUs = (elapsedSec * 1_000_000.0).toLong()
        return if (sourceDurationUs > 0L) {
            (sourceDurationUs - elapsedUs).coerceIn(0L, sourceDurationUs)
        } else {
            0L
        }
    }

    val stretch = timeStretch / 100.0
    val sourceTimeSec = compTimeSec / stretch
    var sourceTimeUs = (sourceTimeSec * 1_000_000.0).toLong()

    if (sourceDurationUs > 0L) {
        sourceTimeUs = sourceTimeUs.coerceIn(0L, sourceDurationUs)
    } else {
        sourceTimeUs = sourceTimeUs.coerceAtLeast(0L)
    }
    return sourceTimeUs
}

/**
 * Maps composition frames to source media frame numbers taking both composition and source frame rates into account.
 */
fun computeSourceMediaFrame(
    compositionFrame: Long,
    compositionFps: Float,
    startTimeFrame: Long,
    timeStretch: Float,
    sourceFps: Float = 30f,
    sourceDurationFrames: Long = Long.MAX_VALUE
): Long {
    val compFpsSafe = compositionFps.coerceAtLeast(1f)
    val srcFpsSafe = sourceFps.coerceAtLeast(1f)
    val compTimeSec = (compositionFrame - startTimeFrame).toDouble() / compFpsSafe

    // Freeze frame
    if (timeStretch == 0f) {
        return 0L
    }

    // Reverse playback support
    if (timeStretch < 0f) {
        val absStretch = -timeStretch / 100.0
        val elapsedSec = compTimeSec / absStretch
        val elapsedFrames = (elapsedSec * srcFpsSafe).toLong()
        return if (sourceDurationFrames < Long.MAX_VALUE) {
            (sourceDurationFrames - elapsedFrames).coerceIn(0L, sourceDurationFrames)
        } else {
            0L
        }
    }

    val stretch = timeStretch / 100.0
    val sourceTimeSec = compTimeSec / stretch
    val frame = (sourceTimeSec * srcFpsSafe).toLong()
    return frame.coerceIn(0L, sourceDurationFrames.coerceAtLeast(0L))
}

