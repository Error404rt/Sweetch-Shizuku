package com.error404rt.sweetch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional system-wide gesture bridge. Android requires the user to enable this
 * accessibility service explicitly. Text is read only from the long-clicked node
 * and is passed to Sweetch only when the user presses volume-down shortly after.
 */
class SweetchAccessibilityService : AccessibilityService() {
    private var lastLongClickAt = 0L
    private var lastContextText = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) return
        lastLongClickAt = SystemClock.uptimeMillis()
        lastContextText = extractText(event.source, event)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN || event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) {
            return false
        }
        val elapsed = SystemClock.uptimeMillis() - lastLongClickAt
        if (lastLongClickAt == 0L || elapsed !in 0..1800) return false

        lastLongClickAt = 0L
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (lastContextText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, lastContextText)
        }
        lastContextText = ""
        runCatching { startActivity(launch) }
        return true
    }

    override fun onInterrupt() {
        lastLongClickAt = 0L
        lastContextText = ""
    }

    private fun extractText(source: AccessibilityNodeInfo?, event: AccessibilityEvent): String {
        val values = buildList {
            source?.text?.toString()?.let(::add)
            source?.contentDescription?.toString()?.let(::add)
            event.text.forEach { it?.toString()?.let(::add) }
        }
        return values.firstOrNull { it.isNotBlank() }?.trim()?.take(8000).orEmpty()
    }
}
