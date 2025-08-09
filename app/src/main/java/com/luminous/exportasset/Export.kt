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
import kotlin.math.min

// ---- Renderers ----
fun renderBitmap(strokes: List<StrokeData>, width: Int, height: Int): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.BLACK
    }
    val minDim = min(width, height).toFloat()
    strokes.forEach { s ->
        val p = AndroidPath()
        if (s.points.isNotEmpty()) {
            val f = s.points.first()
            p.moveTo(f.x * width, f.y * height)
            for (i in 1 until s.points.size) {
                val pt = s.points[i]
                p.lineTo(pt.x * width, pt.y * height)
            }
            paint.strokeWidth = s.widthFraction * minDim
            canvas.drawPath(p, paint)
        }
    }
    return bmp
}

fun renderSvg(strokes: List<StrokeData>, width: Int, height: Int): String {
    val sb = StringBuilder()
    sb.append("""
        <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\">
    """.trimIndent())
    val minDim = min(width, height).toFloat()
    strokes.forEach { s ->
        if (s.points.isEmpty()) return@forEach
        val d = buildString {
            val f = s.points.first()
            append("M ")
            append(f.x * width)
            append(' ')
            append(f.y * height)
            for (i in 1 until s.points.size) {
                val pt = s.points[i]
                append(" L ")
                append(pt.x * width)
                append(' ')
                append(pt.y * height)
            }
        }
        val strokeWidth = s.widthFraction * minDim
        sb.append("<path d=\"")
        sb.append(d)
        sb.append("\" fill=\"none\" stroke=\"black\" stroke-width=\"$strokeWidth\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
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
