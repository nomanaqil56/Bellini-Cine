package com.bellinicine.test

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.Project
import com.bellinicine.core.render.ExportConfig
import com.bellinicine.core.render.ExportEngine
import com.bellinicine.core.render.ExportResult
import com.bellinicine.core.storage.ProjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceHardwareValidationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testHardwareMediaCodecEncoderAvailable() {
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        assertNotNull("H.264 / AVC encoder must be present on physical device", encoder)
        val codecName = encoder.name
        val colorFmt = ExportEngine.selectSupportedColorFormat(encoder, MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.release()

        println("PHYSICAL DEVICE ENCODER: $codecName, SELECTED COLOR FORMAT: 0x${Integer.toHexString(colorFmt)}")
        assertTrue("Codec must have a valid name", codecName.isNotBlank())
    }

    @Test
    fun testDeviceHardwareVideoExportExecution() {
        runBlocking {
            val testComp = Composition(
                id = "device_test_comp",
                name = "Device Test Comp",
                width = 720,
                height = 1280,
                frameRate = 30f,
                durationFrames = 30L, // 1 second test clip for fast on-device validation
                layers = listOf(
                    Layer(
                        id = "layer_solid",
                        name = "Background Solid",
                        type = LayerType.SOLID,
                        data = LayerData.Solid(color = 0xFF222222, width = 720, height = 1280),
                        inPointFrame = 0L,
                        outPointFrame = 30L
                    ),
                    Layer(
                        id = "layer_text",
                        name = "Device Text",
                        type = LayerType.TEXT,
                        data = LayerData.Text(text = "HARDWARE EXPORT OK"),
                        inPointFrame = 0L,
                        outPointFrame = 30L
                    )
                )
            )

            val config = ExportConfig(
                width = 720,
                height = 1280,
                fps = 30f,
                bitrate = 4_000_000,
                format = "MP4"
            )

            var lastProgress = 0f
            val result = ExportEngine.exportComposition(
                context = context,
                composition = testComp,
                config = config,
                onProgress = { p -> lastProgress = p }
            )

            println("EXPORT RESULT: $result, LAST PROGRESS: $lastProgress")

            if (result is ExportResult.Failure) {
                println("EXPORT ERROR: ${result.errorMessage}")
                result.cause?.printStackTrace()
            }

            assertTrue(
                "Hardware export on real device must succeed, but got: $result",
                result is ExportResult.Success
            )

            val success = result as ExportResult.Success
            val file = File(success.filePath)
            assertTrue("Exported file must exist on physical device", file.exists())
            assertTrue("Exported file must be > 0 bytes (actual: ${file.length()})", file.length() > 0)
            assertEquals("Exported width must match config", 720, success.width)
            assertEquals("Exported height must match config", 1280, success.height)

            val verified = ExportEngine.verifyExportedMp4(file, isAndroidDeviceRuntime = true)
            assertTrue("Exported MP4 must be valid and verified by MediaMetadataRetriever", verified)
        }
    }

    @Test
    fun testDeviceStorageAtomicPersistence() {
        runBlocking {
            val project = Project(
                id = "device_test_proj",
                name = "Physical Device Project",
                compositions = listOf(
                    Composition(
                        id = "c1",
                        name = "Comp 1",
                        width = 1080,
                        height = 1920
                    )
                )
            )

            ProjectRepository.saveProject(context, project)
            val loaded = ProjectRepository.loadProject(context, project.id)

            assertNotNull("Loaded project must not be null", loaded)
            assertEquals(project.id, loaded?.id)
            assertEquals(project.name, loaded?.name)
            assertEquals(1, loaded?.compositions?.size)

            // Clean up
            File(context.filesDir, "projects/${project.id}.json").delete()
        }
    }
}
