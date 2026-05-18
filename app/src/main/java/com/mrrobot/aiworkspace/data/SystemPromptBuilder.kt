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
        activeSkills: List<Skill> = emptyList()
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
}
