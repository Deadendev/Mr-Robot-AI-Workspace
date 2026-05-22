package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mrrobot.aiworkspace.R
import com.mrrobot.aiworkspace.navigation.Route

/**
 * Kai 9000-styled home screen.
 *
 * Minimalist welcome layout that replaces the legacy dashboard. The bottom
 * navigation bar has been removed app-wide, so this screen owns:
 *
 *   - Top icon row: history, sessions menu, mute toggle, settings.
 *   - Centered dual-circle "Kai" logo with "Welcome to Kai 9000" title.
 *   - "Start Interactive UI" pill that opens the AI chat.
 *   - Bottom "Ask a question" composer that opens the chat to type.
 *
 * Every workspace feature (Agents, Skills, Sandbox, Memories, Soul,
 * Marketplace, Profile, AI providers, General) is now reachable from the
 * Settings tab selector, opened via the gear icon in the top right.
 */
@Composable
fun WelcomeScreen(nav: NavController) {
    var muted by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            HomeTopBar(
                muted = muted,
                onToggleMute = { muted = !muted },
                onHistoryClick = { nav.navigate(Route.Chat.path) },
                onSessionsClick = { nav.navigate(Route.Chat.path) },
                onSettingsClick = { nav.navigate(Route.Settings.path) }
            )

            Spacer(Modifier.weight(1f))

            HomeHero(
                onStartInteractiveUi = { nav.navigate(Route.Chat.path) }
            )

            Spacer(Modifier.weight(1f))

            AskQuestionBar(
                onClick = { nav.navigate(Route.Chat.path) }
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ───── top icon row ─────────────────────────────────────────────────────

@Composable
private fun HomeTopBar(
    muted: Boolean,
    onToggleMute: () -> Unit,
    onHistoryClick: () -> Unit,
    onSessionsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val tint = MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onHistoryClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_history),
                contentDescription = "Conversation history",
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onSessionsClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_menu),
                contentDescription = "Sessions",
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleMute) {
            Icon(
                painter = painterResource(
                    id = if (muted) R.drawable.ic_lucide_volume_off
                    else R.drawable.ic_lucide_sparkles
                ),
                contentDescription = if (muted) "Sound off" else "Sound on",
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onSettingsClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_settings),
                contentDescription = "Settings",
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ───── center hero (logo + welcome + button) ────────────────────────────

@Composable
private fun HomeHero(onStartInteractiveUi: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        KaiDualCircleLogo(size = 92.dp)

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Welcome to Kai 9000",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(28.dp))

        StartInteractiveUiButton(onClick = onStartInteractiveUi)
    }
}

@Composable
private fun KaiDualCircleLogo(size: androidx.compose.ui.unit.Dp) {
    // Two overlapping flat-purple circles. The lighter circle is offset to
    // the upper-left, the deeper one to the lower-right. Sizes are
    // proportional to `size` so the logo scales cleanly.
    val lightPurple = Color(0xFFA78BFA)
    val deepPurple = Color(0xFF7C3AED)

    val circleSize = size * 0.78f
    val overlap = size * 0.30f

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .offset(x = -overlap / 2, y = 0.dp)
                .clip(CircleShape)
                .background(lightPurple)
        )
        Box(
            modifier = Modifier
                .size(circleSize)
                .offset(x = overlap / 2, y = 0.dp)
                .clip(CircleShape)
                .background(deepPurple)
        )
    }
}

@Composable
private fun StartInteractiveUiButton(onClick: () -> Unit) {
    val gradient = Brush.horizontalGradient(
        listOf(
            Color(0xFFA78BFA),
            Color(0xFF7C3AED),
            Color(0xFFA78BFA)
        )
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 2.dp,
                brush = gradient,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 32.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Start Interactive UI",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ───── bottom "Ask a question" composer ─────────────────────────────────

@Composable
private fun AskQuestionBar(onClick: () -> Unit) {
    val borderBrush = Brush.horizontalGradient(
        listOf(
            Color(0xFFA78BFA),
            Color(0xFF7C3AED)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .border(
                width = 2.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lucide_paperclip),
            contentDescription = "Attach",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = "Ask a question",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        AskBarTrailingLogo()
    }
}

@Composable
private fun AskBarTrailingLogo() {
    // A subtle dual-arc "AI" mark to mirror the trailing logo in the
    // Kai 9000 mockup. Rendered as an outlined sparkle so the ask bar
    // visually calls out the AI affordance without using a brand asset.
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lucide_sparkles),
            contentDescription = "AI",
            tint = Color(0xFF22C55E),
            modifier = Modifier.size(18.dp)
        )
    }
}
