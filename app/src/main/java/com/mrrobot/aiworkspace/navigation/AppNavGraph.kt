package com.mrrobot.aiworkspace.navigation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mrrobot.aiworkspace.R
import com.mrrobot.aiworkspace.ui.screens.AgentsScreen
import com.mrrobot.aiworkspace.ui.screens.ChatScreen
import com.mrrobot.aiworkspace.ui.screens.MarketplaceScreen
import com.mrrobot.aiworkspace.ui.screens.MemoriesScreen
import com.mrrobot.aiworkspace.ui.screens.ProfileScreen
import com.mrrobot.aiworkspace.ui.screens.SandboxScreen
import com.mrrobot.aiworkspace.ui.screens.SettingsScreen
import com.mrrobot.aiworkspace.ui.screens.SkillsScreen
import com.mrrobot.aiworkspace.ui.screens.SoulHeartbeatScreen
import com.mrrobot.aiworkspace.ui.screens.WelcomeScreen

/**
 * Routes the app exposes to the navigation host.
 *
 * The bottom navigation bar has been removed — the home screen now owns
 * the top-level entry points. Every former bottom-nav destination is
 * still reachable, either directly from the home screen (Chat) or
 * through the Settings tab selector (Agents, Skills, Sandbox, Store,
 * Memories, Soul, Profile).
 */
sealed class Route(
    val path: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    object Welcome : Route("welcome", "Home", R.drawable.ic_lucide_home)
    object Chat : Route("chat", "AI", R.drawable.ic_lucide_sparkles)
    object Agents : Route("agents", "Agents", R.drawable.ic_lucide_bot)
    object Skills : Route("skills", "Skills", R.drawable.ic_lucide_wand)
    object Sandbox : Route("sandbox", "Sandbox", R.drawable.ic_lucide_terminal)
    object Market : Route("market", "Store", R.drawable.ic_lucide_store)
    object Settings : Route("settings", "Settings", R.drawable.ic_lucide_settings)
    object Profile : Route("profile", "Profile", R.drawable.ic_lucide_user)
    object Memories : Route("memories", "Memories", R.drawable.ic_lucide_sparkles)
    object SoulHeartbeat : Route("soul-heartbeat", "Soul", R.drawable.ic_lucide_cpu)
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Welcome.path
    ) {
        composable(Route.Welcome.path) {
            WelcomeScreen(navController)
        }

        composable(Route.Chat.path) {
            ChatScreen(navController = navController)
        }

        composable(Route.Agents.path) {
            AgentsScreen()
        }

        composable(Route.Skills.path) {
            SkillsScreen()
        }

        composable(Route.Sandbox.path) {
            SandboxScreen()
        }

        composable(Route.Market.path) {
            MarketplaceScreen()
        }

        composable(Route.Settings.path) {
            SettingsScreen()
        }

        composable(Route.Profile.path) {
            ProfileScreen()
        }

        composable(Route.Memories.path) {
            MemoriesScreen()
        }

        composable(Route.SoulHeartbeat.path) {
            SoulHeartbeatScreen()
        }
    }
}
