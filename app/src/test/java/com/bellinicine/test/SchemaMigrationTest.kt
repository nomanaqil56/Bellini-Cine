package com.bellinicine.test

import com.bellinicine.core.storage.ProjectRepository
import com.bellinicine.core.storage.UnsupportedSchemaVersionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SchemaMigrationTest {

    @Test
    fun legacyV0ProjectFixtureMigratesCorrectlyToV1() {
        val v0Json = """
            {
                "id": "proj-legacy-v0",
                "name": "Legacy V0 Project",
                "compositions": [
                    {
                        "id": "comp-legacy-1",
                        "name": "Legacy Comp 1",
                        "width": 1080,
                        "height": 1920,
                        "pixelAspectRatio": 1.0,
                        "frameRate": 30.0,
                        "durationFrames": 300,
                        "backgroundColor": -16777216,
                        "layers": [
                            {
                                "id": "layer-1",
                                "name": "Old Solid",
                                "type": "SOLID",
                                "data": {
                                    "type": "com.bellinicine.core.model.LayerData.Solid",
                                    "color": -65536,
                                    "width": 1080,
                                    "height": 1920
                                },
                                "inPointFrame": 0,
                                "outPointFrame": 300,
                                "startTimeFrame": 0,
                                "timeStretch": 100.0,
                                "isVisible": true,
                                "isAudioMuted": false,
                                "isLocked": false,
                                "isSolo": false,
                                "isShy": false,
                                "is3D": false,
                                "motionBlur": false,
                                "blendMode": "NORMAL",
                                "trackMatteType": "NONE",
                                "parentLayerId": null,
                                "transform": {
                                    "position": {
                                        "name": "Position",
                                        "defaultValue": { "x": 540.0, "y": 960.0, "z": 0.0 },
                                        "keyframes": [],
                                        "isKeyframed": false,
                                        "expression": null
                                    },
                                    "scale": {
                                        "name": "Scale",
                                        "defaultValue": { "x": 100.0, "y": 100.0, "z": 100.0 },
                                        "keyframes": [],
                                        "isKeyframed": false,
                                        "expression": null
                                    },
                                    "rotation": {
                                        "name": "Rotation",
                                        "defaultValue": 0.0,
                                        "keyframes": [],
                                        "isKeyframed": false,
                                        "expression": null
                                    },
                                    "opacity": {
                                        "name": "Opacity",
                                        "defaultValue": 100.0,
                                        "keyframes": [],
                                        "isKeyframed": false,
                                        "expression": null
                                    },
                                    "anchorPoint": {
                                        "name": "Anchor Point",
                                        "defaultValue": { "x": 540.0, "y": 960.0, "z": 0.0 },
                                        "keyframes": [],
                                        "isKeyframed": false,
                                        "expression": null
                                    }
                                },
                                "masks": [],
                                "effects": [],
                                "labelColorIndex": 0
                            }
                        ],
                        "workAreaStartFrame": 0,
                        "workAreaEndFrame": 300
                    }
                ]
            }
        """.trimIndent()

        val migrated = ProjectRepository.decodeAndMigrateString(v0Json)
        assertNotNull(migrated)
        assertEquals("proj-legacy-v0", migrated.id)
        assertEquals("Legacy V0 Project", migrated.name)
        assertEquals(1, migrated.version)
        assertEquals("comp-legacy-1", migrated.activeCompositionId)
        assertTrue("Assets list must be initialized", migrated.assets.isEmpty())
        assertEquals(1, migrated.compositions.size)
        assertEquals("Legacy Comp 1", migrated.compositions[0].name)
        assertEquals(1, migrated.compositions[0].layers.size)
        assertEquals("Old Solid", migrated.compositions[0].layers[0].name)
    }

    @Test
    fun unsupportedFutureSchemaVersionIsSafelyRejected() {
        val futureJson = """
            {
                "id": "proj-future",
                "name": "Future Project",
                "version": 99,
                "compositions": []
            }
        """.trimIndent()

        try {
            ProjectRepository.decodeAndMigrateString(futureJson)
            fail("Must throw UnsupportedSchemaVersionException for future schema versions")
        } catch (e: UnsupportedSchemaVersionException) {
            assertTrue("Exception message should cite version 99", e.message?.contains("99") == true)
        }
    }

    @Test
    fun malformedJsonThrowsIllegalArgumentException() {
        try {
            ProjectRepository.decodeAndMigrateString("\"just a string\"")
            fail("Must throw IllegalArgumentException for non-object JSON")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must be an object") == true)
        }
    }
}
