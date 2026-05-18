package com.mrrobot.aiworkspace.sandbox

/**
 * A single line in the terminal transcript.
 */
sealed interface TerminalLine {
    val text: String

    data class Command(override val text: String) : TerminalLine
    data class Output(override val text: String) : TerminalLine
    data class Error(override val text: String) : TerminalLine
}
