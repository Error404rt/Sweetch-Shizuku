package com.error404rt.sweetch

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.min

/** Local PDF/image operations. Inputs stay untouched and outputs are written to Sweetch folders. */
internal object DocumentOperations {
    fun pdfToImages(context: Context, source: Uri): Int {
        val descriptor = context.contentResolver.openFileDescriptor(source, "r")
            ?: throw IOException("Не удалось открыть PDF")
        val base = displayName(context, source).substringBeforeLast('.', "document")
            .replace(Regex("[^A-Za-z0-9А-Яа-я._ -]"), "_").take(70).ifBlank { "document" }
        var rendered = 0
        PdfRenderer(descriptor).use { renderer ->
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page ->
                    val scale = 2
                    val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    try {
                        publishImage(context, bitmap, "${base}_page_${index + 1}.png")
                        rendered++
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        if (rendered == 0) throw IOException("В PDF нет страниц")
        return rendered
    }

    fun imagesToPdf(context: Context, sources: List<Uri>): String {
        require(sources.isNotEmpty()) { "Сначала выберите изображения" }
        val document = PdfDocument()
        try {
            sources.forEachIndexed { index, uri ->
                val bitmap = decodeImage(context, uri)
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    val margin = 24f
                    val maxWidth = 595f - margin * 2
                    val maxHeight = 842f - margin * 2
                    val scale = min(maxWidth / bitmap.width, maxHeight / bitmap.height)
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    val left = (595f - width) / 2f
                    val top = (842f - height) / 2f
                    page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            val temp = File(context.cacheDir, "sweetch-images-${System.nanoTime()}.pdf")
            temp.outputStream().use { document.writeTo(it) }
            try {
                val name = "Sweetch-images-${System.currentTimeMillis()}.pdf"
                publishDownload(context, temp, name, "application/pdf")
                return name
            } finally {
                temp.delete()
            }
        } finally {
            document.close()
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Не удалось открыть изображение")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("Выбранный файл не является изображением")
        var sample = 1
        while (bounds.outWidth / sample > 5000 || bounds.outHeight / sample > 5000) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Не удалось декодировать изображение")
    }

    private fun publishImage(context: Context, bitmap: Bitmap, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sweetch")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Android не создал изображение")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Не удалось сохранить страницу PDF")
            } ?: throw IOException("Не удалось открыть выходной файл")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun publishDownload(context: Context, source: File, name: String, mime: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Sweetch")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Android не создал PDF в Downloads")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Не удалось открыть PDF для записи")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index) ?: "document"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }
}
