package com.bellinicine.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import com.bellinicine.core.model.TrackMatteType
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Unified Software & Hardware Compositing Pipeline for Bellini Cine.
 * Features:
 * - Cascading parent-child world matrix transformations
 * - Isolated offscreen Track Mattes (Alpha, Alpha Inverted, Luma BT.709, Luma Inverted)
 * - Deterministic Effect Engine with typed schemas & keyframe evaluation
 * - Hardware video frame decoding and LRU frame cache
 * - Vector geometries (Rectangles, Ellipses, Stars, Polygons)
 * - 20+ After Effects blend modes
 */
object CompositorPipeline {

    var videoFrameProvider: VideoFrameProvider = VideoFrameDecoder
    var compositionProvider: ((String) -> Composition?)? = null

    /**
     * Renders a composition frame into a dedicated Bitmap.
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
        renderEvaluatedLayers(canvas, evaluatedLayers, frame, composition.frameRate)
        canvas.restore()

        return bitmap
    }

    /**
     * Renders evaluated layers onto an Android Canvas with world matrices,
     * masks, isolated track mattes, and real pixel effects.
     */
    fun renderEvaluatedLayers(
        canvas: Canvas,
        evaluatedLayers: List<EvaluatedLayer>,
        frame: Long = 0L,
        fps: Float = 30f
    ) {
        val consumedAsMatte = BooleanArray(evaluatedLayers.size)
        for (i in evaluatedLayers.indices) {
            val layer = evaluatedLayers[i].layer
            if (layer.trackMatteType != TrackMatteType.NONE && i > 0) {
                consumedAsMatte[i - 1] = true
            }
        }

        val clip = canvas.clipBounds
        val bufferW = if (clip != null && clip.width() > 0) clip.width() else 1920
        val bufferH = if (clip != null && clip.height() > 0) clip.height() else 1080

        for (i in evaluatedLayers.indices) {
            if (consumedAsMatte[i]) continue

            val eval = evaluatedLayers[i]
            val layer = eval.layer
            val alpha = (eval.transform.opacity / 100f).coerceIn(0f, 1f)
            if (alpha <= 0.001f) continue

            if (layer.trackMatteType != TrackMatteType.NONE && i > 0) {
                val matteEval = evaluatedLayers[i - 1]
                renderWithTrackMatte(canvas, eval, matteEval, layer.trackMatteType, bufferW, bufferH, frame, fps)
            } else {
                renderSingleEvaluatedLayer(canvas, eval, frame, fps)
            }
        }
    }

