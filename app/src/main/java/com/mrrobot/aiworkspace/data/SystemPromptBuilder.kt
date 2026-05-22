package com.mrrobot.aiworkspace.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Composes the full chat system prompt: active agent + soul + real-time
 * context + memory tools + web search tool + memories.
 *
 * Layered prompt order:
 *   1. Active agent persona (if any agent is activated)
 *   2. Soul (user-customizable base persona)
 *   3. Memories grouped by category
 *
 * Format example:
 *   You are Android Architect.
 *   Mobile Architecture
 *
 *   Builds production-grade Android app structures...
 *
 *   ## Skills
 *   - Kotlin
 *   - Jetpack Compose
 *
 *   ---
 *
 *   <soul prompt>
 *
 *   ## Your Memories
 *   - **key**: content
 *
 *   ## User Preferences
 *   - **key**: content
 *
 *   ## Learnings (reinforced 5x)
 *   - **key**: content
 *
 *   ## Known Issues & Resolutions
 *   - **key**: content
 */
object SystemPromptBuilder {

    fun build(
        soul: SoulConfig,
        memories: List<MemoryEntry>,
        activeAgent: Agent? = null,
        activeSkills: List<Skill> = emptyList(),
        sandboxAvailable: Boolean = false,
        dynamicUiEnabled: Boolean = false
    ): String = buildString {
        if (activeAgent != null) {
            append("You are ${activeAgent.name}.\n")
            append(activeAgent.role).append('\n')
            if (activeAgent.description.isNotBlank()) {
                append('\n').append(activeAgent.description).append('\n')
            }
            if (activeAgent.systemPrompt.isNotBlank()) {
                append('\n').append(activeAgent.systemPrompt).append('\n')
            }
            if (activeAgent.skills.isNotEmpty()) {
                append("\n## Skills\n")
                activeAgent.skills.forEach { append("- ").append(it).append('\n') }
            }
            append("\n---\n\n")
        }

        append(soul.effectivePrompt())

        appendActiveSkills(activeSkills)

        // Always-on real-time context (date/time/timezone) — eliminates
        // "I don't have access to the current time" responses.
        append(buildRealTimeContext())

        // Web search tool — teaches the model to emit [SEARCH ...]
        // directives for anything time-sensitive (news, prices, weather,
        // sports scores, latest releases, etc.).
        append(SEARCH_TOOL_INSTRUCTIONS)

        // Memory tools — taught to every model so natural-language
        // "remember my name is Alex" gets persisted automatically.
        append(MEMORY_TOOL_INSTRUCTIONS)

        // Sandbox tools — only advertised when the on-device Linux sandbox
        // is actually ready, so the model doesn't hallucinate file ops when
        // the user hasn't installed it yet.
        if (sandboxAvailable) {
            append(SANDBOX_TOOL_INSTRUCTIONS)
        }

        // Dynamic UI — when the user has the General-tab toggle ON, allow
        // the model to emit `[CHIP "..."]` directives that render as
        // tappable suggestion chips below its reply. When OFF, omit the
        // section entirely so the model produces plain markdown only.
        if (dynamicUiEnabled) {
            append(DYNAMIC_UI_INSTRUCTIONS)
        }

        val byCategory = memories.groupBy { it.category }

        appendCategory(
            header = "Your Memories",
            entries = byCategory[MemoryCategory.GENERAL].orEmpty(),
            withHitCount = false
        )
        appendCategory(
            header = "User Preferences",
            entries = byCategory[MemoryCategory.PREFERENCE].orEmpty(),
            withHitCount = false
        )
        appendCategory(
            header = "Learnings",
            entries = byCategory[MemoryCategory.LEARNING].orEmpty(),
            withHitCount = true
        )
        appendCategory(
            header = "Known Issues & Resolutions",
            entries = byCategory[MemoryCategory.ERROR].orEmpty(),
            withHitCount = false
        )
    }

