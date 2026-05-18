package com.mrrobot.aiworkspace.data

/**
 * Extracts the AI's sandbox tool directives from a chat reply.
 *
 * Recognized directives:
 *   [SHELL <command>]
 *   [FILE_WRITE <path>]
 *   <content>
 *   [/FILE_WRITE]
 *   [FILE_READ <path>]
 *   [FILE_DELETE <path>]
 */
object SandboxDirectiveParser {

    sealed interface Directive {
        data class Shell(val command: String) : Directive
        data class Read(val path: String) : Directive
        data class Delete(val path: String) : Directive
        data class Write(val path: String, val content: String) : Directive
    }

    private val SHELL_REGEX = Regex(
        """\[\s*SHELL\s+([^\n\]]+?)\s*]""",
        RegexOption.IGNORE_CASE
    )
    private val READ_REGEX = Regex(
        """\[\s*FILE_READ\s+([^\n\]]+?)\s*]""",
        RegexOption.IGNORE_CASE
    )
    private val DELETE_REGEX = Regex(
        """\[\s*FILE_DELETE\s+([^\n\]]+?)\s*]""",
        RegexOption.IGNORE_CASE
    )
    private val WRITE_REGEX = Regex(
        """\[\s*FILE_WRITE\s+([^\n\]]+?)\s*]\s*\n(.*?)\n?\[\s*/\s*FILE_WRITE\s*]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun extract(reply: String): List<Directive> {
        if (!reply.contains('[')) return emptyList()

        val ordered = sortedMapOf<Int, Directive>()
        val writeSpans = mutableListOf<IntRange>()

        for (m in WRITE_REGEX.findAll(reply)) {
            val path = m.groupValues[1].trim().trim('"', '\'')
            val body = m.groupValues[2]
            if (path.isNotBlank()) {
                ordered[m.range.first] = Directive.Write(path, body)
                writeSpans += m.range
            }
        }

        fun insideWrite(range: IntRange): Boolean =
            writeSpans.any { it.first <= range.first && range.last <= it.last }

        for (m in SHELL_REGEX.findAll(reply)) {
            if (insideWrite(m.range)) continue
            val cmd = m.groupValues[1].trim()
            if (cmd.isNotBlank()) ordered[m.range.first] = Directive.Shell(cmd)
        }
        for (m in READ_REGEX.findAll(reply)) {
            if (insideWrite(m.range)) continue
            val path = m.groupValues[1].trim().trim('"', '\'')
            if (path.isNotBlank()) ordered[m.range.first] = Directive.Read(path)
        }
        for (m in DELETE_REGEX.findAll(reply)) {
            if (insideWrite(m.range)) continue
            val path = m.groupValues[1].trim().trim('"', '\'')
            if (path.isNotBlank()) ordered[m.range.first] = Directive.Delete(path)
        }

        return ordered.values.toList()
    }

    fun strip(reply: String): String {
        return reply
            .replace(WRITE_REGEX, "")
            .replace(SHELL_REGEX, "")
            .replace(READ_REGEX, "")
            .replace(DELETE_REGEX, "")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
