package com.mrrobot.aiworkspace.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mrrobot.aiworkspace.R
import com.mrrobot.aiworkspace.data.ChatSession
import com.mrrobot.aiworkspace.navigation.Route
import com.mrrobot.aiworkspace.ui.components.BrainStatusBanner
import com.mrrobot.aiworkspace.ui.components.MarkdownText
import com.mrrobot.aiworkspace.ui.components.WaitingResponseRow
import com.mrrobot.aiworkspace.viewmodel.ChatAttachment
import com.mrrobot.aiworkspace.viewmodel.ChatUiMessage
import com.mrrobot.aiworkspace.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SuggestionPrompts = listOf(
    "Review my latest Android build errors and suggest fixes",
    "Refactor this Kotlin file for readability",
    "Write a Compose screen from a UI description",
    "/agent Android Architect",
    "/help"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    navController: NavController? = null
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var showAttachSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showClearAllDialog by remember { mutableStateOf(false) }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addAttachments(uris.map { uriToAttachment(context, it) })
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addAttachments(uris.map { uriToAttachment(context, it) })
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            val attachment = uriToAttachment(context, cameraImageUri!!)
            viewModel.addAttachments(
                listOf(
                    attachment.copy(
                        name = "Camera Photo",
                        displayName = "Camera Photo"
                    )
                )
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            cameraImageUri = uri
            if (uri != null) cameraLauncher.launch(uri)
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to take photos.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
                .trim()

            if (spokenText.isNotBlank()) {
                val merged = if (state.input.isBlank()) {
                    spokenText
                } else {
                    state.input + " " + spokenText
                }
                viewModel.updateInput(merged)
            }
        }
    }

    LaunchedEffect(state.messages.size, state.isLoading) {
        if (state.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = sheetState,
            containerColor = scheme.surface,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AttachmentSheetContent(
                onGalleryClick = {
                    showAttachSheet = false
                    galleryLauncher.launch("image/*")
                },
                onVideoClick = {
                    showAttachSheet = false
                    galleryLauncher.launch("video/*")
                },
                onFilesClick = {
                    showAttachSheet = false
                    fileLauncher.launch(arrayOf("*/*"))
                },
                onCameraClick = {
                    showAttachSheet = false
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            )
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear all chats?") },
            text = {
                Text(
                    "This will permanently delete every saved conversation. " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllSessions()
                        showClearAllDialog = false
                    }
                ) {
                    Text(
                        text = "Delete all",
                        color = scheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = scheme.surface,
                drawerTonalElevation = 0.dp,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                ChatHistoryDrawer(
                    sessions = state.sessions,
                    activeSessionId = state.activeSessionId,
                    onClose = { scope.launch { drawerState.close() } },
                    onNewChat = {
                        viewModel.newChat()
                        scope.launch { drawerState.close() }
                    },
                    onSessionClick = { sessionId ->
                        viewModel.loadSession(sessionId)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    onClearAll = { showClearAllDialog = true }
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            scheme.background,
                            scheme.surface.copy(alpha = 0.98f),
                            scheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                ChatTopBar(
                    provider = state.provider,
                    model = state.model,
                    isReady = state.isProviderReady,
                    activeSessionTitle = state.sessions
                        .firstOrNull { it.id == state.activeSessionId }
                        ?.title,
                    onOpenHistory = { scope.launch { drawerState.open() } },
                    onOpenSandbox = { navController?.navigate(Route.Sandbox.path) },
                    onNewChat = { viewModel.newChat() }
                )

                BrainStatusBanner(
                    activeAgentEmoji = state.activeAgent?.iconEmoji,
                    activeAgentName = state.activeAgent?.name,
                    memoryCount = state.memoryCount,
                    hasCustomSoul = state.hasCustomSoul,
                    heartbeatEnabled = state.heartbeatEnabled,
                    heartbeatRunning = state.heartbeatRunning,
                    onAgentClick = { navController?.navigate(Route.Agents.path) },
                    onMemoriesClick = { navController?.navigate(Route.Memories.path) },
                    onSoulClick = { navController?.navigate(Route.SoulHeartbeat.path) },
                    onHeartbeatClick = { viewModel.runHeartbeatNow(silent = false) }
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.messages.isEmpty() ||
                        (state.messages.size == 1 && state.messages.first().role == "assistant")
                    ) {
                        item {
                            EmptyChatState(
                                isReady = state.isProviderReady,
                                model = state.model,
                                provider = state.provider,
                                onPrompt = { viewModel.useSuggestion(it) }
                            )
                        }
                    } else {
                        val lastAssistantId = state.messages
                            .lastOrNull { it.role == "assistant" }?.id

                        items(
                            items = state.messages,
                            key = { it.id },
                            contentType = { it.role }
                        ) { message ->
                            ChatBubble(
                                message = message,
                                isLastAssistant = message.id == lastAssistantId,
                                onRegenerate = if (message.id == lastAssistantId && !state.isLoading) {
                                    { viewModel.regenerateLastAnswer() }
                                } else null
                            )
                        }
                    }

                    if (state.isLoading) {
                        item(key = "loading_thinking") {
                            WaitingResponseRow(
                                executingTools = state.executingTools,
                                statusText = if (state.executingTools.isNotEmpty()) {
                                    "Running tools"
                                } else {
                                    "Thinking"
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = state.error.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ErrorPanel(
                            message = state.error,
                            onRetry = { viewModel.retryLast() }
                        )
                    }
                }

                AttachmentPreviewStrip(
                    attachments = state.selectedAttachments,
                    onRemove = { viewModel.removeAttachment(it) }
                )

                PromptComposer(
                    input = state.input,
                    isLoading = state.isLoading,
                    canRegenerate = state.messages.any { it.role == "user" },
                    queuedFiles = state.queuedFiles,
                    onInputChange = viewModel::updateInput,
                    onMicClick = {
                        launchSpeechInput(
                            context = context,
                            launcher = speechLauncher::launch
                        )
                    },
                    onAttachClick = { showAttachSheet = true },
                    onSend = { viewModel.send() },
                    onStop = { viewModel.stopGeneration() },
                    onRegenerate = { viewModel.regenerateLastAnswer() }
                )
            }
        }
    }
}

/* ================================================================
 *  Top bar
 * ================================================================ */

@Composable
private fun ChatTopBar(
    provider: String,
    model: String,
    isReady: Boolean,
    activeSessionTitle: String?,
    onOpenHistory: () -> Unit,
    onOpenSandbox: () -> Unit,
    onNewChat: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = scheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularIconButton(
                    iconRes = R.drawable.ic_lucide_history,
                    contentDescription = "Chat history",
                    onClick = onOpenHistory
                )

                Spacer(Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeSessionTitle?.takeIf { it.isNotBlank() } ?: "Mr. Robot",
                        color = scheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(isReady = isReady)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isReady) {
                                "$provider \u00B7 ${shortModel(model)}"
                            } else {
                                "No active model"
                            },
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                CircularIconButton(
                    iconRes = R.drawable.ic_lucide_terminal,
                    contentDescription = "Linux sandbox",
                    onClick = onOpenSandbox,
                    tint = scheme.tertiary
                )

                CircularIconButton(
                    iconRes = R.drawable.ic_lucide_edit,
                    contentDescription = "New chat",
                    onClick = onNewChat,
                    tint = scheme.primary
                )
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = scheme.outline.copy(alpha = 0.25f)
            )
        }
    }
}

/**
 * Trim provider model identifiers like "openai/gpt-4o-mini" → "gpt-4o-mini"
 * for a cleaner status line. Falls back to the full string if there's no slash.
 */
private fun shortModel(model: String): String {
    if (model.isBlank()) return ""
    val slash = model.lastIndexOf('/')
    return if (slash >= 0 && slash < model.length - 1) model.substring(slash + 1) else model
}

@Composable
private fun CircularIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StatusDot(isReady: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val color = if (isReady) scheme.tertiary else scheme.error

    val transition = rememberInfiniteTransition(label = "status_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = if (isReady) 1f else 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_pulse_alpha"
    )

    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(pulse)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun AssistantAvatar(
    size: androidx.compose.ui.unit.Dp,
    ring: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(scheme.primary, scheme.tertiary)
                )
            )
            .then(
                if (ring) Modifier.shadow(elevation = 2.dp, shape = CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lucide_bot),
            contentDescription = null,
            tint = scheme.onPrimary,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/* ================================================================
 *  Empty state
 * ================================================================ */

@Composable
private fun EmptyChatState(
    isReady: Boolean,
    model: String,
    provider: String,
    onPrompt: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AssistantAvatar(size = 64.dp, ring = true)

        Spacer(Modifier.height(20.dp))

        Text(
            text = "How can I help you today?",
            color = scheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (isReady) {
                "$provider \u00B7 ${shortModel(model)}"
            } else {
                "Add an API key in Settings to get started."
            },
            color = scheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SuggestionPrompts.forEach { prompt ->
                SuggestionCard(
                    text = prompt,
                    onClick = { onPrompt(prompt) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    text: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_message_square),
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = text,
                color = scheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* ================================================================
 *  Chat bubble
 * ================================================================ */

@Composable
private fun ChatBubble(
    message: ChatUiMessage,
    isLastAssistant: Boolean = false,
    onRegenerate: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isUser = message.role == "user"
    val isSystem = message.role == "system"

    if (isSystem) {
        // Compact pill for system / side-effect messages.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = scheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.3f))
            ) {
                Text(
                    text = message.content,
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AssistantAvatar(size = 30.dp)
            Spacer(Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            val bubbleColor = if (isUser) {
                scheme.primary
            } else {
                scheme.surfaceVariant.copy(alpha = 0.6f)
            }

            val textColor = if (isUser) scheme.onPrimary else scheme.onSurface

            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                color = bubbleColor,
                border = if (isUser) {
                    null
                } else {
                    BorderStroke(1.dp, scheme.outline.copy(alpha = 0.25f))
                },
                tonalElevation = 0.dp,
                shadowElevation = if (isUser) 1.dp else 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (isUser) {
                        // User messages stay plain text (no markdown)
                        Text(
                            text = message.content,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        // Assistant messages render markdown
                        MarkdownText(
                            content = message.content,
                            textColor = textColor
                        )
                    }
                }
            }

            // Action row below bubble
            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (message.attachments.isNotEmpty()) {
                    Text(
                        text = "${message.attachments.size} attachment" +
                            if (message.attachments.size == 1) "" else "s",
                        color = scheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!isUser) {
                    // Copy button
                    Row(
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(message.content))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lucide_copy),
                            contentDescription = "Copy",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Copy",
                            color = scheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Regenerate button (only on the last assistant message)
                    if (onRegenerate != null) {
                        Row(
                            modifier = Modifier.clickable { onRegenerate() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lucide_refresh),
                                contentDescription = "Regenerate",
                                tint = scheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Regenerate",
                                color = scheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ================================================================
 *  Thinking indicator (kept as fallback, but primary is WaitingResponseRow)
 * ================================================================ */

@Composable
private fun ThinkingBubble() {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantAvatar(size = 30.dp)
        Spacer(Modifier.width(10.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = scheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThinkingDots(color = scheme.primary)
                Text(
                    text = "Thinking",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ThinkingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    val a1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d1"
    )
    val a2 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d2"
    )
    val a3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Dot(color, a1)
        Dot(color, a2)
        Dot(color, a3)
    }
}

@Composable
private fun Dot(color: Color, alphaValue: Float) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .alpha(alphaValue.coerceIn(0.35f, 1f))
            .clip(CircleShape)
            .background(color)
    )
}

/* ================================================================
 *  Error
 * ================================================================ */

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = scheme.errorContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(scheme.error.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = scheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Request failed",
                    color = scheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message,
                    color = scheme.onErrorContainer.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            Surface(
                modifier = Modifier.clickable { onRetry() },
                shape = RoundedCornerShape(999.dp),
                color = scheme.error.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "Retry",
                    color = scheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/* ================================================================
 *  Composer
 * ================================================================ */

@Composable
private fun PromptComposer(
    input: String,
    isLoading: Boolean,
    canRegenerate: Boolean,
    queuedFiles: Int,
    onInputChange: (String) -> Unit,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = scheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = scheme.outline.copy(alpha = 0.25f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ComposerIconButton(
                            iconRes = R.drawable.ic_lucide_plus,
                            contentDescription = "Attach",
                            badgeCount = queuedFiles,
                            onClick = onAttachClick
                        )

                        OutlinedTextField(
                            value = input,
                            onValueChange = onInputChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 160.dp)
                                .defaultMinSize(minHeight = 48.dp),
                            placeholder = {
                                Text(
                                    text = "Message Mr. Robot…",
                                    color = scheme.onSurfaceVariant,
                                    fontSize = 15.sp
                                )
                            },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 6,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent,
                                cursorColor = scheme.primary,
                                focusedTextColor = scheme.onSurface,
                                unfocusedTextColor = scheme.onSurface,
                                focusedPlaceholderColor = scheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = scheme.onSurfaceVariant
                            )
                        )

                        ComposerIconButton(
                            iconRes = R.drawable.ic_lucide_mic,
                            contentDescription = "Voice input",
                            onClick = onMicClick
                        )

                        SendButton(
                            isLoading = isLoading,
                            canSend = input.isNotBlank() || isLoading,
                            canRegenerate = canRegenerate,
                            onSend = onSend,
                            onStop = onStop,
                            onRegenerate = onRegenerate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerIconButton(
    iconRes: Int,
    contentDescription: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.coerceAtMost(9).toString(),
                    color = scheme.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SendButton(
    isLoading: Boolean,
    canSend: Boolean,
    canRegenerate: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    val showRegenerate = !isLoading && !canSend && canRegenerate

    val bgColor = when {
        isLoading -> scheme.error
        showRegenerate -> scheme.surfaceVariant
        canSend -> scheme.primary
        else -> scheme.primary.copy(alpha = 0.4f)
    }

    val iconTint = when {
        isLoading -> scheme.onError
        showRegenerate -> scheme.onSurface
        else -> scheme.onPrimary
    }

    val iconRes = when {
        isLoading -> R.drawable.ic_lucide_square
        showRegenerate -> R.drawable.ic_lucide_refresh
        else -> R.drawable.ic_lucide_send
    }

    val description = when {
        isLoading -> "Stop generating"
        showRegenerate -> "Regenerate last response"
        else -> "Send message"
    }

    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(enabled = isLoading || canSend || showRegenerate) {
                when {
                    isLoading -> onStop()
                    canSend -> onSend()
                    showRegenerate -> onRegenerate()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = iconTint
            )
        } else {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = description,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/* ================================================================
 *  Attachment bottom sheet
 * ================================================================ */

@Composable
private fun AttachmentSheetContent(
    onGalleryClick: () -> Unit,
    onVideoClick: () -> Unit,
    onFilesClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Attach to message",
            color = scheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AttachmentOption(
                iconRes = R.drawable.ic_lucide_image,
                label = "Gallery",
                tint = scheme.primary,
                onClick = onGalleryClick
            )

            AttachmentOption(
                iconRes = R.drawable.ic_lucide_image,
                label = "Videos",
                tint = scheme.secondary,
                onClick = onVideoClick
            )

            AttachmentOption(
                iconRes = R.drawable.ic_lucide_file,
                label = "Files",
                tint = scheme.tertiary,
                onClick = onFilesClick
            )

            AttachmentOption(
                iconRes = R.drawable.ic_lucide_camera,
                label = "Camera",
                tint = scheme.error,
                onClick = onCameraClick
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AttachmentOption(
    iconRes: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = tint.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = label,
            color = scheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/* ================================================================
 *  Attachment preview strip (above the composer)
 * ================================================================ */

@Composable
private fun AttachmentPreviewStrip(
    attachments: List<ChatAttachment>,
    onRemove: (ChatAttachment) -> Unit
) {
    if (attachments.isEmpty()) return

    val scheme = MaterialTheme.colorScheme

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = attachments,
            key = { it.stableKey }
        ) { attachment ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = scheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 10.dp,
                        top = 6.dp,
                        bottom = 6.dp,
                        end = 4.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (attachment.isImage) R.drawable.ic_lucide_image
                            else R.drawable.ic_lucide_file
                        ),
                        contentDescription = null,
                        tint = if (attachment.isImage) scheme.primary else scheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = attachment.displayName.take(20),
                        color = scheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = { onRemove(attachment) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lucide_x),
                            contentDescription = "Remove",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ================================================================
 *  Chat history drawer
 * ================================================================ */

@Composable
private fun ChatHistoryDrawer(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onSessionClick: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_arrow_left),
                    contentDescription = "Close history",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            Text(
                text = "Chats",
                color = scheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (sessions.isNotEmpty()) {
                IconButton(onClick = onClearAll, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_trash),
                        contentDescription = "Clear all",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = scheme.outline.copy(alpha = 0.25f)
        )

        // New chat button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .clickable { onNewChat() },
            shape = RoundedCornerShape(14.dp),
            color = scheme.primary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_edit),
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "New chat",
                    color = scheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Sessions list
        if (sessions.isEmpty()) {
            EmptyHistoryState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val grouped = groupSessionsByRecency(sessions)
                grouped.forEach { (label, group) ->
                    item(key = "header_$label") {
                        Text(
                            text = label,
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                top = 12.dp,
                                bottom = 6.dp
                            )
                        )
                    }

                    items(
                        items = group,
                        key = { it.id }
                    ) { session ->
                        SessionRow(
                            session = session,
                            isActive = session.id == activeSessionId,
                            onClick = { onSessionClick(session.id) },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_message_square),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "No conversations yet",
            color = scheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Send a message to start your first chat. It'll appear here.",
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) {
            scheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        border = if (isActive) {
            BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_message_square),
                contentDescription = null,
                tint = if (isActive) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { "Untitled chat" },
                    color = if (isActive) scheme.primary else scheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (session.preview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = session.preview,
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_trash),
                    contentDescription = "Delete chat",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Group sessions into "Today / Yesterday / Last 7 days / Older" buckets,
 * preserving newest-first ordering inside each bucket.
 */
private fun groupSessionsByRecency(
    sessions: List<ChatSession>
): List<Pair<String, List<ChatSession>>> {
    if (sessions.isEmpty()) return emptyList()

    val now = Calendar.getInstance()
    val today = startOfDay(now.timeInMillis)
    val yesterday = today - 24L * 60 * 60 * 1000
    val sevenDaysAgo = today - 7L * 24 * 60 * 60 * 1000

    val todayGroup = mutableListOf<ChatSession>()
    val yesterdayGroup = mutableListOf<ChatSession>()
    val weekGroup = mutableListOf<ChatSession>()
    val olderGroup = mutableListOf<ChatSession>()

    sessions.forEach { session ->
        val ts = session.updatedAt
        when {
            ts >= today -> todayGroup.add(session)
            ts >= yesterday -> yesterdayGroup.add(session)
            ts >= sevenDaysAgo -> weekGroup.add(session)
            else -> olderGroup.add(session)
        }
    }

    val result = mutableListOf<Pair<String, List<ChatSession>>>()
    if (todayGroup.isNotEmpty()) result.add("TODAY" to todayGroup)
    if (yesterdayGroup.isNotEmpty()) result.add("YESTERDAY" to yesterdayGroup)
    if (weekGroup.isNotEmpty()) result.add("LAST 7 DAYS" to weekGroup)
    if (olderGroup.isNotEmpty()) result.add("OLDER" to olderGroup)
    return result
}

private fun startOfDay(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Suppress("unused")
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneMinute = 60_000L
    val oneHour = 60 * oneMinute
    val oneDay = 24 * oneHour
    return when {
        diff < oneMinute -> "now"
        diff < oneHour -> "${diff / oneMinute}m"
        diff < oneDay -> "${diff / oneHour}h"
        diff < 7 * oneDay -> "${diff / oneDay}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

/* ================================================================
 *  Utility: URI to Attachment
 * ================================================================ */

private fun uriToAttachment(context: Context, uri: Uri): ChatAttachment {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: ""
    var fileName = "Attachment"
    var fileSize = 0L

    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: "Attachment"
            if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
        }
    }

    val sizeLabel = when {
        fileSize < 1024 -> "$fileSize B"
        fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
        else -> "${"%.1f".format(fileSize / (1024.0 * 1024.0))} MB"
    }

    return ChatAttachment(
        uri = uri,
        name = fileName,
        mimeType = mimeType,
        sizeBytes = fileSize,
        sizeLabel = sizeLabel,
        extractionStatus = "Queued"
    )
}

private fun createCameraImageUri(context: Context): Uri? {
    return try {
        val cacheDir = File(context.cacheDir, "camera_photos")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val imageFile = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    } catch (e: Exception) {
        null
    }
}

/* ================================================================
 *  Voice input
 * ================================================================ */

private fun launchSpeechInput(
    context: Context,
    launcher: (Intent) -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Mr. Robot")
    }

    runCatching {
        launcher(intent)
    }.onFailure {
        Toast.makeText(
            context,
            "Voice input is not available on this device.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
