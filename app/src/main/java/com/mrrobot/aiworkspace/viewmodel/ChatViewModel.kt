package com.mrrobot.aiworkspace.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrrobot.aiworkspace.data.Agent
import com.mrrobot.aiworkspace.data.AgentConfigStore
import com.mrrobot.aiworkspace.data.AgentStore
import com.mrrobot.aiworkspace.data.AppSettings
import com.mrrobot.aiworkspace.data.ChatHistoryStore
import com.mrrobot.aiworkspace.data.ChatMessage
import com.mrrobot.aiworkspace.data.ChatRepository
import com.mrrobot.aiworkspace.data.ChatSession
import com.mrrobot.aiworkspace.data.HeartbeatManager
import com.mrrobot.aiworkspace.data.MemoryCategory
import com.mrrobot.aiworkspace.data.MemoryStore
import com.mrrobot.aiworkspace.data.SettingsStore
import com.mrrobot.aiworkspace.data.SkillStore
import com.mrrobot.aiworkspace.data.StoredChatMessage
import com.mrrobot.aiworkspace.data.deriveSessionTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatAttachment(
    val id: Long = System.nanoTime(),
    val uri: Uri? = null,
    val name: String = "Attachment",
    val displayName: String = name,
    val mimeType: String? = "",
    val sizeBytes: Long = 0L,
    val sizeLabel: String = "",
    val type: String = "",
    val readableText: String = "",
    val extractedText: String? = null,
    val description: String = "",
    val imageDataUrl: String? = null,
    val extractionStatus: String = "",
    val stableKey: String = UUID.randomUUID().toString()
) {
    val isImage: Boolean
        get() = mimeType.orEmpty().startsWith("image/") || !imageDataUrl.isNullOrBlank()

    val isReadable: Boolean
        get() = readableText.isNotBlank() || !extractedText.isNullOrBlank()

    constructor(
        name: String,
        displayName: String = name,
        mimeType: String? = "",
        sizeBytes: Long = 0L,
        sizeLabel: String = "",
        extractedText: String? = null,
        imageDataUrl: String? = null,
        extractionStatus: String = ""
    ) : this(
        id = System.nanoTime(),
        uri = null,
        name = name,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sizeLabel = sizeLabel,
        extractedText = extractedText,
        readableText = extractedText.orEmpty(),
        imageDataUrl = imageDataUrl,
        extractionStatus = extractionStatus
    )

    constructor(
        uri: Uri?,
        name: String,
        mimeType: String? = "",
        sizeBytes: Long = 0L,
        sizeLabel: String = "",
        extractedText: String? = null,
        imageDataUrl: String? = null,
        extractionStatus: String = ""
    ) : this(
        id = System.nanoTime(),
        uri = uri,
        name = name,
        displayName = name,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sizeLabel = sizeLabel,
        extractedText = extractedText,
        readableText = extractedText.orEmpty(),
        imageDataUrl = imageDataUrl,
        extractionStatus = extractionStatus
    )
}

data class ChatUiMessage(
    val id: Long = System.nanoTime(),
    val role: String,
    val content: String,
    val attachments: List<ChatAttachment> = emptyList()
)

