package com.mrrobot.aiworkspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrrobot.aiworkspace.data.Skill
import com.mrrobot.aiworkspace.data.SkillCategory
import com.mrrobot.aiworkspace.data.SkillStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SkillsUiState(
    val skills: List<Skill> = emptyList(),
    val showEditor: Boolean = false,
    val editing: Skill? = null,
    val draftName: String = "",
    val draftDescription: String = "",
    val draftInstructions: String = "",
    val draftCategory: SkillCategory = SkillCategory.GENERAL,
    val draftEnabled: Boolean = true,
    val error: String = ""
) {
    val enabledCount: Int get() = skills.count { it.enabled }
    val totalCount: Int get() = skills.size

    val canSave: Boolean
        get() = draftName.isNotBlank() && draftInstructions.isNotBlank()
}

class SkillsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SkillStore(application.applicationContext)

    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.skillsFlow.collect { skills ->
                _uiState.value = _uiState.value.copy(skills = skills)
            }
        }
    }

    /* ----------------- Editor lifecycle ----------------- */

    fun openCreateEditor() {
        _uiState.value = _uiState.value.copy(
            showEditor = true,
            editing = null,
            draftName = "",
            draftDescription = "",
            draftInstructions = "",
            draftCategory = SkillCategory.GENERAL,
            draftEnabled = true,
            error = ""
        )
    }

    fun openEditEditor(skill: Skill) {
        _uiState.value = _uiState.value.copy(
            showEditor = true,
            editing = skill,
            draftName = skill.name,
            draftDescription = skill.description,
            draftInstructions = skill.instructions,
            draftCategory = skill.category,
            draftEnabled = skill.enabled,
            error = ""
        )
    }

    fun closeEditor() {
        _uiState.value = _uiState.value.copy(
            showEditor = false,
            editing = null,
            draftName = "",
            draftDescription = "",
            draftInstructions = "",
            draftCategory = SkillCategory.GENERAL,
            draftEnabled = true,
            error = ""
        )
    }

    fun updateDraftName(value: String) {
        _uiState.value = _uiState.value.copy(draftName = value, error = "")
    }

    fun updateDraftDescription(value: String) {
        _uiState.value = _uiState.value.copy(draftDescription = value)
    }

    fun updateDraftInstructions(value: String) {
        _uiState.value = _uiState.value.copy(draftInstructions = value, error = "")
    }

    fun updateDraftCategory(value: SkillCategory) {
        _uiState.value = _uiState.value.copy(draftCategory = value)
    }

    fun updateDraftEnabled(value: Boolean) {
        _uiState.value = _uiState.value.copy(draftEnabled = value)
    }

    /* ----------------- Persistence ----------------- */

    fun saveDraft() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.value = state.copy(
                error = "A skill needs both a name and instructions."
            )
            return
        }

        val base = state.editing ?: Skill(
            name = state.draftName.trim(),
            instructions = state.draftInstructions.trim(),
            category = state.draftCategory,
            enabled = state.draftEnabled
        )

        val updated = base.copy(
            name = state.draftName.trim(),
            description = state.draftDescription.trim(),
            instructions = state.draftInstructions.trim(),
            category = state.draftCategory,
            enabled = state.draftEnabled
        )

        viewModelScope.launch {
            store.upsert(updated)
            closeEditor()
        }
    }

    fun toggleSkill(id: String, enabled: Boolean) {
        viewModelScope.launch {
            store.setEnabled(id, enabled)
        }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch {
            store.delete(id)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            store.resetToDefaults()
        }
    }
}
