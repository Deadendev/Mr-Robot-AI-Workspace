package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrrobot.aiworkspace.R
import com.mrrobot.aiworkspace.data.Skill
import com.mrrobot.aiworkspace.data.SkillCategory
import com.mrrobot.aiworkspace.ui.components.CyberButton
import com.mrrobot.aiworkspace.ui.components.GlassCard
import com.mrrobot.aiworkspace.ui.components.ScreenShell
import com.mrrobot.aiworkspace.ui.components.Subtitle
import com.mrrobot.aiworkspace.ui.components.Title
import com.mrrobot.aiworkspace.viewmodel.SkillsViewModel

@Composable
fun SkillsScreen(viewModel: SkillsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    ScreenShell {
        SkillsHeader(
            enabled = state.enabledCount,
            total = state.totalCount,
            onAddClick = { viewModel.openCreateEditor() }
        )

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.skills.isEmpty()) {
                item {
                    EmptySkillsCard(onAddClick = { viewModel.openCreateEditor() })
                }
            } else {
                items(
                    items = state.skills,
                    key = { it.id }
                ) { skill ->
                    SkillCard(
                        skill = skill,
                        onToggle = { enabled ->
                            viewModel.toggleSkill(skill.id, enabled)
                        },
                        onEdit = { viewModel.openEditEditor(skill) },
                        onDelete = { pendingDeleteId = skill.id }
                    )
                }
            }
        }
    }

    if (state.showEditor) {
        SkillEditorDialog(
            isEditing = state.editing != null,
            name = state.draftName,
            description = state.draftDescription,
            instructions = state.draftInstructions,
            category = state.draftCategory,
            enabled = state.draftEnabled,
            error = state.error,
            canSave = state.canSave,
            onNameChange = viewModel::updateDraftName,
            onDescriptionChange = viewModel::updateDraftDescription,
            onInstructionsChange = viewModel::updateDraftInstructions,
            onCategoryChange = viewModel::updateDraftCategory,
            onEnabledChange = viewModel::updateDraftEnabled,
            onSave = viewModel::saveDraft,
            onCancel = viewModel::closeEditor
        )
    }

    pendingDeleteId?.let { id ->
        val skill = state.skills.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete skill?") },
            text = {
                Text(
                    "\"${skill?.name.orEmpty()}\" will be removed. " +
                        if (skill?.isBuiltIn == true) {
                            "Built-in skills can be restored later via Reset to defaults."
                        } else {
                            "This action cannot be undone."
                        }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSkill(id)
                        pendingDeleteId = null
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* ================================================================
 *  Header
 * ================================================================ */

@Composable
private fun SkillsHeader(
    enabled: Int,
    total: Int,
    onAddClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Title("Skills")
            Spacer(Modifier.height(4.dp))
            Subtitle(
                if (total == 0) {
                    "Equip Mr. Robot with custom capabilities."
                } else {
                    "$enabled of $total active. Toggle, edit, or add more."
                }
            )
        }

        Surface(
            modifier = Modifier
                .size(44.dp)
                .clickable { onAddClick() },
            shape = CircleShape,
            color = scheme.primary,
            shadowElevation = 2.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_plus),
                    contentDescription = "Add skill",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/* ================================================================
 *  Empty state
 * ================================================================ */

@Composable
private fun EmptySkillsCard(onAddClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_wand),
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "No skills yet",
                color = scheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Add your first skill to give Mr. Robot a persistent capability " +
                    "across every chat — like \"always reply in JSON\" or \"act as a Kotlin reviewer\".",
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(16.dp))

            CyberButton("Add a skill") { onAddClick() }
        }
    }
}

/* ================================================================
 *  Skill card
 * ================================================================ */

@Composable
private fun SkillCard(
    skill: Skill,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (skill.enabled) {
                            scheme.primary.copy(alpha = 0.16f)
                        } else {
                            scheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = skill.category.emoji,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = skill.name,
                        color = scheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (skill.isBuiltIn) {
                        Spacer(Modifier.width(8.dp))
                        BuiltInBadge()
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = skill.category.displayName,
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                if (skill.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = skill.description,
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = skill.enabled,
                onCheckedChange = onToggle
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_edit),
                    contentDescription = "Edit skill",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_trash),
                    contentDescription = "Delete skill",
                    tint = scheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun BuiltInBadge() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = scheme.tertiary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, scheme.tertiary.copy(alpha = 0.5f))
    ) {
        Text(
            text = "BUILT-IN",
            color = scheme.tertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/* ================================================================
 *  Editor dialog
 * ================================================================ */

@Composable
private fun SkillEditorDialog(
    isEditing: Boolean,
    name: String,
    description: String,
    instructions: String,
    category: SkillCategory,
    enabled: Boolean,
    error: String,
    canSave: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onCategoryChange: (SkillCategory) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = if (isEditing) "Edit skill" else "Add a skill",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Reply in JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Short description (optional)") },
                    placeholder = { Text("One line summary") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = instructions,
                    onValueChange = onInstructionsChange,
                    label = { Text("Instructions") },
                    placeholder = {
                        Text("What should Mr. Robot always do when this skill is on?")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Category",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(6.dp))

                CategoryPicker(
                    selected = category,
                    onSelect = onCategoryChange
                )

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Enabled",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = canSave
            ) {
                Text(
                    text = if (isEditing) "Save" else "Add",
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CategoryPicker(
    selected: SkillCategory,
    onSelect: (SkillCategory) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SkillCategory.values().forEach { cat ->
            val isSelected = cat == selected
            Surface(
                modifier = Modifier.clickable { onSelect(cat) },
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) {
                    scheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        scheme.primary.copy(alpha = 0.55f)
                    } else {
                        scheme.outline.copy(alpha = 0.4f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = cat.emoji, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = cat.displayName,
                        color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
