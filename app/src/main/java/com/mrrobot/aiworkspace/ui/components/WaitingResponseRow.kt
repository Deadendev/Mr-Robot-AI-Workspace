package com.mrrobot.aiworkspace.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrrobot.aiworkspace.R

/**
 * Displays tool-execution feedback during AI processing. Shows which
 * tools are currently running (shell commands, file reads/writes, web
 * searches) so the user isn't left staring at a plain "thinking" bubble.
 *
 * Mirrors Kai's `WaitingResponseRow` pattern: each executing tool gets
 * a labeled chip, and the overall row pulses to indicate activity.
 */
@Composable
fun WaitingResponseRow(
    executingTools: List<String> = emptyList(),
    statusText: String = "Thinking",
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "tool_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tool_row_pulse"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primary, scheme.tertiary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_bot),
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = scheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier.alpha(pulse)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Status text + progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .width(24.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = scheme.primary,
                        trackColor = scheme.surfaceVariant
                    )
                    Text(
                        text = statusText,
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tool chips
                if (executingTools.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        executingTools.forEach { tool ->
                            ToolChip(tool)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String) {
    val scheme = MaterialTheme.colorScheme

    val (icon, displayLabel) = parseToolLabel(label)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = scheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = displayLabel,
                color = scheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

private fun parseToolLabel(label: String): Pair<Int, String> {
    return when {
        label.startsWith("shell:") -> R.drawable.ic_lucide_terminal to label
        label.startsWith("read:") -> R.drawable.ic_lucide_file to label
        label.startsWith("write:") -> R.drawable.ic_lucide_file to label
        label.startsWith("delete:") -> R.drawable.ic_lucide_trash to label
        label.startsWith("search:") || label.contains("web") ->
            R.drawable.ic_lucide_sparkles to label
        else -> R.drawable.ic_lucide_cpu to label
    }
}
