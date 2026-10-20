package com.error404rt.sweetch

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.IOException
import java.util.Locale

/**
 * Real, local image operations for user-selected SAF documents.
 * Originals are never overwritten; each result is written to Pictures/Sweetch.
 */
internal object MediaFileOperations {
    private data class OutputFormat(val extension: String, val mime: String, val compress: Bitmap.CompressFormat, val quality: Int)

    private fun format(label: String): OutputFormat = when (label.uppercase(Locale.ROOT)) {
        "JPEG", "JPG" -> OutputFormat("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 92)
        "PNG" -> OutputFormat("png", "image/png", Bitmap.CompressFormat.PNG, 100)
        "WEBP" -> if (Build.VERSION.SDK_INT >= 30) {
            OutputFormat("webp", "image/webp", Bitmap.CompressFormat.WEBP_LOSSY, 88)
        } else {
            @Suppress("DEPRECATION")
            OutputFormat("webp", "image/webp", Bitmap.CompressFormat.WEBP, 88)
        }
        else -> throw IllegalArgumentException("Unsupported output format: $label")
    }

    fun convertImage(context: Context, source: Uri, target: String) {
        val output = format(target)
        val bitmap = decode(context, source)
        try { save(context, source, bitmap, output, "converted") } finally { bitmap.recycle() }
    }

    fun compressImage(context: Context, source: Uri) {
        val bitmap = decode(context, source)
        try { save(context, source, bitmap, format("JPEG").copy(quality = 76), "compressed") } finally { bitmap.recycle() }
    }

    fun rotateImage(context: Context, source: Uri, degrees: Float) {
        val original = decode(context, source)
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        if (rotated !== original) original.recycle()
        try { save(context, source, rotated, format("PNG"), "rotated") } finally { rotated.recycle() }
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot open selected file")
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IllegalArgumentException("This file is not a supported bitmap image")
        }
        // Keep memory usage bounded on large camera images while retaining useful output resolution.
        var sample = 1
        while (options.outWidth / sample > 6000 || options.outHeight / sample > 6000 ||
            (options.outWidth / sample.toLong()) * (options.outHeight / sample.toLong()) > 36_000_000L) {
            sample *= 2
        }
        val decoded = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decoded) }
            ?: throw IOException("Cannot decode selected image")
    }

    private fun save(context: Context, source: Uri, bitmap: Bitmap, format: OutputFormat, suffix: String) {
        val resolver = context.contentResolver
        val base = displayName(context, source).substringBeforeLast('.', displayName(context, source))
            .replace(Regex("[^A-Za-z0-9А-Яа-я._ -]"), "_")
            .trim().take(80).ifBlank { "image" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${base}_${suffix}.${format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sweetch")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val outputUri = resolver.insert(collection, values) ?: throw IOException("Android could not create an output image")
        try {
            val stream = resolver.openOutputStream(outputUri, "w") ?: throw IOException("Cannot open output stream")
            stream.use {
                if (!bitmap.compress(format.compress, format.quality, it)) {
                    throw IOException("Image encoder failed")
                }
                it.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(outputUri, ready, null, null)
            }
        } catch (error: Exception) {
            try { resolver.delete(outputUri, null, null) } catch (_: Exception) { }
            throw error
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index) ?: "image"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "image"
    }
}
