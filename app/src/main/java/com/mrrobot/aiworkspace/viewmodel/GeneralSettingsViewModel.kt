package com.mrrobot.aiworkspace.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrrobot.aiworkspace.HeartbeatService
import com.mrrobot.aiworkspace.data.AgentConfigStore
import com.mrrobot.aiworkspace.data.AppThemeMode
import com.mrrobot.aiworkspace.data.ImportOutcome
import com.mrrobot.aiworkspace.data.SettingsImportExport
import com.mrrobot.aiworkspace.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GeneralSettingsUiState(
    val isDaemonEnabled: Boolean = false,
    val isDynamicUiEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.Auto,
    val importMessage: String = "",
    val isImportSuccess: Boolean = false,
    val isLoaded: Boolean = false
)

/**
 * Backs the new General settings tab — Kai 9000-parity.
 *
 * Sources of truth:
 *  - Daemon Mode  -> [AgentConfigStore.heartbeatConfigFlow] (`enabled` flag)
 *  - Dynamic UI  -> [SettingsStore.settingsFlow] (`dynamicUiEnabled`)
 *  - Theme        -> [SettingsStore.settingsFlow] (`themeMode`)
 *
 * Toggling Daemon Mode also flips the [HeartbeatService] foreground service
 * so the change takes effect immediately, matching Kai's behavior.
 */
class GeneralSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application.applicationContext)
    private val agentConfigStore = AgentConfigStore(application.applicationContext)

    private val _uiState = MutableStateFlow(GeneralSettingsUiState())
    val uiState: StateFlow<GeneralSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsStore.settingsFlow,
                agentConfigStore.heartbeatConfigFlow
            ) { settings, heartbeat ->
                GeneralSettingsUiState(
                    isDaemonEnabled = heartbeat.enabled,
                    isDynamicUiEnabled = settings.dynamicUiEnabled,
                    themeMode = settings.themeMode,
                    importMessage = _uiState.value.importMessage,
                    isImportSuccess = _uiState.value.isImportSuccess,
                    isLoaded = true
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onToggleDaemon(enabled: Boolean) {
        viewModelScope.launch {
            agentConfigStore.setHeartbeatEnabled(enabled)
            // Apply immediately — start or stop the foreground service so the
            // user sees the heartbeat notification appear/disappear right away.
            HeartbeatService.applyConfigFromBackground(getApplication())
        }
    }

    fun onToggleDynamicUi(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setDynamicUiEnabled(enabled)
        }
    }

    fun onChangeThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsStore.setThemeMode(mode)
        }
    }

    /** Build the export JSON synchronously for the file-picker callback. */
    suspend fun buildExportJson(): String =
        SettingsImportExport.exportToJson(getApplication())

    fun onImportFromUri(uri: Uri, replace: Boolean) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val json = app.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: return@runCatching ImportOutcome.Failure("Could not read file")
                    SettingsImportExport.importFromJson(app, json, replace = replace)
                }.getOrElse { e ->
                    ImportOutcome.Failure(e.message ?: "Unknown error")
                }
            }

            _uiState.value = _uiState.value.copy(
                importMessage = describe(outcome),
                isImportSuccess = outcome !is ImportOutcome.Failure
            )

            // After a successful import the daemon flag may have flipped — make
            // the foreground service match the newly-restored state.
            if (outcome !is ImportOutcome.Failure) {
                HeartbeatService.applyConfigFromBackground(app)
            }
        }
    }

    fun onExportSucceeded() {
        _uiState.value = _uiState.value.copy(
            importMessage = "Settings exported",
            isImportSuccess = true
        )
    }

    fun onExportFailed(reason: String) {
        _uiState.value = _uiState.value.copy(
            importMessage = "Export failed: $reason",
            isImportSuccess = false
        )
    }

    fun clearMessage() {
        if (_uiState.value.importMessage.isNotBlank()) {
            _uiState.value = _uiState.value.copy(importMessage = "")
        }
    }

    private fun describe(outcome: ImportOutcome): String = when (outcome) {
        is ImportOutcome.Success ->
            "Settings imported (${outcome.imported} keys)"
        is ImportOutcome.PartialSuccess ->
            "Imported with ${outcome.errorCount} skipped entries"
        is ImportOutcome.Failure ->
            "Import failed: ${outcome.message}"
    }
}
