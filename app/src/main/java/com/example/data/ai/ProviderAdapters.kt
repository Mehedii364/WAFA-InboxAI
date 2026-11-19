package com.example.data.ai

import com.example.core.security.KeyMasker
import com.example.core.security.NetworkSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

abstract class BaseHttpAiProvider(
    private val baseHttpClient: OkHttpClient
) : AIProviderInterface {

    protected fun clientWithTimeout(timeoutSeconds: Int): OkHttpClient {
        val clamped = timeoutSeconds.coerceIn(5, 120).toLong()
        return baseHttpClient.newBuilder()
            .connectTimeout(clamped.coerceAtMost(20), TimeUnit.SECONDS)
            .readTimeout(clamped, TimeUnit.SECONDS)
            .writeTimeout(clamped.coerceAtMost(30), TimeUnit.SECONDS)
            .build()
    }

    protected fun validateBaseUrl(baseUrl: String): Result<String> {
        return NetworkSecurity.validateHttpsUrl(baseUrl)
    }

    override suspend fun testConnection(credentials: ProviderCredentials): ConnectionTestResult {
        val start = System.currentTimeMillis()
        val auth = authenticate(credentials)
        if (auth.isFailure) {
            return ConnectionTestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - start,
                discoveredModelCount = 0,
                message = auth.exceptionOrNull()?.message ?: "Invalid credentials",
                errorCode = AiErrorCode.MISSING_API_KEY.name
            )
        }
        return when (val result = listModels(credentials)) {
            is AiResult.Success -> ConnectionTestResult(
                success = true,
                latencyMs = System.currentTimeMillis() - start,
                discoveredModelCount = result.data.size,
                message = "Connected to ${providerType.displayName} (${result.data.size} models discovered)."
            )
            is AiResult.Failure -> ConnectionTestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - start,
                discoveredModelCount = 0,
                message = result.error.message,
                errorCode = result.error.code.name
            )
        }
    }

    override suspend fun generateStructuredOutput(
        credentials: ProviderCredentials,
        request: AiRequest
    ): AiResult<AiResponse> = generateText(credentials, request.copy(expectJson = true))

    override fun handleError(httpStatus: Int?, rawBody: String?, throwable: Throwable?): AiProviderError {
        if (throwable is SocketTimeoutException) {
            return AiProviderError(
                code = AiErrorCode.TIMEOUT,
                message = "${providerType.displayName} timed out waiting for response.",
                providerType = providerType
            )
        }
        if (throwable is IOException) {
            return AiProviderError(
                code = AiErrorCode.NETWORK_ERROR,
                message = "Network error communicating with ${providerType.displayName}: ${throwable.message ?: "unreachable"}",
                providerType = providerType
            )
        }
        val snippet = extractErrorMessage(rawBody)
        val code = when (httpStatus) {
            401 -> AiErrorCode.UNAUTHORIZED
            402 -> AiErrorCode.INSUFFICIENT_CREDITS
            403 -> AiErrorCode.FORBIDDEN
            408 -> AiErrorCode.TIMEOUT
            429 -> AiErrorCode.RATE_LIMITED
            in 500..599 -> AiErrorCode.SERVER_ERROR
            else -> {
                val lower = snippet.lowercase()
                when {
                    lower.contains("credit") || lower.contains("billing") || lower.contains("quota") ->
                        AiErrorCode.INSUFFICIENT_CREDITS
                    lower.contains("rate") -> AiErrorCode.RATE_LIMITED
                    else -> AiErrorCode.UNKNOWN
                }
            }
        }
        val safeMsg = KeyMasker.redactSecrets(
            if (snippet.isNotBlank()) "${providerType.displayName} (HTTP ${httpStatus ?: "?"}): $snippet"
            else "${providerType.displayName} failed (HTTP ${httpStatus ?: "?"})."
        )
        return AiProviderError(code = code, message = safeMsg, providerType = providerType, httpStatus = httpStatus)
    }

    private fun extractErrorMessage(rawBody: String?): String {
        if (rawBody.isNullOrBlank()) return ""
        return runCatching {
            val obj = JSONObject(rawBody)
            when {
                obj.has("error") && obj.get("error") is JSONObject ->
                    obj.getJSONObject("error").optString("message", rawBody.take(240))
                obj.has("error") -> obj.optString("error", rawBody.take(240))
                obj.has("errors") -> obj.optJSONArray("errors")?.optJSONObject(0)?.optString("message", "") ?: ""
                obj.has("message") -> obj.optString("message", rawBody.take(240))
                else -> rawBody.take(240)
            }
        }.getOrElse { rawBody.take(240) }
    }
}

