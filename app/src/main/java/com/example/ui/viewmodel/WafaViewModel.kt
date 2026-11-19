package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiProviderType
import com.example.data.ai.AiResult
import com.example.data.ai.ConnectionTestResult
import com.example.data.email.BackendHealthResult
import com.example.data.email.OAuthEmailProvider
import com.example.data.local.*
import com.example.data.repository.WafaRepository
import com.example.domain.agents.DeepSecurityOutput
import com.example.domain.agents.GroundedInboxAnswer
import com.example.domain.agents.ScheduleExtractionOutput
import com.example.domain.agents.WafaDefaultCategories
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class AskInboxMessage(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val answer: GroundedInboxAnswer? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class WafaUiState(
    val preferences: WafaPreferences = WafaPreferences(),
    val currentUser: UserEntity? = null,
    val accounts: List<ConnectedAccountEntity> = emptyList(),
    val providers: List<AiProviderConfigEntity> = emptyList(),
    val models: List<AiModelEntity> = emptyList(),
    val routingRules: List<RoutingRuleEntity> = emptyList(),
    val usageLogs: List<AiUsageLogEntity> = emptyList(),
    val emails: List<EmailEntity> = emptyList(),
    val summaries: Map<String, AiSummaryEntity> = emptyMap(),
    val drafts: List<AiDraftEntity> = emptyList(),
    val reminders: List<FollowUpReminderEntity> = emptyList(),
    val automationRules: List<AutomationRuleEntity> = emptyList(),
    val automationExecutions: List<AutomationExecutionEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val customCategories: List<CustomCategoryEntity> = emptyList(),
    val askInboxHistory: List<AskInboxMessage> = emptyList(),
    val lastSecurityAudit: Pair<String, DeepSecurityOutput>? = null,
    val lastScheduleResult: Pair<String, ScheduleExtractionOutput>? = null,
    val lastProviderTest: Pair<AiProviderType, ConnectionTestResult>? = null,
    val lastBackendHealth: BackendHealthResult? = null,
    val isBusy: Boolean = false,
    val busyMessage: String = "",
    val bannerMessage: String? = null,
    val bannerIsError: Boolean = false,
    val pendingOAuthUri: Uri? = null,
    val isVaultHardwareBacked: Boolean = false
) {
    val allCategories: List<String>
        get() = (WafaDefaultCategories.core + customCategories.map { it.name }).distinct()

    val unreadCount: Int get() = emails.count { !it.isRead && !it.isArchived }
    val importantCount: Int get() = emails.count { !it.isArchived && (it.priority == "Urgent" || it.priority == "High" || it.category == "Important") }
    val pendingReplyCount: Int get() = emails.count { !it.isArchived && it.followUpState == "NEEDS_MY_REPLY" }
    val activeReminderCount: Int get() = reminders.count { it.status == "PENDING" || it.status == "SNOOZED" }
    val unreadNotificationCount: Int get() = notifications.count { !it.isRead }
    val pendingApprovalCount: Int get() = automationExecutions.count { it.status == "PENDING_APPROVAL" }
    val configuredProviderCount: Int get() = providers.count { it.enabled && it.encryptedApiKey.isNotBlank() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WafaViewModel(private val repo: WafaRepository) : ViewModel() {

    private val _ephemeral = MutableStateFlow(EphemeralState())

    private data class EphemeralState(
        val askInboxHistory: List<AskInboxMessage> = emptyList(),
        val lastSecurityAudit: Pair<String, DeepSecurityOutput>? = null,
        val lastScheduleResult: Pair<String, ScheduleExtractionOutput>? = null,
        val lastProviderTest: Pair<AiProviderType, ConnectionTestResult>? = null,
        val lastBackendHealth: BackendHealthResult? = null,
        val isBusy: Boolean = false,
        val busyMessage: String = "",
        val bannerMessage: String? = null,
        val bannerIsError: Boolean = false,
        val pendingOAuthUri: Uri? = null
    )

    private val prefsFlow = repo.prefsStore.preferencesFlow

    private val userScopedFlow: Flow<UserDataBundle> = prefsFlow
        .map { it.activeUserId }
        .distinctUntilChanged()
        .flatMapLatest { userId ->
            if (userId.isBlank()) {
                flowOf(UserDataBundle())
            } else {
                val partA = combine(
                    repo.db.userDao().observeById(userId),
                    repo.db.accountDao().observeAccounts(userId),
                    repo.db.aiProviderDao().observeProviders(userId),
                    repo.db.aiProviderDao().observeModels(userId),
                    repo.db.aiProviderDao().observeRoutingRules(userId)
                ) { user, accounts, providers, models, rules ->
                    PartA(user, accounts, providers, models, rules)
                }
                val partB = combine(
                    repo.db.aiProviderDao().observeUsageLogs(userId),
                    repo.db.emailDao().observeAllEmails(userId),
                    repo.db.emailDao().observeSummaries(userId),
                    repo.db.workspaceDao().observeDrafts(userId),
                    repo.db.workspaceDao().observeReminders(userId)
                ) { logs, emails, summaries, drafts, reminders ->
                    PartB(logs, emails, summaries.associateBy { it.emailId }, drafts, reminders)
                }
                val partC = combine(
                    repo.db.workspaceDao().observeAutomationRules(userId),
                    repo.db.workspaceDao().observeAutomationExecutions(userId),
                    repo.db.workspaceDao().observeAttachments(userId),
                    repo.db.workspaceDao().observeNotifications(userId),
                    repo.db.workspaceDao().observeAuditLogs(userId)
                ) { rules, execs, atts, notifs, audits ->
                    PartC(rules, execs, atts, notifs, audits)
                }
                combine(
                    partA,
                    partB,
                    partC,
                    repo.db.emailDao().observeCustomCategories(userId)
                ) { a, b, c, customCats ->
                    UserDataBundle(
                        user = a.user,
                        accounts = a.accounts,
                        providers = a.providers,
                        models = a.models,
                        routingRules = a.rules,
                        usageLogs = b.logs,
                        emails = b.emails,
                        summaries = b.summaries,
                        drafts = b.drafts,
                        reminders = b.reminders,
                        automationRules = c.rules,
                        automationExecutions = c.execs,
                        attachments = c.atts,
                        notifications = c.notifs,
                        auditLogs = c.audits,
                        customCategories = customCats
                    )
                }
            }
        }

    val uiState: StateFlow<WafaUiState> = combine(
        prefsFlow,
        userScopedFlow,
        _ephemeral
    ) { prefs, data, eph ->
        WafaUiState(
            preferences = prefs,
            currentUser = data.user,
            accounts = data.accounts,
            providers = data.providers,
            models = data.models,
            routingRules = data.routingRules,
            usageLogs = data.usageLogs,
            emails = data.emails,
            summaries = data.summaries,
            drafts = data.drafts,
            reminders = data.reminders,
            automationRules = data.automationRules,
            automationExecutions = data.automationExecutions,
            attachments = data.attachments,
            notifications = data.notifications,
            auditLogs = data.auditLogs,
            customCategories = data.customCategories,
            askInboxHistory = eph.askInboxHistory,
            lastSecurityAudit = eph.lastSecurityAudit,
            lastScheduleResult = eph.lastScheduleResult,
            lastProviderTest = eph.lastProviderTest,
            lastBackendHealth = eph.lastBackendHealth,
            isBusy = eph.isBusy,
            busyMessage = eph.busyMessage,
            bannerMessage = eph.bannerMessage,
            bannerIsError = eph.bannerIsError,
            pendingOAuthUri = eph.pendingOAuthUri,
            isVaultHardwareBacked = repo.vault.isHardwareBacked()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WafaUiState(isVaultHardwareBacked = repo.vault.isHardwareBacked())
    )

    private fun currentUserId(): String = uiState.value.preferences.activeUserId

    private fun setBusy(busy: Boolean, message: String = "") {
        _ephemeral.update { it.copy(isBusy = busy, busyMessage = message) }
    }

    fun showMessage(message: String, isError: Boolean = false) {
        _ephemeral.update { it.copy(bannerMessage = message, bannerIsError = isError) }
    }

    fun dismissBanner() {
        _ephemeral.update { it.copy(bannerMessage = null, bannerIsError = false) }
    }

    fun consumePendingOAuthUri() {
        _ephemeral.update { it.copy(pendingOAuthUri = null) }
    }

    // --- Theme, Language & Settings ---

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.prefsStore.setThemeMode(mode) }
    fun setLanguage(lang: AppLanguage) = viewModelScope.launch { repo.prefsStore.setLanguage(lang) }
    fun setOnboardingCompleted(done: Boolean) = viewModelScope.launch { repo.prefsStore.setOnboardingCompleted(done) }
    fun setSelectedAccountFilter(accountId: String) = viewModelScope.launch { repo.prefsStore.setSelectedAccountId(accountId) }
    fun setSyncInterval(minutes: Int) = viewModelScope.launch { repo.prefsStore.setSyncInterval(minutes) }

    fun setOperationMode(mode: AiOperationMode) = viewModelScope.launch {
        repo.prefsStore.setOperationMode(mode)
        val uid = currentUserId()
        if (uid.isNotBlank()) repo.logAudit(uid, "OPERATION_MODE_CHANGED", "USER", mode.name, "Switched AI operation mode to ${mode.labelEn}.")
        showMessage("Operation mode set to ${mode.labelEn}.")
    }

    fun updatePermissions(
        allowAutoCategorize: Boolean? = null,
        allowAutoLabel: Boolean? = null,
        allowAutoReminder: Boolean? = null,
        allowAutoDraft: Boolean? = null,
        allowAutoArchive: Boolean? = null,
        allowAutoMarkRead: Boolean? = null,
        allowPaidFallback: Boolean? = null
    ) = viewModelScope.launch {
        repo.prefsStore.updatePermissions(
            allowAutoCategorize, allowAutoLabel, allowAutoReminder,
            allowAutoDraft, allowAutoArchive, allowAutoMarkRead, allowPaidFallback
        )
    }

    fun revokeAllAiPermissions() = viewModelScope.launch {
        repo.prefsStore.revokeAllAiPermissions()
        val uid = currentUserId()
        if (uid.isNotBlank()) repo.logAudit(uid, "ALL_AI_PERMISSIONS_REVOKED", "USER", uid, "Revoked all automated AI permissions and reverted to Copilot Mode.")
        showMessage("All automated AI permissions revoked. Reverted to Copilot Mode.")
    }

    // --- Authentication & Backend ---

    fun registerLocal(fullName: String, email: String, password: String, onSuccess: () -> Unit) = viewModelScope.launch {
        setBusy(true, "Creating encrypted workspace...")
        val res = repo.registerUser(fullName, email, password)
        setBusy(false)
        res.fold(
            onSuccess = {
                showMessage("Welcome, ${it.fullName}!")
                onSuccess()
            },
            onFailure = { showMessage(it.message ?: "Registration failed.", isError = true) }
        )
    }

    fun signInLocal(email: String, password: String, onSuccess: () -> Unit) = viewModelScope.launch {
        setBusy(true, "Verifying credentials...")
        val res = repo.signInLocal(email, password)
        setBusy(false)
        res.fold(
            onSuccess = {
                showMessage("Signed in as ${it.email}.")
                onSuccess()
            },
            onFailure = { showMessage(it.message ?: "Sign-in failed.", isError = true) }
        )
    }

    fun signInBackend(baseUrl: String, email: String, password: String, onSuccess: () -> Unit) = viewModelScope.launch {
        setBusy(true, "Authenticating over HTTPS...")
        val res = repo.signInBackend(baseUrl, email, password)
        setBusy(false)
        res.fold(
            onSuccess = {
                showMessage("Connected to WAFA backend as ${it.email}.")
                onSuccess()
            },
            onFailure = { showMessage(it.message ?: "Backend sign-in failed.", isError = true) }
        )
    }

    fun checkBackendHealth(baseUrl: String) = viewModelScope.launch {
        setBusy(true, "Testing HTTPS backend...")
        val res = repo.testBackendConnection(baseUrl)
        setBusy(false)
        _ephemeral.update { it.copy(lastBackendHealth = res) }
        showMessage(res.message, isError = !res.healthy)
    }

    fun signOut() = viewModelScope.launch {
        repo.signOut(currentUserId())
        _ephemeral.update { EphemeralState() }
    }

    fun deleteAccountAndWipeData() = viewModelScope.launch {
        setBusy(true, "Wiping local vault and account data...")
        repo.deleteAllUserData(currentUserId())
        setBusy(false)
        _ephemeral.update { EphemeralState(bannerMessage = "Account and encrypted vault wiped.") }
    }

    // --- AI Providers & Router ---

    fun saveProviderConfig(
        providerType: AiProviderType,
        rawApiKey: String?,
        baseUrl: String,
        accountId: String,
        enabled: Boolean,
        isPaidProvider: Boolean,
        defaultModel: String,
        dailyRequestLimit: Int,
        dailyTokenLimit: Int
    ) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val res = repo.saveProviderConfig(
            uid, providerType, rawApiKey, baseUrl, accountId,
            enabled, isPaidProvider, defaultModel, dailyRequestLimit, dailyTokenLimit
        )
        res.fold(
            onSuccess = { showMessage("Saved encrypted config for ${providerType.displayName}.") },
            onFailure = { showMessage(it.message ?: "Could not save provider.", isError = true) }
        )
    }

    fun deleteProviderKey(providerType: AiProviderType) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        repo.deleteProviderKey(uid, providerType)
        showMessage("Deleted key for ${providerType.displayName}.")
    }

    fun testAndFetchModels(providerType: AiProviderType) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Testing ${providerType.displayName} & discovering live models...")
        val res = repo.testAndDiscoverProvider(uid, providerType)
        setBusy(false)
        _ephemeral.update { it.copy(lastProviderTest = providerType to res) }
        showMessage(res.message, isError = !res.success)
    }

    fun saveRoutingRule(rule: RoutingRuleEntity) = viewModelScope.launch {
        repo.saveRoutingRule(rule)
        showMessage("Updated routing rule for ${rule.taskType}.")
    }

    // --- Connected Email Accounts & Sync ---

    fun launchOAuthPkce(provider: OAuthEmailProvider, clientId: String, includeWriteSend: Boolean) {
        val res = repo.buildOAuthIntentUri(provider, clientId, includeWriteSend)
        res.fold(
            onSuccess = { uri -> _ephemeral.update { it.copy(pendingOAuthUri = uri) } },
            onFailure = { showMessage(it.message ?: "OAuth setup error.", isError = true) }
        )
    }

    fun handleOAuthCallback(callbackUri: Uri) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Completing OAuth 2.0 PKCE token exchange...")
        val res = repo.completeOAuthCallback(uid, callbackUri)
        setBusy(false)
        res.fold(
            onSuccess = { showMessage("Connected ${it.provider}: ${it.emailAddress}") },
            onFailure = { showMessage(it.message ?: "OAuth exchange failed.", isError = true) }
        )
    }

    fun connectOAuthTokenDirectly(
        provider: OAuthEmailProvider,
        accessToken: String,
        refreshToken: String,
        clientId: String,
        scopes: String
    ) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Verifying ${provider.name} OAuth token...")
        val res = repo.connectOAuthTokenDirectly(uid, provider, accessToken, refreshToken, clientId, scopes)
        setBusy(false)
        res.fold(
            onSuccess = { showMessage("Verified & connected ${it.emailAddress} (${it.provider}).") },
            onFailure = { showMessage(it.message ?: "Connection verification failed.", isError = true) }
        )
    }

    fun saveImapBridgeAccount(
        providerType: String,
        emailAddress: String,
        imapHost: String,
        imapPort: Int,
        smtpHost: String,
        smtpPort: Int
    ) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val res = repo.saveImapBridgeAccount(uid, providerType, emailAddress, imapHost, imapPort, smtpHost, smtpPort)
        res.fold(
            onSuccess = { showMessage("Configured ${it.emailAddress} ($providerType).") },
            onFailure = { showMessage(it.message ?: "Could not save IMAP config.", isError = true) }
        )
    }

    fun disconnectAccount(accountId: String) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        repo.disconnectAccount(uid, accountId)
        showMessage("Account disconnected and cached emails removed.")
    }

    fun syncAllAccounts() = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Synchronizing connected accounts...")
        val res = repo.syncAllAccounts(uid)
        setBusy(false)
        res.fold(
            onSuccess = { showMessage("Synchronized $it message(s).") },
            onFailure = { showMessage(it.message ?: "Sync failed.", isError = true) }
        )
    }

    fun importEmlUri(uri: Uri) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Parsing .eml file...")
        val res = repo.importEmlFromUri(uid, uri)
        setBusy(false)
        res.fold(
            onSuccess = { showMessage("Imported '${it.subject}' into Smart Inbox.") },
            onFailure = { showMessage(it.message ?: "Could not parse .eml file.", isError = true) }
        )
    }

    fun importAttachmentUri(uri: Uri, emailId: String = "") = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Inspecting attachment safely...")
        val res = repo.importAttachmentFromUri(uid, uri, emailId)
        setBusy(false)
        res.fold(
            onSuccess = { showMessage("Inspected '${it.fileName}' (${if (it.isSafe) "Safe" else "Warning"}). ") },
            onFailure = { showMessage(it.message ?: "Attachment inspection failed.", isError = true) }
        )
    }

    // --- Email Actions & AI Agents ---

    fun toggleRead(email: EmailEntity) = viewModelScope.launch {
        repo.setEmailRead(currentUserId(), email, !email.isRead)
    }

    fun archiveEmail(email: EmailEntity) = viewModelScope.launch {
        repo.archiveEmail(currentUserId(), email)
        showMessage("Archived '${email.subject}'.")
    }

    fun deleteEmail(email: EmailEntity) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        repo.db.emailDao().deleteById(uid, email.id)
        repo.logAudit(uid, "EMAIL_DELETED_LOCAL", "USER", email.id, "Deleted '${email.subject}' from local cache.")
        showMessage("Removed email from local inbox.")
    }

    fun updateCategory(email: EmailEntity, category: String) = viewModelScope.launch {
        repo.db.emailDao().setCategory(currentUserId(), email.id, category)
    }

    fun addCustomCategory(name: String, description: String) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val clean = name.trim()
        if (clean.isEmpty()) return@launch
        repo.db.emailDao().insertCustomCategory(
            CustomCategoryEntity(UUID.randomUUID().toString(), uid, clean, description.trim())
        )
        showMessage("Added custom category '$clean'.")
    }

    fun deleteCustomCategory(id: String) = viewModelScope.launch {
        repo.db.emailDao().deleteCustomCategory(currentUserId(), id)
    }

    fun classifyEmailWithAi(email: EmailEntity) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val state = uiState.value
        setBusy(true, "Running Inbox & Priority Agents...")
        val res = repo.aiAgents.classifyAndPrioritize(
            uid, email, state.allCategories, state.preferences.allowPaidFallback
        )
        setBusy(false)
        when (res) {
            is AiResult.Success -> {
                val out = res.data
                repo.db.emailDao().upsert(
                    email.copy(
                        category = out.category,
                        priority = out.priority,
                        priorityReason = out.priorityReason,
                        followUpState = out.followUpState
                    )
                )
                showMessage("Classified as ${out.category} / ${out.priority} via ${out.providerUsed.displayName}.")
            }
            is AiResult.Failure -> showMessage(res.error.message, isError = true)
        }
    }

    fun summarizeEmailWithAi(email: EmailEntity) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val thread = repo.db.emailDao().getThread(uid, email.threadId).ifEmpty { listOf(email) }
        setBusy(true, "Running Summary Agent...")
        val res = repo.aiAgents.summarizeEmailOrThread(uid, thread, uiState.value.preferences.allowPaidFallback)
        setBusy(false)
        when (res) {
            is AiResult.Success -> {
                val s = res.data
                repo.db.emailDao().upsertSummary(
                    AiSummaryEntity(
                        emailId = email.id,
                        userId = uid,
                        shortSummary = s.shortSummary,
                        detailedSummary = s.detailedSummary,
                        conversationSummary = s.conversationSummary,
                        actionItemsCsv = s.actionItems.joinToString("\n"),
                        deadlinesCsv = s.deadlines.joinToString("\n"),
                        importantDatesCsv = s.importantDates.joinToString("\n"),
                        entitiesCsv = s.entities.joinToString(", "),
                        financialAmountsCsv = s.financialAmounts.joinToString(", "),
                        sourceReference = "Email #${email.remoteMessageId.take(12)} (${email.subject})",
                        providerUsed = s.providerUsed.displayName,
                        modelUsed = s.modelUsed
                    )
                )
                showMessage("Summary generated via ${s.providerUsed.displayName} (${s.modelUsed}).")
            }
            is AiResult.Failure -> showMessage(res.error.message, isError = true)
        }
    }

    fun generateDraftWithAi(
        sourceEmail: EmailEntity?,
        recipientEmail: String,
        tone: String,
        languageCode: String,
        customInstructions: String
    ) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Running AI Reply Writer...")
        val res = repo.aiAgents.generateDraft(
            uid, sourceEmail, tone, languageCode, customInstructions,
            uiState.value.preferences.allowPaidFallback
        )
        setBusy(false)
        when (res) {
            is AiResult.Success -> {
                val draft = AiDraftEntity(
                    id = UUID.randomUUID().toString(),
                    userId = uid,
                    accountId = sourceEmail?.accountId ?: uiState.value.accounts.firstOrNull()?.id.orEmpty(),
                    emailId = sourceEmail?.id.orEmpty(),
                    threadId = sourceEmail?.threadId.orEmpty(),
                    recipientEmail = recipientEmail.ifBlank { sourceEmail?.senderEmail.orEmpty() },
                    subject = res.data.subject,
                    body = res.data.body,
                    tone = tone,
                    language = languageCode,
                    status = "DRAFT",
                    providerUsed = res.data.providerUsed.displayName,
                    modelUsed = res.data.modelUsed
                )
                repo.db.workspaceDao().upsertDraft(draft)
                showMessage("Draft created via ${res.data.providerUsed.displayName} (saved for review — not sent).")
            }
            is AiResult.Failure -> showMessage(res.error.message, isError = true)
        }
    }

    fun updateDraft(draft: AiDraftEntity) = viewModelScope.launch {
        repo.db.workspaceDao().upsertDraft(draft.copy(updatedAt = System.currentTimeMillis()))
        showMessage("Draft updated.")
    }

    fun deleteDraft(draftId: String) = viewModelScope.launch {
        repo.db.workspaceDao().deleteDraft(currentUserId(), draftId)
        showMessage("Draft deleted.")
    }

    fun syncDraftToProvider(draft: AiDraftEntity) = viewModelScope.launch {
        setBusy(true, "Saving draft to email provider...")
        val res = repo.syncDraftToProvider(currentUserId(), draft)
        setBusy(false)
        res.fold(
            onSuccess = { showMessage("Draft synced remotely (ID: ${it.take(12)}).") },
            onFailure = { showMessage(it.message ?: "Remote draft sync failed.", isError = true) }
        )
    }

    fun sendDraftConfirmed(draft: AiDraftEntity) = viewModelScope.launch {
        setBusy(true, "Sending confirmed email...")
        val res = repo.sendDraftWithExplicitConfirmation(currentUserId(), draft)
        setBusy(false)
        res.fold(
            onSuccess = { showMessage("Email sent to ${draft.recipientEmail}.") },
            onFailure = { showMessage(it.message ?: "Send failed.", isError = true) }
        )
    }

    fun askYourInbox(question: String) = viewModelScope.launch {
        val clean = question.trim()
        if (clean.isEmpty()) return@launch
        val uid = currentUserId().ifBlank { return@launch }
        val state = uiState.value
        setBusy(true, "Searching inbox & grounding AI answer...")
        val res = repo.aiAgents.askYourInbox(
            uid, clean, state.emails, state.preferences.language.code, state.preferences.allowPaidFallback
        )
        setBusy(false)
        val entry = when (res) {
            is AiResult.Success -> AskInboxMessage(question = clean, answer = res.data)
            is AiResult.Failure -> AskInboxMessage(question = clean, error = res.error.message)
        }
        _ephemeral.update { it.copy(askInboxHistory = listOf(entry) + it.askInboxHistory) }
    }

    fun runSecurityAudit(email: EmailEntity) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val atts = repo.db.workspaceDao().getAttachmentsForEmail(uid, email.id).map { it.fileName }
        setBusy(true, "Running Security & Phishing Agent...")
        val output = repo.aiAgents.runSecurityAudit(uid, email, atts, uiState.value.preferences.allowPaidFallback)
        setBusy(false)
        repo.db.emailDao().upsert(
            email.copy(
                securityRiskLevel = output.heuristicReport.riskLevel.name,
                securityScore = output.heuristicReport.score,
                securityFlagsCsv = output.heuristicReport.flags.joinToString(" | ")
            )
        )
        _ephemeral.update { it.copy(lastSecurityAudit = email.id to output) }
        showMessage("Security scan complete: ${output.aiVerdict} (score ${output.heuristicReport.score}/100).")
    }

    fun runScheduleAgent(email: EmailEntity) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Running Schedule Agent...")
        val res = repo.aiAgents.extractSchedule(uid, email, uiState.value.preferences.allowPaidFallback)
        setBusy(false)
        when (res) {
            is AiResult.Success -> {
                _ephemeral.update { it.copy(lastScheduleResult = email.id to res.data) }
                showMessage("Extracted ${res.data.appointments.size} appointment(s) & ${res.data.deadlines.size} deadline(s).")
            }
            is AiResult.Failure -> showMessage(res.error.message, isError = true)
        }
    }

    fun runFollowUpAgent(email: EmailEntity) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        setBusy(true, "Running Follow-up Agent...")
        val res = repo.aiAgents.analyzeFollowUp(uid, email, uiState.value.preferences.allowPaidFallback)
        setBusy(false)
        when (res) {
            is AiResult.Success -> {
                val out = res.data
                repo.db.emailDao().upsert(email.copy(followUpState = out.followUpType))
                if (out.followUpType != "INFORMATIONAL_ONLY") {
                    repo.db.workspaceDao().upsertReminder(
                        FollowUpReminderEntity(
                            id = UUID.randomUUID().toString(),
                            userId = uid,
                            emailId = email.id,
                            emailSubject = email.subject,
                            senderEmail = email.senderEmail,
                            followUpType = out.followUpType,
                            dueAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L,
                            extractedDeadlineText = out.extractedDeadlineText,
                            suggestedMessage = out.suggestedFollowUpMessage
                        )
                    )
                }
                showMessage("Follow-up state: ${out.followUpType} (${out.providerUsed.displayName}).")
            }
            is AiResult.Failure -> showMessage(res.error.message, isError = true)
        }
    }

    fun summarizeAttachment(attachment: AttachmentEntity, question: String = "") = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        if (attachment.extractedText.isBlank()) {
            showMessage("No readable text extracted from ${attachment.fileName}.", isError = true)
            return@launch
        }
        setBusy(true, "Running Attachment Intelligence Agent...")
        val res = repo.aiAgents.analyzeAttachmentText(
            uid, attachment.fileName, attachment.mimeType, attachment.extractedText,
            question, uiState.value.preferences.allowPaidFallback
        )
        setBusy(false)
        when (res) {
            is AiResult.Success -> {
                repo.db.workspaceDao().upsertAttachment(attachment.copy(aiSummary = res.data.text))
                showMessage("Analyzed '${attachment.fileName}' via ${res.data.providerUsed.displayName}.")
            }
            is AiResult.Failure -> showMessage(res.error.message, isError = true)
        }
    }

    fun deleteAttachment(attachmentId: String) = viewModelScope.launch {
        repo.db.workspaceDao().deleteAttachment(currentUserId(), attachmentId)
    }

    // --- Follow-ups & Reminders ---

    fun createReminder(
        email: EmailEntity?,
        subject: String,
        senderEmail: String,
        followUpType: String,
        dueInHours: Int,
        deadlineNote: String
    ) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val reminder = FollowUpReminderEntity(
            id = UUID.randomUUID().toString(),
            userId = uid,
            emailId = email?.id.orEmpty(),
            emailSubject = subject.ifBlank { email?.subject ?: "Follow-up Task" },
            senderEmail = senderEmail.ifBlank { email?.senderEmail.orEmpty() },
            followUpType = followUpType,
            dueAt = System.currentTimeMillis() + dueInHours.coerceAtLeast(1) * 3_600_000L,
            extractedDeadlineText = deadlineNote
        )
        repo.db.workspaceDao().upsertReminder(reminder)
        showMessage("Follow-up reminder scheduled.")
    }

    fun snoozeReminder(id: String, extraHours: Int = 24) = viewModelScope.launch {
        val newDue = System.currentTimeMillis() + extraHours * 3_600_000L
        repo.db.workspaceDao().snoozeReminder(currentUserId(), id, newDue)
        showMessage("Snoozed reminder by ${extraHours}h.")
    }

    fun completeReminder(id: String) = viewModelScope.launch {
        repo.db.workspaceDao().updateReminderStatus(currentUserId(), id, "COMPLETED")
        showMessage("Reminder marked completed.")
    }

    fun deleteReminder(id: String) = viewModelScope.launch {
        repo.db.workspaceDao().deleteReminder(currentUserId(), id)
    }

    // --- Automation Studio ---

    fun createAutomationRule(
        name: String,
        description: String,
        triggerType: String,
        conditionValue: String,
        aiAnalysisTask: String,
        requiresApproval: Boolean,
        actionType: String,
        actionParam: String
    ) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        if (name.isBlank()) {
            showMessage("Workflow name is required.", isError = true)
            return@launch
        }
        val rule = AutomationRuleEntity(
            id = UUID.randomUUID().toString(),
            userId = uid,
            name = name.trim(),
            description = description.trim(),
            triggerType = triggerType,
            conditionValue = conditionValue.trim(),
            aiAnalysisTask = aiAnalysisTask,
            requiresApproval = requiresApproval,
            actionType = actionType,
            actionParam = actionParam.trim(),
            enabled = true
        )
        repo.db.workspaceDao().upsertAutomationRule(rule)
        repo.logAudit(uid, "AUTOMATION_RULE_CREATED", "USER", rule.id, "Created rule '${rule.name}' ($triggerType -> $actionType).")
        showMessage("Workflow '${rule.name}' created.")
    }

    fun toggleAutomationRule(id: String, enabled: Boolean) = viewModelScope.launch {
        repo.db.workspaceDao().setAutomationRuleEnabled(currentUserId(), id, enabled)
    }

    fun deleteAutomationRule(id: String) = viewModelScope.launch {
        repo.db.workspaceDao().deleteAutomationRule(currentUserId(), id)
    }

    fun runAutomationsOnInbox() = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val state = uiState.value
        if (state.emails.isEmpty()) {
            showMessage("No inbox emails to evaluate.", isError = true)
            return@launch
        }
        setBusy(true, "Evaluating active workflows (duplicate-protected)...")
        var triggered = 0
        for (email in state.emails.filter { !it.isArchived }) {
            triggered += repo.automationEngine.evaluateEmailAgainstRules(uid, email, state.preferences).size
        }
        setBusy(false)
        showMessage("Evaluated workflows: $triggered new execution(s) recorded.")
    }

    fun approveExecution(execution: AutomationExecutionEntity) = viewModelScope.launch {
        val uid = currentUserId().ifBlank { return@launch }
        val state = uiState.value
        val rule = state.automationRules.firstOrNull { it.id == execution.ruleId }
        val email = state.emails.firstOrNull { it.id == execution.emailId }
        if (rule == null || email == null) {
            showMessage("Associated workflow rule or email no longer exists.", isError = true)
            return@launch
        }
        setBusy(true, "Executing approved workflow...")
        val outcome = repo.automationEngine.approveAndExecute(uid, execution, rule, email, state.preferences)
        setBusy(false)
        showMessage("${outcome.first}: ${outcome.second}", isError = outcome.first == "FAILED")
    }

    fun rejectExecution(executionId: String) = viewModelScope.launch {
        repo.automationEngine.rejectExecution(currentUserId(), executionId)
        showMessage("Workflow execution rejected.")
    }

    // --- Notifications ---

    fun markNotificationRead(id: String) = viewModelScope.launch {
        repo.db.workspaceDao().markNotificationRead(currentUserId(), id)
    }

    fun markAllNotificationsRead() = viewModelScope.launch {
        repo.db.workspaceDao().markAllNotificationsRead(currentUserId())
    }

    fun clearNotifications() = viewModelScope.launch {
        repo.db.workspaceDao().clearNotifications(currentUserId())
    }

    // Internal combine helper holders
    private data class PartA(
        val user: UserEntity?,
        val accounts: List<ConnectedAccountEntity>,
        val providers: List<AiProviderConfigEntity>,
        val models: List<AiModelEntity>,
        val rules: List<RoutingRuleEntity>
    )

    private data class PartB(
        val logs: List<AiUsageLogEntity>,
        val emails: List<EmailEntity>,
        val summaries: Map<String, AiSummaryEntity>,
        val drafts: List<AiDraftEntity>,
        val reminders: List<FollowUpReminderEntity>
    )

    private data class PartC(
        val rules: List<AutomationRuleEntity>,
        val execs: List<AutomationExecutionEntity>,
        val atts: List<AttachmentEntity>,
        val notifs: List<NotificationEntity>,
        val audits: List<AuditLogEntity>
    )

    private data class UserDataBundle(
        val user: UserEntity? = null,
        val accounts: List<ConnectedAccountEntity> = emptyList(),
        val providers: List<AiProviderConfigEntity> = emptyList(),
        val models: List<AiModelEntity> = emptyList(),
        val routingRules: List<RoutingRuleEntity> = emptyList(),
        val usageLogs: List<AiUsageLogEntity> = emptyList(),
        val emails: List<EmailEntity> = emptyList(),
        val summaries: Map<String, AiSummaryEntity> = emptyMap(),
        val drafts: List<AiDraftEntity> = emptyList(),
        val reminders: List<FollowUpReminderEntity> = emptyList(),
        val automationRules: List<AutomationRuleEntity> = emptyList(),
        val automationExecutions: List<AutomationExecutionEntity> = emptyList(),
        val attachments: List<AttachmentEntity> = emptyList(),
        val notifications: List<NotificationEntity> = emptyList(),
        val auditLogs: List<AuditLogEntity> = emptyList(),
        val customCategories: List<CustomCategoryEntity> = emptyList()
    )

    class Factory(private val repo: WafaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WafaViewModel(repo) as T
        }
    }
}