    /**
     * Renders a single evaluated layer with its world transform matrix, vector masks, and effects.
     */
    fun renderSingleEvaluatedLayer(
        canvas: Canvas,
        eval: EvaluatedLayer,
        frame: Long = 0L,
        fps: Float = 30f
    ) {
        val layer = eval.layer
        val alpha = (eval.transform.opacity / 100f).coerceIn(0f, 1f)
        if (alpha <= 0.001f) return

        // 1. Convert 4x4 OpenGL column-major world matrix into Android 2D Affine Matrix
        val m = eval.worldMatrix
        val androidMatrix = Matrix()
        androidMatrix.setValues(floatArrayOf(
            m[0], m[4], m[12],
            m[1], m[5], m[13],
            0f,   0f,   1f
        ))

        canvas.save()
        canvas.concat(androidMatrix)

        // 2. Setup Layer Paint & Blend Mode
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.alpha = (alpha * 255).toInt()
        applyBlendMode(paint, layer.blendMode)

        // 3. Apply Full Vector Masks (Curved Bezier Paths, Inversion, Feathering, Opacity)
        if (layer.masks.isNotEmpty()) {
            val clipPath = Path()
            var hasActiveMasks = false

            for (mask in layer.masks) {
                if (mask.vertices.isNotEmpty()) {
                    hasActiveMasks = true
                    val maskPath = Path()
                    val first = mask.vertices.first().point
                    maskPath.moveTo(first.x, first.y)

                    for (j in 1 until mask.vertices.size) {
                        val prev = mask.vertices[j - 1]
                        val curr = mask.vertices[j]
                        val cp1x = prev.point.x + prev.outTangent.x
                        val cp1y = prev.point.y + prev.outTangent.y
                        val cp2x = curr.point.x + curr.inTangent.x
                        val cp2y = curr.point.y + curr.inTangent.y

                        if (prev.outTangent.x != 0f || prev.outTangent.y != 0f || curr.inTangent.x != 0f || curr.inTangent.y != 0f) {
                            maskPath.cubicTo(cp1x, cp1y, cp2x, cp2y, curr.point.x, curr.point.y)
                        } else {
                            maskPath.lineTo(curr.point.x, curr.point.y)
                        }
                    }

                    if (mask.isClosed) {
                        val last = mask.vertices.last()
                        val firstVert = mask.vertices.first()
                        val cp1x = last.point.x + last.outTangent.x
                        val cp1y = last.point.y + last.outTangent.y
                        val cp2x = firstVert.point.x + firstVert.inTangent.x
                        val cp2y = firstVert.point.y + firstVert.inTangent.y

                        if (last.outTangent.x != 0f || last.outTangent.y != 0f || firstVert.inTangent.x != 0f || firstVert.inTangent.y != 0f) {
                            maskPath.cubicTo(cp1x, cp1y, cp2x, cp2y, firstVert.point.x, firstVert.point.y)
                        }
                        maskPath.close()
                    }

                    // Apply Mask Expansion if set
                    val exp = mask.expansion.defaultValue
                    val processedPath = if (exp != 0f) {
                        val expMatrix = Matrix().apply {
                            val bounds = RectF()
                            maskPath.computeBounds(bounds, true)
                            val sx = if (bounds.width() > 0) (bounds.width() + exp * 2f) / bounds.width() else 1f
                            val sy = if (bounds.height() > 0) (bounds.height() + exp * 2f) / bounds.height() else 1f
                            setScale(sx, sy, bounds.centerX(), bounds.centerY())
                        }
                        val expanded = Path()
                        maskPath.transform(expMatrix, expanded)
                        expanded
                    } else maskPath

                    // Apply Mask Inversion
                    val finalPath = if (mask.inverted) {
                        val inv = Path()
                        inv.addRect(RectF(-8000f, -8000f, 18000f, 18000f), Path.Direction.CW)
                        inv.op(processedPath, Path.Op.DIFFERENCE)
                        inv
                    } else processedPath

                    when (mask.mode) {
                        MaskMode.ADD -> clipPath.op(finalPath, Path.Op.UNION)
                        MaskMode.SUBTRACT -> clipPath.op(finalPath, Path.Op.DIFFERENCE)
                        MaskMode.INTERSECT -> clipPath.op(finalPath, Path.Op.INTERSECT)
                        else -> clipPath.addPath(finalPath)
                    }

                    // Apply Mask Feathering filter if configured
                    val featherVal = mask.feather.defaultValue
                    if (featherVal > 0.5f) {
                        paint.maskFilter = BlurMaskFilter(featherVal.coerceIn(1f, 100f), BlurMaskFilter.Blur.NORMAL)
                    }

                    // Scale layer alpha by mask opacity
                    paint.alpha = (paint.alpha * (mask.opacity.defaultValue / 100f).coerceIn(0f, 1f)).toInt()
                }
            }
            if (hasActiveMasks) {
                canvas.clipPath(clipPath)
            }
        }

        // 4. Render Layer Content (with Effects if present)
        val hasActiveEffects = layer.effects.any { it.enabled }
        if (hasActiveEffects) {
            renderLayerWithEffects(canvas, layer, paint, alpha, frame, fps)
        } else {
            renderLayerDirect(canvas, layer, paint, alpha, frame, fps)
        }

        canvas.restore()
    }

