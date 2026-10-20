package com.error404rt.sweetch

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpException
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * yt-dlp adapter for media pages. Download work is asynchronous; completed files
 * are copied from app-private staging into the public Downloads/Sweetch collection.
 */
internal object MediaDownloader {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val directFileExtensions = setOf(
        "7z", "aac", "avi", "bmp", "csv", "doc", "docx", "flac", "gif", "gz",
        "heic", "jpeg", "jpg", "m4a", "m4v", "mkv", "mov", "mp3", "mp4", "mpeg",
        "ogg", "opus", "pdf", "png", "ppt", "pptx", "rar", "rtf", "svg", "tar",
        "txt", "wav", "webm", "webp", "xls", "xlsx", "zip"
    )

    fun isLikelyDirectFile(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return false
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: return false
        return extension in directFileExtensions
    }

    suspend fun downloadMediaPage(context: Context, rawUrl: String, onProgress: (String) -> Unit) {
        val uri = Uri.parse(rawUrl.trim())
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Введите корректную ссылку HTTP(S)." }
        val staging = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "media-staging")
        if (!staging.exists() && !staging.mkdirs()) throw IOException("Не удалось создать папку загрузки")
        val before = staging.listFiles()?.map { it.name }?.toSet().orEmpty()
        onMain { onProgress("Инициализирую yt-dlp…") }
        try {
            YtDlp.init(context.applicationContext)
        } catch (error: YtDlpException) {
            throw IOException("Не удалось инициализировать движок yt-dlp: ${error.message}", error)
        }
        val template = File(staging, "%(title).120B [%(id)s].%(ext)s").absolutePath
        val request = YtDlpRequest(uri.toString())
            .setOutputTemplate(template)
            .addOption("--no-warnings")
            .addOption("--no-mtime")
            .addOption("-f", "best[height<=1080]/best")
        onMain { onProgress("Разбираю страницу и загружаю медиа…") }
        val response = try {
            YtDlp.executeAsync(request) { progress, eta, line ->
                val message = when {
                    progress >= 0f -> "Загрузка: ${progress.toInt()}% · ETA ${eta}s"
                    line.isNotBlank() -> line.take(120)
                    else -> "Загружаю медиа…"
                }
                onMain { onProgress(message) }
            }.get(24, TimeUnit.HOURS)
        } catch (error: Exception) {
            throw IOException("Ошибка yt-dlp: ${error.cause?.message ?: error.message}", error)
        }
        if (!response.isSuccess) {
            throw IOException("yt-dlp завершился с кодом ${response.exitCode}. Сервис мог потребовать cookies или дополнительную проверку.")
        }
        val completed = staging.listFiles()?.filter { it.isFile && it.name !in before && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }.orEmpty()
        if (completed.isEmpty()) throw IOException("yt-dlp завершился без готового файла")
        var copied = 0
        completed.forEach { file ->
            publishToDownloads(context, file)
            copied++
            runCatching { file.delete() }
        }
        onMain { onProgress("Готово: сохранено файлов — $copied, папка Downloads/Sweetch") }
    }

    private fun publishToDownloads(context: Context, source: File) {
        val resolver = context.contentResolver
        val mime = when (source.extension.lowercase(Locale.ROOT)) {
            "mp4", "m4v", "mov" -> "video/mp4"
            "mkv", "webm", "avi" -> "video/*"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, source.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Sweetch")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val target = resolver.insert(collection, values) ?: throw IOException("Android не создал файл в Downloads")
        try {
            resolver.openOutputStream(target, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            } ?: throw IOException("Не удалось открыть файл назначения")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(target, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(target, null, null) }
            throw error
        }
    }

    private fun onMain(action: () -> Unit) {
        mainHandler.post(action)
    }
}