/**
 * 1. Google Gemini API Provider (native v1beta generateContent & models discovery)
 */
class GeminiProvider(httpClient: OkHttpClient) : BaseHttpAiProvider(httpClient) {
    override val providerType = AiProviderType.GEMINI

    override fun authenticate(credentials: ProviderCredentials): Result<Map<String, String>> {
        val key = credentials.apiKey.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Gemini API key is required."))
        validateBaseUrl(credentials.baseUrl).getOrElse { return Result.failure(it) }
        return Result.success(mapOf("x-goog-api-key" to key))
    }

    override suspend fun listModels(credentials: ProviderCredentials): AiResult<List<DiscoveredModel>> =
        withContext(Dispatchers.IO) {
            val headers = authenticate(credentials).getOrElse {
                return@withContext AiResult.Failure(
                    AiProviderError(AiErrorCode.MISSING_API_KEY, it.message ?: "Missing key", providerType)
                )
            }
            val baseUrl = credentials.baseUrl.trimEnd('/')
            val reqBuilder = Request.Builder().url("$baseUrl/models").get()
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            try {
                clientWithTimeout(20).newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext AiResult.Failure(handleError(resp.code, body, null))
                    }
                    val root = JSONObject(body)
                    val arr = root.optJSONArray("models") ?: JSONArray()
                    val models = mutableListOf<DiscoveredModel>()
                    for (i in 0 until arr.length()) {
                        val m = arr.optJSONObject(i) ?: continue
                        val methods = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                        val supportsGen = (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                        if (!supportsGen) continue
                        val rawName = m.optString("name", "").removePrefix("models/")
                        if (rawName.isBlank()) continue
                        models += DiscoveredModel(
                            modelId = rawName,
                            displayName = m.optString("displayName", rawName),
                            supportsJson = true,
                            supportsVision = rawName.contains("gemini", ignoreCase = true),
                            contextWindow = m.optInt("inputTokenLimit", 0)
                        )
                    }
                    AiResult.Success(models)
                }
            } catch (t: Throwable) {
                AiResult.Failure(handleError(null, null, t))
            }
        }

    override suspend fun generateText(
        credentials: ProviderCredentials,
        request: AiRequest
    ): AiResult<AiResponse> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val headers = authenticate(credentials).getOrElse {
            return@withContext AiResult.Failure(
                AiProviderError(AiErrorCode.MISSING_API_KEY, it.message ?: "Missing key", providerType)
            )
        }
        val modelId = request.model.trim().removePrefix("models/")
        if (modelId.isEmpty()) {
            return@withContext AiResult.Failure(
                AiProviderError(AiErrorCode.INVALID_CONFIG, "No Gemini model selected. Fetch & choose a model first.", providerType)
            )
        }
        val baseUrl = credentials.baseUrl.trimEnd('/')
        val payload = JSONObject().apply {
            if (request.systemPrompt.isNotBlank()) {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.systemPrompt))))
            }
            put("contents", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", request.userPrompt)))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
                if (request.expectJson) put("responseMimeType", "application/json")
            })
        }

        val reqBuilder = Request.Builder()
            .url("$baseUrl/models/$modelId:generateContent")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        try {
            clientWithTimeout(request.timeoutSeconds).newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext AiResult.Failure(handleError(resp.code, body, null))
                }
                val root = JSONObject(body)
                val candidates = root.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val text = buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
                        }
                    }
                }.trim()
                if (text.isEmpty()) {
                    return@withContext AiResult.Failure(
                        AiProviderError(AiErrorCode.INVALID_RESPONSE, "Gemini returned an empty response.", providerType)
                    )
                }
                AiResult.Success(
                    AiResponse(
                        text = text,
                        providerUsed = providerType,
                        modelUsed = modelId,
                        usage = getUsage(body),
                        latencyMs = System.currentTimeMillis() - start
                    )
                )
            }
        } catch (t: Throwable) {
            AiResult.Failure(handleError(null, null, t))
        }
    }

    override fun getUsage(rawBody: String): AiUsage = runCatching {
        val meta = JSONObject(rawBody).optJSONObject("usageMetadata") ?: return AiUsage()
        val prompt = meta.optInt("promptTokenCount", 0)
        val completion = meta.optInt("candidatesTokenCount", 0)
        val total = meta.optInt("totalTokenCount", prompt + completion)
        AiUsage(prompt, completion, total)
    }.getOrDefault(AiUsage())
}

