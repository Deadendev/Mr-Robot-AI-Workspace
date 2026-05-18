package com.mrrobot.aiworkspace.data

data class ChatMessage(
    val role: String,
    val content: String
)

/**
 * The result of a chat send: the cleaned reply (after stripping directives
 * the AI emitted) plus the side-effects:
 *  - memories saved/forgotten
 *  - web searches performed (so the UI can show "🔍 Searched: ...")
 */
data class ChatReply(
    val text: String,
    val savedMemoryKeys: List<String> = emptyList(),
    val forgottenMemoryKeys: List<String> = emptyList(),
    val searchedQueries: List<String> = emptyList()
) {
    val didMutateMemory: Boolean
        get() = savedMemoryKeys.isNotEmpty() || forgottenMemoryKeys.isNotEmpty()

    val didSearch: Boolean
        get() = searchedQueries.isNotEmpty()
}

/**
 * Repository for sending chat messages.
 *
 * Composes the system prompt from:
 *   1. Active agent (from [AgentStore])
 *   2. User's "soul" (from [AgentConfigStore])
 *   3. Real-time context + memory tools + search tool
 *   4. Persistent memories (from [MemoryStore])
 *
 * After the model replies, this class:
 *   - Runs any `[SEARCH ...]` directives via [WebSearchTool] and re-prompts
 *     the model with the results so it can answer with live data.
 *   - Persists any `[REMEMBER ...]` / `[FORGET ...]` directives via
 *     [MemoryDirectiveParser].
 *   - Returns the final user-visible reply in [ChatReply].
 */
class ChatRepository(
    private val agentConfigStore: AgentConfigStore? = null,
    private val memoryStore: MemoryStore? = null,
    private val agentStore: AgentStore? = null,
    private val skillStore: SkillStore? = null
) {

    private val directiveParser: MemoryDirectiveParser? =
        memoryStore?.let { MemoryDirectiveParser(it) }

    suspend fun sendMessage(
        settings: AppSettings,
        messages: List<ChatMessage>
    ): Result<ChatReply> {
        val systemPrompt = buildSystemPrompt()
        return runCatching {
            val workingMessages = messages.toMutableList()
            val performedSearches = mutableListOf<String>()

            var attempts = 0
            var raw = ProviderChatClient.generateReply(
                settings = settings,
                messages = workingMessages,
                systemPrompt = systemPrompt
            )

            // Tool loop: if the model asked for searches, run them and feed
            // the results back in a follow-up turn. Cap at 2 rounds to avoid
            // infinite loops with confused models.
            while (attempts < 2) {
                val queries = SearchDirectiveParser.extractQueries(raw)
                if (queries.isEmpty()) break

                val toolBlock = StringBuilder()
                queries.take(3).forEach { query ->
                    val results = WebSearchTool.search(query)
                    performedSearches.add(query)
                    toolBlock.append(WebSearchTool.formatForPrompt(query, results))
                    toolBlock.append("\n\n")
                }

                workingMessages.add(
                    ChatMessage(
                        role = "assistant",
                        content = SearchDirectiveParser.stripDirectives(raw)
                    )
                )
                workingMessages.add(
                    ChatMessage(
                        role = "user",
                        content =
                            "Tool results from your search request " +
                                "(you should now compose your final answer " +
                                "using these and cite source URLs):\n\n" +
                                toolBlock.toString().trim()
                    )
                )

                raw = ProviderChatClient.generateReply(
                    settings = settings,
                    messages = workingMessages,
                    systemPrompt = systemPrompt
                )
                attempts++
            }

            // Strip any leftover [SEARCH ...] directives that didn't trigger
            // (e.g. the model emitted one on the final turn).
            val cleanedOfSearch = SearchDirectiveParser.stripDirectives(raw)

            // Apply memory directives.
            val parser = directiveParser
            if (parser != null) {
                val parsed = parser.applyDirectives(cleanedOfSearch)
                ChatReply(
                    text = parsed.cleaned,
                    savedMemoryKeys = parsed.saved,
                    forgottenMemoryKeys = parsed.forgotten,
                    searchedQueries = performedSearches
                )
            } else {
                ChatReply(
                    text = cleanedOfSearch,
                    searchedQueries = performedSearches
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
     */
    suspend fun buildSystemPrompt(): String? {
        val soul = agentConfigStore?.getSoul() ?: return null
        val memories = memoryStore?.getAll().orEmpty()
        val activeAgent = agentStore?.getActiveAgent()
        val activeSkills = skillStore?.getEnabled().orEmpty()
        return SystemPromptBuilder.build(
            soul = soul,
            memories = memories,
            activeAgent = activeAgent,
            activeSkills = activeSkills
        )
    }
}
