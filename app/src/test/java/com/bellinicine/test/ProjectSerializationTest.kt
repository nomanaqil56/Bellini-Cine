package com.bellinicine.test

import com.bellinicine.core.model.AnimatedProperty
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.Layer
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.LayerType
import com.bellinicine.core.model.Project
import com.bellinicine.core.model.Transform
import com.bellinicine.core.model.Vec3
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProjectSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testProjectSerializationRoundTrip() {
        val solidLayer = Layer(
            id = "layer-1",
            name = "Background Solid",
            type = LayerType.SOLID,
            data = LayerData.Solid(0xFF16161D, 1080, 1920),
            inPointFrame = 0L,
            outPointFrame = 300L,
            transform = Transform(
                position = AnimatedProperty("Position", Vec3(540f, 960f, 0f))
            )
        )

        val comp = Composition(
            id = "comp-1",
            name = "Main Comp",
            width = 1080,
            height = 1920,
            frameRate = 30f,
            durationFrames = 300L,
            layers = listOf(solidLayer)
        )

        val project = Project(
            id = "proj-1",
            name = "Test Cine Project",
            compositions = listOf(comp),
            activeCompositionId = "comp-1",
            version = 1
        )

        // 1. Serialize to JSON
        val encoded = json.encodeToString(project)
        assertNotNull(encoded)

        // 2. Deserialize back
        val decoded = json.decodeFromString<Project>(encoded)
        assertEquals(project.id, decoded.id)
        assertEquals(project.name, decoded.name)
        assertEquals(1, decoded.compositions.size)

        val decodedComp = decoded.compositions[0]
        assertEquals(1080, decodedComp.width)
        assertEquals(1920, decodedComp.height)
        assertEquals(1, decodedComp.layers.size)

        val decodedLayer = decodedComp.layers[0]
        assertEquals("layer-1", decodedLayer.id)
        assertEquals(LayerType.SOLID, decodedLayer.type)
        assertEquals(540f, decodedLayer.transform.position.defaultValue.x, 0.001f)
    }
}
