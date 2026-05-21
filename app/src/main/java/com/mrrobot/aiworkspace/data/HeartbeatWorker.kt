package com.mrrobot.aiworkspace.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Doze-resistant failsafe layer underneath [HeartbeatScheduler].
 *
 * The primary path is still [com.mrrobot.aiworkspace.HeartbeatService] with
 * its 60-second polling loop — that gives sub-minute latency under normal
 * conditions. But aggressive OEM battery managers (MIUI, EMUI, OnePlus)
 * can kill foreground services even while the activity is alive, and
 * Doze can suspend our process during deep idle.
 *
 * WorkManager survives both. Android schedules periodic work even in
 * Doze (subject to maintenance windows) and respects the system's
 * battery decisions instead of fighting them. The minimum period is 15
 * minutes, so this layer can't replace the FGS for the usual 30-min
 * cadence — but it's a hard guarantee that *something* fires at least
 * every ~15-30 min even when the FGS is gone.
 *
 * If the FGS happens to fire first, [HeartbeatManager.isHeartbeatDue]
 * returns false on the worker's tick and we no-op. No double-firing.
 */
class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            tick(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            // Log and let WorkManager retry on its own backoff schedule.
            // We avoid Result.failure() because that would stop future
            // periodic invocations of THIS work entry, and the next
            // periodic interval will give us another shot anyway.
            Log.w(TAG, "Heartbeat worker tick failed", t)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
        const val UNIQUE_NAME = "mr_robot_heartbeat_failsafe"

        /**
         * Schedule the periodic worker. Idempotent —
         * [ExistingPeriodicWorkPolicy.KEEP] means repeated calls don't
         * disturb an already-scheduled run.
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancel the failsafe. Idempotent. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_NAME)
        }

        /**
         * Run one heartbeat tick if due. Shared between the worker and
         * any future ad-hoc trigger. Constructs the dependency graph
         * the same way [HeartbeatScheduler] does.
         */
        private suspend fun tick(context: Context) {
            val agentConfigStore = AgentConfigStore(context)
            val config = agentConfigStore.getHeartbeatConfig()
            if (!config.enabled) return

            val settings = SettingsStore(context).settingsFlow.first()
            if (!settings.hasActiveConfiguration()) return

            val memoryStore = MemoryStore(context)
            val chatRepository = ChatRepository(
                agentConfigStore = agentConfigStore,
                memoryStore = memoryStore
            )
            val heartbeatManager = HeartbeatManager(
                agentConfigStore = agentConfigStore,
                memoryStore = memoryStore,
                chatRepository = chatRepository
            )

            if (!heartbeatManager.isHeartbeatDue()) return

            val entry = heartbeatManager.runHeartbeat(settings)
            if (entry.success) {
                Log.d(TAG, "Heartbeat ok at ${entry.timestampEpochMs}")
            } else {
                Log.w(TAG, "Heartbeat failed: ${entry.error}")
            }
        }
    }
}
