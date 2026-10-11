package com.bellinicine.core.render

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.bellinicine.core.model.Composition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

data class ExportConfig(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Float = 30f,
    val bitrate: Int = 12_000_000, // 12 Mbps
    val format: String = "MP4" // "MP4", "PNG"
)

/**
 * High-performance hardware video encoding and export engine for Bellini Cine.
 * Features typed ExportResult, cancellation support, and robust MediaCodec lifecycle.
 */
object ExportEngine {

    private const val TAG = "ExportEngine"

    suspend fun exportComposition(
        context: Context,
        composition: Composition,
        config: ExportConfig,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }

        // Dedicated single-frame PNG master export path if explicitly chosen
        if (config.format.equals("PNG", ignoreCase = true)) {
            return@withContext exportStillSnapshot(composition, config, outputDir)
        }

        val outputFile = File(outputDir, "BelliniCine_${System.currentTimeMillis()}.mp4")
        val totalFrames = composition.durationFrames
        val frameDurationUs = (1_000_000L / config.fps.coerceAtLeast(1f)).toLong()

        // 1. Setup MediaFormat for H.264 video
        val mediaFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            config.width,
            config.height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps.toInt())
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe every second
        }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false
        var wasCancelled = false

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            for (frame in 0 until totalFrames) {
                if (isCancelled()) {
                    wasCancelled = true
                    break
                }

                // Render composition frame to Bitmap using unified CompositorPipeline
                val frameBitmap = CompositorPipeline.renderCompositionToBitmap(
                    composition = composition,
                    frame = frame,
                    targetWidth = config.width,
                    targetHeight = config.height
                )

                // Convert Bitmap to NV12 / YUV420 byte buffer for encoder
                val yuvBytes = bitmapToYuv420(frameBitmap, config.width, config.height)
                frameBitmap.recycle()

                // Queue input frame into MediaCodec
                val inputIndex = encoder.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(yuvBytes)
                        val presentationTimeUs = frame * frameDurationUs
                        encoder.queueInputBuffer(inputIndex, 0, yuvBytes.size, presentationTimeUs, 0)
                    }
                }

                // Drain available encoder output
                var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
                while (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    when (outputIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                val newFormat = encoder.outputFormat
                                trackIndex = muxer.addTrack(newFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        else -> {
                            val outputBuffer = encoder.getOutputBuffer(outputIndex)
                            if (outputBuffer != null) {
                                if (bufferInfo.size > 0 && muxerStarted) {
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                    outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
                }

                val progress = (frame + 1).toFloat() / totalFrames.toFloat()
                withContext(Dispatchers.Main) {
                    onProgress(progress)
                }
            }

            if (wasCancelled) {
                if (outputFile.exists()) outputFile.delete()
                return@withContext ExportResult.Cancelled
            }

            // Signal End of Stream
            val eosIndex = encoder.dequeueInputBuffer(10_000L)
            if (eosIndex >= 0) {
                encoder.queueInputBuffer(eosIndex, 0, 0, totalFrames * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            // Drain remaining frames until EOS
            var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            while (outputIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                when (outputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            val newFormat = encoder.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
                outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 5_000L)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Hardware video encode failed: ${e.message}", e)
            if (outputFile.exists()) outputFile.delete()
            return@withContext ExportResult.Failure(
                errorMessage = "Hardware video encode failed: ${e.message ?: "Unknown codec error"}",
                cause = e
            )
        } finally {
            try {
                encoder?.stop()
                encoder?.release()
                if (muxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (_: Exception) {}
        }

        if (outputFile.exists() && outputFile.length() > 0) {
            ExportResult.Success(
                filePath = outputFile.absolutePath,
                format = "video/mp4",
                width = config.width,
                height = config.height,
                durationFrames = totalFrames,
                fileSize = outputFile.length()
            )
        } else {
            ExportResult.Failure(
                errorMessage = "Export produced an empty or missing output file."
            )
        }
    }

    private fun exportStillSnapshot(
        composition: Composition,
        config: ExportConfig,
        outputDir: File
    ): ExportResult {
        return try {
            val snapshot = CompositorPipeline.renderCompositionToBitmap(composition, 0L, config.width, config.height)
            val file = File(outputDir, "BelliniCine_Frame_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                snapshot.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            snapshot.recycle()
            ExportResult.Success(
                filePath = file.absolutePath,
                format = "image/png",
                width = config.width,
                height = config.height,
                durationFrames = 1L,
                fileSize = file.length()
            )
        } catch (e: Exception) {
            ExportResult.Failure("Failed to export still PNG frame: ${e.message}", e)
        }
    }

    private fun bitmapToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // Y calculation
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                // Subsampled U & V calculation (every 2x2 pixels)
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
