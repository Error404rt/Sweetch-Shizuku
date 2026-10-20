package com.error404rt.sweetch

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.IOException
import java.util.Locale

internal enum class MediaKind { IMAGE, AUDIO, VIDEO, OTHER }

/** Real on-device audio/video transcoding using the maintained FFmpegKit runtime. */
internal object MediaTransformOperations {
    fun classify(context: Context, uri: Uri): MediaKind {
        val mime = runCatching { context.contentResolver.getType(uri).orEmpty() }.getOrDefault("")
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif", "tif", "tiff") -> MediaKind.IMAGE
            mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma") -> MediaKind.AUDIO
            mime.startsWith("video/") || ext in setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "mpeg", "mpg", "3gp") -> MediaKind.VIDEO
            else -> MediaKind.OTHER
        }
    }

    private data class Target(val extension: String, val args: List<String>, val mime: String)

    suspend fun convert(context: Context, source: Uri, format: String): String {
        val target = when (format.uppercase(Locale.ROOT)) {
            "MP3" -> Target("mp3", listOf("-vn", "-c:a", "libmp3lame", "-b:a", "192k"), "audio/mpeg")
            "M4A" -> Target("m4a", listOf("-vn", "-c:a", "aac", "-b:a", "192k"), "audio/mp4")
            "WAV" -> Target("wav", listOf("-vn", "-c:a", "pcm_s16le"), "audio/wav")
            "MP4" -> Target("mp4", listOf("-c:v", "mpeg4", "-q:v", "4", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart"), "video/mp4")
            "WEBM" -> Target("webm", listOf("-c:v", "libvpx", "-b:v", "1M", "-c:a", "libopus", "-b:a", "128k"), "video/webm")
            "GIF" -> Target("gif", listOf("-vf", "fps=12,scale=640:-1:flags=lanczos"), "image/gif")
            else -> throw IllegalArgumentException("Неподдерживаемый формат: $format")
        }
        return run(context, source, target, "converted")
    }

    suspend fun compressVideo(context: Context, source: Uri): String =
        run(context, source, Target("mp4", listOf("-c:v", "mpeg4", "-q:v", "7", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"), "video/mp4"), "compressed")

    suspend fun thumbnail(context: Context, source: Uri): String =
        run(context, source, Target("jpg", listOf("-vf", "thumbnail,scale=640:-1", "-frames:v", "1", "-q:v", "3"), "image/jpeg"), "thumbnail")

    private fun run(context: Context, source: Uri, target: Target, suffix: String): String {
        val resolver = context.contentResolver
        val cache = File(context.cacheDir, "media-op-${System.nanoTime()}")
        if (!cache.mkdirs()) throw IOException("Не удалось создать временную папку")
        try {
            val inputName = displayName(context, source)
            val inputExtension = inputName.substringAfterLast('.', "bin").lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]"), "").take(10).ifBlank { "bin" }
            val input = File(cache, "input.$inputExtension")
            resolver.openInputStream(source)?.use { stream -> input.outputStream().use { stream.copyTo(it) } }
                ?: throw IOException("Не удалось прочитать исходный файл")
            val output = File(cache, "result.${target.extension}")
            val args = listOf("-y", "-i", input.absolutePath) + target.args + listOf(output.absolutePath)
            val command = args.joinToString(" ") { arg ->
                if (arg.matches(Regex("[A-Za-z0-9_./:+,=%-]+"))) arg
                else "'" + arg.replace("'", "'\\''") + "'"
            }
            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode) || !output.isFile || output.length() == 0L) {
                val detail = session.failStackTrace ?: session.output?.takeLast(500)
                throw IOException("FFmpeg не смог обработать файл: ${detail ?: session.returnCode}")
            }
            val sourceBase = inputName.substringBeforeLast('.', inputName)
                .replace(Regex("[^A-Za-z0-9А-Яа-я._ -]"), "_").take(70).ifBlank { "media" }
            val outputName = "${sourceBase}_${suffix}.${target.extension}"
            publish(context, output, outputName, target.mime)
            return outputName
        } finally {
            cache.deleteRecursively()
        }
    }

    private fun publish(context: Context, source: File, name: String, mime: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Sweetch")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Android не создал файл в Downloads")
        try {
            resolver.openOutputStream(target, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Не удалось открыть выходной файл")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(target, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(target, null, null) }
            throw error
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index) ?: "media"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "media"
    }
}
