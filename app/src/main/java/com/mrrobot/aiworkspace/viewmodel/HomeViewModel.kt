package com.mrrobot.aiworkspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrrobot.aiworkspace.data.AgentConfigStore
import com.mrrobot.aiworkspace.data.ApiProvider
import com.mrrobot.aiworkspace.data.AppSettings
import com.mrrobot.aiworkspace.data.ChatHistoryStore
import com.mrrobot.aiworkspace.data.ChatSession
import com.mrrobot.aiworkspace.data.MemoryStore
import com.mrrobot.aiworkspace.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HomeUiState(
    val isLoading: Boolean = true,

    // Provider
    val activeProvider: String = "Not configured",
    val activeModel: String = "",
    val isProviderReady: Boolean = false,

    // Credits (OpenRouter only)
    val creditsTotal: Double? = null,
    val creditsUsed: Double? = null,
    val creditsRemaining: Double? = null,
    val creditsFetchError: String? = null,
    val isCreditsLoading: Boolean = false,

    // Usage (local tracking)
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,

    // Recent chats
    val recentChats: List<ChatSession> = emptyList(),

    // Heartbeat
    val heartbeatEnabled: Boolean = false,
    val lastHeartbeatTime: Long = 0L,
    val heartbeatSuccessCount: Int = 0,
    val heartbeatFailCount: Int = 0,

    // Memories
    val memoryCount: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application.applicationContext)
    private val chatHistoryStore = ChatHistoryStore(application.applicationContext)
    private val agentConfigStore = AgentConfigStore(application.applicationContext)
    private val memoryStore = MemoryStore(application.applicationContext)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun refresh() {
        loadDashboard()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val settings = withContext(Dispatchers.IO) {
                settingsStore.settingsFlow.first()
            }

            val providerReady = settings.hasActiveConfiguration()
            val providerName = if (providerReady) {
                settings.selectedProvider.displayName
            } else {
                "Not configured"
            }
            val modelName = if (providerReady) {
                settings.activeModel()
            } else {
                ""
            }

            // Load chat history stats
            val sessions = withContext(Dispatchers.IO) {
                chatHistoryStore.getAll()
            }
            val totalMessages = sessions.sumOf { it.messageCount }

            // Load heartbeat info
            val heartbeatConfig = withContext(Dispatchers.IO) {
                agentConfigStore.getHeartbeatConfig()
            }
            val heartbeatLog = withContext(Dispatchers.IO) {
                agentConfigStore.getHeartbeatLog()
            }

            // Load memories
            val memories = withContext(Dispatchers.IO) {
                memoryStore.getAll()
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                activeProvider = providerName,
                activeModel = modelName,
                isProviderReady = providerReady,
                totalConversations = sessions.size,
                totalMessages = totalMessages,
                recentChats = sessions.take(5),
                heartbeatEnabled = heartbeatConfig.enabled,
                lastHeartbeatTime = heartbeatConfig.lastHeartbeatEpochMs,
                heartbeatSuccessCount = heartbeatLog.count { it.success },
                heartbeatFailCount = heartbeatLog.count { !it.success },
                memoryCount = memories.size
            )

            // Fetch credits if OpenRouter is active
            if (providerReady && settings.selectedProvider == ApiProvider.OpenRouter) {
                fetchOpenRouterCredits(settings.openRouterApiKey)
            }
        }
    }

    private fun fetchOpenRouterCredits(apiKey: String) {
        if (apiKey.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreditsLoading = true)
            val result = withContext(Dispatchers.IO) {
                fetchCreditsFromApi(apiKey)
            }
            result.fold(
                onSuccess = { (total, used) ->
                    _uiState.value = _uiState.value.copy(
                        isCreditsLoading = false,
                        creditsTotal = total,
                        creditsUsed = used,
                        creditsRemaining = if (total > 0) total - used else null,
                        creditsFetchError = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isCreditsLoading = false,
                        creditsFetchError = error.message ?: "Failed to fetch credits"
                    )
                }
            )
        }
    }

    private fun fetchCreditsFromApi(apiKey: String): Result<Pair<Double, Double>> {
        return try {
            val url = URL("https://openrouter.ai/api/v1/auth/key")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return Result.failure(Exception("HTTP $code"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: json

            // OpenRouter returns: { data: { limit, usage, limit_remaining, ... } }
            val limit = data.optDouble("limit", 0.0)
            val usage = data.optDouble("usage", 0.0)

            Result.success(Pair(limit, usage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
