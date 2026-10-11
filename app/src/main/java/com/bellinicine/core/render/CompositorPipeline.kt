package com.bellinicine.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.bellinicine.core.engine.CompositionEvaluator
import com.bellinicine.core.engine.EvaluatedLayer
import com.bellinicine.core.model.BlendMode
import com.bellinicine.core.model.Composition
import com.bellinicine.core.model.LayerData
import com.bellinicine.core.model.MaskMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Unified Software & Hardware Compositing Pipeline for Bellini Cine.
 * Evaluates, composites, and renders all layer types, vector geometries,
 * typography, blend modes, and masks consistently for both viewport preview and video export.
 */
object CompositorPipeline {

    /**
     * Renders a composition frame into a dedicated Bitmap (e.g. for video encoding or caching).
     */
    fun renderCompositionToBitmap(
        composition: Composition,
        frame: Long,
        targetWidth: Int = composition.width,
        targetHeight: Int = composition.height
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        canvas.drawColor(composition.backgroundColor.toInt())

        val evaluatedLayers = CompositionEvaluator.evaluateComposition(composition, frame)

        val scaleX = targetWidth.toFloat() / composition.width.toFloat()
        val scaleY = targetHeight.toFloat() / composition.height.toFloat()

        canvas.save()
        canvas.scale(scaleX, scaleY)
        renderEvaluatedLayers(canvas, evaluatedLayers)
        canvas.restore()

        return bitmap
    }

    /**
     * Renders a list of evaluated layers onto an Android Canvas.
     */
    fun renderEvaluatedLayers(canvas: Canvas, evaluatedLayers: List<EvaluatedLayer>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (eval in evaluatedLayers) {
            val layer = eval.layer
            val transform = eval.transform
            val alpha = (transform.opacity / 100f).coerceIn(0f, 1f)
            if (alpha <= 0.001f) continue

            canvas.save()

            // 1. Setup Layer Transform Matrix: Translation * Rotation * Scale * -AnchorPoint
            canvas.translate(transform.position.x, transform.position.y)
            canvas.rotate(transform.rotationZ)
            canvas.scale(transform.scale.x / 100f, transform.scale.y / 100f)
            canvas.translate(-transform.anchorPoint.x, -transform.anchorPoint.y)

            // 2. Setup Blend Mode
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.alpha = (alpha * 255).toInt()
            applyBlendMode(paint, layer.blendMode)

            // 3. Apply Layer Masks
            if (layer.masks.isNotEmpty()) {
                val clipPath = Path()
                for (mask in layer.masks) {
                    if (mask.vertices.isNotEmpty()) {
                        val maskPath = Path()
                        val first = mask.vertices.first().point
                        maskPath.moveTo(first.x, first.y)
                        for (i in 1 until mask.vertices.size) {
                            val pt = mask.vertices[i].point
                            maskPath.lineTo(pt.x, pt.y)
                        }
                        if (mask.isClosed) maskPath.close()

                        when (mask.mode) {
                            MaskMode.ADD -> clipPath.op(maskPath, Path.Op.UNION)
                            MaskMode.SUBTRACT -> clipPath.op(maskPath, Path.Op.DIFFERENCE)
                            MaskMode.INTERSECT -> clipPath.op(maskPath, Path.Op.INTERSECT)
                            else -> clipPath.addPath(maskPath)
                        }
                    }
                }
                canvas.clipPath(clipPath)
            }

            // 4. Render Content according to Layer Type
            when (val data = layer.data) {
                is LayerData.Solid -> {
                    paint.color = data.color.toInt()
                    paint.alpha = (alpha * 255).toInt()
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(0f, 0f, data.width.toFloat(), data.height.toFloat(), paint)
                }

                is LayerData.Text -> {
                    paint.color = data.fontColor.toInt()
                    paint.alpha = (alpha * 255).toInt()
                    paint.textSize = data.fontSize
                    paint.typeface = Typeface.create(data.fontFamily, Typeface.BOLD)
                    paint.letterSpacing = data.tracking / 100f
                    // Draw text baseline
                    val fontMetrics = paint.fontMetrics
                    val y = -fontMetrics.ascent
                    canvas.drawText(data.text, 0f, y, paint)
                }

                is LayerData.Shape -> {
                    renderVectorShape(canvas, data, paint, alpha)
                }

                is LayerData.Footage -> {
                    // Footage bitmap loading & rendering
                    renderFootagePlaceholder(canvas, data, paint, alpha)
                }

                is LayerData.Adjustment -> {
                    // Adjustment layer applying effects
                }

                else -> {
                    // Null, Camera, Light (Non-visual layer representations)
                }
            }

            canvas.restore()
        }
    }

