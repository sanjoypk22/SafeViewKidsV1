package com.safeviewkids.v1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class AppMonitorService : Service() {

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    private val protectedPackages = setOf(
        "com.google.android.youtube",
        "com.instagram.android",
        "com.facebook.katana"
    )

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            1001,
            createNotification()
        )

        startMonitoring()
    }

    private fun startMonitoring() {

        serviceScope.launch {

            while (isActive) {

                val foregroundApp =
                    getForegroundPackage()

                if (foregroundApp in protectedPackages) {

                    println(
                        "SafeView Kids: Protected app detected = $foregroundApp"
                    )
                }

                delay(1000)
            }
        }
    }

    private fun getForegroundPackage(): String? {

        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 5000

        val stats =
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

        return stats
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private fun createNotificationChannel() {

        val channel = NotificationChannel(
            "safeview_monitor",
            "SafeView Kids Monitor",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager =
            getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {

        return Notification.Builder(
            this,
            "safeview_monitor"
        )
            .setContentTitle("SafeView Kids")
            .setContentText("Protection is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
