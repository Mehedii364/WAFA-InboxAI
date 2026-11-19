package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val backendBaseUrl: String = "",
    val encryptedBackendToken: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "connected_accounts",
    indices = [Index(value = ["userId"]), Index(value = ["userId", "provider", "emailAddress"], unique = true)]
)
data class ConnectedAccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val provider: String, // GMAIL, OUTLOOK, YAHOO_IMAP, GENERIC_IMAP, LOCAL_EML
    val emailAddress: String,
    val displayName: String,
    val encryptedAccessToken: String = "",
    val encryptedRefreshToken: String = "",
    val tokenExpiresAt: Long = 0L,
    val oauthClientId: String = "",
    val scopes: String = "",
    val imapHost: String = "",
    val imapPort: Int = 993,
    val smtpHost: String = "",
    val smtpPort: Int = 587,
    val syncCursor: String = "",
    val lastSyncedAt: Long = 0L,
    val status: String = "CONNECTED", // CONNECTED, TOKEN_EXPIRED, NEEDS_BACKEND_BRIDGE, ERROR
    val lastError: String = "",
    val preferredAiProvider: String = "",
    val preferredAiModel: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_provider_configs",
    indices = [Index(value = ["userId"]), Index(value = ["userId", "providerType"], unique = true)]
)
data class AiProviderConfigEntity(
    @PrimaryKey val id: String, // "$userId:$providerType"
    val userId: String,
    val providerType: String, // GEMINI, GROQ, OPENROUTER, HUGGINGFACE, CLOUDFLARE, XPIKI
    val displayName: String,
    val baseUrl: String,
    val accountId: String = "", // Cloudflare Account ID
    val authHeaderMode: String = "BEARER", // BEARER, X_GOOG_API_KEY
    val encryptedApiKey: String = "",
    val maskedKey: String = "",
    val enabled: Boolean = false,
    val isPaidProvider: Boolean = false,
    val defaultModel: String = "",
    val dailyRequestLimit: Int = 500,
    val dailyTokenLimit: Int = 250_000,
    val connectionVerified: Boolean = false,
    val lastTestedAt: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val lastError: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_models",
    indices = [Index(value = ["userId", "providerType"])]
)
data class AiModelEntity(
    @PrimaryKey val id: String, // "$userId:$providerType:$modelId"
    val userId: String,
    val providerType: String,
    val modelId: String,
    val displayName: String,
    val supportsJson: Boolean = true,
    val supportsVision: Boolean = false,
    val contextWindow: Int = 0,
    val promptCostPer1k: Double? = null,
    val completionCostPer1k: Double? = null,
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "routing_rules",
    indices = [Index(value = ["userId", "taskType"], unique = true)]
)
data class RoutingRuleEntity(
    @PrimaryKey val id: String, // "$userId:$taskType"
    val userId: String,
    val taskType: String, // DEFAULT, FAST, REASONING, DOCUMENT, CLASSIFICATION, SUMMARIZATION, DRAFTING, SECURITY, RESEARCH, FOLLOWUP
    val primaryProvider: String,
    val primaryModel: String,
    val fallbackProvider: String = "",
    val fallbackModel: String = "",
    val allowPaidFallback: Boolean = false,
    val timeoutSeconds: Int = 45,
    val maxRetries: Int = 2
)

