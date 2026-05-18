package com.mrrobot.aiworkspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrrobot.aiworkspace.data.AiModels
import com.mrrobot.aiworkspace.data.ApiProvider
import com.mrrobot.aiworkspace.data.AppSettings
import com.mrrobot.aiworkspace.data.AppThemeMode
import com.mrrobot.aiworkspace.data.ProviderModelConfig
import com.mrrobot.aiworkspace.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
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
    val huggingFaceModel: String = "meta-llama/Llama-3.3-70B-Instruct",

    val savedMessage: String = "",
    val isLoaded: Boolean = false
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
        val saved = when (provider) {
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

        return AiModels.byIdOrNull(saved)
            ?.takeIf { it.apiProvider == provider }
            ?.id
            ?: AiModels.defaultForProvider(provider).id
    }

    fun hasActiveConfiguration(): Boolean {
        return keyFor(selectedProvider).isNotBlank()
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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application.applicationContext)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.settingsFlow.collect { settings: AppSettings ->
                _uiState.value = _uiState.value.copy(
                    apiKey = settings.apiKey,
                    model = settings.model,
                    themeMode = settings.themeMode,
                    selectedProvider = settings.selectedProvider,

                    openRouterApiKey = settings.openRouterApiKey,
                    openAiApiKey = settings.openAiApiKey,
                    anthropicApiKey = settings.anthropicApiKey,
                    geminiApiKey = settings.geminiApiKey,
                    groqApiKey = settings.groqApiKey,
                    mistralApiKey = settings.mistralApiKey,
                    deepSeekApiKey = settings.deepSeekApiKey,
                    xAiApiKey = settings.xAiApiKey,
                    cohereApiKey = settings.cohereApiKey,
                    perplexityApiKey = settings.perplexityApiKey,
                    togetherApiKey = settings.togetherApiKey,
                    fireworksApiKey = settings.fireworksApiKey,
                    moonshotApiKey = settings.moonshotApiKey,
                    zaiApiKey = settings.zaiApiKey,
                    nvidiaNimApiKey = settings.nvidiaNimApiKey,
                    huggingFaceApiKey = settings.huggingFaceApiKey,

                    openRouterModel = settings.openRouterModel,
                    openAiModel = settings.openAiModel,
                    anthropicModel = settings.anthropicModel,
                    geminiModel = settings.geminiModel,
                    groqModel = settings.groqModel,
                    mistralModel = settings.mistralModel,
                    deepSeekModel = settings.deepSeekModel,
                    xAiModel = settings.xAiModel,
                    cohereModel = settings.cohereModel,
                    perplexityModel = settings.perplexityModel,
                    togetherModel = settings.togetherModel,
                    fireworksModel = settings.fireworksModel,
                    moonshotModel = settings.moonshotModel,
                    zaiModel = settings.zaiModel,
                    nvidiaNimModel = settings.nvidiaNimModel,
                    huggingFaceModel = settings.huggingFaceModel,

                    isLoaded = true
                )
            }
        }
    }

    fun updateThemeMode(value: AppThemeMode) {
        _uiState.value = _uiState.value.copy(
            themeMode = value,
            savedMessage = ""
        )
    }

    fun saveProviderConfiguration(
        provider: ApiProvider,
        model: String,
        apiKey: String,
        activate: Boolean
    ) {
        val safeKey = apiKey.trim()

        if (safeKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                savedMessage = "Add a valid API key before saving."
            )
            return
        }

        viewModelScope.launch {
            val updated = applyProviderConfig(
                state = _uiState.value,
                provider = provider,
                apiKey = safeKey,
                model = model
            )

            val activeProvider = if (activate) {
                provider
            } else {
                updated.selectedProvider
            }

            saveState(
                state = updated,
                selectedProvider = activeProvider
            )

            _uiState.value = updated.copy(
                selectedProvider = activeProvider,
                model = updated.modelFor(activeProvider),
                savedMessage = if (activate) {
                    "${provider.displayName} saved and activated"
                } else {
                    "${provider.displayName} key saved"
                }
            )
        }
    }

    fun activateProvider(provider: ApiProvider) {
        val current = _uiState.value

        if (current.keyFor(provider).isBlank()) {
            _uiState.value = current.copy(
                savedMessage = "No API key saved for ${provider.displayName}."
            )
            return
        }

        viewModelScope.launch {
            saveState(
                state = current,
                selectedProvider = provider
            )

            _uiState.value = current.copy(
                selectedProvider = provider,
                model = current.modelFor(provider),
                savedMessage = "${provider.displayName} activated"
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val current = _uiState.value

            saveState(
                state = current,
                selectedProvider = current.selectedProvider
            )

            _uiState.value = current.copy(
                savedMessage = "Settings saved"
            )
        }
    }

    fun clear() {
        viewModelScope.launch {
            store.clearSettings()

            _uiState.value = SettingsUiState(
                savedMessage = "Settings cleared",
                isLoaded = true
            )
        }
    }

    private suspend fun saveState(
        state: SettingsUiState,
        selectedProvider: ApiProvider
    ) {
        store.saveAllSettings(
            selectedProvider = selectedProvider,
            themeMode = state.themeMode,

            openRouterApiKey = state.openRouterApiKey,
            openAiApiKey = state.openAiApiKey,
            anthropicApiKey = state.anthropicApiKey,
            geminiApiKey = state.geminiApiKey,
            groqApiKey = state.groqApiKey,
            mistralApiKey = state.mistralApiKey,
            deepSeekApiKey = state.deepSeekApiKey,
            xAiApiKey = state.xAiApiKey,
            cohereApiKey = state.cohereApiKey,
            perplexityApiKey = state.perplexityApiKey,
            togetherApiKey = state.togetherApiKey,
            fireworksApiKey = state.fireworksApiKey,
            moonshotApiKey = state.moonshotApiKey,
            zaiApiKey = state.zaiApiKey,
            nvidiaNimApiKey = state.nvidiaNimApiKey,
            huggingFaceApiKey = state.huggingFaceApiKey,

            openRouterModel = state.openRouterModel,
            openAiModel = state.openAiModel,
            anthropicModel = state.anthropicModel,
            geminiModel = state.geminiModel,
            groqModel = state.groqModel,
            mistralModel = state.mistralModel,
            deepSeekModel = state.deepSeekModel,
            xAiModel = state.xAiModel,
            cohereModel = state.cohereModel,
            perplexityModel = state.perplexityModel,
            togetherModel = state.togetherModel,
            fireworksModel = state.fireworksModel,
            moonshotModel = state.moonshotModel,
            zaiModel = state.zaiModel,
            nvidiaNimModel = state.nvidiaNimModel,
            huggingFaceModel = state.huggingFaceModel
        )
    }

    private fun applyProviderConfig(
        state: SettingsUiState,
        provider: ApiProvider,
        apiKey: String,
        model: String
    ): SettingsUiState {
        val safeModel = AiModels.byIdOrNull(model)
            ?.takeIf { it.apiProvider == provider }
            ?.id
            ?: AiModels.defaultForProvider(provider).id

        return when (provider) {
            ApiProvider.OpenRouter -> state.copy(
                apiKey = apiKey,
                openRouterApiKey = apiKey,
                openRouterModel = safeModel
            )

            ApiProvider.OpenAI -> state.copy(
                openAiApiKey = apiKey,
                openAiModel = safeModel
            )

            ApiProvider.Anthropic -> state.copy(
                anthropicApiKey = apiKey,
                anthropicModel = safeModel
            )

            ApiProvider.Gemini -> state.copy(
                geminiApiKey = apiKey,
                geminiModel = safeModel
            )

            ApiProvider.Groq -> state.copy(
                groqApiKey = apiKey,
                groqModel = safeModel
            )

            ApiProvider.Mistral -> state.copy(
                mistralApiKey = apiKey,
                mistralModel = safeModel
            )

            ApiProvider.DeepSeek -> state.copy(
                deepSeekApiKey = apiKey,
                deepSeekModel = safeModel
            )

            ApiProvider.XAI -> state.copy(
                xAiApiKey = apiKey,
                xAiModel = safeModel
            )

            ApiProvider.Cohere -> state.copy(
                cohereApiKey = apiKey,
                cohereModel = safeModel
            )

            ApiProvider.Perplexity -> state.copy(
                perplexityApiKey = apiKey,
                perplexityModel = safeModel
            )

            ApiProvider.Together -> state.copy(
                togetherApiKey = apiKey,
                togetherModel = safeModel
            )

            ApiProvider.Fireworks -> state.copy(
                fireworksApiKey = apiKey,
                fireworksModel = safeModel
            )

            ApiProvider.Moonshot -> state.copy(
                moonshotApiKey = apiKey,
                moonshotModel = safeModel
            )

            ApiProvider.ZAI -> state.copy(
                zaiApiKey = apiKey,
                zaiModel = safeModel
            )

            ApiProvider.NvidiaNim -> state.copy(
                nvidiaNimApiKey = apiKey,
                nvidiaNimModel = safeModel
            )

            ApiProvider.HuggingFace -> state.copy(
                huggingFaceApiKey = apiKey,
                huggingFaceModel = safeModel
            )
        }
    }
}
