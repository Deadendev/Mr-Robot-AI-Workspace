package com.mrrobot.aiworkspace.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Long-lived polling loop that drives [HeartbeatManager] without depending on
 * any UI lifecycle. Runs on a process-lifetime [SupervisorJob] + [Dispatchers.IO]
 * scope so heartbeats keep firing as long as the OS keeps the process alive
 * (which on Android means [com.mrrobot.aiworkspace.HeartbeatService] holding a
 * foreground notification).
 *
 * Modelled on Kai's `TaskScheduler` (commonMain).
 *
 * Lifecycle:
 *  - [start] is idempotent. Repeated calls (e.g. from `HeartbeatService.onCreate`
 *    plus `MainActivity.onStart`) are no-ops if the loop is already running.
 *  - [stop] cancels the loop. The scope itself is process-lifetime so it can be
 *    re-[start]ed after a stop.
 *
 * Each tick:
 *  1. delay [POLL_INTERVAL_MS]
 *  2. read the current heartbeat config + active app settings
 *  3. only proceed if heartbeat is enabled and an API provider is configured
 *  4. if [HeartbeatManager.isHeartbeatDue] returns true, run one heartbeat
 *
 * Exceptions inside the loop are caught and logged. The scheduler MUST keep
 * running across individual heartbeat failures (network blip, model error,
 * etc.) — we record the failure into the log and continue.
 */
class HeartbeatScheduler(
    appContext: Context,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS
) {

    // Re-construct stores against the application context. Independent
    // instances are fine because they all wrap DataStore / SharedPreferences,
    // which are process-singletons under the hood.
    private val agentConfigStore = AgentConfigStore(appContext)
    private val memoryStore = MemoryStore(appContext)
    private val settingsStore = SettingsStore(appContext)
    private val chatRepository = ChatRepository(
        agentConfigStore = agentConfigStore,
        memoryStore = memoryStore
    )
    private val heartbeatManager = HeartbeatManager(
        agentConfigStore = agentConfigStore,
        memoryStore = memoryStore,
        chatRepository = chatRepository
    )

    private val schedulerScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("HeartbeatScheduler")
    )

    @Volatile
    private var loopJob: Job? = null

    /**
     * Starts the polling loop if it isn't already running. Safe to call from
     * any thread.
     */
    fun start() {
        if (loopJob?.isActive == true) return
        synchronized(this) {
            if (loopJob?.isActive == true) return
            loopJob = schedulerScope.launch { runLoop() }
            Log.d(TAG, "Heartbeat scheduler started")
        }
    }

    /** Cancels the loop. Idempotent. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        Log.d(TAG, "Heartbeat scheduler stopped")
    }

    fun isRunning(): Boolean = loopJob?.isActive == true

    private suspend fun runLoop() {
        while (schedulerScope.isActive) {
            delay(pollIntervalMs)
            runCatching { tick() }.onFailure { t ->
                if (t is CancellationException) throw t
                Log.w(TAG, "Heartbeat tick failed", t)
            }
        }
    }

    private suspend fun tick() {
        val config = agentConfigStore.getHeartbeatConfig()
        if (!config.enabled) return

        val settings = settingsStore.settingsFlow.first()
        // No point running a heartbeat the model can't actually answer.
        if (!settings.hasActiveConfiguration()) return

        if (!heartbeatManager.isHeartbeatDue()) return

        val entry = heartbeatManager.runHeartbeat(settings)
        if (entry.success) {
            Log.d(TAG, "Heartbeat ok at ${entry.timestampEpochMs}")
        } else {
            Log.w(TAG, "Heartbeat failed: ${entry.error}")
        }
    }

    companion object {
        private const val TAG = "HeartbeatScheduler"

        /**
         * How often the loop wakes up to check `isHeartbeatDue`. The user-facing
         * `intervalMinutes` setting (default 30) is enforced inside
         * [HeartbeatManager.isHeartbeatDue], so this can stay short without
         * causing extra model calls.
         */
        const val POLL_INTERVAL_MS: Long = 60_000L

        // Process-singleton. The foreground service holds the only strong
        // reference in practice, but other entry points (MainActivity.onStart)
        // re-assert via `start()` to survive aggressive OEM kills.
        @Volatile
        private var instance: HeartbeatScheduler? = null

        fun getInstance(context: Context): HeartbeatScheduler {
            return instance ?: synchronized(this) {
                instance ?: HeartbeatScheduler(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
