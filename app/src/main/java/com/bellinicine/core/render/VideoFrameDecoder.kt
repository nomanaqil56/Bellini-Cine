package com.bellinicine.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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
 * Supports MP4, MOV, WebM, MKV, and standard still image formats.
 */
object VideoFrameDecoder : VideoFrameProvider {

    private const val TAG = "VideoFrameDecoder"
    private const val MAX_CACHE_SIZE_BYTES = 64 * 1024 * 1024 // 64 MB frame cache

    private val frameCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    private val metadataCache = mutableMapOf<String, MediaMetadata>()
    private val retrieverMap = mutableMapOf<String, MediaMetadataRetriever>()

    private val videoExtensions = setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp")

    fun isVideoFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return videoExtensions.contains(ext)
    }

    fun isImageFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return imageExtensions.contains(ext)
    }

    override fun getMetadata(mediaUri: String): MediaMetadata? {
        metadataCache[mediaUri]?.let { return it }

        val file = File(mediaUri)
        if (!file.exists() || !file.isFile) return null

        return try {
            if (isVideoFile(mediaUri)) {
                val retriever = getOrCreateRetriever(mediaUri) ?: return null
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

                val width = widthStr?.toIntOrNull() ?: 1920
                val height = heightStr?.toIntOrNull() ?: 1080
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                val rotation = rotationStr?.toIntOrNull() ?: 0

                val meta = MediaMetadata(
                    width = if (rotation == 90 || rotation == 270) height else width,
                    height = if (rotation == 90 || rotation == 270) width else height,
                    durationUs = durationMs * 1_000L,
                    rotation = rotation,
                    frameRate = 30f,
                    isVideo = true
                )
                metadataCache[mediaUri] = meta
                meta
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val meta = MediaMetadata(
                    width = options.outWidth,
                    height = options.outHeight,
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

    override fun getFrame(
        mediaUri: String,
        frameNumber: Long,
        fps: Float,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val file = File(mediaUri)
        if (!file.exists() || !file.isFile) return null

        val isVideo = isVideoFile(mediaUri)
        val cacheKey = if (isVideo) "$mediaUri#$frameNumber" else mediaUri

        synchronized(frameCache) {
            frameCache.get(cacheKey)?.let { return it }
        }

        return try {
            if (isVideo) {
                val retriever = getOrCreateRetriever(mediaUri) ?: return null
                val timeUs = ((frameNumber.toDouble() / fps.coerceAtLeast(1f)) * 1_000_000.0).toLong()
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap != null) {
                    synchronized(frameCache) {
                        frameCache.put(cacheKey, bitmap)
                    }
                }
                bitmap
            } else {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    synchronized(frameCache) {
                        frameCache.put(cacheKey, bitmap)
                    }
                }
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode frame $frameNumber for $mediaUri: ${e.message}")
            null
        }
    }

    private fun getOrCreateRetriever(mediaUri: String): MediaMetadataRetriever? {
        synchronized(retrieverMap) {
            retrieverMap[mediaUri]?.let { return it }
            return try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(mediaUri)
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
