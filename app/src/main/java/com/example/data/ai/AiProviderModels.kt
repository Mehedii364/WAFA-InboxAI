package com.example.data.ai

enum class AiProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultAuthMode: String,
    val requiresAccountId: Boolean = false,
    val notes: String
) {
    GEMINI(
        displayName = "Google Gemini API",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultAuthMode = "X_GOOG_API_KEY",
        notes = "Native Gemini generateContent API with multimodal & JSON support."
    ),
    GROQ(
        displayName = "Groq API",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultAuthMode = "BEARER",
        notes = "Ultra-low-latency OpenAI-compatible inference for fast triage & classification."
    ),
    OPENROUTER(
        displayName = "OpenRouter API",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultAuthMode = "BEARER",
        notes = "Unified access to frontier models with model pricing metadata."
    ),
    HUGGINGFACE(
        displayName = "Hugging Face Inference Providers",
        defaultBaseUrl = "https://router.huggingface.co/v1",
        defaultAuthMode = "BEARER",
        notes = "Open-source inference router with adjustable endpoint."
    ),
    CLOUDFLARE(
        displayName = "Cloudflare Workers AI",
        defaultBaseUrl = "https://api.cloudflare.com/client/v4",
        defaultAuthMode = "BEARER",
        requiresAccountId = true,
        notes = "Requires both Cloudflare Account ID and Workers AI API Token."
    ),
    XPIKI(
        displayName = "XPIKI API",
        defaultBaseUrl = "https://api.xpiki.com/v1",
        defaultAuthMode = "BEARER",
        notes = "OpenAI-compatible /models and /chat/completions endpoint; verify live compatibility via Test Connection."
    )
}

enum class AiTaskType(val label: String) {
    DEFAULT("Default General"),
    FAST("Fast Triage"),
    REASONING("Deep Reasoning"),
    DOCUMENT("Document & Attachment"),
    CLASSIFICATION("Email Classification"),
    SUMMARIZATION("Email Summarization"),
    DRAFTING("Professional Drafting"),
    SECURITY("Security & Phishing"),
    RESEARCH("Ask Your Inbox"),
    FOLLOWUP("Follow-up Detection")
}

data class ProviderCredentials(
    val providerType: AiProviderType,
    val apiKey: String,
    val baseUrl: String,
    val accountId: String = "",
    val authHeaderMode: String = providerType.defaultAuthMode
)

data class AiRequest(
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val expectJson: Boolean = false,
    val temperature: Double = 0.2,
    val maxTokens: Int = 1200,
    val timeoutSeconds: Int = 45
)

data class AiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = promptTokens + completionTokens
)

data class AiResponse(
    val text: String,
    val providerUsed: AiProviderType,
    val modelUsed: String,
    val usage: AiUsage,
    val latencyMs: Long,
    val fallbackUsed: Boolean = false
)

data class DiscoveredModel(
    val modelId: String,
    val displayName: String,
    val supportsJson: Boolean = true,
    val supportsVision: Boolean = false,
    val contextWindow: Int = 0,
    val promptCostPer1k: Double? = null,
    val completionCostPer1k: Double? = null
)

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val discoveredModelCount: Int,
    val message: String,
    val errorCode: String = ""
)

enum class AiErrorCode(val retryable: Boolean) {
    MISSING_API_KEY(false),
    INVALID_CONFIG(false),
    UNAUTHORIZED(false),
    FORBIDDEN(false),
    INSUFFICIENT_CREDITS(false),
    RATE_LIMITED(true),
    TIMEOUT(true),
    SERVER_ERROR(true),
    NETWORK_ERROR(true),
    USAGE_LIMIT_EXCEEDED(false),
    PAID_FALLBACK_BLOCKED(false),
    INVALID_RESPONSE(false),
    UNKNOWN(false)
}

data class AiProviderError(
    val code: AiErrorCode,
    val message: String,
    val providerType: AiProviderType?,
    val httpStatus: Int? = null
)

sealed class AiResult<out T> {
    data class Success<T>(val data: T) : AiResult<T>()
    data class Failure(val error: AiProviderError) : AiResult<Nothing>()
}

interface AIProviderInterface {
    val providerType: AiProviderType
    fun authenticate(credentials: ProviderCredentials): Result<Map<String, String>>
    suspend fun listModels(credentials: ProviderCredentials): AiResult<List<DiscoveredModel>>
    suspend fun testConnection(credentials: ProviderCredentials): ConnectionTestResult
    suspend fun generateText(credentials: ProviderCredentials, request: AiRequest): AiResult<AiResponse>
    suspend fun generateStructuredOutput(credentials: ProviderCredentials, request: AiRequest): AiResult<AiResponse>
    fun getUsage(rawBody: String): AiUsage
    fun handleError(httpStatus: Int?, rawBody: String?, throwable: Throwable?): AiProviderError
}