    private fun renderVectorShape(canvas: Canvas, shape: LayerData.Shape, paint: Paint, alpha: Float) {
        val path = Path()
        val w = shape.width
        val h = shape.height

        when (shape.shapeType.lowercase()) {
            "rectangle", "rect" -> {
                if (shape.cornerRadius > 0f) {
                    val r = shape.cornerRadius
                    path.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
                } else {
                    path.addRect(0f, 0f, w, h, Path.Direction.CW)
                }
            }

            "ellipse", "circle" -> {
                path.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
            }

            "polygon" -> {
                val cx = w / 2f
                val cy = h / 2f
                val radius = min(cx, cy)
                val sides = shape.points.coerceAtLeast(3)
                for (i in 0 until sides) {
                    val angle = (i * 2.0 * PI / sides) - (PI / 2.0)
                    val px = (cx + radius * cos(angle)).toFloat()
                    val py = (cy + radius * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }

            "star" -> {
                val cx = w / 2f
                val cy = h / 2f
                val outerRadius = min(cx, cy)
                val innerRadius = outerRadius * shape.innerRadiusRatio.coerceIn(0.1f, 0.9f)
                val points = shape.points.coerceAtLeast(3) * 2

                for (i in 0 until points) {
                    val radius = if (i % 2 == 0) outerRadius else innerRadius
                    val angle = (i * PI / points * 2.0) - (PI / 2.0)
                    val px = (cx + radius * cos(angle)).toFloat()
                    val py = (cy + radius * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }

            else -> {
                path.addRect(0f, 0f, w, h, Path.Direction.CW)
            }
        }

        // Draw Fill
        paint.style = Paint.Style.FILL
        paint.color = shape.fillColor.toInt()
        paint.alpha = (alpha * 255).toInt()
        canvas.drawPath(path, paint)

        // Draw Stroke
        if (shape.strokeWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = shape.strokeWidth
            paint.color = shape.strokeColor.toInt()
            paint.alpha = (alpha * 255).toInt()
            canvas.drawPath(path, paint)
        }
    }

    private fun renderFootagePlaceholder(canvas: Canvas, footage: LayerData.Footage, paint: Paint, alpha: Float) {
        val w = footage.naturalWidth.coerceAtLeast(400).toFloat()
        val h = footage.naturalHeight.coerceAtLeast(300).toFloat()

        paint.style = Paint.Style.FILL
        paint.color = 0xFF2A2A38.toInt()
        paint.alpha = (alpha * 255).toInt()
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0xFF7C4DFF.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 28f
        canvas.drawText("Footage: ${footage.assetId}", 24f, h / 2f, paint)
    }

    private fun applyBlendMode(paint: Paint, blendMode: BlendMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mode = when (blendMode) {
                BlendMode.NORMAL -> android.graphics.BlendMode.SRC_OVER
                BlendMode.MULTIPLY -> android.graphics.BlendMode.MULTIPLY
                BlendMode.SCREEN -> android.graphics.BlendMode.SCREEN
                BlendMode.OVERLAY -> android.graphics.BlendMode.OVERLAY
                BlendMode.DARKEN -> android.graphics.BlendMode.DARKEN
                BlendMode.LIGHTEN -> android.graphics.BlendMode.LIGHTEN
                BlendMode.ADD, BlendMode.LINEAR_DODGE -> android.graphics.BlendMode.PLUS
                BlendMode.COLOR_DODGE -> android.graphics.BlendMode.COLOR_DODGE
                BlendMode.COLOR_BURN -> android.graphics.BlendMode.COLOR_BURN
                BlendMode.HARD_LIGHT -> android.graphics.BlendMode.HARD_LIGHT
                BlendMode.SOFT_LIGHT -> android.graphics.BlendMode.SOFT_LIGHT
                BlendMode.DIFFERENCE -> android.graphics.BlendMode.DIFFERENCE
                BlendMode.EXCLUSION -> android.graphics.BlendMode.EXCLUSION
                BlendMode.HUE -> android.graphics.BlendMode.HUE
                BlendMode.SATURATION -> android.graphics.BlendMode.SATURATION
                BlendMode.COLOR -> android.graphics.BlendMode.COLOR
                BlendMode.LUMINOSITY -> android.graphics.BlendMode.LUMINOSITY
                else -> android.graphics.BlendMode.SRC_OVER
            }
            paint.blendMode = mode
        } else {
            val porterDuff = when (blendMode) {
                BlendMode.NORMAL -> PorterDuff.Mode.SRC_OVER
                BlendMode.MULTIPLY -> PorterDuff.Mode.MULTIPLY
                BlendMode.SCREEN -> PorterDuff.Mode.SCREEN
                BlendMode.ADD, BlendMode.LINEAR_DODGE -> PorterDuff.Mode.ADD
                BlendMode.LIGHTEN -> PorterDuff.Mode.LIGHTEN
                BlendMode.DARKEN -> PorterDuff.Mode.DARKEN
                else -> PorterDuff.Mode.SRC_OVER
            }
            paint.xfermode = PorterDuffXfermode(porterDuff)
        }
    }
}
