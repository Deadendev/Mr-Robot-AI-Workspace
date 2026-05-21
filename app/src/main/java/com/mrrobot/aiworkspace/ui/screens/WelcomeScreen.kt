package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mrrobot.aiworkspace.R
import com.mrrobot.aiworkspace.navigation.Route
import com.mrrobot.aiworkspace.ui.components.GlassCard
import com.mrrobot.aiworkspace.ui.components.ScreenShell
import com.mrrobot.aiworkspace.viewmodel.HomeUiState
import com.mrrobot.aiworkspace.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WelcomeScreen(
    nav: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    ScreenShell {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                DashboardHeader(onRefresh = { viewModel.refresh() })

                Spacer(Modifier.height(16.dp))

                // Provider status card
                ProviderStatusCard(state, nav)

                Spacer(Modifier.height(12.dp))

                // Credits / Token usage
                AnimatedVisibility(
                    visible = state.creditsTotal != null || state.creditsUsed != null || state.isCreditsLoading,
                    enter = fadeIn()
                ) {
                    CreditsCard(state)
                }

                if (state.creditsTotal != null || state.creditsUsed != null || state.isCreditsLoading) {
                    Spacer(Modifier.height(12.dp))
                }

                // Stats row
                StatsRow(state)

                Spacer(Modifier.height(12.dp))

                // Quick actions
                QuickActionsCard(nav)

                Spacer(Modifier.height(12.dp))

                // Heartbeat status
                HeartbeatCard(state, nav)

                Spacer(Modifier.height(12.dp))

                // Recent conversations
                if (state.recentChats.isNotEmpty()) {
                    RecentChatsCard(state, nav)
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(onRefresh: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Mr. Robot",
                color = scheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Dashboard",
                color = scheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_refresh),
                contentDescription = "Refresh",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ProviderStatusCard(state: HomeUiState, nav: NavController) {
    val scheme = MaterialTheme.colorScheme

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isProviderReady) scheme.tertiary
                        else scheme.error
                    )
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isProviderReady) "Connected" else "Offline",
                    color = if (state.isProviderReady) scheme.tertiary else scheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.activeProvider,
                    color = scheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.activeModel.isNotBlank()) {
                    Text(
                        text = shortModel(state.activeModel),
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            Surface(
                modifier = Modifier.clickable { nav.navigate(Route.Settings.path) },
                shape = RoundedCornerShape(999.dp),
                color = scheme.primary.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = if (state.isProviderReady) "Switch" else "Setup",
                    color = scheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CreditsCard(state: HomeUiState) {
    val scheme = MaterialTheme.colorScheme

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Credits",
                color = scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (state.isCreditsLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.creditsFetchError != null) {
            Text(
                text = state.creditsFetchError,
                color = scheme.error,
                fontSize = 12.sp
            )
        } else {
            val used = state.creditsUsed ?: 0.0
            val total = state.creditsTotal ?: 0.0
            val remaining = state.creditsRemaining

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Used",
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$${formatCredits(used)}",
                        color = scheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (remaining != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Remaining",
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$${formatCredits(remaining)}",
                            color = if (remaining < 0.01) scheme.error else scheme.tertiary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                val progress = (used / total).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (progress > 0.9f) scheme.error else scheme.primary,
                    trackColor = scheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}% of $${formatCredits(total)} used",
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun StatsRow(state: HomeUiState) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            value = state.totalConversations.toString(),
            label = "Chats",
            color = scheme.primary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = state.totalMessages.toString(),
            label = "Messages",
            color = scheme.secondary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = state.memoryCount.toString(),
            label = "Memories",
            color = scheme.tertiary
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QuickActionsCard(nav: NavController) {
    val scheme = MaterialTheme.colorScheme

    GlassCard {
        Text(
            text = "Quick Actions",
            color = scheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionItem(
                iconRes = R.drawable.ic_lucide_message_square,
                label = "New Chat",
                color = scheme.primary,
                onClick = { nav.navigate(Route.Chat.path) }
            )
            QuickActionItem(
                iconRes = R.drawable.ic_lucide_bot,
                label = "Agents",
                color = scheme.secondary,
                onClick = { nav.navigate(Route.Agents.path) }
            )
            QuickActionItem(
                iconRes = R.drawable.ic_lucide_terminal,
                label = "Terminal",
                color = scheme.tertiary,
                onClick = { nav.navigate(Route.Sandbox.path) }
            )
            QuickActionItem(
                iconRes = R.drawable.ic_lucide_settings,
                label = "Settings",
                color = scheme.onSurfaceVariant,
                onClick = { nav.navigate(Route.Settings.path) }
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    iconRes: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HeartbeatCard(state: HomeUiState, nav: NavController) {
    val scheme = MaterialTheme.colorScheme

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.heartbeatEnabled) scheme.tertiary
                                else scheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Heartbeat",
                        color = scheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (state.heartbeatEnabled) {
                    val lastRun = if (state.lastHeartbeatTime > 0) {
                        "Last: ${formatTime(state.lastHeartbeatTime)}"
                    } else {
                        "Never run yet"
                    }
                    Text(
                        text = lastRun,
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${state.heartbeatSuccessCount} ok / ${state.heartbeatFailCount} failed",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "Disabled. Enable in Soul/Heartbeat settings.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Surface(
                modifier = Modifier.clickable { nav.navigate(Route.SoulHeartbeat.path) },
                shape = RoundedCornerShape(999.dp),
                color = scheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "Configure",
                    color = scheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentChatsCard(state: HomeUiState, nav: NavController) {
    val scheme = MaterialTheme.colorScheme

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Conversations",
                color = scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "View all",
                color = scheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { nav.navigate(Route.Chat.path) }
            )
        }

        Spacer(Modifier.height(10.dp))

        state.recentChats.forEachIndexed { index, session ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { nav.navigate(Route.Chat.path) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_message_square),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title.ifBlank { "Untitled chat" },
                        color = scheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (session.preview.isNotBlank()) {
                        Text(
                            text = session.preview,
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = formatTime(session.updatedAt),
                    color = scheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            if (index < state.recentChats.lastIndex) {
                HorizontalDivider(
                    color = scheme.outline.copy(alpha = 0.15f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

private fun shortModel(model: String): String {
    if (model.isBlank()) return ""
    val slash = model.lastIndexOf('/')
    return if (slash >= 0 && slash < model.length - 1) model.substring(slash + 1) else model
}

private fun formatCredits(value: Double): String {
    return if (value < 0.01) {
        "0.00"
    } else if (value < 1.0) {
        "%.4f".format(value)
    } else {
        "%.2f".format(value)
    }
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    val oneMinute = 60_000L
    val oneHour = 60 * oneMinute
    val oneDay = 24 * oneHour
    return when {
        diff < oneMinute -> "now"
        diff < oneHour -> "${diff / oneMinute}m ago"
        diff < oneDay -> "${diff / oneHour}h ago"
        diff < 7 * oneDay -> "${diff / oneDay}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
}