/**
 * Shared OpenAI-compatible base with per-provider header, endpoint, and pricing customization
 * used by Groq, OpenRouter, Hugging Face Inference Providers, and XPIKI.
 */
abstract class OpenAiCompatibleProvider(
    httpClient: OkHttpClient,
    override val providerType: AiProviderType
) : BaseHttpAiProvider(httpClient) {

    protected open fun extraHeaders(credentials: ProviderCredentials): Map<String, String> = emptyMap()

    protected open fun modelsEndpoint(baseUrl: String): String = "${baseUrl.trimEnd('/')}/models"

    protected open fun chatEndpoint(baseUrl: String): String = "${baseUrl.trimEnd('/')}/chat/completions"

    override fun authenticate(credentials: ProviderCredentials): Result<Map<String, String>> {
        val key = credentials.apiKey.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("${providerType.displayName} API key is required."))
        validateBaseUrl(credentials.baseUrl).getOrElse { return Result.failure(it) }
        val map = mutableMapOf("Authorization" to "Bearer $key")
        map.putAll(extraHeaders(credentials))
        return Result.success(map)
    }

    override suspend fun listModels(credentials: ProviderCredentials): AiResult<List<DiscoveredModel>> =
        withContext(Dispatchers.IO) {
            val headers = authenticate(credentials).getOrElse {
                return@withContext AiResult.Failure(
                    AiProviderError(AiErrorCode.MISSING_API_KEY, it.message ?: "Missing key", providerType)
                )
            }
            val reqBuilder = Request.Builder().url(modelsEndpoint(credentials.baseUrl)).get()
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            try {
                clientWithTimeout(20).newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext AiResult.Failure(handleError(resp.code, body, null))
                    }
                    AiResult.Success(parseModelsArray(body))
                }
            } catch (t: Throwable) {
                AiResult.Failure(handleError(null, null, t))
            }
        }

    protected open fun parseModelsArray(body: String): List<DiscoveredModel> {
        val root = JSONObject(body)
        val arr = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
        val result = mutableListOf<DiscoveredModel>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id", "").ifBlank { item.optString("name", "") }
            if (id.isBlank()) continue
            val pricing = item.optJSONObject("pricing")
            val promptPer1k = pricing?.optString("prompt", "")?.toDoubleOrNull()?.times(1000.0)
            val compPer1k = pricing?.optString("completion", "")?.toDoubleOrNull()?.times(1000.0)
            val contextLen = item.optInt("context_length", item.optInt("context_window", 0))
            val name = item.optString("name", id)
            val vision = id.contains("vision", true) || id.contains("vl", true) ||
                id.contains("4o", true) || id.contains("gemini", true) || id.contains("claude-3", true)
            result += DiscoveredModel(
                modelId = id,
                displayName = name,
                supportsJson = true,
                supportsVision = vision,
                contextWindow = contextLen,
                promptCostPer1k = promptPer1k,
                completionCostPer1k = compPer1k
            )
        }
        return result
    }

    override suspend fun generateText(
        credentials: ProviderCredentials,
        request: AiRequest
    ): AiResult<AiResponse> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val headers = authenticate(credentials).getOrElse {
            return@withContext AiResult.Failure(
                AiProviderError(AiErrorCode.MISSING_API_KEY, it.message ?: "Missing key", providerType)
            )
        }
        val modelId = request.model.trim()
        if (modelId.isEmpty()) {
            return@withContext AiResult.Failure(
                AiProviderError(AiErrorCode.INVALID_CONFIG, "No ${providerType.displayName} model selected.", providerType)
            )
        }

        val messages = JSONArray().apply {
            if (request.systemPrompt.isNotBlank()) {
                put(JSONObject().put("role", "system").put("content", request.systemPrompt))
            }
            put(JSONObject().put("role", "user").put("content", request.userPrompt))
        }
        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messages)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            if (request.expectJson && providerType != AiProviderType.HUGGINGFACE) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }

        val reqBuilder = Request.Builder()
            .url(chatEndpoint(credentials.baseUrl))
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        try {
            clientWithTimeout(request.timeoutSeconds).newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext AiResult.Failure(handleError(resp.code, body, null))
                }
                val root = JSONObject(body)
                val choices = root.optJSONArray("choices")
                val text = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) {
                    return@withContext AiResult.Failure(
                        AiProviderError(AiErrorCode.INVALID_RESPONSE, "${providerType.displayName} returned empty message content.", providerType)
                    )
                }
                AiResult.Success(
                    AiResponse(
                        text = text,
                        providerUsed = providerType,
                        modelUsed = root.optString("model", modelId).ifBlank { modelId },
                        usage = getUsage(body),
                        latencyMs = System.currentTimeMillis() - start
                    )
                )
            }
        } catch (t: Throwable) {
            AiResult.Failure(handleError(null, null, t))
        }
    }

    override fun getUsage(rawBody: String): AiUsage = runCatching {
        val usage = JSONObject(rawBody).optJSONObject("usage") ?: return AiUsage()
        val prompt = usage.optInt("prompt_tokens", 0)
        val completion = usage.optInt("completion_tokens", 0)
        val total = usage.optInt("total_tokens", prompt + completion)
        AiUsage(prompt, completion, total)
    }.getOrDefault(AiUsage())
}

