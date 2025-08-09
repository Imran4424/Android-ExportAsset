package com.luminous.exportasset

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

private fun densifyNormalized(
    points: List<Offset>,
    width: Int,
    height: Int,
    maxGapPx: Float = 1.5f
): List<Offset> {
    if (points.size < 2) return points
    val out = ArrayList<Offset>(points.size * 2)
    out += points.first()
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val dxPx = (b.x - a.x) * width
        val dyPx = (b.y - a.y) * height
        val distPx = hypot(dxPx, dyPx)
        val steps = floor(distPx / maxGapPx).toInt()
        if (steps > 0) {
            for (s in 1..steps) {
                val t = s / (steps + 1f)
                out += Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        out += b
    }
    return out
}

// ---- Renderers ----
fun renderBitmap(
    strokes: List<StrokeData>,
    width: Int,
    height: Int,
    backgroundColor: Int? = null
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)

    // Fill background if requested (keeps alpha if null)
    if (backgroundColor != null) canvas.drawColor(backgroundColor)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.BLACK
    }

    val minDim = min(width, height).toFloat()

    strokes.forEach { s ->
        val pts = densifyNormalized(s.points, width, height)  // <— key line
        if (pts.isEmpty()) return@forEach

        val p = AndroidPath()
        p.moveTo(pts.first().x * width, pts.first().y * height)
        for (i in 1 until pts.size) {
            val pt = pts[i]
            p.lineTo(pt.x * width, pt.y * height)
        }

        paint.color = s.color.toArgb()                         // respect stroke color
        paint.strokeWidth = s.widthFraction * minDim
        canvas.drawPath(p, paint)
    }

    return bmp
}

private fun Color.toSvgHex(): String {
    // outputs #RRGGBB (alpha ignored in SVG stroke here)
    val a = (alpha * 255).toInt()
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

// ARGB Int -> "#RRGGBB" (alpha ignored; good for solid fills)
private fun Int.toSvgHex(): String {
    val r = (this ushr 16) and 0xFF
    val g = (this ushr 8) and 0xFF
    val b = this and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}

fun renderSvg(
    strokes: List<StrokeData>,
    width: Int,
    height: Int,
    backgroundColor: Int? = null // null => transparent
): String {
    val sb = StringBuilder()

    sb.append(
        """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height" shape-rendering="geometricPrecision">"""
    )

    // Solid background if requested
    if (backgroundColor != null) {
        sb.append("""<rect width="100%" height="100%" fill="${backgroundColor.toSvgHex()}" />""")
    }

    val minDim = min(width, height).toFloat()

    strokes.forEach { s ->
        val pts = densifyNormalized(s.points, width, height)  // <— key line
        if (pts.isEmpty()) return@forEach

        val d = buildString {
            append("M ${pts.first().x * width} ${pts.first().y * height}")
            for (i in 1 until pts.size) {
                val pt = pts[i]
                append(" L ${pt.x * width} ${pt.y * height}")
            }
        }
        val strokeWidth = s.widthFraction * minDim
        sb.append(
            """<path d="$d" fill="none" stroke="${s.color.toSvgHex()}" stroke-width="$strokeWidth" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke"/>"""
        )
    }
    sb.append("</svg>")
    return sb.toString()
}

// ---- Writers ----
fun savePng(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Canvas")
                put(MediaStore.Images.Media.WIDTH, bitmap.width)
                put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                    val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    null
                }
            } else null
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val file = java.io.File(dir, fileName)
            java.io.FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun saveSvg(context: Context, svg: String, fileName: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/svg+xml")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(svg.toByteArray())
                    }
                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    false
                }
            } else false
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(dir, fileName)
            java.io.FileOutputStream(file).use { fos -> fos.write(svg.toByteArray()) }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
