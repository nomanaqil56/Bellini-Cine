package com.bellinicine.test

import com.bellinicine.core.render.CompositorPipeline
import com.bellinicine.core.render.VideoFrameDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoFrameExtractionTest {

    @Test
    fun computeSourceMediaFrameRespectsStartTimeAndNormalSpeed() {
        // Normal 100% speed, startTime = 0
        assertEquals(0L, CompositorPipeline.computeSourceMediaFrame(compositionFrame = 0L, startTimeFrame = 0L, timeStretch = 100f))
        assertEquals(45L, CompositorPipeline.computeSourceMediaFrame(compositionFrame = 45L, startTimeFrame = 0L, timeStretch = 100f))

        // Normal 100% speed, startTime = 30 (layer begins at comp frame 30)
        assertEquals(0L, CompositorPipeline.computeSourceMediaFrame(compositionFrame = 30L, startTimeFrame = 30L, timeStretch = 100f))
        assertEquals(15L, CompositorPipeline.computeSourceMediaFrame(compositionFrame = 45L, startTimeFrame = 30L, timeStretch = 100f))
    }

    @Test
    fun computeSourceMediaFrameHandlesTimeStretchSlowMotionAndFastForward() {
        // 200% time stretch (half speed slow-motion): 60 comp frames advance 30 source frames
        val slowMoFrame = CompositorPipeline.computeSourceMediaFrame(compositionFrame = 60L, startTimeFrame = 0L, timeStretch = 200f)
        assertEquals(30L, slowMoFrame)

        // 50% time stretch (double speed fast-forward): 30 comp frames advance 60 source frames
        val fastForwardFrame = CompositorPipeline.computeSourceMediaFrame(compositionFrame = 30L, startTimeFrame = 0L, timeStretch = 50f)
        assertEquals(60L, fastForwardFrame)
    }

    @Test
    fun computeSourceMediaFrameClampsNegativeFrames() {
        // If composition frame is before startTimeFrame, clamp to 0
        val clamped = CompositorPipeline.computeSourceMediaFrame(compositionFrame = 10L, startTimeFrame = 20L, timeStretch = 100f)
        assertEquals(0L, clamped)
    }

    @Test
    fun isVideoFileAndIsImageFileDetection() {
        assertTrue(VideoFrameDecoder.isVideoFile("file:///storage/emulated/0/DCIM/video.mp4"))
        assertTrue(VideoFrameDecoder.isVideoFile("content://media/external/video/media/123"))
        assertTrue(VideoFrameDecoder.isVideoFile("/path/to/movie.mov"))
        assertTrue(VideoFrameDecoder.isVideoFile("clip.webm"))

        assertTrue(VideoFrameDecoder.isImageFile("photo.png"))
        assertTrue(VideoFrameDecoder.isImageFile("content://media/external/images/media/456"))
        assertTrue(VideoFrameDecoder.isImageFile("/path/to/graphic.webp"))
        assertTrue(VideoFrameDecoder.isImageFile("picture.jpg"))

        assertFalse(VideoFrameDecoder.isVideoFile("audio.mp3"))
        assertFalse(VideoFrameDecoder.isImageFile("video.mp4"))
    }

    @Test
    fun isMediaAvailableCorrectlyDetectsExistingAndMissingFiles() {
        val tempFile = File.createTempFile("test_media_detect", ".txt")
        try {
            assertTrue("Existing file must report available", VideoFrameDecoder.isMediaAvailable(null, tempFile.absolutePath))
            assertFalse("Non-existent path must report unavailable", VideoFrameDecoder.isMediaAvailable(null, "/invalid/missing_media.mp4"))
        } finally {
            tempFile.delete()
        }
    }
}
