package com.mrrobot.aiworkspace.data

/**
 * Parses Dynamic-UI directives that the AI emits at the end of a reply when
 * the user has Dynamic UI enabled in General settings (Kai 9000-parity).
 *
 * Currently supported syntax:
 *
 *   [CHIP "Take me to settings"]
 *   [CHIP "Show recent memories"]
 *
 * Each chip becomes a tappable suggestion under the assistant bubble. Tapping
 * a chip drops its label into the chat input (via `ChatViewModel.useSuggestion`)
 * so the user can review and send — same model Kai uses for inline UI.
 *
 * The parser is permissive: it accepts straight, smart, or single quotes, and
 * tolerates leading dashes or bullet markup the model sometimes adds.
 */
object DynamicUiDirectiveParser {

    /** Result of parsing a reply: the original minus any directives, plus the chips. */
    data class Parsed(val cleaned: String, val chips: List<String>)

    private val CHIP_REGEX = Regex(
        """\[CHIP\s+["“”'‘’]([^"“”'‘’\n\r\]]{1,80})["“”'‘’]\s*]""",
        RegexOption.IGNORE_CASE
    )

    fun parse(reply: String): Parsed {
        if (reply.isBlank() || !reply.contains("[CHIP", ignoreCase = true)) {
            return Parsed(cleaned = reply, chips = emptyList())
        }

        val chips = mutableListOf<String>()
        CHIP_REGEX.findAll(reply).forEach { match ->
            val label = match.groupValues[1].trim()
            if (label.isNotEmpty()) chips += label
        }

        // Cap to 6 chips so a runaway model can't flood the bubble.
        val capped = chips.distinct().take(MAX_CHIPS)

        // Strip every chip directive from the body, then collapse the trailing
        // whitespace runs the strip leaves behind.
        val cleaned = CHIP_REGEX.replace(reply, "")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        return Parsed(cleaned = cleaned, chips = capped)
    }

    private const val MAX_CHIPS = 6
}