    private fun StringBuilder.appendActiveSkills(skills: List<Skill>) {
        val active = skills.filter { it.enabled && it.instructions.isNotBlank() }
        if (active.isEmpty()) return

        append("\n\n## Active Skills\n")
        append(
            "These are user-equipped capabilities. Apply each one to every reply " +
                "unless the user explicitly asks otherwise.\n\n"
        )
        active.forEach { skill ->
            append("### ").append(skill.name).append('\n')
            append(skill.instructions.trim()).append("\n\n")
        }
    }

    private fun StringBuilder.appendCategory(
        header: String,
        entries: List<MemoryEntry>,
        withHitCount: Boolean
    ) {
        if (entries.isEmpty()) return

        append("\n\n## ").append(header).append("\n")
        entries.forEach { entry ->
            append("- **").append(entry.key).append("**")
            if (withHitCount && entry.hitCount > 1) {
                append(" (reinforced ").append(entry.hitCount).append("x)")
            }
            append(": ").append(entry.content).append('\n')
        }
    }

    private fun buildRealTimeContext(): String {
        val tz = TimeZone.getDefault()
        val now = Date()
        val dateFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            .apply { timeZone = tz }
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .apply { timeZone = tz }
        val tzLabel = tz.getDisplayName(false, TimeZone.SHORT, Locale.getDefault())

        return buildString {
            append("\n\n## Current Real-Time Context\n")
            append("- Current date: ").append(dateFmt.format(now)).append('\n')
            append("- Current local time: ").append(timeFmt.format(now))
                .append(' ').append(tzLabel).append('\n')
            append("- Timezone: ").append(tz.id).append('\n')
            append("Use this for any time/date question. Do not say you don't know the date.\n")
            append('\n')
            append("## Multimodal Attachments\n")
            append("The user can attach files of any kind. The app extracts content for you:\n")
            append("- Images (jpg/png/webp/heic/etc.) are sent as actual visible images. Look at them and describe / analyze what you see.\n")
            append("- Videos: a thumbnail frame is attached as an image, plus duration / resolution / bitrate metadata in the text. Describe what's in the frame and reason from the metadata.\n")
            append("- PDFs and Office docs (docx/odt/xlsx/pptx): extracted text is in the user message under \"Attached context\".\n")
            append("- Plain text, source code, JSON/XML/YAML/CSV/HTML: full content (up to ~64 KB) is in \"Attached context\".\n")
            append("- Audio: metadata only (duration, title, artist, bitrate). You cannot hear the audio.\n")
            append("- Unknown binaries: a hex preview of the first 512 bytes plus MIME / size.\n")
            append("If an image is attached, never claim you can't see images — describe it.\n")
        }
    }

    private val SEARCH_TOOL_INSTRUCTIONS = """


## Web Search Tool

You can search the live web. When the user asks anything time-sensitive
that you cannot answer from your training data — current weather, today's
news, stock or crypto prices, sports scores, latest software/product
releases, "what is X right now", recently published facts — you MUST
emit a search directive on its own line:

[SEARCH your concise query here]

Rules:
  - Emit the directive on its own line, with no surrounding code fences.
  - Use a focused query (no quotes, no boolean operators).
  - You may emit up to 3 search directives per reply.
  - After your directives, stop. Do not invent search results. The app
    will run the search and feed the results back so you can compose a
    final, grounded answer in a follow-up turn.
  - In the follow-up turn, you will see a "Search results" block in the
    user message. Cite the sources by URL when you use them.
  - Do NOT search for static knowledge you already know (geography,
    historical facts, established science, definitions, code syntax).

Examples that should trigger a search:
  - "What's the weather in Tokyo right now?"
  - "Who won last night's Lakers game?"
  - "What's the latest iPhone model?"
  - "Bitcoin price today"
  - "Has Kotlin 2.2 been released?"

""".trimIndent()

