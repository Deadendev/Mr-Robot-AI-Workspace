package com.mrrobot.aiworkspace.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.mrrobot.aiworkspace.R

private enum class SettingsTab(
    val label: String,
    @DrawableRes val iconRes: Int
) {
    General(label = "General", iconRes = R.drawable.ic_lucide_sun_moon_exact),
    AI(label = "AI", iconRes = R.drawable.ic_lucide_settings),
    Agents(label = "Agents", iconRes = R.drawable.ic_lucide_bot),
    Skills(label = "Skills", iconRes = R.drawable.ic_lucide_wand),
    Memories(label = "Memories", iconRes = R.drawable.ic_lucide_sparkles),
    Soul(label = "Soul", iconRes = R.drawable.ic_lucide_cpu),
    Sandbox(label = "Sandbox", iconRes = R.drawable.ic_lucide_terminal),
    Store(label = "Store", iconRes = R.drawable.ic_lucide_store),
    Profile(label = "Profile", iconRes = R.drawable.ic_lucide_user)
}

/**
 * Top-level Settings screen — Kai 9000 inspired tab layout.
 *
 * Replaces the old "More" screen. All workspace features live here as
 * horizontal pill tabs across the top; tapping a pill swaps in the
 * matching screen below without leaving the Settings tab.
 */
@Composable
fun SettingsScreen() {
    var currentTab by remember { mutableStateOf(SettingsTab.General) }

    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.surface.copy(alpha = 0.98f),
                        scheme.surfaceVariant.copy(alpha = 0.72f)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader()

            SettingsTabSelector(
                tabs = SettingsTab.entries,
                currentTab = currentTab,
                onSelectTab = { currentTab = it }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                when (currentTab) {
                    SettingsTab.General -> GeneralSettingsScreen()
                    SettingsTab.AI -> AiSettingsScreen()
                    SettingsTab.Agents -> AgentsScreen()
                    SettingsTab.Skills -> SkillsScreen()
                    SettingsTab.Memories -> MemoriesScreen()
                    SettingsTab.Soul -> SoulHeartbeatScreen()
                    SettingsTab.Sandbox -> SandboxScreen()
                    SettingsTab.Store -> MarketplaceScreen()
                    SettingsTab.Profile -> ProfileScreen()
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Settings",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 34.sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Manage AI providers, agents, skills, memories, soul, sandbox, marketplace, and profile.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SettingsTabSelector(
    tabs: Iterable<SettingsTab>,
    currentTab: SettingsTab,
    onSelectTab: (SettingsTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = currentTab == tab

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelectTab(tab) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                } else {
                    Color.Transparent
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = tab.iconRes),
                        contentDescription = tab.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = tab.label,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
