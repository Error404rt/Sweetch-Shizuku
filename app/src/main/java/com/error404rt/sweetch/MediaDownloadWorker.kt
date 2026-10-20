package com.error404rt.sweetch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.Data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistent foreground worker for media-page downloads. It survives activity closure
 * and publishes progress to the system notification and WorkManager state.
 */
internal class MediaDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)?.trim().orEmpty()
        if (url.isBlank()) return Result.failure(workDataOf(KEY_MESSAGE to "Не указана ссылка"))
        return try {
            setForeground(createForegroundInfo("Подготавливаю загрузку…"))
            withContext(Dispatchers.IO) {
                MediaDownloader.downloadMediaPage(applicationContext, url) { message ->
                    setProgressAsync(workDataOf(KEY_MESSAGE to message))
                    setForegroundAsync(createForegroundInfo(message))
                }
            }
            Result.success(workDataOf(KEY_MESSAGE to "Загрузка завершена"))
        } catch (error: Exception) {
            val message = error.message ?: "Загрузка не удалась"
            setForeground(createForegroundInfo(message, ongoing = false))
            Result.failure(workDataOf(KEY_MESSAGE to message))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Загрузка медиа…")

    private fun createForegroundInfo(message: String, ongoing: Boolean = true): ForegroundInfo {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Sweetch · загрузка медиа")
            .setContentText(message.take(180))
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Sweetch downloads", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Progress of media downloads started in Sweetch"
                    }
                )
            }
        }
    }

    companion object {
        const val KEY_URL = "media_url"
        const val KEY_MESSAGE = "message"
        private const val CHANNEL_ID = "sweetch_media_downloads"
        private const val NOTIFICATION_ID = 4107

        fun enqueue(context: Context, url: String) {
            val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                .setInputData(workDataOf(KEY_URL to url))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