@Entity(
    tableName = "ai_usage_logs",
    indices = [Index(value = ["userId", "createdAt"])]
)
data class AiUsageLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val providerType: String,
    val modelId: String,
    val taskType: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val latencyMs: Long = 0L,
    val estimatedCostUsd: Double? = null,
    val success: Boolean,
    val errorCode: String = "",
    val errorMessage: String = "",
    val fallbackUsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "emails",
    indices = [
        Index(value = ["userId", "receivedAt"]),
        Index(value = ["userId", "threadId"]),
        Index(value = ["userId", "accountId", "remoteMessageId"], unique = true)
    ]
)
data class EmailEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val accountId: String,
    val remoteMessageId: String,
    val threadId: String,
    val senderName: String,
    val senderEmail: String,
    val replyTo: String = "",
    val recipients: String = "",
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val authHeaders: String = "",
    val category: String = "Primary",
    val priority: String = "Normal", // Urgent, High, Normal, Low
    val priorityReason: String = "",
    val followUpState: String = "NONE", // NONE, NEEDS_MY_REPLY, AWAITING_THEIR_REPLY, HAS_DEADLINE, INFORMATIONAL_ONLY
    val labelsCsv: String = "",
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val isStarred: Boolean = false,
    val hasAttachments: Boolean = false,
    val securityRiskLevel: String = "SAFE",
    val securityScore: Int = 0,
    val securityFlagsCsv: String = "",
    val receivedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_summaries",
    indices = [Index(value = ["userId"])]
)
data class AiSummaryEntity(
    @PrimaryKey val emailId: String,
    val userId: String,
    val shortSummary: String,
    val detailedSummary: String,
    val conversationSummary: String = "",
    val actionItemsCsv: String = "",
    val deadlinesCsv: String = "",
    val importantDatesCsv: String = "",
    val entitiesCsv: String = "",
    val financialAmountsCsv: String = "",
    val sourceReference: String = "",
    val providerUsed: String,
    val modelUsed: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_drafts",
    indices = [Index(value = ["userId", "updatedAt"])]
)
data class AiDraftEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val accountId: String,
    val emailId: String = "",
    val threadId: String = "",
    val recipientEmail: String,
    val subject: String,
    val body: String,
    val tone: String = "Professional",
    val language: String = "en",
    val status: String = "DRAFT", // DRAFT, PENDING_APPROVAL, SENT, DISCARDED
    val providerUsed: String = "",
    val modelUsed: String = "",
    val remoteDraftId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "followup_reminders",
    indices = [Index(value = ["userId", "dueAt"])]
)
data class FollowUpReminderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val emailId: String,
    val emailSubject: String,
    val senderEmail: String,
    val followUpType: String, // NEEDS_MY_REPLY, AWAITING_THEIR_REPLY, HAS_DEADLINE, CUSTOM
    val dueAt: Long,
    val extractedDeadlineText: String = "",
    val suggestedMessage: String = "",
    val status: String = "PENDING", // PENDING, SNOOZED, COMPLETED, DISMISSED
    val notifiedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "automation_rules",
    indices = [Index(value = ["userId"])]
)
data class AutomationRuleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val description: String = "",
    val triggerType: String, // NEW_EMAIL, SPECIFIC_SENDER, SUBJECT_KEYWORD, ATTACHMENT_RECEIVED, IMPORTANT_DETECTED, FOLLOWUP_DEADLINE
    val conditionValue: String = "",
    val aiAnalysisTask: String = "NONE", // NONE, CLASSIFICATION, PRIORITY, SUMMARIZATION, SECURITY
    val requiresApproval: Boolean = true,
    val actionType: String, // APPLY_CATEGORY, ADD_LABEL, CREATE_REMINDER, PREPARE_DRAFT, ARCHIVE, MARK_READ, CREATE_TASK, REQUEST_APPROVAL
    val actionParam: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "automation_executions",
    indices = [
        Index(value = ["userId", "executedAt"]),
        Index(value = ["triggerEventKey"], unique = true)
    ]
)
data class AutomationExecutionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val ruleId: String,
    val ruleName: String,
    val emailId: String,
    val triggerEventKey: String, // "$userId:$ruleId:$emailId" -> idempotency lock
    val status: String, // PENDING_APPROVAL, EXECUTED, BLOCKED_MODE, REJECTED, FAILED
    val resultMessage: String,
    val executedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attachments",
    indices = [Index(value = ["userId", "emailId"])]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val emailId: String = "",
    val remoteAttachmentId: String = "",
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localUri: String = "",
    val extractedText: String = "",
    val aiSummary: String = "",
    val isSafe: Boolean = true,
    val safetyWarning: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["userId", "createdAt"])]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val category: String, // FOLLOW_UP, APPROVAL, SECURITY, SYNC, SYSTEM
    val relatedEmailId: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["userId", "createdAt"])]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val action: String,
    val actor: String, // USER, AI_AGENT, AUTOMATION, SYSTEM
    val targetId: String = "",
    val details: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "custom_categories",
    indices = [Index(value = ["userId", "name"], unique = true)]
)
data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
