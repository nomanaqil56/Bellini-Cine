package com.bellinicine.core.render

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.bellinicine.core.engine.EvaluatedLayer
import com.bellinicine.core.model.LayerData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Hardware-accelerated OpenGL ES 3.2 compositor for Bellini Cine.
 */
class GlRenderer {

    private var solidProgram: Int = 0
    private var quadVbo: Int = 0
    private val projectionMatrix = FloatArray(16)

    fun initialize(viewportWidth: Int, viewportHeight: Int) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        solidProgram = createProgram(ShaderSources.VERTEX_SHADER_QUAD, ShaderSources.FRAGMENT_SHADER_SOLID)

        // Setup 1x1 quad mesh vertices: (x, y, u, v)
        val quadVertices = floatArrayOf(
            0f, 0f, 0f, 0f,
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
        val vboArray = IntArray(1)
        GLES30.glGenBuffers(1, vboArray, 0)
        quadVbo = vboArray[0]

        val buffer: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        buffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadVertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        updateViewport(viewportWidth, viewportHeight)
    }

    fun updateViewport(width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        // Orthographic projection matrix: (0, width, height, 0) for 2D screen coordinates
        Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1000f, 1000f)
    }

    fun renderFrame(
        evaluatedLayers: List<EvaluatedLayer>,
        compWidth: Int,
        compHeight: Int,
        backgroundColor: Long
    ) {
        // Clear background
        val a = ((backgroundColor shr 24) and 0xFF) / 255f
        val r = ((backgroundColor shr 16) and 0xFF) / 255f
        val g = ((backgroundColor shr 8) and 0xFF) / 255f
        val b = (backgroundColor and 0xFF) / 255f
        GLES30.glClearColor(r, g, b, a)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Render each active evaluated layer
        for (evalLayer in evaluatedLayers) {
            val layer = evalLayer.layer
            val transform = evalLayer.transform

            when (val data = layer.data) {
                is LayerData.Solid -> {
                    renderSolidLayer(data, evalLayer.worldMatrix, transform.opacity)
                }
                is LayerData.Text,
                is LayerData.Shape -> {
                    renderSolidLayer(
                        LayerData.Solid(0xFF7C4DFF, 300, 100),
                        evalLayer.worldMatrix,
                        transform.opacity
                    )
                }
                else -> {
                    // Footage, Adjustment, Null, Camera, Light
                }
            }
        }
    }

    private fun renderSolidLayer(solid: LayerData.Solid, worldMatrix: FloatArray, opacity: Float) {
        GLES30.glUseProgram(solidProgram)

        // Scale quad by layer natural dimensions (width x height)
        val scaledWorldMatrix = FloatArray(16)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        Matrix.scaleM(scaleMatrix, 0, solid.width.toFloat(), solid.height.toFloat(), 1f)
        Matrix.multiplyMM(scaledWorldMatrix, 0, worldMatrix, 0, scaleMatrix, 0)

        val uProjLoc = GLES30.glGetUniformLocation(solidProgram, "uProjection")
        val uWorldLoc = GLES30.glGetUniformLocation(solidProgram, "uWorldMatrix")
        val uColorLoc = GLES30.glGetUniformLocation(solidProgram, "uColor")
        val uOpacLoc = GLES30.glGetUniformLocation(solidProgram, "uOpacity")

        GLES30.glUniformMatrix4fv(uProjLoc, 1, false, projectionMatrix, 0)
        GLES30.glUniformMatrix4fv(uWorldLoc, 1, false, scaledWorldMatrix, 0)

        val sr = ((solid.color shr 16) and 0xFF) / 255f
        val sg = ((solid.color shr 8) and 0xFF) / 255f
        val sb = (solid.color and 0xFF) / 255f
        val sa = ((solid.color shr 24) and 0xFF) / 255f
        GLES30.glUniform4f(uColorLoc, sr, sg, sb, sa)
        GLES30.glUniform1f(uOpacLoc, opacity)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vShader)
        GLES30.glAttachShader(program, fShader)
        GLES30.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e("GlRenderer", "Shader compilation error: $log")
        }
        return shader
    }
}
