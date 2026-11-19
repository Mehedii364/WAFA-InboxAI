package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.core.security.KeyMasker
import com.example.core.security.NetworkSecurity
import com.example.core.security.PasswordHasher
import com.example.core.security.PromptSecurityGuard
import com.example.core.security.SecretVault
import com.example.data.ai.AiErrorCode
import com.example.data.ai.AiProviderError
import com.example.data.ai.AiProviderRegistry
import com.example.data.ai.AiProviderType
import com.example.data.ai.AiResult
import com.example.data.ai.AiTaskType
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.IntelligentModelRouter
import com.example.data.ai.ProviderCredentials
import com.example.data.email.AttachmentInspector
import com.example.data.email.BackendHealthResult
import com.example.data.email.GmailApiClient
import com.example.data.email.OAuthEmailProvider
import com.example.data.email.OAuthPkceManager
import com.example.data.email.OutlookGraphClient
import com.example.data.email.RemoteEmailMessage
import com.example.data.email.WafaBackendClient
import com.example.data.jobs.WafaNotificationHelper
import com.example.data.jobs.WafaSyncJobService
import com.example.data.local.*
import com.example.domain.agents.*
import kotlinx.coroutines.flow.first
import java.util.UUID

class WafaRepository(
    private val context: Context,
    val db: WafaDatabase,
    val prefsStore: PreferencesStore,
    val vault: SecretVault,
    val providerRegistry: AiProviderRegistry,
    val modelRouter: IntelligentModelRouter,
    val oauthManager: OAuthPkceManager,
    val gmailClient: GmailApiClient,
    val outlookClient: OutlookGraphClient,
    val backendClient: WafaBackendClient,
    val aiAgents: WafaAiAgents,
    val automationEngine: AutomationEngine
) {
    private val userDao = db.userDao()
    private val accountDao = db.accountDao()
    private val aiDao = db.aiProviderDao()
    private val emailDao = db.emailDao()
    private val workspaceDao = db.workspaceDao()

    // --- Authentication & User Lifecycle ---

    suspend fun registerUser(fullName: String, email: String, password: String): Result<UserEntity> {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanName.length < 2) return Result.failure(IllegalArgumentException("Enter your full name (at least 2 characters)."))
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Enter a valid email address."))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters."))
        }
        if (userDao.getByEmail(cleanEmail) != null) {
            return Result.failure(IllegalStateException("An account with this email already exists. Please sign in."))
        }

        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            fullName = cleanName,
            email = cleanEmail,
            passwordHash = PasswordHasher.hash(password)
        )
        userDao.insert(user)
        initializeDefaultProviderSlots(user.id)
        prefsStore.setActiveUser(user.id)
        logAudit(user.id, "USER_REGISTERED", "USER", user.id, "Created secure local account ($cleanEmail).")
        return Result.success(user)
    }

    suspend fun signInLocal(email: String, password: String): Result<UserEntity> {
        val cleanEmail = email.trim().lowercase()
        val user = userDao.getByEmail(cleanEmail)
            ?: return Result.failure(IllegalArgumentException("Account not found for $cleanEmail."))
        if (!PasswordHasher.verify(password, user.passwordHash)) {
            return Result.failure(SecurityException("Invalid email or password."))
        }
        val updated = user.copy(lastLoginAt = System.currentTimeMillis())
        userDao.update(updated)
        initializeDefaultProviderSlots(user.id)
        prefsStore.setActiveUser(user.id)
        logAudit(user.id, "USER_SIGNED_IN", "USER", user.id, "Authenticated locally.")
        return Result.success(updated)
    }

    suspend fun signInBackend(baseUrl: String, email: String, password: String): Result<UserEntity> {
        val authRes = backendClient.login(baseUrl, email, password).getOrElse { return Result.failure(it) }
        val existing = userDao.getByEmail(authRes.email.lowercase())
        val encryptedToken = vault.encrypt(authRes.sessionToken)
        val user = if (existing != null) {
            existing.copy(
                fullName = authRes.fullName.ifBlank { existing.fullName },
                backendBaseUrl = baseUrl.trimEnd('/'),
                encryptedBackendToken = encryptedToken,
                lastLoginAt = System.currentTimeMillis()
            ).also { userDao.update(it) }
        } else {
            UserEntity(
                id = authRes.userId.ifBlank { UUID.randomUUID().toString() },
                fullName = authRes.fullName,
                email = authRes.email.lowercase(),
                passwordHash = PasswordHasher.hash(password),
                backendBaseUrl = baseUrl.trimEnd('/'),
                encryptedBackendToken = encryptedToken
            ).also { userDao.insert(it) }
        }
        initializeDefaultProviderSlots(user.id)
        prefsStore.setBackendBaseUrl(baseUrl.trimEnd('/'))
        prefsStore.setActiveUser(user.id)
        logAudit(user.id, "BACKEND_SIGNED_IN", "USER", user.id, "Authenticated with HTTPS backend.")
        return Result.success(user)
    }

    suspend fun testBackendConnection(baseUrl: String): BackendHealthResult {
        val result = backendClient.checkHealth(baseUrl)
        if (result.healthy) {
            prefsStore.setBackendBaseUrl(baseUrl.trim().trimEnd('/'))
        }
        return result
    }

    suspend fun signOut(userId: String) {
        if (userId.isNotBlank()) {
            logAudit(userId, "USER_SIGNED_OUT", "USER", userId, "Session closed.")
        }
        prefsStore.clearSession()
    }

    suspend fun deleteAllUserData(userId: String) {
        if (userId.isBlank()) return
        accountDao.deleteAllForUser(userId)
        aiDao.deleteAllProviders(userId)
        aiDao.deleteAllModels(userId)
        aiDao.deleteAllRoutingRules(userId)
        aiDao.deleteAllUsageLogs(userId)
        emailDao.deleteAllForUser(userId)
        emailDao.deleteAllSummaries(userId)
        workspaceDao.deleteAllDrafts(userId)
        workspaceDao.deleteAllReminders(userId)
        workspaceDao.deleteAllRules(userId)
        workspaceDao.deleteAllExecutions(userId)
        workspaceDao.deleteAllAttachments(userId)
        workspaceDao.clearNotifications(userId)
        workspaceDao.deleteAllAuditLogs(userId)
        userDao.deleteUser(userId)
        prefsStore.clearSession()
        prefsStore.setOnboardingCompleted(false)
    }

    private suspend fun initializeDefaultProviderSlots(userId: String) {
        val existing = aiDao.getProviders(userId).associateBy { it.providerType }
        for (provider in AiProviderType.entries) {
            if (!existing.containsKey(provider.name)) {
                aiDao.upsertProvider(
                    AiProviderConfigEntity(
                        id = "$userId:${provider.name}",
                        userId = userId,
                        providerType = provider.name,
                        displayName = provider.displayName,
                        baseUrl = provider.defaultBaseUrl,
                        authHeaderMode = provider.defaultAuthMode,
                        enabled = false,
                        isPaidProvider = provider == AiProviderType.OPENROUTER
                    )
                )
            }
        }
        // Seed configurable routing recommendations (never hardcoded assumptions)
        val recommendations = listOf(
            RoutingRuleEntity("$userId:${AiTaskType.DEFAULT.name}", userId, AiTaskType.DEFAULT.name, AiProviderType.GEMINI.name, "", AiProviderType.GROQ.name, ""),
            RoutingRuleEntity("$userId:${AiTaskType.CLASSIFICATION.name}", userId, AiTaskType.CLASSIFICATION.name, AiProviderType.GROQ.name, "", AiProviderType.GEMINI.name, ""),
            RoutingRuleEntity("$userId:${AiTaskType.SUMMARIZATION.name}", userId, AiTaskType.SUMMARIZATION.name, AiProviderType.GEMINI.name, "", AiProviderType.OPENROUTER.name, ""),
            RoutingRuleEntity("$userId:${AiTaskType.DRAFTING.name}", userId, AiTaskType.DRAFTING.name, AiProviderType.OPENROUTER.name, "", AiProviderType.GEMINI.name, ""),
            RoutingRuleEntity("$userId:${AiTaskType.DOCUMENT.name}", userId, AiTaskType.DOCUMENT.name, AiProviderType.GEMINI.name, "", AiProviderType.OPENROUTER.name, ""),
            RoutingRuleEntity("$userId:${AiTaskType.SECURITY.name}", userId, AiTaskType.SECURITY.name, AiProviderType.GEMINI.name, "", AiProviderType.GROQ.name, ""),
            RoutingRuleEntity("$userId:${AiTaskType.RESEARCH.name}", userId, AiTaskType.RESEARCH.name, AiProviderType.GEMINI.name, "", AiProviderType.OPENROUTER.name, ""),
            RoutingRuleEntity("$userId:${AiTaskType.FOLLOWUP.name}", userId, AiTaskType.FOLLOWUP.name, AiProviderType.GROQ.name, "", AiProviderType.GEMINI.name, "")
        )
        for (rule in recommendations) {
            if (aiDao.getRoutingRule(userId, rule.taskType) == null) {
                aiDao.upsertRoutingRule(rule)
            }
        }
    }

    // --- AI Provider BYOK Management ---

    suspend fun saveProviderConfig(
        userId: String,
        providerType: AiProviderType,
        rawApiKey: String?,
        baseUrl: String,
        accountId: String,
        enabled: Boolean,
        isPaidProvider: Boolean,
        defaultModel: String,
        dailyRequestLimit: Int,
        dailyTokenLimit: Int
    ): Result<AiProviderConfigEntity> {
        val validUrl = NetworkSecurity.validateHttpsUrl(baseUrl).getOrElse { return Result.failure(it) }
        val existing = aiDao.getProvider(userId, providerType.name)

        val trimmedNewKey = rawApiKey?.trim().orEmpty()
        val encryptedKey = when {
            trimmedNewKey.isNotEmpty() -> vault.encrypt(trimmedNewKey)
            existing != null -> existing.encryptedApiKey
            else -> ""
        }
        val maskedKey = when {
            trimmedNewKey.isNotEmpty() -> KeyMasker.mask(trimmedNewKey)
            existing != null -> existing.maskedKey
            else -> ""
        }
        val keyChanged = trimmedNewKey.isNotEmpty()
        val updated = AiProviderConfigEntity(
            id = "$userId:${providerType.name}",
            userId = userId,
            providerType = providerType.name,
            displayName = providerType.displayName,
            baseUrl = validUrl,
            accountId = accountId.trim(),
            authHeaderMode = providerType.defaultAuthMode,
            encryptedApiKey = encryptedKey,
            maskedKey = maskedKey,
            enabled = enabled && encryptedKey.isNotBlank(),
            isPaidProvider = isPaidProvider,
            defaultModel = defaultModel.trim(),
            dailyRequestLimit = dailyRequestLimit.coerceAtLeast(0),
            dailyTokenLimit = dailyTokenLimit.coerceAtLeast(0),
            connectionVerified = if (keyChanged) false else (existing?.connectionVerified ?: false),
            lastTestedAt = existing?.lastTestedAt ?: 0L,
            lastLatencyMs = existing?.lastLatencyMs ?: 0L,
            lastError = if (keyChanged) "" else (existing?.lastError ?: ""),
            updatedAt = System.currentTimeMillis()
        )
        aiDao.upsertProvider(updated)
        logAudit(
            userId = userId,
            action = "AI_PROVIDER_CONFIG_SAVED",
            actor = "USER",
            targetId = providerType.name,
            details = "Updated ${providerType.displayName} (enabled=${updated.enabled}, keyConfigured=${encryptedKey.isNotBlank()})."
        )
        return Result.success(updated)
    }

    suspend fun deleteProviderKey(userId: String, providerType: AiProviderType) {
        val existing = aiDao.getProvider(userId, providerType.name) ?: return
        aiDao.upsertProvider(
            existing.copy(
                encryptedApiKey = "",
                maskedKey = "",
                enabled = false,
                connectionVerified = false,
                defaultModel = "",
                lastError = "",
                updatedAt = System.currentTimeMillis()
            )
        )
        aiDao.clearModelsForProvider(userId, providerType.name)
        logAudit(userId, "AI_PROVIDER_KEY_DELETED", "USER", providerType.name, "Revoked & deleted encrypted key for ${providerType.displayName}.")
    }

    suspend fun testAndDiscoverProvider(userId: String, providerType: AiProviderType): ConnectionTestResult {
        val config = aiDao.getProvider(userId, providerType.name)
            ?: return ConnectionTestResult(false, 0L, 0, "Provider slot missing.", AiErrorCode.INVALID_CONFIG.name)
        if (config.encryptedApiKey.isBlank()) {
            return ConnectionTestResult(false, 0L, 0, "Enter and save an API key first.", AiErrorCode.MISSING_API_KEY.name)
        }
        val rawKey = runCatching { vault.decrypt(config.encryptedApiKey) }.getOrElse {
            return ConnectionTestResult(false, 0L, 0, "Could not decrypt stored key; re-save key.", AiErrorCode.MISSING_API_KEY.name)
        }
        val creds = ProviderCredentials(
            providerType = providerType,
            apiKey = rawKey,
            baseUrl = config.baseUrl,
            accountId = config.accountId,
            authHeaderMode = config.authHeaderMode
        )
        val adapter = providerRegistry.get(providerType)
        val start = System.currentTimeMillis()
        return when (val modelsRes = adapter.listModels(creds)) {
            is AiResult.Success -> {
                val latency = System.currentTimeMillis() - start
                val entities = modelsRes.data.map { m ->
                    AiModelEntity(
                        id = "$userId:${providerType.name}:${m.modelId}",
                        userId = userId,
                        providerType = providerType.name,
                        modelId = m.modelId,
                        displayName = m.displayName,
                        supportsJson = m.supportsJson,
                        supportsVision = m.supportsVision,
                        contextWindow = m.contextWindow,
                        promptCostPer1k = m.promptCostPer1k,
                        completionCostPer1k = m.completionCostPer1k
                    )
                }
                aiDao.clearModelsForProvider(userId, providerType.name)
                if (entities.isNotEmpty()) aiDao.insertModels(entities)

                val chosenDefault = config.defaultModel.ifBlank { entities.firstOrNull()?.modelId.orEmpty() }
                aiDao.upsertProvider(
                    config.copy(
                        enabled = true,
                        defaultModel = chosenDefault,
                        connectionVerified = true,
                        lastTestedAt = System.currentTimeMillis(),
                        lastLatencyMs = latency,
                        lastError = "",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                logAudit(userId, "AI_PROVIDER_VERIFIED", "USER", providerType.name, "Verified ${providerType.displayName}: ${entities.size} models (${latency}ms).")
                ConnectionTestResult(
                    success = true,
                    latencyMs = latency,
                    discoveredModelCount = entities.size,
                    message = "Verified ${providerType.displayName} in ${latency}ms (${entities.size} models)."
                )
            }
            is AiResult.Failure -> {
                val latency = System.currentTimeMillis() - start
                aiDao.upsertProvider(
                    config.copy(
                        connectionVerified = false,
                        lastTestedAt = System.currentTimeMillis(),
                        lastLatencyMs = latency,
                        lastError = modelsRes.error.message,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                ConnectionTestResult(
                    success = false,
                    latencyMs = latency,
                    discoveredModelCount = 0,
                    message = modelsRes.error.message,
                    errorCode = modelsRes.error.code.name
                )
            }
        }
    }

    suspend fun saveRoutingRule(rule: RoutingRuleEntity) {
        aiDao.upsertRoutingRule(rule)
        logAudit(rule.userId, "ROUTING_RULE_UPDATED", "USER", rule.taskType, "Primary=${rule.primaryProvider}/${rule.primaryModel}, Fallback=${rule.fallbackProvider}.")
    }

    // --- Email Accounts & Synchronization ---

    fun buildOAuthIntentUri(
        provider: OAuthEmailProvider,
        clientId: String,
        includeWriteSend: Boolean
    ): Result<Uri> = oauthManager.createAuthorizationUrl(provider, clientId, includeWriteSend)

    suspend fun completeOAuthCallback(userId: String, callbackUri: Uri): Result<ConnectedAccountEntity> {
        val (session, tokenResp) = oauthManager.handleCallbackUri(callbackUri).getOrElse {
            return Result.failure(it)
        }
        return verifyAndSaveOAuthAccount(
            userId = userId,
            provider = session.provider,
            accessToken = tokenResp.accessToken,
            refreshToken = tokenResp.refreshToken,
            expiresInSec = tokenResp.expiresInSec,
            clientId = session.clientId,
            scopes = tokenResp.scope
        )
    }

    suspend fun connectOAuthTokenDirectly(
        userId: String,
        provider: OAuthEmailProvider,
        accessToken: String,
        refreshToken: String,
        clientId: String,
        scopes: String
    ): Result<ConnectedAccountEntity> {
        if (accessToken.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("OAuth Access Token is required."))
        }
        return verifyAndSaveOAuthAccount(
            userId = userId,
            provider = provider,
            accessToken = accessToken.trim(),
            refreshToken = refreshToken.trim(),
            expiresInSec = 3600L,
            clientId = clientId.trim(),
            scopes = scopes.ifBlank { provider.defaultReadScope }
        )
    }

    private suspend fun verifyAndSaveOAuthAccount(
        userId: String,
        provider: OAuthEmailProvider,
        accessToken: String,
        refreshToken: String,
        expiresInSec: Long,
        clientId: String,
        scopes: String
    ): Result<ConnectedAccountEntity> {
        val profile = when (provider) {
            OAuthEmailProvider.GMAIL -> gmailClient.getProfile(accessToken)
            OAuthEmailProvider.OUTLOOK -> outlookClient.getProfile(accessToken)
        }.getOrElse { return Result.failure(it) }

        val account = ConnectedAccountEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            provider = provider.name,
            emailAddress = profile.emailAddress,
            displayName = profile.displayName,
            encryptedAccessToken = vault.encrypt(accessToken),
            encryptedRefreshToken = if (refreshToken.isNotBlank()) vault.encrypt(refreshToken) else "",
            tokenExpiresAt = System.currentTimeMillis() + (expiresInSec * 1000L),
            oauthClientId = clientId,
            scopes = scopes,
            syncCursor = "",
            status = "CONNECTED"
        )
        accountDao.upsert(account)
        logAudit(userId, "EMAIL_ACCOUNT_CONNECTED", "USER", account.id, "Connected ${provider.name} account ${profile.emailAddress}.")
        WafaSyncJobService.schedulePeriodicSync(context, prefsStore.preferencesFlow.first().syncIntervalMinutes)
        syncAccount(userId, account.id)
        return Result.success(account)
    }

    suspend fun saveImapBridgeAccount(
        userId: String,
        providerType: String, // YAHOO_IMAP or GENERIC_IMAP
        emailAddress: String,
        imapHost: String,
        imapPort: Int,
        smtpHost: String,
        smtpPort: Int
    ): Result<ConnectedAccountEntity> {
        val cleanEmail = emailAddress.trim()
        if (!cleanEmail.contains("@")) return Result.failure(IllegalArgumentException("Enter a valid email address."))
        if (imapHost.isBlank()) return Result.failure(IllegalArgumentException("IMAP host is required (e.g. imap.mail.yahoo.com)."))
        val prefs = prefsStore.preferencesFlow.first()
        val hasBackend = prefs.backendBaseUrl.startsWith("https://")
        val account = ConnectedAccountEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            provider = providerType,
            emailAddress = cleanEmail,
            displayName = cleanEmail.substringBefore("@"),
            imapHost = imapHost.trim(),
            imapPort = imapPort,
            smtpHost = smtpHost.trim(),
            smtpPort = smtpPort,
            status = if (hasBackend) "CONNECTED" else "NEEDS_BACKEND_BRIDGE",
            lastError = if (hasBackend) "" else "Configure your HTTPS PHP Backend URL to sync IMAP/SMTP accounts securely."
        )
        accountDao.upsert(account)
        logAudit(userId, "IMAP_ACCOUNT_CONFIGURED", "USER", account.id, "Configured $providerType ($cleanEmail -> $imapHost:$imapPort).")
        return Result.success(account)
    }

    suspend fun updateAccountAiOverride(userId: String, accountId: String, provider: String, model: String) {
        val acc = accountDao.getById(userId, accountId) ?: return
        accountDao.upsert(acc.copy(preferredAiProvider = provider, preferredAiModel = model))
    }

    suspend fun disconnectAccount(userId: String, accountId: String) {
        val acc = accountDao.getById(userId, accountId) ?: return
        emailDao.deleteByAccount(userId, accountId)
        accountDao.delete(userId, accountId)
        logAudit(userId, "EMAIL_ACCOUNT_DISCONNECTED", "USER", accountId, "Disconnected ${acc.provider} (${acc.emailAddress}) and wiped cached mail.")
    }

    suspend fun syncAllAccounts(userId: String): Result<Int> {
        val accounts = accountDao.getAccounts(userId).filter { it.isActive && it.provider != "LOCAL_EML" }
        if (accounts.isEmpty()) {
            return Result.failure(IllegalStateException("No connected email accounts to sync. Connect Gmail, Outlook, or IMAP first."))
        }
        var totalImported = 0
        var lastErr: String? = null
        for (acc in accounts) {
            val res = syncAccount(userId, acc.id)
            if (res.isSuccess) totalImported += res.getOrDefault(0)
            else lastErr = res.exceptionOrNull()?.message
        }
        prefsStore.setLastGlobalSyncAt(System.currentTimeMillis())
        return if (totalImported > 0 || lastErr == null) Result.success(totalImported)
        else Result.failure(IllegalStateException(lastErr))
    }

    suspend fun syncAccount(userId: String, accountId: String): Result<Int> {
        val acc = accountDao.getById(userId, accountId)
            ?: return Result.failure(IllegalArgumentException("Account not found."))
        val prefs = prefsStore.preferencesFlow.first()

        return when (acc.provider) {
            "GMAIL", "OUTLOOK" -> {
                val token = resolveValidAccessToken(acc).getOrElse { err ->
                    accountDao.upsert(acc.copy(status = "TOKEN_EXPIRED", lastError = err.message ?: "Token expired"))
                    return Result.failure(err)
                }
                val batchRes = if (acc.provider == "GMAIL") {
                    gmailClient.syncMessages(token, acc.syncCursor)
                } else {
                    outlookClient.syncMessages(token, acc.syncCursor)
                }
                batchRes.fold(
                    onSuccess = { batch ->
                        val count = persistRemoteMessages(userId, acc, batch.messages, prefs)
                        accountDao.upsert(
                            acc.copy(
                                syncCursor = batch.nextCursor,
                                lastSyncedAt = System.currentTimeMillis(),
                                status = "CONNECTED",
                                lastError = ""
                            )
                        )
                        prefsStore.setLastGlobalSyncAt(System.currentTimeMillis())
                        Result.success(count)
                    },
                    onFailure = { err ->
                        accountDao.upsert(acc.copy(status = "ERROR", lastError = err.message ?: "Sync failed"))
                        Result.failure(err)
                    }
                )
            }
            "YAHOO_IMAP", "GENERIC_IMAP" -> {
                val user = userDao.getById(userId)
                val baseUrl = prefs.backendBaseUrl.ifBlank { user?.backendBaseUrl.orEmpty() }
                val token = user?.encryptedBackendToken?.takeIf { it.isNotBlank() }?.let {
                    runCatching { vault.decrypt(it) }.getOrNull()
                }.orEmpty()
                if (baseUrl.isBlank() || token.isBlank()) {
                    val msg = "IMAP/SMTP accounts require an authenticated HTTPS PHP backend bridge."
                    accountDao.upsert(acc.copy(status = "NEEDS_BACKEND_BRIDGE", lastError = msg))
                    return Result.failure(IllegalStateException(msg))
                }
                backendClient.syncImapBridge(baseUrl, token, acc.id).fold(
                    onSuccess = { msgs ->
                        val count = persistRemoteMessages(userId, acc, msgs, prefs)
                        accountDao.upsert(acc.copy(lastSyncedAt = System.currentTimeMillis(), status = "CONNECTED", lastError = ""))
                        Result.success(count)
                    },
                    onFailure = { err ->
                        accountDao.upsert(acc.copy(status = "ERROR", lastError = err.message ?: "IMAP bridge error"))
                        Result.failure(err)
                    }
                )
            }
            else -> Result.success(0)
        }
    }

    private suspend fun resolveValidAccessToken(account: ConnectedAccountEntity): Result<String> {
        val currentToken = runCatching { vault.decrypt(account.encryptedAccessToken) }.getOrElse { "" }
        val isNearExpiry = account.tokenExpiresAt > 0L && System.currentTimeMillis() >= (account.tokenExpiresAt - 60_000L)
        if (currentToken.isNotBlank() && !isNearExpiry) return Result.success(currentToken)

        if (account.encryptedRefreshToken.isNotBlank() && account.oauthClientId.isNotBlank()) {
            val refreshToken = runCatching { vault.decrypt(account.encryptedRefreshToken) }.getOrElse { "" }
            val oauthProvider = runCatching { OAuthEmailProvider.valueOf(account.provider) }.getOrNull()
            if (refreshToken.isNotBlank() && oauthProvider != null) {
                val refreshed = oauthManager.refreshAccessToken(oauthProvider, account.oauthClientId, refreshToken)
                if (refreshed.isSuccess) {
                    val tok = refreshed.getOrThrow()
                    accountDao.upsert(
                        account.copy(
                            encryptedAccessToken = vault.encrypt(tok.accessToken),
                            encryptedRefreshToken = vault.encrypt(tok.refreshToken),
                            tokenExpiresAt = System.currentTimeMillis() + tok.expiresInSec * 1000L,
                            status = "CONNECTED"
                        )
                    )
                    return Result.success(tok.accessToken)
                }
            }
        }
        return if (currentToken.isNotBlank()) Result.success(currentToken)
        else Result.failure(IllegalStateException("Access token missing or expired. Reconnect ${account.emailAddress}."))
    }

    private suspend fun persistRemoteMessages(
        userId: String,
        account: ConnectedAccountEntity,
        messages: List<RemoteEmailMessage>,
        prefs: WafaPreferences
    ): Int {
        var count = 0
        for (m in messages) {
            val emailId = "$userId:${account.id}:${m.remoteId}"
            val sec = PromptSecurityGuard.analyzeEmail(
                senderName = m.senderName,
                senderEmail = m.senderEmail,
                replyTo = m.replyTo.ifBlank { null },
                subject = m.subject,
                body = m.bodyText,
                attachmentNames = m.attachments.map { it.fileName },
                authHeaders = m.authHeaders.ifBlank { null }
            )
            val entity = EmailEntity(
                id = emailId,
                userId = userId,
                accountId = account.id,
                remoteMessageId = m.remoteId,
                threadId = m.threadId,
                senderName = m.senderName,
                senderEmail = m.senderEmail,
                replyTo = m.replyTo,
                recipients = m.recipients,
                subject = m.subject,
                snippet = m.snippet.ifBlank { m.bodyText.take(140) },
                bodyText = m.bodyText,
                authHeaders = m.authHeaders,
                labelsCsv = m.labels.joinToString(","),
                isRead = m.isRead,
                isArchived = m.isArchived,
                isStarred = m.isStarred,
                hasAttachments = m.attachments.isNotEmpty(),
                securityRiskLevel = sec.riskLevel.name,
                securityScore = sec.score,
                securityFlagsCsv = sec.flags.joinToString(" | "),
                receivedAt = m.receivedAt,
                syncedAt = System.currentTimeMillis()
            )
            emailDao.upsert(entity)
            count++

            for (att in m.attachments) {
                workspaceDao.upsertAttachment(
                    AttachmentEntity(
                        id = "$emailId:${att.remoteAttachmentId}",
                        userId = userId,
                        emailId = emailId,
                        remoteAttachmentId = att.remoteAttachmentId,
                        fileName = att.fileName,
                        mimeType = att.mimeType,
                        sizeBytes = att.sizeBytes,
                        isSafe = att.fileName !in sec.dangerousAttachments,
                        safetyWarning = if (att.fileName in sec.dangerousAttachments) "Potentially dangerous executable attachment." else ""
                    )
                )
            }
            if (sec.score >= 50) {
                workspaceDao.insertNotification(
                    NotificationEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        title = "Security Alert (${sec.riskLevel.name})",
                        message = "'${entity.subject}' from ${entity.senderEmail}: ${sec.flags.firstOrNull() ?: sec.recommendation}",
                        category = "SECURITY",
                        relatedEmailId = entity.id
                    )
                )
                WafaNotificationHelper.postNotification(
                    context = context,
                    id = entity.id.hashCode(),
                    channelId = WafaNotificationHelper.CHANNEL_SECURITY,
                    title = "WAFA Security Alert (${sec.riskLevel.name})",
                    message = "${entity.subject} — ${sec.recommendation}"
                )
            }
            automationEngine.evaluateEmailAgainstRules(userId, entity, prefs)
        }
        return count
    }

    // --- Real Local .EML & Attachment Import via Android SAF ---

    suspend fun importEmlFromUri(userId: String, uri: Uri): Result<EmailEntity> {
        val inspected = AttachmentInspector.inspectUri(context, uri).getOrElse { return Result.failure(it) }
        val parsed = AttachmentInspector.parseEml(inspected.extractedText)
        val localAccountId = ensureLocalEmlAccount(userId)
        val sec = PromptSecurityGuard.analyzeEmail(
            senderName = parsed.senderName,
            senderEmail = parsed.senderEmail,
            replyTo = parsed.replyTo.ifBlank { null },
            subject = parsed.subject,
            body = parsed.bodyText,
            authHeaders = parsed.authHeaders.ifBlank { null }
        )
        val msgId = UUID.randomUUID().toString()
        val entity = EmailEntity(
            id = "$userId:$localAccountId:$msgId",
            userId = userId,
            accountId = localAccountId,
            remoteMessageId = msgId,
            threadId = msgId,
            senderName = parsed.senderName,
            senderEmail = parsed.senderEmail,
            replyTo = parsed.replyTo,
            recipients = parsed.recipients,
            subject = parsed.subject,
            snippet = parsed.bodyText.take(150),
            bodyText = parsed.bodyText,
            authHeaders = parsed.authHeaders,
            labelsCsv = "IMPORTED_EML",
            isRead = false,
            securityRiskLevel = sec.riskLevel.name,
            securityScore = sec.score,
            securityFlagsCsv = sec.flags.joinToString(" | "),
            receivedAt = parsed.receivedAt
        )
        emailDao.upsert(entity)
        val prefs = prefsStore.preferencesFlow.first()
        automationEngine.evaluateEmailAgainstRules(userId, entity, prefs)
        logAudit(userId, "EML_IMPORTED", "USER", entity.id, "Imported local .eml file '${inspected.fileName}'.")
        return Result.success(entity)
    }

    suspend fun importAttachmentFromUri(userId: String, uri: Uri, emailId: String = ""): Result<AttachmentEntity> {
        val inspected = AttachmentInspector.inspectUri(context, uri).getOrElse { return Result.failure(it) }
        val entity = AttachmentEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            emailId = emailId,
            fileName = inspected.fileName,
            mimeType = inspected.mimeType,
            sizeBytes = inspected.sizeBytes,
            localUri = uri.toString(),
            extractedText = inspected.extractedText,
            isSafe = inspected.isSafe,
            safetyWarning = inspected.safetyWarning
        )
        workspaceDao.upsertAttachment(entity)
        logAudit(userId, "ATTACHMENT_INSPECTED", "USER", entity.id, "Inspected '${entity.fileName}' (${entity.sizeBytes} bytes, safe=${entity.isSafe}).")
        return Result.success(entity)
    }

    private suspend fun ensureLocalEmlAccount(userId: String): String {
        val existing = accountDao.getAccounts(userId).firstOrNull { it.provider == "LOCAL_EML" }
        if (existing != null) return existing.id
        val acc = ConnectedAccountEntity(
            id = "$userId:local_eml",
            userId = userId,
            provider = "LOCAL_EML",
            emailAddress = "local-eml-imports@device",
            displayName = "Imported .EML Files",
            status = "CONNECTED"
        )
        accountDao.upsert(acc)
        return acc.id
    }

    // --- Email Mutations (Synced back to Provider where Authorized) ---

    suspend fun setEmailRead(userId: String, email: EmailEntity, isRead: Boolean) {
        emailDao.setRead(userId, email.id, isRead)
        val acc = accountDao.getById(userId, email.accountId) ?: return
        val token = resolveValidAccessToken(acc).getOrNull() ?: return
        when (acc.provider) {
            "GMAIL" -> gmailClient.modifyMessage(
                accessToken = token,
                messageId = email.remoteMessageId,
                addLabels = if (isRead) emptyList() else listOf("UNREAD"),
                removeLabels = if (isRead) listOf("UNREAD") else emptyList()
            )
            "OUTLOOK" -> outlookClient.markRead(token, email.remoteMessageId, isRead)
        }
    }

    suspend fun archiveEmail(userId: String, email: EmailEntity) {
        emailDao.setArchived(userId, email.id, true)
        val acc = accountDao.getById(userId, email.accountId) ?: return
        val token = resolveValidAccessToken(acc).getOrNull() ?: return
        when (acc.provider) {
            "GMAIL" -> gmailClient.modifyMessage(token, email.remoteMessageId, removeLabels = listOf("INBOX"))
            "OUTLOOK" -> outlookClient.archiveMessage(token, email.remoteMessageId)
        }
        logAudit(userId, "EMAIL_ARCHIVED", "USER", email.id, "Archived '${email.subject}'.")
    }

    suspend fun sendDraftWithExplicitConfirmation(userId: String, draft: AiDraftEntity): Result<Unit> {
        val acc = accountDao.getById(userId, draft.accountId)
            ?: accountDao.getAccounts(userId).firstOrNull { it.provider == "GMAIL" || it.provider == "OUTLOOK" }
            ?: return Result.failure(
                IllegalStateException("No send-capable Gmail or Outlook account connected. Connect an OAuth account with send permission first.")
            )
        val token = resolveValidAccessToken(acc).getOrElse { return Result.failure(it) }
        val sendResult = when (acc.provider) {
            "GMAIL" -> gmailClient.sendMessage(token, draft.recipientEmail, draft.subject, draft.body, draft.threadId).map { }
            "OUTLOOK" -> outlookClient.sendMail(token, draft.recipientEmail, draft.subject, draft.body)
            else -> Result.failure(IllegalStateException("Sending via ${acc.provider} requires the HTTPS PHP backend SMTP bridge."))
        }
        return sendResult.onSuccess {
            workspaceDao.upsertDraft(draft.copy(status = "SENT", updatedAt = System.currentTimeMillis()))
            logAudit(userId, "EMAIL_SENT_CONFIRMED", "USER", draft.id, "Sent confirmed email to ${draft.recipientEmail} via ${acc.provider}.")
        }
    }

    suspend fun syncDraftToProvider(userId: String, draft: AiDraftEntity): Result<String> {
        val acc = accountDao.getById(userId, draft.accountId)
            ?: accountDao.getAccounts(userId).firstOrNull { it.provider == "GMAIL" || it.provider == "OUTLOOK" }
            ?: return Result.failure(IllegalStateException("Connect a Gmail or Outlook account to save remote drafts."))
        val token = resolveValidAccessToken(acc).getOrElse { return Result.failure(it) }
        val res = when (acc.provider) {
            "GMAIL" -> gmailClient.createDraft(token, draft.recipientEmail, draft.subject, draft.body, draft.threadId)
            "OUTLOOK" -> outlookClient.createDraft(token, draft.recipientEmail, draft.subject, draft.body)
            else -> Result.failure(IllegalStateException("Remote draft sync unavailable for ${acc.provider}."))
        }
        return res.onSuccess { remoteId ->
            workspaceDao.upsertDraft(draft.copy(remoteDraftId = remoteId, updatedAt = System.currentTimeMillis()))
        }
    }

    // --- Scheduled Background Execution (JobService) ---

    suspend fun runScheduledBackgroundCycle() {
        val prefs = prefsStore.preferencesFlow.first()
        val userId = prefs.activeUserId
        if (userId.isBlank()) return

        // 1. Evaluate due follow-up reminders without sending duplicate notifications
        val now = System.currentTimeMillis()
        val due = workspaceDao.getDueUnnotifiedReminders(userId, now)
        for (reminder in due) {
            workspaceDao.markReminderNotified(userId, reminder.id, now)
            workspaceDao.insertNotification(
                NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = "Follow-up Due",
                    message = "'${reminder.emailSubject}' (${reminder.senderEmail}) — ${reminder.followUpType}",
                    category = "FOLLOW_UP",
                    relatedEmailId = reminder.emailId
                )
            )
            WafaNotificationHelper.postNotification(
                context = context,
                id = reminder.id.hashCode(),
                channelId = WafaNotificationHelper.CHANNEL_FOLLOWUPS,
                title = "WAFA Follow-up Due",
                message = "${reminder.emailSubject} (${reminder.senderEmail})"
            )
        }

        // 2. Sync connected accounts if network available
        if (NetworkSecurity.isNetworkAvailable(context)) {
            runCatching { syncAllAccounts(userId) }
        }
    }

    suspend fun logAudit(userId: String, action: String, actor: String, targetId: String, details: String) {
        workspaceDao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                action = action,
                actor = actor,
                targetId = targetId,
                details = KeyMasker.redactSecrets(details)
            )
        )
    }
}
