package com.mrrobot.aiworkspace.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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

sealed class Route(
    val path: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    object Welcome : Route(
        path = "welcome",
        label = "Home",
        iconRes = R.drawable.ic_lucide_home
    )

    object Chat : Route(
        path = "chat",
        label = "AI",
        iconRes = R.drawable.ic_lucide_sparkles
    )

    object Agents : Route(
        path = "agents",
        label = "Agents",
        iconRes = R.drawable.ic_lucide_bot
    )

    object Skills : Route(
        path = "skills",
        label = "Skills",
        iconRes = R.drawable.ic_lucide_wand
    )

    object Sandbox : Route(
        path = "sandbox",
        label = "Sandbox",
        iconRes = R.drawable.ic_lucide_terminal
    )

    object Market : Route(
        path = "market",
        label = "Store",
        iconRes = R.drawable.ic_lucide_store
    )

    object Settings : Route(
        path = "settings",
        label = "Settings",
        iconRes = R.drawable.ic_lucide_settings
    )

    object Profile : Route(
        path = "profile",
        label = "Profile",
        iconRes = R.drawable.ic_lucide_user
    )

    object Memories : Route(
        path = "memories",
        label = "Memories",
        iconRes = R.drawable.ic_lucide_sparkles
    )

    object SoulHeartbeat : Route(
        path = "soul-heartbeat",
        label = "Soul",
        iconRes = R.drawable.ic_lucide_cpu
    )
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    val bottomItems = listOf(
        Route.Welcome,
        Route.Chat,
        Route.Agents,
        Route.Skills,
        Route.Settings
    )

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            val currentRoute =
                navController.currentBackStackEntryAsState().value?.destination?.route

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = androidx.compose.ui.unit.Dp.Hairline
            ) {
                bottomItems.forEach { route ->
                    NavigationBarItem(
                        selected = isBottomItemSelected(
                            currentRoute = currentRoute,
                            route = route
                        ),
                        onClick = {
                            navController.navigate(route.path) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = route.iconRes),
                                contentDescription = route.label
                            )
                        },
                        label = {
                            Text(
                                text = route.label,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Welcome.path,
            modifier = Modifier.padding(innerPadding)
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
}

private fun isBottomItemSelected(
    currentRoute: String?,
    route: Route
): Boolean {
    if (currentRoute == route.path) return true

    // Settings is the umbrella tab — it should stay highlighted whenever
    // the user is inside any of the sub-features that live under it.
    val settingsRoutes = setOf(
        Route.Sandbox.path,
        Route.Market.path,
        Route.Profile.path,
        Route.Memories.path,
        Route.SoulHeartbeat.path
    )

    return route == Route.Settings && currentRoute in settingsRoutes
}
