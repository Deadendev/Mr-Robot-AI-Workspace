package com.mrrobot.aiworkspace.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "mr_robot_settings")

enum class AppThemeMode {
    Auto,
    Dark,
    Light,
    Cyberpunk,
    Hacker
}

data class AppSettings(
    val apiKey: String = "",
    val model: String = "openai/gpt-4o-mini",
    val themeMode: AppThemeMode = AppThemeMode.Auto,
    val selectedProvider: ApiProvider = ApiProvider.OpenRouter,

    val openRouterApiKey: String = "",
    val openAiApiKey: String = "",
    val anthropicApiKey: String = "",
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val mistralApiKey: String = "",
    val deepSeekApiKey: String = "",
    val xAiApiKey: String = "",
    val cohereApiKey: String = "",
    val perplexityApiKey: String = "",
    val togetherApiKey: String = "",
    val fireworksApiKey: String = "",
    val moonshotApiKey: String = "",
    val zaiApiKey: String = "",
    val nvidiaNimApiKey: String = "",
    val huggingFaceApiKey: String = "",

    val openRouterModel: String = "openai/gpt-4o-mini",
    val openAiModel: String = "gpt-4o-mini",
    val anthropicModel: String = "claude-3-5-sonnet-latest",
    val geminiModel: String = "gemini-1.5-flash",
    val groqModel: String = "llama-3.3-70b-versatile",
    val mistralModel: String = "mistral-large-latest",
    val deepSeekModel: String = "deepseek-chat",
    val xAiModel: String = "grok-4-1-fast-non-reasoning-latest",
    val cohereModel: String = "command-a-03-2025",
    val perplexityModel: String = "sonar-pro",
    val togetherModel: String = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
    val fireworksModel: String = "accounts/fireworks/models/llama-v3p3-70b-instruct",
    val moonshotModel: String = "kimi-latest",
    val zaiModel: String = "glm-4.6",
    val nvidiaNimModel: String = "meta/llama-3.3-70b-instruct",
    val huggingFaceModel: String = "meta-llama/Llama-3.3-70B-Instruct"
) {
    fun keyFor(provider: ApiProvider): String {
        return when (provider) {
            ApiProvider.OpenRouter -> openRouterApiKey.ifBlank { apiKey }
            ApiProvider.OpenAI -> openAiApiKey
            ApiProvider.Anthropic -> anthropicApiKey
            ApiProvider.Gemini -> geminiApiKey
            ApiProvider.Groq -> groqApiKey
            ApiProvider.Mistral -> mistralApiKey
            ApiProvider.DeepSeek -> deepSeekApiKey
            ApiProvider.XAI -> xAiApiKey
            ApiProvider.Cohere -> cohereApiKey
            ApiProvider.Perplexity -> perplexityApiKey
            ApiProvider.Together -> togetherApiKey
            ApiProvider.Fireworks -> fireworksApiKey
            ApiProvider.Moonshot -> moonshotApiKey
            ApiProvider.ZAI -> zaiApiKey
            ApiProvider.NvidiaNim -> nvidiaNimApiKey
            ApiProvider.HuggingFace -> huggingFaceApiKey
        }
    }

    fun modelFor(provider: ApiProvider): String {
        val stored = when (provider) {
            ApiProvider.OpenRouter -> openRouterModel.ifBlank { model }
            ApiProvider.OpenAI -> openAiModel
            ApiProvider.Anthropic -> anthropicModel
            ApiProvider.Gemini -> geminiModel
            ApiProvider.Groq -> groqModel
            ApiProvider.Mistral -> mistralModel
            ApiProvider.DeepSeek -> deepSeekModel
            ApiProvider.XAI -> xAiModel
            ApiProvider.Cohere -> cohereModel
            ApiProvider.Perplexity -> perplexityModel
            ApiProvider.Together -> togetherModel
            ApiProvider.Fireworks -> fireworksModel
            ApiProvider.Moonshot -> moonshotModel
            ApiProvider.ZAI -> zaiModel
            ApiProvider.NvidiaNim -> nvidiaNimModel
            ApiProvider.HuggingFace -> huggingFaceModel
        }

        return AiModels.byIdOrNull(stored)
            ?.takeIf { it.apiProvider == provider }
            ?.id
            ?: AiModels.defaultForProvider(provider).id
    }

    fun activeApiKey(): String {
        return keyFor(selectedProvider)
    }

    fun activeModel(): String {
        return modelFor(selectedProvider)
    }

    fun hasActiveConfiguration(): Boolean {
        return activeApiKey().isNotBlank()
    }

    fun configuredProviderModels(): List<ProviderModelConfig> {
        return ApiProvider.values()
            .map { provider ->
                ProviderModelConfig(
                    provider = provider,
                    modelId = modelFor(provider),
                    apiKey = keyFor(provider)
                )
            }
            .filter { it.hasKey }
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("openrouter_api_key")
        val MODEL = stringPreferencesKey("selected_model")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")

        val OPENROUTER_API_KEY = stringPreferencesKey("api_key_openrouter")
        val OPENAI_API_KEY = stringPreferencesKey("api_key_openai")
        val ANTHROPIC_API_KEY = stringPreferencesKey("api_key_anthropic")
        val GEMINI_API_KEY = stringPreferencesKey("api_key_gemini")
        val GROQ_API_KEY = stringPreferencesKey("api_key_groq")
        val MISTRAL_API_KEY = stringPreferencesKey("api_key_mistral")
        val DEEPSEEK_API_KEY = stringPreferencesKey("api_key_deepseek")
        val XAI_API_KEY = stringPreferencesKey("api_key_xai")
        val COHERE_API_KEY = stringPreferencesKey("api_key_cohere")
        val PERPLEXITY_API_KEY = stringPreferencesKey("api_key_perplexity")
        val TOGETHER_API_KEY = stringPreferencesKey("api_key_together")
        val FIREWORKS_API_KEY = stringPreferencesKey("api_key_fireworks")
        val MOONSHOT_API_KEY = stringPreferencesKey("api_key_moonshot")
        val ZAI_API_KEY = stringPreferencesKey("api_key_zai")
        val NVIDIANIM_API_KEY = stringPreferencesKey("api_key_nvidianim")
        val HUGGINGFACE_API_KEY = stringPreferencesKey("api_key_huggingface")

        val OPENROUTER_MODEL = stringPreferencesKey("model_openrouter")
        val OPENAI_MODEL = stringPreferencesKey("model_openai")
        val ANTHROPIC_MODEL = stringPreferencesKey("model_anthropic")
        val GEMINI_MODEL = stringPreferencesKey("model_gemini")
        val GROQ_MODEL = stringPreferencesKey("model_groq")
        val MISTRAL_MODEL = stringPreferencesKey("model_mistral")
        val DEEPSEEK_MODEL = stringPreferencesKey("model_deepseek")
        val XAI_MODEL = stringPreferencesKey("model_xai")
        val COHERE_MODEL = stringPreferencesKey("model_cohere")
        val PERPLEXITY_MODEL = stringPreferencesKey("model_perplexity")
        val TOGETHER_MODEL = stringPreferencesKey("model_together")
        val FIREWORKS_MODEL = stringPreferencesKey("model_fireworks")
        val MOONSHOT_MODEL = stringPreferencesKey("model_moonshot")
        val ZAI_MODEL = stringPreferencesKey("model_zai")
        val NVIDIANIM_MODEL = stringPreferencesKey("model_nvidianim")
        val HUGGINGFACE_MODEL = stringPreferencesKey("model_huggingface")
    }

    val settingsFlow: Flow<AppSettings> =
        context.settingsDataStore.data.map { prefs ->
            val legacyOpenRouterKey = prefs[Keys.API_KEY] ?: ""
            val openRouterKey = prefs[Keys.OPENROUTER_API_KEY] ?: legacyOpenRouterKey

            val selectedProvider = runCatching {
                ApiProvider.valueOf(prefs[Keys.SELECTED_PROVIDER] ?: ApiProvider.OpenRouter.name)
            }.getOrDefault(ApiProvider.OpenRouter)

            val themeMode = parseThemeMode(prefs[Keys.THEME_MODE])

            val openRouterModel = normalizeModel(ApiProvider.OpenRouter, prefs[Keys.OPENROUTER_MODEL] ?: prefs[Keys.MODEL])
            val openAiModel = normalizeModel(ApiProvider.OpenAI, prefs[Keys.OPENAI_MODEL])
            val anthropicModel = normalizeModel(ApiProvider.Anthropic, prefs[Keys.ANTHROPIC_MODEL])
            val geminiModel = normalizeModel(ApiProvider.Gemini, prefs[Keys.GEMINI_MODEL])
            val groqModel = normalizeModel(ApiProvider.Groq, prefs[Keys.GROQ_MODEL])
            val mistralModel = normalizeModel(ApiProvider.Mistral, prefs[Keys.MISTRAL_MODEL])
            val deepSeekModel = normalizeModel(ApiProvider.DeepSeek, prefs[Keys.DEEPSEEK_MODEL])
            val xAiModel = normalizeModel(ApiProvider.XAI, prefs[Keys.XAI_MODEL])
            val cohereModel = normalizeModel(ApiProvider.Cohere, prefs[Keys.COHERE_MODEL])
            val perplexityModel = normalizeModel(ApiProvider.Perplexity, prefs[Keys.PERPLEXITY_MODEL])
            val togetherModel = normalizeModel(ApiProvider.Together, prefs[Keys.TOGETHER_MODEL])
            val fireworksModel = normalizeModel(ApiProvider.Fireworks, prefs[Keys.FIREWORKS_MODEL])
            val moonshotModel = normalizeModel(ApiProvider.Moonshot, prefs[Keys.MOONSHOT_MODEL])
            val zaiModel = normalizeModel(ApiProvider.ZAI, prefs[Keys.ZAI_MODEL])
            val nvidiaNimModel = normalizeModel(ApiProvider.NvidiaNim, prefs[Keys.NVIDIANIM_MODEL])
            val huggingFaceModel = normalizeModel(ApiProvider.HuggingFace, prefs[Keys.HUGGINGFACE_MODEL])

            val activeModel = when (selectedProvider) {
                ApiProvider.OpenRouter -> openRouterModel
                ApiProvider.OpenAI -> openAiModel
                ApiProvider.Anthropic -> anthropicModel
                ApiProvider.Gemini -> geminiModel
                ApiProvider.Groq -> groqModel
                ApiProvider.Mistral -> mistralModel
                ApiProvider.DeepSeek -> deepSeekModel
                ApiProvider.XAI -> xAiModel
                ApiProvider.Cohere -> cohereModel
                ApiProvider.Perplexity -> perplexityModel
                ApiProvider.Together -> togetherModel
                ApiProvider.Fireworks -> fireworksModel
                ApiProvider.Moonshot -> moonshotModel
                ApiProvider.ZAI -> zaiModel
                ApiProvider.NvidiaNim -> nvidiaNimModel
                ApiProvider.HuggingFace -> huggingFaceModel
            }

            AppSettings(
                apiKey = openRouterKey,
                model = activeModel,
                themeMode = themeMode,
                selectedProvider = selectedProvider,

                openRouterApiKey = openRouterKey,
                openAiApiKey = prefs[Keys.OPENAI_API_KEY] ?: "",
                anthropicApiKey = prefs[Keys.ANTHROPIC_API_KEY] ?: "",
                geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
                groqApiKey = prefs[Keys.GROQ_API_KEY] ?: "",
                mistralApiKey = prefs[Keys.MISTRAL_API_KEY] ?: "",
                deepSeekApiKey = prefs[Keys.DEEPSEEK_API_KEY] ?: "",
                xAiApiKey = prefs[Keys.XAI_API_KEY] ?: "",
                cohereApiKey = prefs[Keys.COHERE_API_KEY] ?: "",
                perplexityApiKey = prefs[Keys.PERPLEXITY_API_KEY] ?: "",
                togetherApiKey = prefs[Keys.TOGETHER_API_KEY] ?: "",
                fireworksApiKey = prefs[Keys.FIREWORKS_API_KEY] ?: "",
                moonshotApiKey = prefs[Keys.MOONSHOT_API_KEY] ?: "",
                zaiApiKey = prefs[Keys.ZAI_API_KEY] ?: "",
                nvidiaNimApiKey = prefs[Keys.NVIDIANIM_API_KEY] ?: "",
                huggingFaceApiKey = prefs[Keys.HUGGINGFACE_API_KEY] ?: "",

                openRouterModel = openRouterModel,
                openAiModel = openAiModel,
                anthropicModel = anthropicModel,
                geminiModel = geminiModel,
                groqModel = groqModel,
                mistralModel = mistralModel,
                deepSeekModel = deepSeekModel,
                xAiModel = xAiModel,
                cohereModel = cohereModel,
                perplexityModel = perplexityModel,
                togetherModel = togetherModel,
                fireworksModel = fireworksModel,
                moonshotModel = moonshotModel,
                zaiModel = zaiModel,
                nvidiaNimModel = nvidiaNimModel,
                huggingFaceModel = huggingFaceModel
            )
        }

    suspend fun saveSettings(
        apiKey: String,
        model: String,
        themeMode: AppThemeMode
    ) {
        saveAllSettings(
            selectedProvider = ApiProvider.OpenRouter,
            themeMode = themeMode,
            openRouterApiKey = apiKey,
            openAiApiKey = "",
            anthropicApiKey = "",
            geminiApiKey = "",
            groqApiKey = "",
            mistralApiKey = "",
            deepSeekApiKey = "",
            xAiApiKey = "",
            cohereApiKey = "",
            perplexityApiKey = "",
            togetherApiKey = "",
            fireworksApiKey = "",
            moonshotApiKey = "",
            zaiApiKey = "",
            nvidiaNimApiKey = "",
            huggingFaceApiKey = "",
            openRouterModel = model,
            openAiModel = AiModels.defaultForProvider(ApiProvider.OpenAI).id,
            anthropicModel = AiModels.defaultForProvider(ApiProvider.Anthropic).id,
            geminiModel = AiModels.defaultForProvider(ApiProvider.Gemini).id,
            groqModel = AiModels.defaultForProvider(ApiProvider.Groq).id,
            mistralModel = AiModels.defaultForProvider(ApiProvider.Mistral).id,
            deepSeekModel = AiModels.defaultForProvider(ApiProvider.DeepSeek).id,
            xAiModel = AiModels.defaultForProvider(ApiProvider.XAI).id,
            cohereModel = AiModels.defaultForProvider(ApiProvider.Cohere).id,
            perplexityModel = AiModels.defaultForProvider(ApiProvider.Perplexity).id,
            togetherModel = AiModels.defaultForProvider(ApiProvider.Together).id,
            fireworksModel = AiModels.defaultForProvider(ApiProvider.Fireworks).id,
            moonshotModel = AiModels.defaultForProvider(ApiProvider.Moonshot).id,
            zaiModel = AiModels.defaultForProvider(ApiProvider.ZAI).id,
            nvidiaNimModel = AiModels.defaultForProvider(ApiProvider.NvidiaNim).id,
            huggingFaceModel = AiModels.defaultForProvider(ApiProvider.HuggingFace).id
        )
    }

    suspend fun saveAllSettings(
        selectedProvider: ApiProvider,
        themeMode: AppThemeMode,

        openRouterApiKey: String,
        openAiApiKey: String,
        anthropicApiKey: String,
        geminiApiKey: String,
        groqApiKey: String,
        mistralApiKey: String,
        deepSeekApiKey: String,
        xAiApiKey: String,
        cohereApiKey: String,
        perplexityApiKey: String,
        togetherApiKey: String,
        fireworksApiKey: String,
        moonshotApiKey: String,
        zaiApiKey: String,
        nvidiaNimApiKey: String,
        huggingFaceApiKey: String,

        openRouterModel: String,
        openAiModel: String,
        anthropicModel: String,
        geminiModel: String,
        groqModel: String,
        mistralModel: String,
        deepSeekModel: String,
        xAiModel: String,
        cohereModel: String,
        perplexityModel: String,
        togetherModel: String,
        fireworksModel: String,
        moonshotModel: String,
        zaiModel: String,
        nvidiaNimModel: String,
        huggingFaceModel: String
    ) {
        val safeOpenRouterModel = normalizeModel(ApiProvider.OpenRouter, openRouterModel)
        val safeOpenAiModel = normalizeModel(ApiProvider.OpenAI, openAiModel)
        val safeAnthropicModel = normalizeModel(ApiProvider.Anthropic, anthropicModel)
        val safeGeminiModel = normalizeModel(ApiProvider.Gemini, geminiModel)
        val safeGroqModel = normalizeModel(ApiProvider.Groq, groqModel)
        val safeMistralModel = normalizeModel(ApiProvider.Mistral, mistralModel)
        val safeDeepSeekModel = normalizeModel(ApiProvider.DeepSeek, deepSeekModel)
        val safeXAiModel = normalizeModel(ApiProvider.XAI, xAiModel)
        val safeCohereModel = normalizeModel(ApiProvider.Cohere, cohereModel)
        val safePerplexityModel = normalizeModel(ApiProvider.Perplexity, perplexityModel)
        val safeTogetherModel = normalizeModel(ApiProvider.Together, togetherModel)
        val safeFireworksModel = normalizeModel(ApiProvider.Fireworks, fireworksModel)
        val safeMoonshotModel = normalizeModel(ApiProvider.Moonshot, moonshotModel)
        val safeZaiModel = normalizeModel(ApiProvider.ZAI, zaiModel)
        val safeNvidiaNimModel = normalizeModel(ApiProvider.NvidiaNim, nvidiaNimModel)
        val safeHuggingFaceModel = normalizeModel(ApiProvider.HuggingFace, huggingFaceModel)

        val activeModel = when (selectedProvider) {
            ApiProvider.OpenRouter -> safeOpenRouterModel
            ApiProvider.OpenAI -> safeOpenAiModel
            ApiProvider.Anthropic -> safeAnthropicModel
            ApiProvider.Gemini -> safeGeminiModel
            ApiProvider.Groq -> safeGroqModel
            ApiProvider.Mistral -> safeMistralModel
            ApiProvider.DeepSeek -> safeDeepSeekModel
            ApiProvider.XAI -> safeXAiModel
            ApiProvider.Cohere -> safeCohereModel
            ApiProvider.Perplexity -> safePerplexityModel
            ApiProvider.Together -> safeTogetherModel
            ApiProvider.Fireworks -> safeFireworksModel
            ApiProvider.Moonshot -> safeMoonshotModel
            ApiProvider.ZAI -> safeZaiModel
            ApiProvider.NvidiaNim -> safeNvidiaNimModel
            ApiProvider.HuggingFace -> safeHuggingFaceModel
        }

        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SELECTED_PROVIDER] = selectedProvider.name
            prefs[Keys.MODEL] = activeModel
            prefs[Keys.THEME_MODE] = themeMode.name

            prefs[Keys.OPENROUTER_API_KEY] = openRouterApiKey.trim()
            prefs[Keys.API_KEY] = openRouterApiKey.trim()
            prefs[Keys.OPENAI_API_KEY] = openAiApiKey.trim()
            prefs[Keys.ANTHROPIC_API_KEY] = anthropicApiKey.trim()
            prefs[Keys.GEMINI_API_KEY] = geminiApiKey.trim()
            prefs[Keys.GROQ_API_KEY] = groqApiKey.trim()
            prefs[Keys.MISTRAL_API_KEY] = mistralApiKey.trim()
            prefs[Keys.DEEPSEEK_API_KEY] = deepSeekApiKey.trim()
            prefs[Keys.XAI_API_KEY] = xAiApiKey.trim()
            prefs[Keys.COHERE_API_KEY] = cohereApiKey.trim()
            prefs[Keys.PERPLEXITY_API_KEY] = perplexityApiKey.trim()
            prefs[Keys.TOGETHER_API_KEY] = togetherApiKey.trim()
            prefs[Keys.FIREWORKS_API_KEY] = fireworksApiKey.trim()
            prefs[Keys.MOONSHOT_API_KEY] = moonshotApiKey.trim()
            prefs[Keys.ZAI_API_KEY] = zaiApiKey.trim()
            prefs[Keys.NVIDIANIM_API_KEY] = nvidiaNimApiKey.trim()
            prefs[Keys.HUGGINGFACE_API_KEY] = huggingFaceApiKey.trim()

            prefs[Keys.OPENROUTER_MODEL] = safeOpenRouterModel
            prefs[Keys.OPENAI_MODEL] = safeOpenAiModel
            prefs[Keys.ANTHROPIC_MODEL] = safeAnthropicModel
            prefs[Keys.GEMINI_MODEL] = safeGeminiModel
            prefs[Keys.GROQ_MODEL] = safeGroqModel
            prefs[Keys.MISTRAL_MODEL] = safeMistralModel
            prefs[Keys.DEEPSEEK_MODEL] = safeDeepSeekModel
            prefs[Keys.XAI_MODEL] = safeXAiModel
            prefs[Keys.COHERE_MODEL] = safeCohereModel
            prefs[Keys.PERPLEXITY_MODEL] = safePerplexityModel
            prefs[Keys.TOGETHER_MODEL] = safeTogetherModel
            prefs[Keys.FIREWORKS_MODEL] = safeFireworksModel
            prefs[Keys.MOONSHOT_MODEL] = safeMoonshotModel
            prefs[Keys.ZAI_MODEL] = safeZaiModel
            prefs[Keys.NVIDIANIM_MODEL] = safeNvidiaNimModel
            prefs[Keys.HUGGINGFACE_MODEL] = safeHuggingFaceModel
        }
    }

    suspend fun clearSettings() {
        context.settingsDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun parseThemeMode(value: String?): AppThemeMode {
        return when (value) {
            "Cyber" -> AppThemeMode.Cyberpunk
            else -> runCatching {
                AppThemeMode.valueOf(value ?: AppThemeMode.Auto.name)
            }.getOrDefault(AppThemeMode.Auto)
        }
    }

    private fun normalizeModel(
        provider: ApiProvider,
        value: String?
    ): String {
        // First try the value as-is, then fall back to the upgrade map
        // (so a user whose stored xAI model is "grok-2-latest" silently
        // migrates to "grok-4-1-fast-non-reasoning-latest" instead of
        // hitting "model is deprecated" / "no endpoints found").
        val candidate = AiModels.resolveOrUpgrade(value)
        return AiModels.byIdOrNull(candidate.orEmpty())
            ?.takeIf { it.apiProvider == provider }
            ?.id
            ?: AiModels.defaultForProvider(provider).id
    }
}
