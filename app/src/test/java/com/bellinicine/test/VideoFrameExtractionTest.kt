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
    fun computeSourceMediaFrameHandlesDifferingCompositionAndSourceFps() {
        // 24 fps footage in a 60 fps composition
        // At 1.0 second in composition: compFrame = 60 (at 60fps). Source should be at 24 frames (at 24fps).
        val srcFrame = com.bellinicine.core.render.computeSourceMediaFrame(
            compositionFrame = 60L,
            compositionFps = 60f,
            startTimeFrame = 0L,
            timeStretch = 100f,
            sourceFps = 24f
        )
        assertEquals(24L, srcFrame)

        // At 2.5 seconds: compFrame = 150 (at 60fps). Source frame = 2.5 * 24 = 60.
        val srcFrame2 = com.bellinicine.core.render.computeSourceMediaFrame(
            compositionFrame = 150L,
            compositionFps = 60f,
            startTimeFrame = 0L,
            timeStretch = 100f,
            sourceFps = 24f
        )
        assertEquals(60L, srcFrame2)
    }

    @Test
    fun computeSourceMediaTimestampHandlesReversePlaybackAndFreezeFrame() {
        val durationUs = 10_000_000L // 10 seconds source video

        // Freeze frame (timeStretch = 0)
        val freezeUs = com.bellinicine.core.render.computeSourceMediaTimestampUs(
            compositionFrame = 90L,
            compositionFps = 30f,
            startTimeFrame = 0L,
            timeStretch = 0f,
            sourceDurationUs = durationUs
        )
        assertEquals("Freeze frame must remain at timestamp 0", 0L, freezeUs)

        // Reverse playback (timeStretch = -100%): at 1 sec into comp (30 frames at 30fps),
        // playback should be at duration (10s) - 1s = 9s (9,000,000 us)
        val reverseUs = com.bellinicine.core.render.computeSourceMediaTimestampUs(
            compositionFrame = 30L,
            compositionFps = 30f,
            startTimeFrame = 0L,
            timeStretch = -100f,
            sourceDurationUs = durationUs
        )
        assertEquals(9_000_000L, reverseUs)
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
