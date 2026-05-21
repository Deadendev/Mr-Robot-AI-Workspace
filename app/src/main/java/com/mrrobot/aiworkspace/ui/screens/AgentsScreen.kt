package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrrobot.aiworkspace.R
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
    val scheme = MaterialTheme.colorScheme

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.savedMessage) {
        if (state.savedMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(state.savedMessage)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenShell {
            // ─── Header (matches Skills tab style) ───────────────
            AgentsHeader(
                activeCount = if (state.activeAgent != null) 1 else 0,
                totalCount = state.agents.size + state.customAgents.size,
                onAddClick = { viewModel.showCreateDialog() }
            )

            Spacer(Modifier.height(14.dp))

            // ─── Agent list ──────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Custom agents first
                if (state.customAgents.isNotEmpty()) {
                    item {
                        Text(
                            text = "YOUR AGENTS",
                            color = scheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    items(state.customAgents, key = { it.id }) { agent ->
                        AgentGlassCard(
                            agent = agent,
                            isActive = agent.id == state.activeAgentId,
                            onActivate = { viewModel.activateAgent(agent) },
                            onDeactivate = { viewModel.deactivateAgent() },
                            onClick = { viewModel.selectAgent(agent) }
                        )
                    }

                    item { Spacer(Modifier.height(6.dp)) }
                }

                // Built-in agents
                item {
                    Text(
                        text = "BUILT-IN AGENTS",
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                items(state.agents, key = { it.id }) { agent ->
                    AgentGlassCard(
                        agent = agent,
                        isActive = agent.id == state.activeAgentId,
                        onActivate = { viewModel.activateAgent(agent) },
                        onDeactivate = { viewModel.deactivateAgent() },
                        onClick = { viewModel.selectAgent(agent) }
                    )
                }
            }
        }

        // Snackbar
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

/* ================================================================
 *  Header (matches Skills tab pattern)
 * ================================================================ */

@Composable
private fun AgentsHeader(
    activeCount: Int,
    totalCount: Int,
    onAddClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Title("Agents")
            Spacer(Modifier.height(4.dp))
            Subtitle(
                if (totalCount == 0) {
                    "Build specialized prompts and execution roles."
                } else {
                    "$activeCount of $totalCount active. Tap to configure."
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
                    contentDescription = "Create agent",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/* ================================================================
 *  Agent GlassCard (matches Skills card style)
 * ================================================================ */

@Composable
private fun AgentGlassCard(
    agent: Agent,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    GlassCard(modifier = Modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Emoji avatar in circle (same as Skills)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            scheme.primary.copy(alpha = 0.16f)
                        } else {
                            scheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = agent.iconEmoji,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = agent.name,
                        color = scheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill(text = "ACTIVE", color = scheme.primary)
                    }

                    if (agent.isBuiltIn) {
                        Spacer(Modifier.width(8.dp))
                        BuiltInBadge()
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = agent.role,
                    color = scheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (agent.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = agent.description,
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Skills tags
                if (agent.skills.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        agent.skills.take(3).forEach { skill ->
                            SkillTag(skill)
                        }
                        if (agent.skills.size > 3) {
                            SkillTag("+${agent.skills.size - 3}")
                        }
                    }
                }
            }
        }

        // Action row at bottom (matches Skills edit/delete pattern)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isActive) {
                TextButton(onClick = onDeactivate) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = scheme.error
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Deactivate",
                        color = scheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                TextButton(onClick = onActivate) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = scheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Activate",
                        color = scheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillTag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BuiltInBadge() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = scheme.tertiary.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.tertiary.copy(alpha = 0.5f))
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
 *  Agent Detail Bottom Sheet
 * ================================================================ */

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
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface,
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
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant),
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
                        color = scheme.onSurface
                    )
                    Text(
                        text = "${agent.role} ${if (isActive) "Active" else ""}",
                        fontSize = 14.sp,
                        color = if (isActive) scheme.primary else scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = agent.description,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = scheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Skills
            if (agent.skills.isNotEmpty()) {
                Text(
                    text = "SKILLS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = scheme.onSurfaceVariant
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
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Deactivate")
                    }
                } else {
                    Button(onClick = onActivate, modifier = Modifier.weight(1f)) {
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
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))

            // Task input
            Text(
                text = "GENERATE PROMPT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = scheme.onSurfaceVariant
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
                        containerColor = scheme.surfaceVariant.copy(alpha = 0.6f)
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
                                color = scheme.onSurface
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
                            color = scheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/* ================================================================
 *  Create / Edit Agent Dialog
 * ================================================================ */

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
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Agent" else "Create Agent",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = scheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Emoji picker
            Text(
                text = "ICON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = scheme.onSurfaceVariant
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
                        color = if (selected) scheme.primaryContainer else scheme.surfaceVariant,
                        tonalElevation = if (selected) 4.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = option, fontSize = 22.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            AgentTextField(label = "Agent Name *", value = name, onValueChange = onNameChange, placeholder = "e.g. Data Analyst")
            Spacer(Modifier.height(14.dp))
            AgentTextField(label = "Role", value = role, onValueChange = onRoleChange, placeholder = "e.g. Data Processing & Insights")
            Spacer(Modifier.height(14.dp))
            AgentTextField(label = "Description", value = description, onValueChange = onDescriptionChange, placeholder = "What does this agent do?", minLines = 2)
            Spacer(Modifier.height(14.dp))
            AgentTextField(label = "System Prompt", value = systemPrompt, onValueChange = onSystemPromptChange, placeholder = "You are a specialized AI agent that...", minLines = 3)
            Spacer(Modifier.height(14.dp))
            AgentTextField(label = "Skills (comma-separated)", value = skills, onValueChange = onSkillsChange, placeholder = "Python, SQL, Data Viz, Pandas")

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