    private val MEMORY_TOOL_INSTRUCTIONS = """


## Memory Tools

You have persistent memory across conversations. When the user shares
information you should remember (their name, preferences, project
details, decisions, recurring facts, etc.), you MUST emit a memory
directive at the very end of your reply on its own line(s), in this
exact format:

[REMEMBER key = value]

Trigger this WHENEVER the user:
  - Tells you their name, location, role, or any personal fact
  - States a preference ("I prefer dark mode", "I always use Kotlin")
  - Says something like "remember that...", "don't forget...",
    "my name is...", "for next time..."
  - Decides on a recurring approach, convention, or rule

Use snake_case keys. Example exchanges:

User: "My name is Alex."
You: Nice to meet you, Alex! How can I help today?
[REMEMBER user_name = Alex]

User: "I'm building an Android app called Bolt."
You: Got it. What part of Bolt are we working on?
[REMEMBER current_project = Android app named Bolt]

User: "Always reply in formal English."
You: Understood. I will use formal English from now on.
[REMEMBER tone_preference = formal English]

To delete a memory, emit:

[FORGET key]

You can emit multiple directives, one per line. Directives are
hidden from the user — they are silently persisted. Do NOT mention
the directives in your visible reply, do NOT format them as code,
and do NOT explain them. Just emit them after your normal answer.

If the user explicitly asks "what do you remember about me?", read
the memories listed below and answer naturally.

""".trimIndent()

    private val SANDBOX_TOOL_INSTRUCTIONS = """


## Linux Sandbox Tools

You have access to a real Alpine Linux sandbox running on the user's
device. You can run shell commands and read/write/delete files in it.
Use these whenever the user asks you to inspect their environment,
generate code into a file, run a script, install a package, etc.

Emit directives on their own line(s). The app intercepts them, runs
them, and feeds the results back to you in a follow-up turn — only
then should you compose your final answer to the user.

### Run a shell command

[SHELL <command>]

Examples:
  [SHELL ls -la /root]
  [SHELL python3 -c 'print(2 ** 16)']
  [SHELL apk info | head -20]
  [SHELL cd /root && ls && pwd]

Each [SHELL] runs in a fresh process, so combine multi-step work with
&& or ; on a single line.

### Create or overwrite a file

[FILE_WRITE <absolute-path>]
<file content, possibly multi-line>
[/FILE_WRITE]

The opening tag must be on its own line, and the closing tag must be
on its own line. The body in between is taken literally.

Example:
  [FILE_WRITE /root/hello.py]
  print("hello, world")
  [/FILE_WRITE]

### Read a file

[FILE_READ <path>]

The first ~8 KB of the file are returned to you.

### Delete a file or directory

[FILE_DELETE <path>]

Removes the path recursively. Never delete /, /root, /etc, or system
paths unless the user explicitly asks.

### Rules

  - You may emit any combination of [SEARCH], [SHELL], [FILE_*]
    directives in one reply.
  - Up to 5 tool rounds per chat turn.
  - Stop emitting directives once you have what you need, then write
    your final natural-language answer.
  - Do NOT fabricate output. If you didn't run a command, don't pretend.
  - Directives are silent — never describe them to the user as code.

""".trimIndent()

    private val DYNAMIC_UI_INSTRUCTIONS = """


## Dynamic UI

The user has Dynamic UI enabled for this conversation. When it would help
them, you may emit suggestion **chips** at the very end of your reply,
each on its own line:

[CHIP "Short, action-oriented label"]

The app renders each chip as a tappable button under your bubble. Tapping
a chip drops its label into the chat input so the user can review and
send. Use this to:
  - Offer 2–4 logical follow-up questions after a multi-step explanation.
  - Suggest the next plausible action ("Run the test", "Show the diff",
    "Open Settings").
  - Replace yes/no questions ("Want me to write a test for this?" →
    chips: `[CHIP "Yes, write a test"] [CHIP "No, leave it"]`).

Rules:
  - Maximum 4 chips per reply.
  - Labels must be short (2–7 words), specific, and immediately actionable.
  - Emit chips on their own lines AFTER your normal markdown answer.
  - Don't describe the chips in the reply body — they speak for themselves.
  - Don't wrap chips in code fences. Plain `[CHIP "..."]` only.
  - When the user's request is fully answered and no follow-up makes sense,
    skip chips entirely. They are optional, not mandatory.

You may also use rich markdown freely (headings, lists, tables, fenced
code) — the renderer supports it. Combine clean markdown with chips for
a polished, interactive feel.

""".trimIndent()
}
