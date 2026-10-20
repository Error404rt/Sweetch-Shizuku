package com.error404rt.sweetch

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Companion-app install path for Morphe's separately distributed PO Token Helper.
 *
 * PotHelper exposes a GMS-compatible Binder service, not a generic HTTP endpoint.
 * This class downloads its official release and verifies the published SHA-256
 * before offering the APK to Android's user-confirmed package installer.
 */
internal object PotHelperIntegration {
    const val PACKAGE_NAME = "app.morphe.pot.helper"
    const val RELEASES_URL = "https://github.com/MorpheApp/PotHelper/releases/latest"
    private const val RELEASE_API_URL = "https://api.github.com/repos/MorpheApp/PotHelper/releases/latest"
    private const val SERVICE_ACTION = "app.morphe.pot.helper.potokens.service.START"
    private const val MAIN_ACTIVITY = "app.morphe.pot.helper.MainActivity"
    private const val PREFS = "pot_helper_integration"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_VERSION = "version"
    private const val KEY_SHA256 = "sha256"
    private const val APK_MIME = "application/vnd.android.package-archive"

    data class DownloadSnapshot(
        val id: Long,
        val status: Int,
        val reason: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) {
        val percent: Int?
            get() = if (totalBytes > 0L) {
                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else null
    }

    enum class InstallResult { SETTINGS_OPENED, INSTALLER_STARTED }

    private data class ReleaseAsset(
        val version: String,
        val name: String,
        val url: String,
        val sha256: String
    )

    fun installedVersion(context: Context): String? {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            }
        }.getOrNull() ?: return null