data class ChatUiState(
    val input: String = "",
    val messages: List<ChatUiMessage> = listOf(
        ChatUiMessage(
            role = "assistant",
            content = "Mr. Robot online. Add and activate any supported AI provider in Settings, then send a message."
        )
    ),
    val apiKey: String = "",
    val model: String = "openai/gpt-4o-mini",
    val provider: String = "No active model",
    val assistantStatus: String = "No active model configured",
    val isProviderReady: Boolean = false,
    val isLoading: Boolean = false,
    val error: String = "",
    val lastUserPrompt: String = "",
    val selectedAttachments: List<ChatAttachment> = emptyList(),
    val queuedFiles: Int = 0,
    val readableFiles: Int = 0,
    val visionImages: Int = 0,

    // Session / history
    val activeSessionId: String? = null,
    val sessions: List<ChatSession> = emptyList(),

    // ─── Brain state (agent / memory / soul / heartbeat) ───
    val activeAgent: Agent? = null,
    val memoryCount: Int = 0,
    val hasCustomSoul: Boolean = false,
    val heartbeatEnabled: Boolean = false,
    val heartbeatRunning: Boolean = false
) {
    val userMessages: Int
        get() = messages.count { it.role == "user" }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application.applicationContext)
    private val memoryStore = MemoryStore(application.applicationContext)
    private val agentConfigStore = AgentConfigStore(application.applicationContext)
    private val agentStore = AgentStore(application.applicationContext)
    private val chatHistoryStore = ChatHistoryStore(application.applicationContext)
    private val skillStore = SkillStore(application.applicationContext)

    private val repository = ChatRepository(
        agentConfigStore = agentConfigStore,
        memoryStore = memoryStore,
        agentStore = agentStore,
        skillStore = skillStore
    )

    private val heartbeatManager = HeartbeatManager(
        agentConfigStore = agentConfigStore,
        memoryStore = memoryStore,
        chatRepository = repository
    )

    private var activeJob: Job? = null
    private var activeSettings: AppSettings = AppSettings()

    // Session tracking — null until the user sends their first prompt in this thread.
    private var currentSessionId: String? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Settings (provider/model/key)
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                activeSettings = settings

                val hasActive = settings.hasActiveConfiguration()
                val activeKey = settings.activeApiKey()
                val activeModel = settings.activeModel()

                _uiState.value = _uiState.value.copy(
                    apiKey = activeKey,
                    model = activeModel,
                    provider = if (hasActive) settings.selectedProvider.displayName else "No active model",
                    isProviderReady = hasActive,
                    assistantStatus = if (hasActive) {
                        "${settings.selectedProvider.displayName} ready"
                    } else {
                        "No active model configured"
                    },
                    error = _uiState.value.error
                        .takeUnless { it.contains("OpenRouter", ignoreCase = true) }
                        .orEmpty()
                )
            }
        }

        // Active agent
        viewModelScope.launch {
            agentStore.activeAgentIdFlow.collect { activeId ->
                val agent = if (activeId.isNullOrBlank()) {
                    null
                } else {
                    agentStore.getActiveAgent()
                }
                _uiState.value = _uiState.value.copy(activeAgent = agent)
            }
        }

        // Memory count
        viewModelScope.launch {
            memoryStore.memoriesFlow.collect { mems ->
                _uiState.value = _uiState.value.copy(memoryCount = mems.size)
            }
        }

        // Soul
        viewModelScope.launch {
            agentConfigStore.soulFlow.collect { soul ->
                _uiState.value = _uiState.value.copy(
                    hasCustomSoul = soul.customPrompt.isNotBlank()
                )
            }
        }

        // Heartbeat enabled
        viewModelScope.launch {
            agentConfigStore.heartbeatConfigFlow.collect { config ->
                _uiState.value = _uiState.value.copy(
                    heartbeatEnabled = config.enabled
                )
            }
        }

        // Chat history sessions
        viewModelScope.launch {
            chatHistoryStore.sessionsFlow.collect { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
            }
        }

        // Auto-fire heartbeat if due whenever the chat opens (one shot)
        viewModelScope.launch {
            if (heartbeatManager.isHeartbeatDue() && activeSettings.hasActiveConfiguration()) {
                runHeartbeatNow(silent = true)
            }
        }
    }

    fun updateInput(value: String) {
        _uiState.value = _uiState.value.copy(
            input = value,
            error = ""
        )
    }

    fun useSuggestion(suggestion: String) {
        _uiState.value = _uiState.value.copy(
            input = suggestion,
            error = ""
        )
    }

    fun addAttachments(items: List<ChatAttachment>) {
        if (items.isEmpty()) return

        val merged = (_uiState.value.selectedAttachments + items)
            .distinctBy { it.stableKey }

        _uiState.value = _uiState.value.copy(
            selectedAttachments = merged,
            queuedFiles = merged.size,
            readableFiles = merged.count { it.isReadable },
            visionImages = merged.count { it.isImage },
            error = ""
        )
    }

    fun removeAttachment(attachment: ChatAttachment) {
        removeAttachment(attachment.id)
    }

    fun removeAttachment(id: Long) {
        val updated = _uiState.value.selectedAttachments
            .filterNot { it.id == id }

        _uiState.value = _uiState.value.copy(
            selectedAttachments = updated,
            queuedFiles = updated.size,
            readableFiles = updated.count { it.isReadable },
            visionImages = updated.count { it.isImage }
        )
    }

    fun send() {
        val current = _uiState.value
        val prompt = current.input.trim()

        if (prompt.isBlank() || current.isLoading) return

        // Slash commands run locally — they don't hit the model.
        if (handleSlashCommand(prompt)) {
            _uiState.value = _uiState.value.copy(input = "")
            return
        }

        sendPrompt(
            prompt = prompt,
            appendUserMessage = true
        )
    }

    /**
     * Returns true if the input was a recognized slash command and was handled
     * locally (i.e. the model should NOT be invoked).
     *
     * Supported commands:
     *  - /remember <key> = <content>   → store a memory
     *  - /forget <key>                 → delete a memory
     *  - /memories                     → list stored memories
     *  - /heartbeat                    → run heartbeat now
     *  - /agent <name>                 → activate agent by partial name match
     *  - /agent off                    → deactivate active agent
     *  - /brain                        → show brain status
     *  - /help                         → list commands
     */
    private fun handleSlashCommand(input: String): Boolean {
        if (!input.startsWith("/")) return false

        val parts = input.removePrefix("/").trim().split(Regex("\\s+"), limit = 2)
        val cmd = parts.getOrNull(0)?.lowercase().orEmpty()
        val arg = parts.getOrNull(1).orEmpty()

        return when (cmd) {
            "remember" -> {
                val (key, content) = parseRememberArgs(arg)
                if (key.isBlank() || content.isBlank()) {
                    appendSystemReply(
                        "Usage: `/remember <key> = <content>`\n" +
                            "Example: `/remember user_timezone = America/Los_Angeles`"
                    )
                } else {
                    viewModelScope.launch {
                        memoryStore.store(
                            key = key,
                            content = content,
                            category = MemoryCategory.GENERAL,
                            source = "chat"
                        )
                        appendSystemReply("✅ Remembered **$key** → $content")
                    }
                }
                true
            }

            "forget" -> {
                val key = arg.trim()
                if (key.isBlank()) {
                    appendSystemReply("Usage: `/forget <key>`")
                } else {
                    viewModelScope.launch {
                        val ok = memoryStore.forget(key)
                        appendSystemReply(
                            if (ok) "🗑️ Forgot **$key**"
                            else "No memory with key **$key**"
                        )
                    }
                }
                true
            }

            "memories" -> {
                viewModelScope.launch {
                    val mems = memoryStore.getAll()
                    val body = if (mems.isEmpty()) {
                        "No memories stored yet. Use `/remember key = value` to add one."
                    } else {
                        buildString {
                            append("**${mems.size} memories stored:**\n")
                            mems.take(20).forEach { m ->
                                append("• `").append(m.key).append("`")
                                if (m.hitCount > 1) append(" (×").append(m.hitCount).append(")")
                                append(": ").append(m.content.take(80))
                                if (m.content.length > 80) append("…")
                                append('\n')
                            }
                            if (mems.size > 20) {
                                append("…and ").append(mems.size - 20).append(" more.")
                            }
                        }
                    }
                    appendSystemReply(body)
                }
                true
            }

            "heartbeat" -> {
                runHeartbeatNow(silent = false)
                true
            }

            "agent" -> {
                val query = arg.trim()
                viewModelScope.launch {
                    when {
                        query.isBlank() -> {
                            val active = agentStore.getActiveAgent()
                            appendSystemReply(
                                if (active != null) {
                                    "Active agent: ${active.iconEmoji} **${active.name}** (${active.role})"
                                } else {
                                    "No agent is active. Use `/agent <name>` to activate one."
                                }
                            )
                        }
                        query.equals("off", ignoreCase = true) -> {
                            agentStore.setActiveAgentId(null)
                            appendSystemReply("Agent deactivated.")
                        }
                        else -> {
                            val all = com.mrrobot.aiworkspace.data.AgentCatalog.builtInAgents +
                                agentStore.getCustomAgents()
                            val match = all.firstOrNull {
                                it.name.equals(query, ignoreCase = true)
                            } ?: all.firstOrNull {
                                it.name.contains(query, ignoreCase = true)
                            }
                            if (match != null) {
                                agentStore.setActiveAgentId(match.id)
                                appendSystemReply("Activated ${match.iconEmoji} **${match.name}**")
                            } else {
                                appendSystemReply("No agent matched \"$query\".")
                            }
                        }
                    }
                }
                true
            }

            "brain" -> {
                viewModelScope.launch {
                    val active = agentStore.getActiveAgent()
                    val mems = memoryStore.getAll()
                    val soul = agentConfigStore.getSoul()
                    val hb = agentConfigStore.getHeartbeatConfig()

                    val body = buildString {
                        append("## Brain status\n")
                        append("• Agent: ")
                        append(if (active != null) "${active.iconEmoji} ${active.name}" else "_none_")
                        append('\n')
                        append("• Memories: ").append(mems.size).append('\n')
                        append("• Soul: ").append(if (soul.customPrompt.isBlank()) "default" else "custom").append('\n')
                        append("• Heartbeat: ")
                        if (hb.enabled) {
                            append("on, every ").append(hb.intervalMinutes).append(" min ")
                            append("(").append(hb.activeHoursStart).append(":00 → ")
                                .append(hb.activeHoursEnd).append(":00)")
                        } else {
                            append("off")
                        }
                        append('\n')
                    }
                    appendSystemReply(body)
                }
                true
            }

            "help" -> {
                appendSystemReply(
                    """
                    **Slash commands**
                    • `/remember <key> = <content>` — store a memory
                    • `/forget <key>` — delete a memory
                    • `/memories` — list stored memories
                    • `/agent <name>` — activate an agent (or `off` to deactivate)
                    • `/heartbeat` — run a self-check now
                    • `/brain` — show agent / memory / soul / heartbeat status
                    • `/help` — this list
                    """.trimIndent()
                )
                true
            }

            else -> false
        }
    }

    private fun parseRememberArgs(raw: String): Pair<String, String> {
        val eq = raw.indexOf('=')
        if (eq <= 0) return "" to ""
        val key = raw.substring(0, eq).trim()
        val content = raw.substring(eq + 1).trim()
        return key to content
    }

    private fun appendSystemReply(content: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatUiMessage(
                role = "assistant",
                content = content
            )
        )
    }

    fun retryLast() {
        val prompt = _uiState.value.lastUserPrompt

        if (prompt.isBlank() || _uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(error = "")

        sendPrompt(
            prompt = prompt,
            appendUserMessage = false
        )
    }

    fun regenerateLastAnswer() {
        val current = _uiState.value

        if (current.isLoading) return

        val lastUser = current.messages
            .lastOrNull { it.role == "user" }
            ?.content
            .orEmpty()

        if (lastUser.isBlank()) return

        val trimmedMessages = current.messages.dropLastWhile {
            it.role == "assistant"
        }

        _uiState.value = current.copy(
            messages = trimmedMessages,
            error = "",
            lastUserPrompt = lastUser
        )

        sendPrompt(
            prompt = lastUser,
            appendUserMessage = false
        )
    }

    fun stopGeneration() {
        activeJob?.cancel()
        activeJob = null

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "Generation stopped."
        )
    }

    fun deactivateAgent() {
        viewModelScope.launch {
            agentStore.setActiveAgentId(null)
        }
    }

    fun runHeartbeatNow(silent: Boolean = false) {
        val current = _uiState.value
        if (current.heartbeatRunning) return
        if (!activeSettings.hasActiveConfiguration()) {
            if (!silent) appendSystemReply("Cannot run heartbeat: no active AI provider.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(heartbeatRunning = true)
            val entry = heartbeatManager.runHeartbeat(activeSettings)
            _uiState.value = _uiState.value.copy(heartbeatRunning = false)

            // Surface non-OK heartbeats in the chat. Suppress HEARTBEAT_OK to stay quiet.
            val response = entry.response.orEmpty()
            val isOk = response.contains("HEARTBEAT_OK", ignoreCase = true)

            if (!silent) {
                if (entry.success && response.isNotBlank() && !isOk) {
                    appendSystemReply("💓 Heartbeat:\n\n$response")
                } else if (!entry.success) {
                    appendSystemReply("⚠️ Heartbeat failed: ${entry.error.orEmpty()}")
                } else {
                    appendSystemReply("💓 Heartbeat ran. All good.")
                }
            } else {
                if (entry.success && response.isNotBlank() && !isOk) {
                    appendSystemReply("💓 Background heartbeat:\n\n$response")
                }
            }
        }
    }

    private fun sendPrompt(
        prompt: String,
        appendUserMessage: Boolean
    ) {
        val settings = activeSettings
        val current = _uiState.value

        if (!settings.hasActiveConfiguration()) {
            _uiState.value = current.copy(
                error = "No active AI model. Open Settings, add an API key, then tap Save & Activate."
            )
            return
        }

        val attachmentContext = buildAttachmentContext(current.selectedAttachments)
        val finalPrompt = if (attachmentContext.isBlank()) {
            prompt
        } else {
            "$prompt\n\nAttached context:\n$attachmentContext"
        }

        val updatedMessages = if (appendUserMessage) {
            current.messages + ChatUiMessage(
                role = "user",
                content = prompt,
                attachments = current.selectedAttachments
            )
        } else {
            current.messages
        }

        _uiState.value = current.copy(
            input = "",
            messages = updatedMessages,
            selectedAttachments = emptyList(),
            queuedFiles = 0,
            readableFiles = 0,
            visionImages = 0,
            isLoading = true,
            error = "",
            lastUserPrompt = prompt,
            apiKey = settings.activeApiKey(),
            model = settings.activeModel(),
            provider = settings.selectedProvider.displayName,
            assistantStatus = "${settings.selectedProvider.displayName} ready",
            isProviderReady = true
        )

        activeJob = viewModelScope.launch {
            val requestMessages = _uiState.value.messages
                .filter { it.role == "user" || it.role == "assistant" }
                .map {
                    ChatMessage(
                        role = it.role,
                        content = it.content
                    )
                }
                .toMutableList()

            if (requestMessages.isNotEmpty() && finalPrompt != prompt) {
                val lastIndex = requestMessages.indexOfLast { it.role == "user" }
                if (lastIndex >= 0) {
                    requestMessages[lastIndex] = requestMessages[lastIndex].copy(
                        content = finalPrompt
                    )
                }
            }

            val result = repository.sendMessage(
                settings = settings,
                messages = requestMessages
            )

            result
                .onSuccess { reply ->
                    val newMessages = mutableListOf<ChatUiMessage>().apply {
                        addAll(_uiState.value.messages)
                        add(
                            ChatUiMessage(
                                role = "assistant",
                                content = reply.text
                            )
                        )
                        // Surface the auto-save / search inline so the user
                        // sees the AI actually did something.
                        if (reply.didMutateMemory || reply.didSearch) {
                            val parts = buildList {
                                if (reply.searchedQueries.isNotEmpty()) {
                                    add("🔍 Searched: " + reply.searchedQueries.joinToString(", "))
                                }
                                if (reply.savedMemoryKeys.isNotEmpty()) {
                                    add("💾 Saved: " + reply.savedMemoryKeys.joinToString(", "))
                                }
                                if (reply.forgottenMemoryKeys.isNotEmpty()) {
                                    add("🗑️ Forgot: " + reply.forgottenMemoryKeys.joinToString(", "))
                                }
                            }
                            add(
                                ChatUiMessage(
                                    role = "system",
                                    content = parts.joinToString(" • ")
                                )
                            )
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        messages = newMessages,
                        isLoading = false,
                        error = ""
                    )

                    // Persist this turn into chat history so it shows up in the drawer.
                    persistCurrentSession()
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@onFailure

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message ?: "AI request failed."
                    )
                }
        }
    }

    private fun buildAttachmentContext(
        attachments: List<ChatAttachment>
    ): String {
        if (attachments.isEmpty()) return ""

        return attachments.joinToString(separator = "\n\n") { attachment ->
            val text = attachment.readableText
                .ifBlank { attachment.extractedText.orEmpty() }
                .ifBlank { attachment.description }

            buildString {
                append("- Name: ")
                append(attachment.displayName.ifBlank { attachment.name })

                if (!attachment.mimeType.isNullOrBlank()) {
                    append("\n  MIME: ")
                    append(attachment.mimeType)
                }

                if (attachment.sizeBytes > 0L) {
                    append("\n  Size: ")
                    append(attachment.sizeBytes)
                    append(" bytes")
                }

                if (!attachment.imageDataUrl.isNullOrBlank()) {
                    append("\n  Image: attached")
                }

                if (attachment.extractionStatus.isNotBlank()) {
                    append("\n  Status: ")
                    append(attachment.extractionStatus)
                }

                if (text.isNotBlank()) {
                    append("\n  Content: ")
                    append(text.take(8000))
                } else {
                    append("\n  Content: File attached but no readable text was extracted yet.")
                }
            }
        }
    }

    fun clearChat() {
        activeJob?.cancel()
        activeJob = null

        currentSessionId = null

        _uiState.value = _uiState.value.copy(
            messages = listOf(
                ChatUiMessage(
                    role = "assistant",
                    content = "Chat cleared. Mr. Robot is ready."
                )
            ),
            error = "",
            input = "",
            selectedAttachments = emptyList(),
            queuedFiles = 0,
            readableFiles = 0,
            visionImages = 0,
            isLoading = false,
            lastUserPrompt = "",
            activeSessionId = null
        )
    }

    /* ============================================================
     *  Chat history (sessions)
     * ============================================================ */

    /**
     * Start a fresh conversation. Persists the current session first if it
     * has any user messages, then resets to the default greeting.
     */
    fun newChat() {
        // Persist whatever was on screen before resetting.
        persistCurrentSession()

        activeJob?.cancel()
        activeJob = null
        currentSessionId = null

        _uiState.value = _uiState.value.copy(
            messages = listOf(
                ChatUiMessage(
                    role = "assistant",
                    content = "Mr. Robot online. Add and activate any supported AI provider in Settings, then send a message."
                )
            ),
            error = "",
            input = "",
            selectedAttachments = emptyList(),
            queuedFiles = 0,
            readableFiles = 0,
            visionImages = 0,
            isLoading = false,
            lastUserPrompt = "",
            activeSessionId = null
        )
    }

    /**
     * Resume a stored session. Saves any unsaved changes from the current
     * thread first, then replaces the visible messages with the session's
     * stored messages.
     */
    fun loadSession(sessionId: String) {
        if (sessionId == currentSessionId) return

        // Save the current thread (if any) before switching away.
        persistCurrentSession()

        viewModelScope.launch {
            val session = chatHistoryStore.getSession(sessionId) ?: return@launch

            activeJob?.cancel()
            activeJob = null
            currentSessionId = session.id

            val uiMessages = session.messages.map { stored ->
                ChatUiMessage(
                    role = stored.role,
                    content = stored.content
                )
            }.ifEmpty {
                listOf(
                    ChatUiMessage(
                        role = "assistant",
                        content = "Resumed an empty chat. Send a message to continue."
                    )
                )
            }

            _uiState.value = _uiState.value.copy(
                messages = uiMessages,
                error = "",
                input = "",
                selectedAttachments = emptyList(),
                queuedFiles = 0,
                readableFiles = 0,
                visionImages = 0,
                isLoading = false,
                lastUserPrompt = uiMessages.lastOrNull { it.role == "user" }?.content.orEmpty(),
                activeSessionId = session.id
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatHistoryStore.deleteSession(sessionId)
            // If the deleted session was the active one, reset to a fresh chat.
            if (currentSessionId == sessionId) {
                currentSessionId = null
                _uiState.value = _uiState.value.copy(activeSessionId = null)
            }
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            chatHistoryStore.deleteAll()
            currentSessionId = null
            _uiState.value = _uiState.value.copy(activeSessionId = null)
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            chatHistoryStore.renameSession(sessionId, newTitle.trim())
        }
    }

    /**
     * Persist the current visible thread to the chat history store. No-op if
     * there are no user messages yet (i.e. the user hasn't said anything).
     */
    private fun persistCurrentSession() {
        val current = _uiState.value
        val storedMessages = current.messages
            .filter { it.role == "user" || it.role == "assistant" || it.role == "system" }
            .map { ui ->
                StoredChatMessage(
                    role = ui.role,
                    content = ui.content
                )
            }

        // Don't bother persisting empty / greeting-only sessions.
        if (storedMessages.none { it.role == "user" }) return

        val sessionId = currentSessionId ?: UUID.randomUUID().toString()
        currentSessionId = sessionId

        val title = deriveSessionTitle(storedMessages)

        viewModelScope.launch {
            val existing = chatHistoryStore.getSession(sessionId)
            val session = ChatSession(
                id = sessionId,
                title = existing?.title?.takeIf { it.isNotBlank() && it != "New chat" } ?: title,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                messages = storedMessages,
                provider = current.provider,
                model = current.model
            )
            chatHistoryStore.saveSession(session)
            // Reflect the active session id on the UI state once we know it.
            if (_uiState.value.activeSessionId != sessionId) {
                _uiState.value = _uiState.value.copy(activeSessionId = sessionId)
            }
        }
    }
}
