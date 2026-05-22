package com.mrrobot.aiworkspace.data

data class ChatMessage(
    val role: String,
    val content: String,
    /**
     * Optional inline image data URLs (e.g. "data:image/jpeg;base64,...") to
     * attach to this message. Only used for user messages and only honoured
     * by providers/models that support multimodal input.
     */
    val imageDataUrls: List<String> = emptyList()
)

/**
 * The result of a chat send: the cleaned reply (after stripping directives
 * the AI emitted) plus the side-effects:
 *  - memories saved/forgotten
 *  - web searches performed
 *  - sandbox tool ops the AI ran (shell / file ops)
 */
data class ChatReply(
    val text: String,
    val savedMemoryKeys: List<String> = emptyList(),
    val forgottenMemoryKeys: List<String> = emptyList(),
    val searchedQueries: List<String> = emptyList(),
    val sandboxOps: List<String> = emptyList(),
    /**
     * Suggestion chips parsed from `[CHIP "..."]` directives the AI emitted.
     * Empty unless the user has Dynamic UI enabled in General settings.
     */
    val suggestionChips: List<String> = emptyList()
) {
    val didMutateMemory: Boolean
        get() = savedMemoryKeys.isNotEmpty() || forgottenMemoryKeys.isNotEmpty()

    val didSearch: Boolean
        get() = searchedQueries.isNotEmpty()

    val didUseSandbox: Boolean
        get() = sandboxOps.isNotEmpty()
}

/**
 * Repository for sending chat messages.
 *
 * Composes the system prompt from:
 *   1. Active agent (from [AgentStore])
 *   2. User's "soul" (from [AgentConfigStore])
 *   3. Real-time context + memory tools + search tool + sandbox tools
 *   4. Persistent memories (from [MemoryStore])
 *
 * After the model replies, this class:
 *   - Runs any `[SEARCH ...]` directives via [WebSearchTool].
 *   - Runs any `[SHELL ...]` / `[FILE_*]` directives via [SandboxToolHost].
 *   - Re-prompts the model with the tool output (up to [MAX_TOOL_ROUNDS]
 *     rounds) so it can compose its final answer.
 *   - Persists any `[REMEMBER ...]` / `[FORGET ...]` directives via
 *     [MemoryDirectiveParser].
 *   - Returns the final user-visible reply in [ChatReply].
 */