/** 2. Groq API Provider */
class GroqProvider(httpClient: OkHttpClient) : OpenAiCompatibleProvider(httpClient, AiProviderType.GROQ)

/** 3. OpenRouter API Provider */
class OpenRouterProvider(httpClient: OkHttpClient) : OpenAiCompatibleProvider(httpClient, AiProviderType.OPENROUTER) {
    override fun extraHeaders(credentials: ProviderCredentials): Map<String, String> = mapOf(
        "HTTP-Referer" to "https://wafa-inboxai.app",
        "X-Title" to "WAFA InboxAI"
    )
}

/** 4. Hugging Face Inference Providers Adapter */
class HuggingFaceProvider(httpClient: OkHttpClient) : OpenAiCompatibleProvider(httpClient, AiProviderType.HUGGINGFACE)

/** 6. XPIKI API Provider (adjustable endpoint & bearer auth) */
class XpikiProvider(httpClient: OkHttpClient) : OpenAiCompatibleProvider(httpClient, AiProviderType.XPIKI)

/**
 * 5. Cloudflare Workers AI Provider (native /client/v4/accounts/{accountId}/ai endpoints)
 */
class CloudflareProvider(httpClient: OkHttpClient) : BaseHttpAiProvider(httpClient) {
    override val providerType = AiProviderType.CLOUDFLARE

    override fun authenticate(credentials: ProviderCredentials): Result<Map<String, String>> {
        val key = credentials.apiKey.trim()
        val accountId = credentials.accountId.trim()
        if (key.isEmpty()) return Result.failure(IllegalArgumentException("Cloudflare API Token is required."))
        if (accountId.isEmpty()) return Result.failure(IllegalArgumentException("Cloudflare Account ID is required."))
        validateBaseUrl(credentials.baseUrl).getOrElse { return Result.failure(it) }
        return Result.success(mapOf("Authorization" to "Bearer $key"))
    }

