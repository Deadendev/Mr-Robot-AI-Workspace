package com.mrrobot.aiworkspace.viewmodel

import androidx.lifecycle.ViewModel
import com.mrrobot.aiworkspace.data.Agent
import com.mrrobot.aiworkspace.data.AgentCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class AgentsUiState(
    val agents: List<Agent> = AgentCatalog.builtInAgents,
    val customAgents: List<Agent> = emptyList(),
    val activeAgentId: String? = null,
    val selectedAgent: Agent? = null,
    val generatedPrompt: String = "",
    val taskInput: String = "",

    // Create/Edit dialog state
    val showCreateDialog: Boolean = false,
    val editingAgent: Agent? = null,
    val editorName: String = "",
    val editorRole: String = "",
    val editorDescription: String = "",
    val editorSystemPrompt: String = "",
    val editorSkills: String = "",
    val editorEmoji: String = "\uD83E\uDD16",

    // Detail sheet
    val showDetailSheet: Boolean = false,

    val savedMessage: String = ""
) {
    val allAgents: List<Agent>
        get() = agents + customAgents

    val activeAgent: Agent?
        get() = allAgents.firstOrNull { it.id == activeAgentId }
}

class AgentsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AgentsUiState())
    val uiState: StateFlow<AgentsUiState> = _uiState.asStateFlow()

    fun selectAgent(agent: Agent) {
        _uiState.value = _uiState.value.copy(
            selectedAgent = agent,
            showDetailSheet = true,
            generatedPrompt = ""
        )
    }

    fun dismissDetail() {
        _uiState.value = _uiState.value.copy(
            showDetailSheet = false
        )
    }

    fun activateAgent(agent: Agent) {
        _uiState.value = _uiState.value.copy(
            activeAgentId = agent.id,
            savedMessage = "${agent.name} activated"
        )
    }

    fun deactivateAgent() {
        _uiState.value = _uiState.value.copy(
            activeAgentId = null,
            savedMessage = "Agent deactivated"
        )
    }

    // ─── Create / Edit ───────────────────────────────────────────────

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = true,
            editingAgent = null,
            editorName = "",
            editorRole = "",
            editorDescription = "",
            editorSystemPrompt = "",
            editorSkills = "",
            editorEmoji = "\uD83E\uDD16"
        )
    }

    fun showEditDialog(agent: Agent) {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = true,
            editingAgent = agent,
            editorName = agent.name,
            editorRole = agent.role,
            editorDescription = agent.description,
            editorSystemPrompt = agent.systemPrompt,
            editorSkills = agent.skills.joinToString(", "),
            editorEmoji = agent.iconEmoji
        )
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            editingAgent = null
        )
    }

    fun updateEditorName(value: String) {
        _uiState.value = _uiState.value.copy(editorName = value)
    }

    fun updateEditorRole(value: String) {
        _uiState.value = _uiState.value.copy(editorRole = value)
    }

    fun updateEditorDescription(value: String) {
        _uiState.value = _uiState.value.copy(editorDescription = value)
    }

    fun updateEditorSystemPrompt(value: String) {
        _uiState.value = _uiState.value.copy(editorSystemPrompt = value)
    }

    fun updateEditorSkills(value: String) {
        _uiState.value = _uiState.value.copy(editorSkills = value)
    }

    fun updateEditorEmoji(value: String) {
        _uiState.value = _uiState.value.copy(editorEmoji = value)
    }

    fun saveAgent() {
        val state = _uiState.value

        if (state.editorName.isBlank()) {
            _uiState.value = state.copy(savedMessage = "Agent name is required.")
            return
        }

        val skills = state.editorSkills
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val editing = state.editingAgent

        if (editing != null && !editing.isBuiltIn) {
            // Update existing custom agent
            val updated = editing.copy(
                name = state.editorName.trim(),
                role = state.editorRole.trim().ifBlank { "Custom Agent" },
                description = state.editorDescription.trim(),
                systemPrompt = state.editorSystemPrompt.trim(),
                skills = skills,
                iconEmoji = state.editorEmoji.ifBlank { "\uD83E\uDD16" }
            )

            val updatedList = state.customAgents.map {
                if (it.id == updated.id) updated else it
            }

            _uiState.value = state.copy(
                customAgents = updatedList,
                showCreateDialog = false,
                editingAgent = null,
                selectedAgent = updated,
                savedMessage = "${updated.name} updated"
            )
        } else {
            // Create new agent
            val newAgent = Agent(
                id = UUID.randomUUID().toString(),
                name = state.editorName.trim(),
                role = state.editorRole.trim().ifBlank { "Custom Agent" },
                description = state.editorDescription.trim(),
                systemPrompt = state.editorSystemPrompt.trim(),
                skills = skills,
                isBuiltIn = false,
                iconEmoji = state.editorEmoji.ifBlank { "\uD83E\uDD16" }
            )

            _uiState.value = state.copy(
                customAgents = state.customAgents + newAgent,
                showCreateDialog = false,
                editingAgent = null,
                savedMessage = "${newAgent.name} created"
            )
        }
    }

    fun deleteAgent(agent: Agent) {
        if (agent.isBuiltIn) return

        val state = _uiState.value
        val updatedCustom = state.customAgents.filter { it.id != agent.id }
        val newActive = if (state.activeAgentId == agent.id) null else state.activeAgentId
        val newSelected = if (state.selectedAgent?.id == agent.id) null else state.selectedAgent

        _uiState.value = state.copy(
            customAgents = updatedCustom,
            activeAgentId = newActive,
            selectedAgent = newSelected,
            showDetailSheet = false,
            savedMessage = "${agent.name} deleted"
        )
    }

    fun duplicateAgent(agent: Agent) {
        val state = _uiState.value
        val clone = agent.copy(
            id = UUID.randomUUID().toString(),
            name = "${agent.name} (Copy)",
            isBuiltIn = false,
            isActive = false,
            createdAt = System.currentTimeMillis()
        )

        _uiState.value = state.copy(
            customAgents = state.customAgents + clone,
            savedMessage = "${clone.name} created"
        )
    }

    // ─── Prompt generation ───────────────────────────────────────────

    fun updateTask(value: String) {
        _uiState.value = _uiState.value.copy(
            taskInput = value,
            generatedPrompt = ""
        )
    }

    fun generatePrompt() {
        val state = _uiState.value
        val agent = state.selectedAgent ?: return
        val task = state.taskInput.ifBlank {
            "Improve the Mr. Robot AI Workspace Android app."
        }

        val prompt = buildString {
            appendLine("# Agent Task")
            appendLine()
            appendLine("You are ${agent.name}.")
            appendLine()
            appendLine("## Role")
            appendLine(agent.role)
            appendLine()
            appendLine("## System Behavior")
            appendLine(agent.systemPrompt)
            appendLine()
            appendLine("## Task")
            appendLine(task)
            appendLine()
            appendLine("## Required Output")
            appendLine("- Give production-ready implementation.")
            appendLine("- Provide full code, not snippets.")
            appendLine("- Explain exact file paths.")
            appendLine("- Avoid placeholders.")
            appendLine("- Optimize for Android MVP quality.")
            if (agent.skills.isNotEmpty()) {
                appendLine()
                appendLine("## Skills To Apply")
                agent.skills.forEach { appendLine("- $it") }
            }
        }

        _uiState.value = state.copy(
            generatedPrompt = prompt
        )
    }

    fun clear() {
        _uiState.value = _uiState.value.copy(
            taskInput = "",
            generatedPrompt = ""
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = "")
    }
}
