package com.error404rt.sweetch

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * Imports a user-selected Netscape cookies.txt into app-private no-backup storage.
 * Cookie contents are never logged or displayed in the UI.
 */
internal object CookieStore {
    private const val COOKIE_FILE = "media-cookies.txt"

    fun import(context: Context, source: Uri): Int {
        val text = context.contentResolver.openInputStream(source)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IOException("Не удалось прочитать файл cookies")
        val normalized = text.removePrefix("\uFEFF")
        val lines = normalized.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
        val hasNetscapeHeader = lines.firstOrNull()?.let {
            it.startsWith("# Netscape HTTP Cookie File") || it.startsWith("# HTTP Cookie File")
        } == true
        val cookieRows = lines.count { line -> !line.startsWith("#") && line.split('\t').size >= 7 }
        if (!hasNetscapeHeader || cookieRows == 0) {
            throw IllegalArgumentException("Ожидается Netscape cookies.txt с заголовком и строками cookies")
        }
        val destination = File(context.noBackupFilesDir, COOKIE_FILE)
        destination.writeText(normalized, Charsets.UTF_8)
        destination.setReadable(false, false)
        destination.setWritable(false, false)
        destination.setReadable(true, true)
        destination.setWritable(true, true)
        return cookieRows
    }

    fun file(context: Context): File? = File(context.noBackupFilesDir, COOKIE_FILE).takeIf { it.isFile && it.length() > 0L }

    fun clear(context: Context) {
        file(context)?.delete()
    }
}
