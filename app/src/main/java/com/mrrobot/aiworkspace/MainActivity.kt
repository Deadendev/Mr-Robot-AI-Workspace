package com.mrrobot.aiworkspace

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mrrobot.aiworkspace.data.AppSettings
import com.mrrobot.aiworkspace.data.AppThemeMode
import com.mrrobot.aiworkspace.data.SettingsStore
import com.mrrobot.aiworkspace.navigation.AppNavGraph
import com.mrrobot.aiworkspace.ui.screens.SplashScreen
import com.mrrobot.aiworkspace.ui.theme.MrRobotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val settingsStore = SettingsStore(applicationContext)

        // Apply system bars eagerly with safe defaults; the Compose layer below
        // re-applies them once the real settings are emitted by the flow. We
        // intentionally do NOT block the main thread on a DataStore read here.
        // Any blocking I/O on cold start is directly visible as launch jank.
        applySystemBars(
            themeMode = AppThemeMode.Auto,
            systemDark = false,
            splashMode = true
        )

        setContent {
            // First emission of `settingsFlow` happens off the main thread.
            // Until it arrives, `settings` reflects the default `AppSettings()`
            // — which is fine for a splash frame.
            val settings by settingsStore.settingsFlow.collectAsState(
                initial = AppSettings()
            )

            val systemDark = isSystemInDarkTheme()
            val view = LocalView.current
            var showSplash by remember { mutableStateOf(true) }

            SideEffect {
                applySystemBars(
                    themeMode = settings.themeMode,
                    systemDark = systemDark,
                    splashMode = showSplash,
                    view = view
                )
            }

            if (showSplash) {
                SplashScreen(
                    themeMode = settings.themeMode,
                    systemDark = systemDark,
                    onFinished = {
                        showSplash = false
                    }
                )
            } else {
                MrRobotTheme(themeMode = settings.themeMode) {
                    AppNavGraph()
                }
            }
        }
    }

    private fun applySystemBars(
        themeMode: AppThemeMode,
        systemDark: Boolean,
        splashMode: Boolean,
        view: android.view.View? = null
    ) {
        val darkUi = isDarkUi(
            themeMode = themeMode,
            systemDark = systemDark
        )

        val barColor = when {
            splashMode && darkUi -> Color.BLACK
            splashMode && !darkUi -> Color.WHITE
            darkUi -> Color.rgb(3, 7, 18)
            else -> Color.rgb(248, 250, 252)
        }

        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        window.decorView.setBackgroundColor(barColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = barColor
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val targetView = view ?: window.decorView
        val controller = WindowInsetsControllerCompat(window, targetView)

        controller.isAppearanceLightStatusBars = !darkUi
        controller.isAppearanceLightNavigationBars = !darkUi
    }

    private fun isDarkUi(
        themeMode: AppThemeMode,
        systemDark: Boolean
    ): Boolean {
        return when (themeMode) {
            AppThemeMode.Auto -> systemDark
            AppThemeMode.Dark -> true
            AppThemeMode.Light -> false
            AppThemeMode.Cyberpunk -> false
            AppThemeMode.Hacker -> true
        }
    }
}
