package com.mrrobot.aiworkspace.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mrrobot.aiworkspace.navigation.Route
import com.mrrobot.aiworkspace.ui.components.GlassCard
import com.mrrobot.aiworkspace.ui.components.ScreenShell
import com.mrrobot.aiworkspace.ui.components.Subtitle
import com.mrrobot.aiworkspace.ui.components.Title

@Composable
fun MoreScreen(navController: NavController) {
    ScreenShell {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            item {
                Title("More")
                Subtitle("Access workspace tools, sandbox, marketplace, settings, and profile.")

                Spacer(Modifier.height(14.dp))

                MoreCard(
                    iconRes = Route.Memories.iconRes,
                    title = "Memories",
                    subtitle = "Persistent facts, preferences, and learnings the AI remembers across chats.",
                    button = "Open Memories",
                    onClick = { navController.navigate(Route.Memories.path) }
                )

                MoreCard(
                    iconRes = Route.SoulHeartbeat.iconRes,
                    title = "Soul & Heartbeat",
                    subtitle = "Customize the AI persona and configure autonomous self-checks.",
                    button = "Open Soul & Heartbeat",
                    onClick = { navController.navigate(Route.SoulHeartbeat.path) }
                )

                MoreCard(
                    iconRes = Route.Sandbox.iconRes,
                    title = "Linux Sandbox",
                    subtitle = "Real Alpine Linux terminal, file browser, and package manager — all running locally on your device.",
                    button = "Open Sandbox",
                    onClick = { navController.navigate(Route.Sandbox.path) }
                )

                MoreCard(
                    iconRes = Route.Market.iconRes,
                    title = "Marketplace",
                    subtitle = "Manage models, tools, agents, workflows, and integrations.",
                    button = "Open Marketplace",
                    onClick = { navController.navigate(Route.Market.path) }
                )

                MoreCard(
                    iconRes = Route.Settings.iconRes,
                    title = "Settings",
                    subtitle = "Configure OpenRouter, model selection, and Auto/Dark/Light theme.",
                    button = "Open Settings",
                    onClick = { navController.navigate(Route.Settings.path) }
                )

                MoreCard(
                    iconRes = Route.Profile.iconRes,
                    title = "Profile",
                    subtitle = "View workspace identity, stats, and export summary.",
                    button = "Open Profile",
                    onClick = { navController.navigate(Route.Profile.path) }
                )
            }
        }
    }
}

@Composable
private fun MoreCard(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    button: String,
    onClick: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Title(title)
        }

        Spacer(Modifier.height(8.dp))

        Subtitle(subtitle)

        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onClick) {
            Text(button)
        }
    }

    Spacer(Modifier.height(12.dp))
}
