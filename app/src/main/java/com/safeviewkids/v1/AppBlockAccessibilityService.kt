package com.safeviewkids.v1

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

class AppBlockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private var foregroundPackage: String? = null
    private var lastBlockTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = serviceInfo.apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            notificationTimeout = 100
        }

        scope.launch {
            while (isActive) {
                checkProtection()
                delay(1000)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            foregroundPackage = event.packageName?.toString()
        }
    }

    private fun checkProtection() {

        val pkg = foregroundPackage ?: return

        if (pkg == packageName) return

        val prefs = getSharedPreferences(
            SafeViewKidsPrefs.PREFS_NAME,
            MODE_PRIVATE
        )

        val selectedApps = prefs.getStringSet(
            SafeViewKidsPrefs.KEY_SELECTED_APPS,
            emptySet()
        ) ?: emptySet()

        val protectedPackage = selectedApps
            .mapNotNull { SafeViewKidsPrefs.APP_PACKAGES[it] }
            .firstOrNull { it == pkg }
            ?: return

        val now = System.currentTimeMillis()

        val unlockUntil = prefs.getLong(
            SafeViewKidsPrefs.KEY_UNLOCK_UNTIL,
            0L
        )

        if (unlockUntil > now) return

        if (unlockUntil != 0L && unlockUntil <= now) {
            prefs.edit()
                .remove(SafeViewKidsPrefs.KEY_UNLOCK_UNTIL)
                .apply()
        }

        val sessionKey =
            SafeViewKidsPrefs.SESSION_PREFIX + protectedPackage

        var sessionStart = prefs.getLong(sessionKey, 0L)

        if (sessionStart == 0L) {
            sessionStart = now

            prefs.edit()
                .putLong(sessionKey, sessionStart)
                .apply()

            return
        }

        val limitMillis =
            prefs.getInt(
                SafeViewKidsPrefs.KEY_LIMIT_SECONDS,
                180
            ).coerceAtLeast(1) * 1000L

        if (now - sessionStart >= limitMillis) {
            blockCurrentApp()
        }
    }

    private fun blockCurrentApp() {

        val now = System.currentTimeMillis()

        if (now - lastBlockTime < 3000L) return

        lastBlockTime = now
        foregroundPackage = null

        performGlobalAction(GLOBAL_ACTION_HOME)

        Handler(Looper.getMainLooper()).postDelayed({

            val intent = Intent(
                this,
                MainActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("blocked", true)
            }

            startActivity(intent)

        }, 250)
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
