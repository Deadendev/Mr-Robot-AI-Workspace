package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrrobot.aiworkspace.data.Agent
import com.mrrobot.aiworkspace.ui.components.*
import com.mrrobot.aiworkspace.viewmodel.AgentsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    viewModel: AgentsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    // Snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.savedMessage) {
        if (state.savedMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(state.savedMessage)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenShell {
            // ─── Header ──────────────────────────────────────────
            PremiumHeader(
                title = "Agents",
                subtitle = "Build specialized prompts and execution roles for your AI workspace."
            )

            Spacer(Modifier.height(16.dp))

            // ─── Active Agent Banner ─────────────────────────────
            state.activeAgent?.let { active ->
                ActiveAgentBanner(
                    agent = active,
                    onDeactivate = { viewModel.deactivateAgent() }
                )
                Spacer(Modifier.height(14.dp))
            }

            // ─── Custom Agents Section ───────────────────────────
            if (state.customAgents.isNotEmpty()) {
                Text(
                    text = "YOUR AGENTS",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(state.customAgents, key = { it.id }) { agent ->
                        CompactAgentChip(
                            agent = agent,
                            isActive = agent.id == state.activeAgentId,
                            onClick = { viewModel.selectAgent(agent) }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // ─── Built-in Agents Grid ────────────────────────────
            Text(
                text = "BUILT-IN AGENTS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(state.agents, key = { it.id }) { agent ->
                    AgentCard(
                        agent = agent,
                        isActive = agent.id == state.activeAgentId,
                        onClick = { viewModel.selectAgent(agent) }
                    )
                }
            }
        }

        // ─── FAB ─────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { viewModel.showCreateDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 80.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Agent"
            )
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
    }

    // ─── Detail Bottom Sheet ─────────────────────────────────────
    if (state.showDetailSheet && state.selectedAgent != null) {
        AgentDetailSheet(
            agent = state.selectedAgent!!,
            isActive = state.selectedAgent!!.id == state.activeAgentId,
            taskInput = state.taskInput,
            generatedPrompt = state.generatedPrompt,
            onTaskChange = viewModel::updateTask,
            onGenerate = { viewModel.generatePrompt() },
            onClear = { viewModel.clear() },
            onCopyPrompt = {
                clipboard.setText(AnnotatedString(state.generatedPrompt))
            },
            onActivate = { viewModel.activateAgent(state.selectedAgent!!) },
            onDeactivate = { viewModel.deactivateAgent() },
            onEdit = {
                viewModel.showEditDialog(state.selectedAgent!!)
            },
            onDuplicate = {
                viewModel.duplicateAgent(state.selectedAgent!!)
                viewModel.dismissDetail()
            },
            onDelete = {
                viewModel.deleteAgent(state.selectedAgent!!)
            },
            onDismiss = { viewModel.dismissDetail() }
        )
    }

    // ─── Create/Edit Dialog ──────────────────────────────────────
    if (state.showCreateDialog) {
        CreateAgentDialog(
            isEditing = state.editingAgent != null,
            name = state.editorName,
            role = state.editorRole,
            description = state.editorDescription,
            systemPrompt = state.editorSystemPrompt,
            skills = state.editorSkills,
            emoji = state.editorEmoji,
            onNameChange = viewModel::updateEditorName,
            onRoleChange = viewModel::updateEditorRole,
            onDescriptionChange = viewModel::updateEditorDescription,
            onSystemPromptChange = viewModel::updateEditorSystemPrompt,
            onSkillsChange = viewModel::updateEditorSkills,
            onEmojiChange = viewModel::updateEditorEmoji,
            onSave = { viewModel.saveAgent() },
            onDismiss = { viewModel.dismissCreateDialog() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Active Agent Banner
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveAgentBanner(
    agent: Agent,
    onDeactivate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = agent.iconEmoji,
                fontSize = 28.sp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Active • ${agent.role}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDeactivate) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Deactivate",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Compact Agent Chip (horizontal scroll for custom agents)
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun CompactAgentChip(
    agent: Agent,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = if (isActive) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = agent.iconEmoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = agent.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = agent.role,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (isActive) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Agent Card (for built-in agents list)
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun AgentCard(
    agent: Agent,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Emoji avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = agent.iconEmoji, fontSize = 24.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = agent.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isActive) {
                        StatusPill(text = "ACTIVE", color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = agent.role,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = agent.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (agent.skills.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        agent.skills.take(3).forEach { skill ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = skill,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (agent.skills.size > 3) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "+${agent.skills.size - 3}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Agent Detail Bottom Sheet
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentDetailSheet(
    agent: Agent,
    isActive: Boolean,
    taskInput: String,
    generatedPrompt: String,
    onTaskChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onClear: () -> Unit,
    onCopyPrompt: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = agent.iconEmoji, fontSize = 30.sp)
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${agent.role} • ${if (isActive) "Active" else agent.status}",
                        fontSize = 14.sp,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = agent.description,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Skills
            if (agent.skills.isNotEmpty()) {
                Text(
                    text = "SKILLS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(agent.skills) { skill ->
                        AssistChip(
                            onClick = {},
                            label = { Text(skill, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isActive) {
                    Button(
                        onClick = onDeactivate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Deactivate")
                    }
                } else {
                    Button(
                        onClick = onActivate,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Activate")
                    }
                }

                OutlinedIconButton(onClick = onDuplicate) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                }

                if (!agent.isBuiltIn) {
                    OutlinedIconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    OutlinedIconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))

            // Task input
            Text(
                text = "GENERATE PROMPT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = taskInput,
                onValueChange = onTaskChange,
                placeholder = { Text("Describe the task for this agent...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Generate Prompt")
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Clear")
                }
            }

            // Generated prompt
            if (generatedPrompt.isNotBlank()) {
                Spacer(Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Generated Prompt",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = onCopyPrompt) {
                                Text("Copy", fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = generatedPrompt,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Create / Edit Agent Dialog
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAgentDialog(
    isEditing: Boolean,
    name: String,
    role: String,
    description: String,
    systemPrompt: String,
    skills: String,
    emoji: String,
    onNameChange: (String) -> Unit,
    onRoleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onSkillsChange: (String) -> Unit,
    onEmojiChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Agent" else "Create Agent",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Emoji picker row
            Text(
                text = "ICON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            val emojiOptions = listOf(
                "\uD83E\uDD16", "\uD83E\uDDE0", "\uD83D\uDCA1", "\uD83D\uDD25",
                "\u26A1", "\uD83C\uDF1F", "\uD83D\uDEE0\uFE0F", "\uD83C\uDFAF",
                "\uD83D\uDCDA", "\uD83D\uDD2C", "\uD83C\uDF10", "\uD83D\uDCA0"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emojiOptions) { option ->
                    val selected = emoji == option
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onEmojiChange(option) },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        tonalElevation = if (selected) 4.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = option, fontSize = 22.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Fields
            AgentTextField(
                label = "Agent Name *",
                value = name,
                onValueChange = onNameChange,
                placeholder = "e.g. Data Analyst"
            )

            Spacer(Modifier.height(14.dp))

            AgentTextField(
                label = "Role",
                value = role,
                onValueChange = onRoleChange,
                placeholder = "e.g. Data Processing & Insights"
            )

            Spacer(Modifier.height(14.dp))

            AgentTextField(
                label = "Description",
                value = description,
                onValueChange = onDescriptionChange,
                placeholder = "What does this agent do?",
                minLines = 2
            )

            Spacer(Modifier.height(14.dp))

            AgentTextField(
                label = "System Prompt",
                value = systemPrompt,
                onValueChange = onSystemPromptChange,
                placeholder = "You are a specialized AI agent that...",
                minLines = 3
            )

            Spacer(Modifier.height(14.dp))

            AgentTextField(
                label = "Skills (comma-separated)",
                value = skills,
                onValueChange = onSkillsChange,
                placeholder = "Python, SQL, Data Viz, Pandas"
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Create Agent",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun AgentTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            maxLines = minLines + 3,
            shape = MaterialTheme.shapes.medium
        )
    }
}
