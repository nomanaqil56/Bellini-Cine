package com.bellinicine.test

import android.graphics.Bitmap
import android.media.MediaCodecInfo
import com.bellinicine.core.render.ExportConfig
import com.bellinicine.core.render.ExportEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class ExportEngineTest {

    @Test
    fun configValidationEnforcesEvenDimensionsAndSafeRanges() {
        val oddConfig = ExportConfig(
            width = 1081,
            height = 1923,
            fps = 140f,
            bitrate = 200_000_000
        )
        val validated = ExportEngine.validateExportConfig(oddConfig)

        assertEquals("Width must be rounded down to even number for YUV420 codecs", 1080, validated.width)
        assertEquals("Height must be rounded down to even number for YUV420 codecs", 1922, validated.height)
        assertEquals(120f, validated.fps, 0.001f)
        assertEquals(100_000_000, validated.bitrate)
    }

    @Test
    fun convertBitmapToYuvProducesCorrectPlanarAndSemiPlanarByteSizes() {
        val w = 4
        val h = 4
        val pixels = IntArray(w * h) { 0xFF00FF00.toInt() }
        val expectedSize = w * h * 3 / 2 // 24 bytes

        val nv12 = ExportEngine.convertPixelsToYuv(pixels, w, h, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
        assertEquals("NV12 SemiPlanar buffer must be exactly W * H * 1.5 bytes", expectedSize, nv12.size)

        val i420 = ExportEngine.convertPixelsToYuv(pixels, w, h, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
        assertEquals("I420 Planar buffer must be exactly W * H * 1.5 bytes", expectedSize, i420.size)
    }

    @Test
    fun verifyExportedMp4DetectsValidFtypHeaderAndRejectsCorrupted() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "bellini_test_export")
        tempDir.mkdirs()

        // Valid ISO Base Media file mock with "ftyp" box at offset 4
        val validFile = File(tempDir, "valid_mock.mp4")
        FileOutputStream(validFile).use { out ->
            val header = ByteArray(64)
            // Box size (e.g. 32)
            header[0] = 0; header[1] = 0; header[2] = 0; header[3] = 32
            // "ftyp"
            header[4] = 'f'.code.toByte()
            header[5] = 't'.code.toByte()
            header[6] = 'y'.code.toByte()
            header[7] = 'p'.code.toByte()
            out.write(header)
        }

        assertTrue("File with valid ftyp header must pass container verification", ExportEngine.verifyExportedMp4(validFile))

        // Empty file
        val emptyFile = File(tempDir, "empty.mp4").apply { writeBytes(ByteArray(0)) }
        assertFalse("Empty file must be rejected", ExportEngine.verifyExportedMp4(emptyFile))

        // Corrupted file (garbage header)
        val corruptedFile = File(tempDir, "garbage.mp4").apply { writeBytes(ByteArray(64) { 0x55.toByte() }) }
        assertFalse("Garbage non-MP4 file must be rejected", ExportEngine.verifyExportedMp4(corruptedFile))

        validFile.delete()
        emptyFile.delete()
        corruptedFile.delete()
        tempDir.delete()
    }
}