class ChatRepository(
    private val agentConfigStore: AgentConfigStore? = null,
    private val memoryStore: MemoryStore? = null,
    private val agentStore: AgentStore? = null,
    private val skillStore: SkillStore? = null,
    private val sandboxToolHost: SandboxToolHost? = null
) {

    private val directiveParser: MemoryDirectiveParser? =
        memoryStore?.let { MemoryDirectiveParser(it) }

    suspend fun sendMessage(
        settings: AppSettings,
        messages: List<ChatMessage>
    ): Result<ChatReply> {
        val systemPrompt = buildSystemPrompt(dynamicUiEnabled = settings.dynamicUiEnabled)
        return runCatching {
            val workingMessages = messages.toMutableList()
            val performedSearches = mutableListOf<String>()
            val sandboxOps = mutableListOf<String>()

            var raw = ProviderChatClient.generateReply(
                settings = settings,
                messages = workingMessages,
                systemPrompt = systemPrompt
            )

            // Tool loop: keep running [SEARCH] / [SHELL] / [FILE_*] directives
            // and feeding the results back until the model stops emitting them
            // or we hit the round cap.
            var rounds = 0
            while (rounds < MAX_TOOL_ROUNDS) {
                val queries = SearchDirectiveParser.extractQueries(raw)
                val sandboxDirectives = SandboxDirectiveParser.extract(raw)

                if (queries.isEmpty() && sandboxDirectives.isEmpty()) break

                val toolBlock = StringBuilder()

                // Web search
                queries.take(3).forEach { query ->
                    val results = WebSearchTool.search(query)
                    performedSearches.add(query)
                    toolBlock.append(WebSearchTool.formatForPrompt(query, results))
                    toolBlock.append("\n\n")
                }

                // Sandbox ops
                if (sandboxToolHost != null && sandboxDirectives.isNotEmpty()) {
                    sandboxDirectives.take(MAX_SANDBOX_OPS_PER_ROUND).forEach { directive ->
                        val result = executeDirective(sandboxToolHost, directive)
                        sandboxOps.add(result.label)
                        toolBlock.append(formatSandboxResult(directive, result))
                        toolBlock.append("\n\n")
                    }
                } else if (sandboxDirectives.isNotEmpty()) {
                    // Model emitted sandbox ops but host isn't wired
                    sandboxDirectives.forEach { directive ->
                        toolBlock.append(formatSandboxUnavailable(directive))
                        toolBlock.append("\n\n")
                    }
                }

                workingMessages.add(
                    ChatMessage(
                        role = "assistant",
                        content = stripAllDirectives(raw)
                    )
                )
                workingMessages.add(
                    ChatMessage(
                        role = "user",
                        content = "Tool results from your previous turn. Compose your " +
                            "final answer using these (or emit more directives if you " +
                            "need additional info). Cite source URLs when you used " +
                            "search results.\n\n" + toolBlock.toString().trim()
                    )
                )

                raw = ProviderChatClient.generateReply(
                    settings = settings,
                    messages = workingMessages,
                    systemPrompt = systemPrompt
                )
                rounds++
            }

            // Strip any leftover directives from the final reply
            val cleanedOfTools = stripAllDirectives(raw)

            // Parse Dynamic-UI suggestion chips off the tail of the reply.
            // When the user has Dynamic UI off, the model wasn't told about
            // the syntax — but in case it emits one anyway we still strip it.
            val withChips = DynamicUiDirectiveParser.parse(cleanedOfTools)
            val cleanedAfterChips = withChips.cleaned
            val chips = if (settings.dynamicUiEnabled) withChips.chips else emptyList()

            // Apply memory directives
            val parser = directiveParser
            if (parser != null) {
                val parsed = parser.applyDirectives(cleanedAfterChips)
                ChatReply(
                    text = parsed.cleaned,
                    savedMemoryKeys = parsed.saved,
                    forgottenMemoryKeys = parsed.forgotten,
                    searchedQueries = performedSearches,
                    sandboxOps = sandboxOps,
                    suggestionChips = chips
                )
            } else {
                ChatReply(
                    text = cleanedAfterChips,
                    searchedQueries = performedSearches,
                    sandboxOps = sandboxOps,
                    suggestionChips = chips
                )
            }
        }
    }

    suspend fun sendMessage(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): Result<ChatReply> {
        val settings = AppSettings(
            apiKey = apiKey,
            model = model,
            selectedProvider = ApiProvider.OpenRouter,
            openRouterApiKey = apiKey,
            openRouterModel = model
        )

        return sendMessage(
            settings = settings,
            messages = messages
        )
    }

    /**
     * Build the composed system prompt. Returns null if no stores are wired
     * (caller falls back to ProviderChatClient's built-in default).
     *
     * @param dynamicUiEnabled when true, includes the `[CHIP "..."]` directive
     *   instructions so the model can render suggestion chips inline. Off by
     *   default for callers that don't (yet) read the General-tab toggle.
     */
    suspend fun buildSystemPrompt(dynamicUiEnabled: Boolean = false): String? {
        val soul = agentConfigStore?.getSoul() ?: return null
        val memories = memoryStore?.getAll().orEmpty()
        val activeAgent = agentStore?.getActiveAgent()
        val activeSkills = skillStore?.getEnabled().orEmpty()
        val sandboxAvailable = sandboxToolHost?.isReady() == true
        return SystemPromptBuilder.build(
            soul = soul,
            memories = memories,
            activeAgent = activeAgent,
            activeSkills = activeSkills,
            sandboxAvailable = sandboxAvailable,
            dynamicUiEnabled = dynamicUiEnabled
        )
    }

    private suspend fun executeDirective(
        host: SandboxToolHost,
        directive: SandboxDirectiveParser.Directive
    ): SandboxToolHost.ToolResult = when (directive) {
        is SandboxDirectiveParser.Directive.Shell -> host.runShell(directive.command)
        is SandboxDirectiveParser.Directive.Read -> host.readFile(directive.path)
        is SandboxDirectiveParser.Directive.Delete -> host.deleteFile(directive.path)
        is SandboxDirectiveParser.Directive.Write -> host.writeFile(directive.path, directive.content)
    }

    private fun formatSandboxResult(
        directive: SandboxDirectiveParser.Directive,
        result: SandboxToolHost.ToolResult
    ): String {
        val header = when (directive) {
            is SandboxDirectiveParser.Directive.Shell ->
                "[SHELL ${directive.command}] -> ${if (result.ok) "ok" else "failed"}"
            is SandboxDirectiveParser.Directive.Read ->
                "[FILE_READ ${directive.path}] -> ${if (result.ok) "ok" else "failed"}"
            is SandboxDirectiveParser.Directive.Delete ->
                "[FILE_DELETE ${directive.path}] -> ${if (result.ok) "ok" else "failed"}"
            is SandboxDirectiveParser.Directive.Write ->
                "[FILE_WRITE ${directive.path}] -> ${if (result.ok) "ok" else "failed"}"
        }
        return "$header\n```\n${result.output.take(8000)}\n```"
    }

    private fun formatSandboxUnavailable(directive: SandboxDirectiveParser.Directive): String {
        val tag = when (directive) {
            is SandboxDirectiveParser.Directive.Shell -> "[SHELL ${directive.command}]"
            is SandboxDirectiveParser.Directive.Read -> "[FILE_READ ${directive.path}]"
            is SandboxDirectiveParser.Directive.Delete -> "[FILE_DELETE ${directive.path}]"
            is SandboxDirectiveParser.Directive.Write -> "[FILE_WRITE ${directive.path}]"
        }
        return "$tag -> unavailable\n```\nLinux sandbox is not available. " +
            "Tell the user to install it from the Linux Sandbox screen.\n```"
    }

    private fun stripAllDirectives(reply: String): String {
        val noSandbox = SandboxDirectiveParser.strip(reply)
        return SearchDirectiveParser.stripDirectives(noSandbox)
    }

    companion object {
        private const val MAX_TOOL_ROUNDS = 5
        private const val MAX_SANDBOX_OPS_PER_ROUND = 4
    }
}
