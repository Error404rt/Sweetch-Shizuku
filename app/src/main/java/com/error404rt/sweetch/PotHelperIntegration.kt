package com.error404rt.sweetch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Integration entry points for Morphe's separately installed PO Token Helper.
 *
 * PotHelper exposes a Google Play Services-compatible Binder API, not a generic
 * HTTP endpoint or a yt-dlp plugin. Detecting the companion app is therefore
 * deliberately kept separate from claiming that yt-dlp can consume its tokens.
 */
internal object PotHelperIntegration {
    const val PACKAGE_NAME = "app.morphe.pot.helper"
    const val RELEASES_URL = "https://github.com/MorpheApp/PotHelper/releases/latest"
    private const val SERVICE_ACTION = "app.morphe.pot.helper.potokens.service.START"
    private const val MAIN_ACTIVITY = "app.morphe.pot.helper.MainActivity"

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
                context.packageManager.resolveService(intent, PackageManager.ResolveInfoFlags.of(0L)) != null
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveService(intent, 0) != null
            }
        }.getOrDefault(false)
    }

    fun status(context: Context): String {
        val version = installedVersion(context)
            ?: return "Не установлен. Скачать можно с официальной страницы релизов PotHelper."

        val service = if (hasExportedService(context)) {
            "служба обнаружена"
        } else {
            "служба не обнаружена"
        }

        return "Установлен: $version; $service. Важно: текущий адаптер yt-dlp в Sweetch пока не использует Binder API PotHelper, поэтому установка сама по себе не включает получение PO Token."
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
}
