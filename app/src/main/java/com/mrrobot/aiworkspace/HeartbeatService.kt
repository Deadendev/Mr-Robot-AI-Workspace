package com.mrrobot.aiworkspace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.mrrobot.aiworkspace.data.AgentConfigStore
import com.mrrobot.aiworkspace.data.HeartbeatScheduler
import com.mrrobot.aiworkspace.data.HeartbeatWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Foreground service whose only job is to keep the app process alive so that
 * [HeartbeatScheduler]'s long-lived coroutine scope keeps polling.
 *
 * Lifecycle contract:
 *  - `onCreate` posts the ongoing notification, marks the service as a
 *    `dataSync` foreground service (Android 14+ requirement), and asks the
 *    scheduler to `start()` (idempotent).
 *  - `onStartCommand` returns `START_STICKY` so the OS recreates us if killed.
 *  - `onTimeout` (Android 15+) is handled by relinquishing the FGS state and
 *    stopping; the scheduler's process-lifetime scope decouples from the
 *    Service's lifecycle, but if the OS kills us hard the next foreground
 *    transition (`MainActivity.onStart`) will re-assert via
 *    [applyConfigFromBackground].
 *
 * NOT magic: aggressive OEM battery managers (MIUI, Huawei, etc.) can still
 * kill the foreground service. The Phase-1 design accepts this and re-asserts
 * the service every time the activity comes to the foreground. A future
 * upgrade can layer a `WorkManager` `PeriodicWorkRequest` underneath as a
 * Doze-resistant failsafe (15-min minimum) — out of scope for this PR.
 */
class HeartbeatService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        try {
            startForegroundCompat()
        } catch (t: Throwable) {
            // Posting a foreground service can fail on locked-down OEM
            // builds or if the FGS quota is exceeded. Don't crash; just log
            // and bail out so the next user interaction can retry.
            Log.w(TAG, "startForeground failed; stopping service", t)
            stopSelf()
            return
        }
        HeartbeatScheduler.getInstance(applicationContext).start()
        Log.d(TAG, "HeartbeatService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTimeout(startId: Int) {
        // Android 15+: dataSync FGS has a per-day budget. When the OS times us
        // out we have to relinquish foreground status. The scheduler keeps
        // running on its own scope until the OS reclaims the process; the
        // next foreground transition will restart us.
        Log.d(TAG, "onTimeout — releasing foreground service status")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Do NOT cancel the scheduler here. The whole point of decoupling the
        // scheduler from the Service is so a re-launch (`START_STICKY`) finds
        // it already running. Cancelling on destroy would create a 60-second
        // gap on every OEM-induced restart.
        super.onDestroy()
        Log.d(TAG, "HeartbeatService destroyed")
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Mr. Robot")
            .setContentText("Heartbeat active")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "HeartbeatService"
        private const val CHANNEL_ID = "mr_robot_heartbeat"
        private const val NOTIFICATION_ID = 4201

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Heartbeat",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the heartbeat self-check running in the background."
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }

        private fun startServiceCompat(context: Context) {
            val intent = Intent(context, HeartbeatService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                // ForegroundServiceStartNotAllowedException (Android 12+) when
                // the app isn't in a state that's allowed to start an FGS.
                // Don't crash; the next legitimate foreground tick from
                // MainActivity.onStart will retry.
                Log.w(TAG, "startForegroundService failed", t)
            }
        }

        /**
         * Stop the service if it's running. Idempotent — does nothing if the
         * service was never started.
         */
        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, HeartbeatService::class.java)
            runCatching { context.applicationContext.stopService(intent) }
            HeartbeatScheduler.getInstance(context.applicationContext).stop()
            HeartbeatWorker.cancel(context.applicationContext)
        }

        /**
         * Read the current [com.mrrobot.aiworkspace.data.HeartbeatConfig] off
         * the main thread and either start or stop the service to match the
         * user's setting. Safe to call from `MainActivity.onStart` or from a
         * settings ViewModel after a save. Performs a DataStore read so MUST
         * be called from a coroutine on a background dispatcher.
         */
        suspend fun applyConfigFromBackground(context: Context) {
            val app = context.applicationContext
            val enabled = withContext(Dispatchers.IO) {
                runCatching { AgentConfigStore(app).getHeartbeatConfig().enabled }
                    .getOrDefault(false)
            }
            if (enabled) {
                startServiceCompat(app)
                // Belt-and-braces: WorkManager periodic worker fires
                // even when the FGS is killed by an aggressive OEM or
                // the OS enters deep Doze. 15-min minimum period; the
                // worker no-ops if the FGS already fired in the same
                // window (isHeartbeatDue is the gate).
                HeartbeatWorker.enqueue(app)
            } else {
                stop(app)
            }
        }
    }
}
