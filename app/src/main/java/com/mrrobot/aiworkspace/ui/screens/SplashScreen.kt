package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mrrobot.aiworkspace.R
import com.mrrobot.aiworkspace.data.AppThemeMode
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    themeMode: AppThemeMode,
    systemDark: Boolean,
    onFinished: () -> Unit
) {
    val useDarkSplash = when (themeMode) {
        AppThemeMode.Auto -> systemDark
        AppThemeMode.Dark -> true
        AppThemeMode.Light -> false
        AppThemeMode.PureBlack -> true
        AppThemeMode.Cyberpunk -> false
        AppThemeMode.Hacker -> true
    }

    val backgroundColor = if (useDarkSplash) {
        Color(0xFF000000)
    } else {
        Color(0xFFFFFFFF)
    }

    val logoRes = if (useDarkSplash) {
        R.drawable.splash_logo_dark
    } else {
        R.drawable.splash_logo_light
    }

    var startAnimation by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "splash_alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.82f,
        animationSpec = tween(
            durationMillis = 700,
            easing = FastOutSlowInEasing
        ),
        label = "splash_scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1400)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = "Mr. Robot Splash Logo",
            modifier = Modifier
                .size(220.dp)
                .scale(scale)
                .alpha(alpha),
            contentScale = ContentScale.Fit
        )
    }
}
