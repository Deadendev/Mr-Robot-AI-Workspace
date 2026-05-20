package com.mrrobot.aiworkspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrrobot.aiworkspace.HeartbeatService
import com.mrrobot.aiworkspace.data.AgentConfigStore
import com.mrrobot.aiworkspace.data.AppSettings
import com.mrrobot.aiworkspace.data.ChatRepository
import com.mrrobot.aiworkspace.data.HeartbeatConfig
import com.mrrobot.aiworkspace.data.HeartbeatLogEntry
import com.mrrobot.aiworkspace.data.HeartbeatManager
import com.mrrobot.aiworkspace.data.MemoryStore
import com.mrrobot.aiworkspace.data.SettingsStore
import com.mrrobot.aiworkspace.data.SoulConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SoulHeartbeatUiState(
    val soulPrompt: String = "",
    val isSoulDirty: Boolean = false,

    val heartbeatEnabled: Boolean = false,
    val intervalMinutes: Int = 30,
    val activeHoursStart: Int = 8,
    val activeHoursEnd: Int = 22,
    val heartbeatPrompt: String = "",
    val isHeartbeatDirty: Boolean = false,

    val log: List<HeartbeatLogEntry> = emptyList(),
    val isRunning: Boolean = false,
    val savedMessage: String = ""
)

class SoulHeartbeatViewModel(application: Application) : AndroidViewModel(application) {

    private val agentConfigStore = AgentConfigStore(application.applicationContext)
    private val memoryStore = MemoryStore(application.applicationContext)
    private val settingsStore = SettingsStore(application.applicationContext)
    private val chatRepository = ChatRepository(
        agentConfigStore = agentConfigStore,
        memoryStore = memoryStore
    )
    private val heartbeatManager = HeartbeatManager(
        agentConfigStore = agentConfigStore,
        memoryStore = memoryStore,
        chatRepository = chatRepository
    )

    private val _uiState = MutableStateFlow(SoulHeartbeatUiState())
    val uiState: StateFlow<SoulHeartbeatUiState> = _uiState.asStateFlow()

    private var activeSettings: AppSettings = AppSettings()

    init {
        viewModelScope.launch {
            agentConfigStore.soulFlow.collect { soul ->
                if (!_uiState.value.isSoulDirty) {
                    _uiState.value = _uiState.value.copy(soulPrompt = soul.customPrompt)
                }
            }
        }
        viewModelScope.launch {
            agentConfigStore.heartbeatConfigFlow.collect { config ->
                if (!_uiState.value.isHeartbeatDirty) {
                    _uiState.value = _uiState.value.copy(
                        heartbeatEnabled = config.enabled,
                        intervalMinutes = config.intervalMinutes,
                        activeHoursStart = config.activeHoursStart,
                        activeHoursEnd = config.activeHoursEnd,
                        heartbeatPrompt = config.customPrompt
                    )
                }
            }
        }
        viewModelScope.launch {
            agentConfigStore.heartbeatLogFlow.collect { log ->
                _uiState.value = _uiState.value.copy(log = log)
            }
        }
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { activeSettings = it }
        }
    }

    // ─── Soul ────────────────────────────────────────────────────

    fun updateSoul(value: String) {
        _uiState.value = _uiState.value.copy(soulPrompt = value, isSoulDirty = true)
    }

    fun saveSoul() {
        val state = _uiState.value
        viewModelScope.launch {
            agentConfigStore.saveSoul(SoulConfig(customPrompt = state.soulPrompt))
            _uiState.value = _uiState.value.copy(
                isSoulDirty = false,
                savedMessage = "Soul saved."
            )
        }
    }

    fun resetSoul() {
        _uiState.value = _uiState.value.copy(
            soulPrompt = "",
            isSoulDirty = true,
            savedMessage = "Soul reset to default. Tap Save to confirm."
        )
    }

    // ─── Heartbeat ───────────────────────────────────────────────

    fun toggleHeartbeat(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            heartbeatEnabled = enabled,
            isHeartbeatDirty = true
        )
    }

    fun setInterval(minutes: Int) {
        _uiState.value = _uiState.value.copy(
            intervalMinutes = minutes.coerceIn(5, 24 * 60),
            isHeartbeatDirty = true
        )
    }

    fun setActiveStart(hour: Int) {
        _uiState.value = _uiState.value.copy(
            activeHoursStart = hour.coerceIn(0, 23),
            isHeartbeatDirty = true
        )
    }

    fun setActiveEnd(hour: Int) {
        _uiState.value = _uiState.value.copy(
            activeHoursEnd = hour.coerceIn(1, 24),
            isHeartbeatDirty = true
        )
    }

    fun updateHeartbeatPrompt(value: String) {
        _uiState.value = _uiState.value.copy(
            heartbeatPrompt = value,
            isHeartbeatDirty = true
        )
    }

    fun saveHeartbeat() {
        val state = _uiState.value
        viewModelScope.launch {
            val current = agentConfigStore.getHeartbeatConfig()
            agentConfigStore.saveHeartbeatConfig(
                HeartbeatConfig(
                    enabled = state.heartbeatEnabled,
                    intervalMinutes = state.intervalMinutes,
                    activeHoursStart = state.activeHoursStart,
                    activeHoursEnd = state.activeHoursEnd,
                    customPrompt = state.heartbeatPrompt,
                    lastHeartbeatEpochMs = current.lastHeartbeatEpochMs
                )
            )
            // Start or stop the foreground heartbeat service immediately. The
            // Activity also re-asserts on every onStart, but that wouldn't
            // help a user who toggles the switch and stays on this screen.
            HeartbeatService.applyConfigFromBackground(getApplication())
            _uiState.value = _uiState.value.copy(
                isHeartbeatDirty = false,
                savedMessage = "Heartbeat saved."
            )
        }
    }

    fun runHeartbeatNow() {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true)
            val entry = heartbeatManager.runHeartbeat(activeSettings)
            _uiState.value = _uiState.value.copy(
                isRunning = false,
                savedMessage = if (entry.success) {
                    "Heartbeat ran: ${entry.response?.take(80) ?: "ok"}"
                } else {
                    "Heartbeat failed: ${entry.error}"
                }
            )
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            agentConfigStore.clearHeartbeatLog()
            _uiState.value = _uiState.value.copy(savedMessage = "Heartbeat log cleared.")
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = "")
    }
}