    private fun renderLayerDirect(
        canvas: Canvas,
        layer: com.bellinicine.core.model.Layer,
        paint: Paint,
        alpha: Float,
        frame: Long,
        fps: Float
    ) {
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
                val fontMetrics = paint.fontMetrics
                val y = -fontMetrics.ascent
                canvas.drawText(data.text, 0f, y, paint)
            }

            is LayerData.Shape -> {
                renderVectorShape(canvas, data, paint, alpha)
            }

            is LayerData.Footage -> {
                renderFootage(canvas, data, paint, alpha, frame, fps)
            }

            is LayerData.Precomp -> {
                val nestedComp = compositionProvider?.invoke(data.compositionId)
                if (nestedComp != null) {
                    val nestedEvaluated = CompositionEvaluator.evaluateComposition(nestedComp, frame)
                    renderEvaluatedLayers(canvas, nestedEvaluated, frame, nestedComp.frameRate)
                } else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = 0xFF00E676.toInt()
                    canvas.drawRect(0f, 0f, 400f, 250f, paint)
                    paint.style = Paint.Style.FILL
                    paint.textSize = 20f
                    paint.typeface = Typeface.DEFAULT_BOLD
                    paint.color = Color.WHITE
                    canvas.drawText("Precomp: ${layer.name}", 20f, 130f, paint)
                }
            }

            is LayerData.Adjustment -> {
                // Adjustment layer applies effects to underlying composite
            }

            is LayerData.NullObject -> {
                // Null objects are invisible transform handles for parenting hierarchies
            }

            else -> {}
        }
    }

    private fun renderLayerWithEffects(
        canvas: Canvas,
        layer: com.bellinicine.core.model.Layer,
        paint: Paint,
        alpha: Float,
        frame: Long,
        fps: Float
    ) {
        val (w, h) = when (val d = layer.data) {
            is LayerData.Solid -> Pair(d.width, d.height)
            is LayerData.Shape -> Pair(d.width.toInt(), d.height.toInt())
            is LayerData.Footage -> Pair(d.naturalWidth, d.naturalHeight)
            is LayerData.Text -> Pair(800, 200)
            else -> Pair(400, 400)
        }
        val safeW = max(1, w)
        val safeH = max(1, h)

        val layerBmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val offscreenCanvas = Canvas(layerBmp)
        val directPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.alpha = 255
        }

        renderLayerDirect(offscreenCanvas, layer, directPaint, 1f, frame, fps)

        // Process applied effects in chain using EffectProcessor
        val processedBmp = EffectProcessor.applyEffects(layerBmp, layer.effects, frame, fps)

        paint.alpha = (alpha * 255).toInt()
        canvas.drawBitmap(processedBmp, 0f, 0f, paint)

        if (processedBmp !== layerBmp) {
            processedBmp.recycle()
        }
        layerBmp.recycle()
    }

    private fun renderFootage(
        canvas: Canvas,
        footage: LayerData.Footage,
        paint: Paint,
        alpha: Float,
        frame: Long,
        fps: Float
    ) {
        val decoded = videoFrameProvider.getFrame(
            mediaUri = footage.mediaUri,
            frameNumber = frame,
            fps = fps,
            targetWidth = footage.naturalWidth,
            targetHeight = footage.naturalHeight
        )

        if (decoded != null) {
            val srcRect = android.graphics.Rect(0, 0, decoded.width, decoded.height)
            val dstRect = RectF(0f, 0f, footage.naturalWidth.toFloat(), footage.naturalHeight.toFloat())
            canvas.drawBitmap(decoded, srcRect, dstRect, paint)
        } else {
            // Informative offline media visual indicator
            val w = max(200f, footage.naturalWidth.toFloat())
            val h = max(100f, footage.naturalHeight.toFloat())

            paint.style = Paint.Style.FILL
            paint.color = 0xFF1E1E26.toInt()
            canvas.drawRect(0f, 0f, w, h, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0xFFFF5252.toInt()
            canvas.drawRect(0f, 0f, w, h, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 24f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.color = Color.WHITE
            val fileName = File(footage.mediaUri).name
            canvas.drawText("Media Offline: $fileName", 20f, h / 2f, paint)
        }
    }

    private fun renderVectorShape(
        canvas: Canvas,
        shape: LayerData.Shape,
        paint: Paint,
        alpha: Float
    ) {
        val path = Path()
        val w = shape.width
        val h = shape.height

        when (shape.shapeType.lowercase()) {
            "rectangle" -> {
                if (shape.cornerRadius > 0f) {
                    path.addRoundRect(RectF(0f, 0f, w, h), shape.cornerRadius, shape.cornerRadius, Path.Direction.CW)
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

    // --- ISOLATED OFFSCREEN TRACK MATTE PIPELINE ---

    private fun renderWithTrackMatte(
        canvas: Canvas,
        targetEval: EvaluatedLayer,
        matteEval: EvaluatedLayer,
        type: TrackMatteType,
        bufferW: Int,
        bufferH: Int,
        frame: Long,
        fps: Float
    ) {
        val safeW = max(1, bufferW)
        val safeH = max(1, bufferH)

        val targetBmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val targetCanvas = Canvas(targetBmp)
        renderSingleEvaluatedLayer(targetCanvas, targetEval, frame, fps)

        val matteBmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val matteCanvas = Canvas(matteBmp)
        renderSingleEvaluatedLayer(matteCanvas, matteEval, frame, fps)

        when (type) {
            TrackMatteType.ALPHA -> applyAlphaMatte(targetBmp, matteBmp, inverted = false)
            TrackMatteType.ALPHA_INVERTED -> applyAlphaMatte(targetBmp, matteBmp, inverted = true)
            TrackMatteType.LUMA -> applyLumaMatte(targetBmp, matteBmp, inverted = false)
            TrackMatteType.LUMA_INVERTED -> applyLumaMatte(targetBmp, matteBmp, inverted = true)
            TrackMatteType.NONE -> {}
        }

        val compPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        applyBlendMode(compPaint, targetEval.layer.blendMode)
        canvas.drawBitmap(targetBmp, 0f, 0f, compPaint)

        targetBmp.recycle()
        matteBmp.recycle()
    }

    fun applyAlphaMattePixels(
        targetPixels: IntArray,
        mattePixels: IntArray,
        length: Int,
        inverted: Boolean
    ) {
        for (i in 0 until length) {
            val tPix = targetPixels[i]
            val tA = (tPix ushr 24) and 0xff
            if (tA == 0) continue

            val mPix = mattePixels[i]
            val mA = (mPix ushr 24) and 0xff
            val factor = if (inverted) (255 - mA) / 255f else mA / 255f

            val newA = (tA * factor).toInt().coerceIn(0, 255)
            val tR = (tPix ushr 16) and 0xff
            val tG = (tPix ushr 8) and 0xff
            val tB = tPix and 0xff

            val newR = (tR * factor).toInt().coerceIn(0, 255)
            val newG = (tG * factor).toInt().coerceIn(0, 255)
            val newB = (tB * factor).toInt().coerceIn(0, 255)

            targetPixels[i] = (newA shl 24) or (newR shl 16) or (newG shl 8) or newB
        }
    }

    fun applyLumaMattePixels(
        targetPixels: IntArray,
        mattePixels: IntArray,
        length: Int,
        inverted: Boolean
    ) {
        for (i in 0 until length) {
            val tPix = targetPixels[i]
            val tA = (tPix ushr 24) and 0xff
            if (tA == 0) continue

            val mPix = mattePixels[i]
            val mA = (mPix ushr 24) and 0xff
            val mR = (mPix ushr 16) and 0xff
            val mG = (mPix ushr 8) and 0xff
            val mB = mPix and 0xff

            // ITU-R BT.709 coefficients
            val luma = (0.2126f * mR + 0.7152f * mG + 0.0722f * mB) / 255f
            val matteAlphaFactor = (mA / 255f) * luma

            val factor = if (inverted) {
                (1f - matteAlphaFactor).coerceIn(0f, 1f)
            } else {
                matteAlphaFactor.coerceIn(0f, 1f)
            }

            val newA = (tA * factor).toInt().coerceIn(0, 255)
            val tR = (tPix ushr 16) and 0xff
            val tG = (tPix ushr 8) and 0xff
            val tB = tPix and 0xff

            val newR = (tR * factor).toInt().coerceIn(0, 255)
            val newG = (tG * factor).toInt().coerceIn(0, 255)
            val newB = (tB * factor).toInt().coerceIn(0, 255)

            targetPixels[i] = (newA shl 24) or (newR shl 16) or (newG shl 8) or newB
        }
    }

    fun applyAlphaMatte(targetBitmap: Bitmap, matteBitmap: Bitmap, inverted: Boolean) {
        val w = min(targetBitmap.width, matteBitmap.width)
        val h = min(targetBitmap.height, matteBitmap.height)
        val len = w * h
        val targetPixels = IntArray(len)
        val mattePixels = IntArray(len)
        targetBitmap.getPixels(targetPixels, 0, w, 0, 0, w, h)
        matteBitmap.getPixels(mattePixels, 0, w, 0, 0, w, h)
        applyAlphaMattePixels(targetPixels, mattePixels, len, inverted)
        targetBitmap.setPixels(targetPixels, 0, w, 0, 0, w, h)
    }

    fun applyLumaMatte(targetBitmap: Bitmap, matteBitmap: Bitmap, inverted: Boolean) {
        val w = min(targetBitmap.width, matteBitmap.width)
        val h = min(targetBitmap.height, matteBitmap.height)
        val len = w * h
        val targetPixels = IntArray(len)
        val mattePixels = IntArray(len)
        targetBitmap.getPixels(targetPixels, 0, w, 0, 0, w, h)
        matteBitmap.getPixels(mattePixels, 0, w, 0, 0, w, h)
        applyLumaMattePixels(targetPixels, mattePixels, len, inverted)
        targetBitmap.setPixels(targetPixels, 0, w, 0, 0, w, h)
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

    /**
     * High-speed separable multi-pass box blur for real-time mobile effects.
     */
    fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        val a = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var asum: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(4) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var aoutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        var ainsum: Int

        for (curY in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            ainsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            aoutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            asum = 0
            for (idx in -radius..radius) {
                p = pix[yi + min(wm, max(idx, 0))]
                sir = stack[idx + radius]
                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                sir[3] = (p shr 24) and 0xff
                rbs = r1 - abs(idx)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                asum += sir[3] * rbs
                if (idx > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    ainsum += sir[3]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    aoutsum += sir[3]
                }
            }
            stackpointer = radius

            for (curX in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                a[yi] = dv[asum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                asum -= aoutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                aoutsum -= sir[3]

                if (curY == 0) {
                    vmin[curX] = min(curX + radius + 1, wm)
                }
                p = pix[yw + vmin[curX]]

                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                sir[3] = (p shr 24) and 0xff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                ainsum += sir[3]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                asum += ainsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                aoutsum += sir[3]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                ainsum -= sir[3]

                yi++
            }
            yw += w
        }

        for (curX in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            ainsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            aoutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            asum = 0
            yp = -radius * w
            for (idx in -radius..radius) {
                yi = max(0, yp) + curX
                sir = stack[idx + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                sir[3] = a[yi]
                rbs = r1 - abs(idx)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                asum += a[yi] * rbs
                if (idx > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    ainsum += sir[3]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    aoutsum += sir[3]
                }
                if (idx < hm) {
                    yp += w
                }
            }
            yi = curX
            stackpointer = radius
            for (curY in 0 until h) {
                pix[yi] = (dv[asum] shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                asum -= aoutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                aoutsum -= sir[3]

                if (curX == 0) {
                    vmin[curY] = min(curY + r1, hm) * w
                }
                p = curX + vmin[curY]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                sir[3] = a[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                ainsum += sir[3]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                asum += ainsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                aoutsum += sir[3]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                ainsum -= sir[3]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun abs(n: Int): Int = if (n < 0) -n else n
}
