package com.error404rt.sweetch

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Minimal, permission-aware Shizuku integration. It never assumes root access;
 * privileged operations must check isGranted before using the binder.
 */
internal object ShizukuBridge {
    private const val REQUEST_CODE = 7401

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isGranted(): Boolean {
        if (!isRunning()) return false
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestPermission() {
        if (isRunning() && !isGranted()) Shizuku.requestPermission(REQUEST_CODE)
    }

    fun status(context: Context): String = when {
        !isRunning() -> "Shizuku не запущен"
        isGranted() -> "Shizuku подключён · разрешение выдано"
        else -> "Shizuku подключён · требуется разрешение"
    }
}