        return packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "версия неизвестна"
    }

    fun isInstalled(context: Context): Boolean = installedVersion(context) != null

    fun hasExportedService(context: Context): Boolean {
        val intent = Intent(SERVICE_ACTION).setPackage(PACKAGE_NAME)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveService(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L)
                ) != null
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveService(intent, 0) != null
            }
        }.getOrDefault(false)
    }

    fun status(context: Context): String {
        val version = installedVersion(context)
            ?: return "PotHelper не установлен. Можно скачать официальный APK с GitHub; перед установкой Sweetch проверит SHA-256 опубликованного релиза."

        val service = if (hasExportedService(context)) {
            "служба обнаружена"
        } else {
            "служба не обнаружена"
        }

        return "PotHelper установлен: $version; $service. Текущий загрузчик yt-dlp в Sweetch пока не вызывает Binder API помощника — установка сама по себе ещё не включает PO Token."
    }

    fun savedDownloadId(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DOWNLOAD_ID, -1L)

    fun queryDownload(context: Context, id: Long): DownloadSnapshot? {
        if (id <= 0L) return null
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DownloadSnapshot(
                id = id,
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            )
        }
    }

    fun downloadStatusText(context: Context, snapshot: DownloadSnapshot?): String {
        if (isInstalled(context)) return status(context)
        if (snapshot == null) return status(context)

        val version = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_VERSION, "latest") ?: "latest"
        val bytes = "${formatBytes(snapshot.downloadedBytes)}${if (snapshot.totalBytes > 0L) " / ${formatBytes(snapshot.totalBytes)}" else ""}"
        return when (snapshot.status) {
            DownloadManager.STATUS_PENDING -> "Подготавливаю официальный PotHelper $version…"
            DownloadManager.STATUS_RUNNING -> {
                val percent = snapshot.percent?.let { "$it%" } ?: bytes
                "Скачиваю PotHelper $version: $percent ($bytes). Прогресс также виден в уведомлении Android."
            }
            DownloadManager.STATUS_PAUSED -> "Загрузка PotHelper приостановлена (код ${snapshot.reason}). Проверьте подключение и системное уведомление."
            DownloadManager.STATUS_SUCCESSFUL -> "APK PotHelper $version скачан ($bytes). Перед установкой Sweetch сверит его SHA-256 с официальным GitHub Release."
            DownloadManager.STATUS_FAILED -> "Не удалось скачать PotHelper (код ${snapshot.reason}). Нажмите «Скачать APK», чтобы повторить."
            else -> status(context)
        }
    }

    /**
     * Resolves the latest asset from GitHub's release API. Only the expected
     * official HTTPS host/path, APK filename and SHA-256 digest are accepted.
     * Call this from Dispatchers.IO because it performs a network request.
     */
    fun enqueueLatestDownload(context: Context): Long {
        val asset = fetchLatestAsset()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val oldVersion = prefs.getString(KEY_VERSION, null)
        if (oldVersion == asset.version && oldId > 0L) {
            val existing = queryDownload(context, oldId)
            if (existing != null && existing.status != DownloadManager.STATUS_FAILED) {
                val uri = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                    .getUriForDownloadedFile(oldId)
                if (existing.status != DownloadManager.STATUS_SUCCESSFUL || uri != null) return oldId
            }
        }

        val request = DownloadManager.Request(Uri.parse(asset.url))
            .setTitle("PotHelper ${asset.version}")
            .setDescription("Официальный APK MorpheApp · SHA-256 будет проверен перед установкой")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, asset.name)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        prefs.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_VERSION, asset.version)
            .putString(KEY_SHA256, asset.sha256)
            .apply()
        return id
    }

    /**
     * Verifies the downloaded APK against the SHA-256 supplied by GitHub Releases,
     * then opens Android's package installer. User consent and the OS install gate
     * remain controlled by Android.
     */
    suspend fun installDownloaded(context: Context, id: Long): InstallResult {
        val uri = withContext(Dispatchers.IO) {
            val snapshot = queryDownload(context, id)
                ?: throw IOException("Загрузка PotHelper не найдена. Скачайте APK повторно.")
            if (snapshot.status != DownloadManager.STATUS_SUCCESSFUL) {
                throw IOException("APK ещё не скачан полностью.")
            }

            val expected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SHA256, null)
                ?.lowercase(Locale.ROOT)
                ?: throw IOException("Нет контрольной суммы официального релиза. Скачайте APK заново.")

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadUri = manager.getUriForDownloadedFile(id)
                ?: throw IOException("Не найден скачанный APK. Скачайте его повторно.")

            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(downloadUri)?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: throw IOException("Не удалось прочитать скачанный APK.")

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!MessageDigest.isEqual(
                    actual.toByteArray(Charsets.US_ASCII),
                    expected.toByteArray(Charsets.US_ASCII)
                )
            ) {
                throw IOException("SHA-256 APK не совпал с официальным релизом. Установка отменена; скачайте файл заново.")
            }
            downloadUri
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallResult.SETTINGS_OPENED
        }

        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(installIntent)
        return InstallResult.INSTALLER_STARTED
    }

    fun openOfficialReleases(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openHelper(context: Context) {
        context.startActivity(
            Intent().setClassName(PACKAGE_NAME, MAIN_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAppDetails(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", PACKAGE_NAME, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun fetchLatestAsset(): ReleaseAsset {
        val connection = (URL(RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 18_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Sweetch-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

        val json = try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("GitHub Releases вернул HTTP $code.")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }

        val version = json.optString("tag_name").takeIf { it.isNotBlank() }
            ?: throw IOException("В GitHub Release не указана версия.")
        val assets = json.optJSONArray("assets") ?: throw IOException("В GitHub Release нет списка APK.")
        var selected: JSONObject? = null
        for (index in 0 until assets.length()) {
            val candidate = assets.optJSONObject(index) ?: continue
            val name = candidate.optString("name")
            if (name.startsWith("pot-helper-") && name.endsWith(".apk", ignoreCase = true)) {
                selected = candidate
                break
            }
        }
        val asset = selected ?: throw IOException("В последнем официальном релизе не найден pot-helper APK.")
        val name = asset.optString("name")
        val downloadUrl = asset.optString("browser_download_url")
        val parsedUrl = runCatching { URL(downloadUrl) }.getOrNull()
            ?: throw IOException("GitHub Release указал неверный URL APK.")
        if (parsedUrl.protocol != "https" ||
            parsedUrl.host != "github.com" ||
            !parsedUrl.path.startsWith("/MorpheApp/PotHelper/releases/download/") ||
            !parsedUrl.path.endsWith(".apk", ignoreCase = true) ||
            name.contains('/') || name.contains('\\')
        ) {
            throw IOException("URL APK не соответствует ожидаемому официальному домену MorpheApp.")
        }

        val digestField = asset.optString("digest")
        val digest = digestField.removePrefix("sha256:").lowercase(Locale.ROOT)
        if (!digest.matches(Regex("[0-9a-f]{64}"))) {
            throw IOException("В официальном релизе отсутствует корректная SHA-256 контрольная сумма APK.")
        }

        return ReleaseAsset(version, name, downloadUrl, digest)
    }

    private fun formatBytes(value: Long): String {
        if (value < 0L) return "—"
        if (value < 1024L) return "$value B"
        val kib = value / 1024.0
        if (kib < 1024.0) return String.format(Locale.ROOT, "%.1f KiB", kib)
        return String.format(Locale.ROOT, "%.1f MiB", kib / 1024.0)
    }
}
