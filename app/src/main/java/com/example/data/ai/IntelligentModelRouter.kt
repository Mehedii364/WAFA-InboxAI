package com.example.data.ai

import com.example.core.security.SecretVault
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.AiProviderDao
import com.example.data.local.AiUsageLogEntity
import com.example.data.local.RoutingRuleEntity
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Centralized Intelligent Model Router:
 * - Resolves primary & fallback providers per AI task type
 * - Enforces daily token/request limits and paid-fallback protection
 * - Retries transient failures with capped exponential backoff (no infinite loops)
 * - Logs real latency, token counts, actual provider/model used, and cost (when pricing data exists)
 */
class IntelligentModelRouter(
    private val providerDao: AiProviderDao,
    private val vault: SecretVault,
    private val registry: AiProviderRegistry,
    private val backoffDelayMs: suspend (Long) -> Unit = { delay(it) }
) {

    suspend fun routeAndExecute(
        userId: String,
        taskType: AiTaskType,
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean = false,
        temperature: Double = 0.2,
        maxTokens: Int = 1200,
        globalAllowPaidFallback: Boolean = false,
        overrideProvider: AiProviderType? = null,
        overrideModel: String? = null
    ): AiResult<AiResponse> {
        val configs = providerDao.getProviders(userId)
        val enabledConfigs = configs.filter { it.enabled && it.encryptedApiKey.isNotBlank() }
        if (enabledConfigs.isEmpty()) {
            return AiResult.Failure(
                AiProviderError(
                    code = AiErrorCode.MISSING_API_KEY,
                    message = "No active AI provider configured. Add and enable an API key in API Provider Manager.",
                    providerType = null
                )
            )
        }

        val taskRule = providerDao.getRoutingRule(userId, taskType.name)
            ?: providerDao.getRoutingRule(userId, AiTaskType.DEFAULT.name)

        val candidates = buildCandidateChain(
            enabledConfigs = enabledConfigs,
            rule = taskRule,
            overrideProvider = overrideProvider,
            overrideModel = overrideModel
        )

        if (candidates.isEmpty()) {
            return AiResult.Failure(
                AiProviderError(
                    code = AiErrorCode.INVALID_CONFIG,
                    message = "No enabled AI provider has a selected model. Open API Provider Manager, fetch models, and pick a default model.",
                    providerType = null
                )
            )
        }

        val maxRetries = (taskRule?.maxRetries ?: 2).coerceIn(0, 3)
        val timeoutSec = (taskRule?.timeoutSeconds ?: 45).coerceIn(10, 120)
        val allowPaidFallback = (taskRule?.allowPaidFallback ?: false) || globalAllowPaidFallback

        var lastError: AiProviderError? = null

        for ((index, candidate) in candidates.withIndex()) {
            val isFallback = index > 0
            val config = candidate.config
            val providerType = AiProviderType.valueOf(config.providerType)

            // Guard: Never automatically fall back to a paid provider without user approval.
            if (isFallback && config.isPaidProvider && !allowPaidFallback) {
                lastError = AiProviderError(
                    code = AiErrorCode.PAID_FALLBACK_BLOCKED,
                    message = "Automatic fallback to ${providerType.displayName} was blocked because paid fallback is disabled.",
                    providerType = providerType
                )
                logUsage(userId, providerType, candidate.modelId, taskType, AiUsage(), 0L, null, false, lastError, true)
                continue
            }

            // Guard: Check per-provider daily limits before dispatching.
            val dayStart = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
            val reqToday = providerDao.countRequestsSince(userId, config.providerType, dayStart)
            val tokensToday = providerDao.sumTokensSince(userId, config.providerType, dayStart)
            if (config.dailyRequestLimit > 0 && reqToday >= config.dailyRequestLimit) {
                lastError = AiProviderError(
                    code = AiErrorCode.USAGE_LIMIT_EXCEEDED,
                    message = "${providerType.displayName} reached its configured daily request limit (${config.dailyRequestLimit}).",
                    providerType = providerType
                )
                continue
            }
            if (config.dailyTokenLimit > 0 && tokensToday >= config.dailyTokenLimit) {
                lastError = AiProviderError(
                    code = AiErrorCode.USAGE_LIMIT_EXCEEDED,
                    message = "${providerType.displayName} reached its configured daily token limit (${config.dailyTokenLimit}).",
                    providerType = providerType
                )
                continue
            }

            val decryptedKey = runCatching { vault.decrypt(config.encryptedApiKey) }.getOrElse {
                lastError = AiProviderError(
                    code = AiErrorCode.MISSING_API_KEY,
                    message = "Could not decrypt API key for ${providerType.displayName}. Please re-enter the key.",
                    providerType = providerType
                )
                continue
            }

            val credentials = ProviderCredentials(
                providerType = providerType,
                apiKey = decryptedKey,
                baseUrl = config.baseUrl,
                accountId = config.accountId,
                authHeaderMode = config.authHeaderMode
            )
            val request = AiRequest(
                model = candidate.modelId,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                expectJson = expectJson,
                temperature = temperature,
                maxTokens = maxTokens,
                timeoutSeconds = timeoutSec
            )

            val adapter = registry.get(providerType)
            var attempt = 0
            while (attempt <= maxRetries) {
                val attemptStart = System.currentTimeMillis()
                val result = if (expectJson) {
                    adapter.generateStructuredOutput(credentials, request)
                } else {
                    adapter.generateText(credentials, request)
                }
                when (result) {
                    is AiResult.Success -> {
                        val response = result.data.copy(fallbackUsed = isFallback)
                        val costUsd = calculateEstimatedCost(
                            userId = userId,
                            providerType = config.providerType,
                            modelId = response.modelUsed,
                            usage = response.usage
                        )
                        logUsage(
                            userId = userId,
                            providerType = providerType,
                            modelId = response.modelUsed,
                            taskType = taskType,
                            usage = response.usage,
                            latencyMs = response.latencyMs,
                            estimatedCostUsd = costUsd,
                            success = true,
                            error = null,
                            fallbackUsed = isFallback
                        )
                        return AiResult.Success(response)
                    }
                    is AiResult.Failure -> {
                        lastError = result.error
                        val elapsed = System.currentTimeMillis() - attemptStart
                        logUsage(
                            userId = userId,
                            providerType = providerType,
                            modelId = candidate.modelId,
                            taskType = taskType,
                            usage = AiUsage(),
                            latencyMs = elapsed,
                            estimatedCostUsd = null,
                            success = false,
                            error = result.error,
                            fallbackUsed = isFallback
                        )
                        val canRetry = result.error.code.retryable && attempt < maxRetries
                        if (canRetry) {
                            attempt++
                            backoffDelayMs(350L * attempt)
                        } else {
                            break
                        }
                    }
                }
            }
        }

        return AiResult.Failure(
            lastError ?: AiProviderError(
                code = AiErrorCode.UNKNOWN,
                message = "All configured AI providers failed to complete the request.",
                providerType = null
            )
        )
    }

    private data class Candidate(
        val config: AiProviderConfigEntity,
        val modelId: String
    )

    private fun buildCandidateChain(
        enabledConfigs: List<AiProviderConfigEntity>,
        rule: RoutingRuleEntity?,
        overrideProvider: AiProviderType?,
        overrideModel: String?
    ): List<Candidate> {
        val byType = enabledConfigs.associateBy { it.providerType }
        val ordered = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()

        fun addIfReady(providerTypeName: String?, preferredModel: String?) {
            if (providerTypeName.isNullOrBlank()) return
            val cfg = byType[providerTypeName] ?: return
            val model = preferredModel?.takeIf { it.isNotBlank() } ?: cfg.defaultModel
            if (model.isBlank()) return
            val dedupeKey = "$providerTypeName:$model"
            if (seen.add(dedupeKey)) {
                ordered += Candidate(cfg, model)
            }
        }

        if (overrideProvider != null) {
            addIfReady(overrideProvider.name, overrideModel)
        }
        if (rule != null) {
            addIfReady(rule.primaryProvider, rule.primaryModel)
            addIfReady(rule.fallbackProvider, rule.fallbackModel)
        }
        for (cfg in enabledConfigs) {
            addIfReady(cfg.providerType, cfg.defaultModel)
        }
        return ordered
    }

    private suspend fun calculateEstimatedCost(
        userId: String,
        providerType: String,
        modelId: String,
        usage: AiUsage
    ): Double? {
        val model = providerDao.getModelsForProvider(userId, providerType)
            .firstOrNull { it.modelId == modelId } ?: return null
        val pCost = model.promptCostPer1k ?: return null
        val cCost = model.completionCostPer1k ?: return null
        return (usage.promptTokens / 1000.0) * pCost + (usage.completionTokens / 1000.0) * cCost
    }

    private suspend fun logUsage(
        userId: String,
        providerType: AiProviderType,
        modelId: String,
        taskType: AiTaskType,
        usage: AiUsage,
        latencyMs: Long,
        estimatedCostUsd: Double?,
        success: Boolean,
        error: AiProviderError?,
        fallbackUsed: Boolean
    ) {
        providerDao.insertUsageLog(
            AiUsageLogEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                providerType = providerType.name,
                modelId = modelId,
                taskType = taskType.name,
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                totalTokens = usage.totalTokens,
                latencyMs = latencyMs,
                estimatedCostUsd = estimatedCostUsd,
                success = success,
                errorCode = error?.code?.name ?: "",
                errorMessage = error?.message ?: "",
                fallbackUsed = fallbackUsed
            )
        )
    }
}