    override suspend fun listModels(credentials: ProviderCredentials): AiResult<List<DiscoveredModel>> =
        withContext(Dispatchers.IO) {
            val headers = authenticate(credentials).getOrElse {
                return@withContext AiResult.Failure(
                    AiProviderError(AiErrorCode.MISSING_API_KEY, it.message ?: "Missing config", providerType)
                )
            }
            val baseUrl = credentials.baseUrl.trimEnd('/')
            val url = "$baseUrl/accounts/${credentials.accountId.trim()}/ai/models/search?task=Text+Generation"
            val reqBuilder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            try {
                clientWithTimeout(20).newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext AiResult.Failure(handleError(resp.code, body, null))
                    }
                    val root = JSONObject(body)
                    val arr = root.optJSONArray("result") ?: JSONArray()
                    val models = mutableListOf<DiscoveredModel>()
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val name = item.optString("name", "")
                        if (name.isBlank()) continue
                        models += DiscoveredModel(
                            modelId = name,
                            displayName = name.substringAfterLast('/'),
                            supportsJson = true,
                            supportsVision = name.contains("vision", true)
                        )
                    }
                    AiResult.Success(models)
                }
            } catch (t: Throwable) {
                AiResult.Failure(handleError(null, null, t))
            }
        }

    override suspend fun generateText(
        credentials: ProviderCredentials,
        request: AiRequest
    ): AiResult<AiResponse> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val headers = authenticate(credentials).getOrElse {
            return@withContext AiResult.Failure(
                AiProviderError(AiErrorCode.MISSING_API_KEY, it.message ?: "Missing config", providerType)
            )
        }
        val modelId = request.model.trim()
        if (modelId.isEmpty()) {
            return@withContext AiResult.Failure(
                AiProviderError(AiErrorCode.INVALID_CONFIG, "No Cloudflare Workers AI model selected.", providerType)
            )
        }
        val baseUrl = credentials.baseUrl.trimEnd('/')
        val url = "$baseUrl/accounts/${credentials.accountId.trim()}/ai/run/$modelId"
        val messages = JSONArray().apply {
            if (request.systemPrompt.isNotBlank()) {
                put(JSONObject().put("role", "system").put("content", request.systemPrompt))
            }
            put(JSONObject().put("role", "user").put("content", request.userPrompt))
        }
        val payload = JSONObject()
            .put("messages", messages)
            .put("max_tokens", request.maxTokens)

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        try {
            clientWithTimeout(request.timeoutSeconds).newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext AiResult.Failure(handleError(resp.code, body, null))
                }
                val root = JSONObject(body)
                val resultObj = root.optJSONObject("result")
                val text = resultObj?.optString("response", "")?.trim().orEmpty()
                if (text.isEmpty()) {
                    return@withContext AiResult.Failure(
                        AiProviderError(AiErrorCode.INVALID_RESPONSE, "Cloudflare Workers AI returned empty response.", providerType)
                    )
                }
                AiResult.Success(
                    AiResponse(
                        text = text,
                        providerUsed = providerType,
                        modelUsed = modelId,
                        usage = getUsage(body),
                        latencyMs = System.currentTimeMillis() - start
                    )
                )
            }
        } catch (t: Throwable) {
            AiResult.Failure(handleError(null, null, t))
        }
    }

    override fun getUsage(rawBody: String): AiUsage = runCatching {
        val usage = JSONObject(rawBody).optJSONObject("result")?.optJSONObject("usage") ?: return AiUsage()
        val prompt = usage.optInt("prompt_tokens", 0)
        val completion = usage.optInt("completion_tokens", 0)
        val total = usage.optInt("total_tokens", prompt + completion)
        AiUsage(prompt, completion, total)
    }.getOrDefault(AiUsage())
}

class AiProviderRegistry(httpClient: OkHttpClient) {
    private val providers: Map<AiProviderType, AIProviderInterface> = mapOf(
        AiProviderType.GEMINI to GeminiProvider(httpClient),
        AiProviderType.GROQ to GroqProvider(httpClient),
        AiProviderType.OPENROUTER to OpenRouterProvider(httpClient),
        AiProviderType.HUGGINGFACE to HuggingFaceProvider(httpClient),
        AiProviderType.CLOUDFLARE to CloudflareProvider(httpClient),
        AiProviderType.XPIKI to XpikiProvider(httpClient)
    )

    fun get(type: AiProviderType): AIProviderInterface =
        providers[type] ?: error("Unsupported provider $type")
}
