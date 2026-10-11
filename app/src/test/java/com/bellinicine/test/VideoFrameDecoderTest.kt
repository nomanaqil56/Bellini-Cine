package com.bellinicine.test

import com.bellinicine.core.render.MediaMetadata
import com.bellinicine.core.render.VideoFrameDecoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for video format detection and metadata descriptors (Finding B).
 */
class VideoFrameDecoderTest {

    @Test
    fun testFileFormatClassification() {
        // Video file formats
        assertTrue(VideoFrameDecoder.isVideoFile("footage.mp4"))
        assertTrue(VideoFrameDecoder.isVideoFile("/storage/emulated/0/DCIM/clip.MOV"))
        assertTrue(VideoFrameDecoder.isVideoFile("render.webm"))
        assertTrue(VideoFrameDecoder.isVideoFile("movie.mkv"))

        // Non-video files
        assertFalse(VideoFrameDecoder.isVideoFile("photo.png"))
        assertFalse(VideoFrameDecoder.isVideoFile("graphic.jpg"))

        // Image file formats
        assertTrue(VideoFrameDecoder.isImageFile("texture.png"))
        assertTrue(VideoFrameDecoder.isImageFile("photo.JPEG"))
        assertTrue(VideoFrameDecoder.isImageFile("banner.webp"))
        assertFalse(VideoFrameDecoder.isImageFile("video.mp4"))
    }

    @Test
    fun testMediaMetadataDescriptor() {
        val meta = MediaMetadata(
            width = 1920,
            height = 1080,
            durationUs = 10_000_000L,
            rotation = 0,
            frameRate = 60f,
            isVideo = true
        )

        assertTrue(meta.isVideo)
        org.junit.Assert.assertEquals(1920, meta.width)
        org.junit.Assert.assertEquals(1080, meta.height)
        org.junit.Assert.assertEquals(60f, meta.frameRate, 0.001f)
    }
}
