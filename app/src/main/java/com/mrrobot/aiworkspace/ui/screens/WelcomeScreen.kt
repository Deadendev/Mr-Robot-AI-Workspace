package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mrrobot.aiworkspace.navigation.Route
import com.mrrobot.aiworkspace.ui.components.CyberButton
import com.mrrobot.aiworkspace.ui.components.GlassCard
import com.mrrobot.aiworkspace.ui.components.PremiumMetric
import com.mrrobot.aiworkspace.ui.components.ScreenShell
import com.mrrobot.aiworkspace.ui.components.StatusPill
import com.mrrobot.aiworkspace.ui.components.Subtitle
import com.mrrobot.aiworkspace.ui.components.Title

@Composable
fun WelcomeScreen(nav: NavController) {
    ScreenShell {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                HomeHeader()

                Spacer(Modifier.height(18.dp))

                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill("Chat")
                        StatusPill("Agents")
                        StatusPill("Workflows")
                    }

                    Spacer(Modifier.height(18.dp))

                    Title("What can I help you with today?")

                    Spacer(Modifier.height(8.dp))

                    Subtitle(
                        "Start a conversation, launch an agent, or build an automation from a calmer AI workspace."
                    )

                    Spacer(Modifier.height(18.dp))

                    CyberButton("Start New Chat") {
                        nav.navigate(Route.Chat.path)
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { nav.navigate(Route.Agents.path) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Open Agents")
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { nav.navigate(Route.Skills.path) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Manage Skills")
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PremiumMetric(
                            label = "Workspace",
                            value = "20+",
                            description = "Screens and tools"
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        PremiumMetric(
                            label = "Themes",
                            value = "5",
                            description = "Set in Settings"
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PremiumMetric(
                            label = "Model",
                            value = "OR",
                            description = "OpenRouter ready"
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        PremiumMetric(
                            label = "Agents",
                            value = "5",
                            description = "Role-based system"
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Mr. Robot AI\nWorkspace",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 38.sp,
            letterSpacing = (-0.8).sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "A calm, powerful Android workspace for chat, agents, workflows, terminal logs, marketplace tools, and OpenRouter models.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 25.sp
        )
    }
}
