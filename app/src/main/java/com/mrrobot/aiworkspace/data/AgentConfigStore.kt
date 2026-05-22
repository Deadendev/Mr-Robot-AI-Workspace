package com.mrrobot.aiworkspace.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

internal val Context.agentConfigDataStore by preferencesDataStore(name = "mr_robot_agent_config")

/** User-editable "soul" — the base system prompt prepended to every chat. */
data class SoulConfig(
    val customPrompt: String = ""
) {
    fun effectivePrompt(): String =
        customPrompt.trim().ifBlank { DEFAULT_SOUL_PROMPT }

    companion object {
        const val DEFAULT_SOUL_PROMPT =
            "You are ALPHA inside Mr. Robot AI Workspace. " +
                "Be precise, practical, and helpful. " +
                "Do not fabricate tool outputs, file contents, or completed work. " +
                "Take the most reasonable interpretation of the user's request and proceed."
    }
}

/** Heartbeat schedule + custom prompt. */
data class HeartbeatConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 30,
    val activeHoursStart: Int = 8,
    val activeHoursEnd: Int = 22,
    val customPrompt: String = "",
    val lastHeartbeatEpochMs: Long = 0L
) {
    fun effectivePrompt(): String =
        customPrompt.trim().ifBlank { DEFAULT_HEARTBEAT_PROMPT }

    companion object {
        const val DEFAULT_HEARTBEAT_PROMPT =
            "[HEARTBEAT] This is an automatic self-check. " +
                "Review your memories and pending tasks. " +
                "If everything looks good and nothing needs attention, " +
                "respond with exactly: HEARTBEAT_OK\n" +
                "If something needs attention (stale memories, due tasks, " +
                "user follow-ups), address it concisely."
    }
}

data class HeartbeatLogEntry(
    val timestampEpochMs: Long,
    val success: Boolean,
    val response: String? = null,
    val error: String? = null
)

/**
 * DataStore-backed storage for the user's "soul" (base system prompt) and heartbeat
 * config / log. Lives in its own DataStore file so it's independent of the API key
 * settings store.
 */
class AgentConfigStore(private val context: Context) {

    private object Keys {
        val SOUL_PROMPT = stringPreferencesKey("soul_prompt")

        val HEARTBEAT_ENABLED = booleanPreferencesKey("heartbeat_enabled")
        val HEARTBEAT_INTERVAL_MINUTES = intPreferencesKey("heartbeat_interval_minutes")
        val HEARTBEAT_ACTIVE_START = intPreferencesKey("heartbeat_active_start")
        val HEARTBEAT_ACTIVE_END = intPreferencesKey("heartbeat_active_end")
        val HEARTBEAT_CUSTOM_PROMPT = stringPreferencesKey("heartbeat_custom_prompt")
        val HEARTBEAT_LAST_RUN_MS = longPreferencesKey("heartbeat_last_run_ms")
        val HEARTBEAT_LOG_JSON = stringPreferencesKey("heartbeat_log_json")
    }

    val soulFlow: Flow<SoulConfig> =
        context.agentConfigDataStore.data.map { prefs ->
            SoulConfig(customPrompt = prefs[Keys.SOUL_PROMPT] ?: "")
        }

    val heartbeatConfigFlow: Flow<HeartbeatConfig> =
        context.agentConfigDataStore.data.map { prefs ->
            HeartbeatConfig(
                enabled = prefs[Keys.HEARTBEAT_ENABLED] ?: false,
                intervalMinutes = prefs[Keys.HEARTBEAT_INTERVAL_MINUTES] ?: 30,
                activeHoursStart = prefs[Keys.HEARTBEAT_ACTIVE_START] ?: 8,
                activeHoursEnd = prefs[Keys.HEARTBEAT_ACTIVE_END] ?: 22,
                customPrompt = prefs[Keys.HEARTBEAT_CUSTOM_PROMPT] ?: "",
                lastHeartbeatEpochMs = prefs[Keys.HEARTBEAT_LAST_RUN_MS] ?: 0L
            )
        }

    val heartbeatLogFlow: Flow<List<HeartbeatLogEntry>> =
        context.agentConfigDataStore.data.map { prefs ->
            decodeLog(prefs[Keys.HEARTBEAT_LOG_JSON] ?: "")
        }

    suspend fun getSoul(): SoulConfig = soulFlow.first()
    suspend fun getHeartbeatConfig(): HeartbeatConfig = heartbeatConfigFlow.first()
    suspend fun getHeartbeatLog(): List<HeartbeatLogEntry> = heartbeatLogFlow.first()

    suspend fun saveSoul(soul: SoulConfig) {
        context.agentConfigDataStore.edit { prefs ->
            prefs[Keys.SOUL_PROMPT] = soul.customPrompt
        }
    }

    suspend fun saveHeartbeatConfig(config: HeartbeatConfig) {
        context.agentConfigDataStore.edit { prefs ->
            prefs[Keys.HEARTBEAT_ENABLED] = config.enabled
            prefs[Keys.HEARTBEAT_INTERVAL_MINUTES] = config.intervalMinutes
            prefs[Keys.HEARTBEAT_ACTIVE_START] = config.activeHoursStart
            prefs[Keys.HEARTBEAT_ACTIVE_END] = config.activeHoursEnd
            prefs[Keys.HEARTBEAT_CUSTOM_PROMPT] = config.customPrompt
            prefs[Keys.HEARTBEAT_LAST_RUN_MS] = config.lastHeartbeatEpochMs
        }
    }

    /**
     * Daemon Mode toggle (Kai 9000 parity). Flips just the `enabled` flag and
     * leaves interval/active-hours/prompt alone so users don't lose their
     * existing schedule when they toggle the daemon off and on. Pair with
     * [HeartbeatService.applyConfigFromBackground] to actually start/stop the
     * foreground service.
     */
    suspend fun setHeartbeatEnabled(enabled: Boolean) {
        context.agentConfigDataStore.edit { prefs ->
            prefs[Keys.HEARTBEAT_ENABLED] = enabled
        }
    }

    suspend fun markHeartbeatRun(timestampMs: Long = System.currentTimeMillis()) {
        context.agentConfigDataStore.edit { prefs ->
            prefs[Keys.HEARTBEAT_LAST_RUN_MS] = timestampMs
        }
    }

    suspend fun appendHeartbeatLog(entry: HeartbeatLogEntry, maxEntries: Int = 20) {
        val current = getHeartbeatLog().toMutableList()
        current.add(0, entry)
        val trimmed = current.take(maxEntries)
        context.agentConfigDataStore.edit { prefs ->
            prefs[Keys.HEARTBEAT_LOG_JSON] = encodeLog(trimmed)
        }
    }

    suspend fun clearHeartbeatLog() {
        context.agentConfigDataStore.edit { prefs ->
            prefs[Keys.HEARTBEAT_LOG_JSON] = ""
        }
    }

    private fun encodeLog(entries: List<HeartbeatLogEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("ts", e.timestampEpochMs)
                    .put("ok", e.success)
                    .put("response", e.response ?: JSONObject.NULL)
                    .put("error", e.error ?: JSONObject.NULL)
            )
        }
        return arr.toString()
    }

    private fun decodeLog(raw: String): List<HeartbeatLogEntry> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                HeartbeatLogEntry(
                    timestampEpochMs = obj.optLong("ts", 0L),
                    success = obj.optBoolean("ok", false),
                    response = obj.optString("response").takeIf { it.isNotBlank() && it != "null" },
                    error = obj.optString("error").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }.getOrDefault(emptyList())
    }
}
