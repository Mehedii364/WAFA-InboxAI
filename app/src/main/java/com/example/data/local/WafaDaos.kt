package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeById(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM connected_accounts WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAccounts(userId: String): Flow<List<ConnectedAccountEntity>>

    @Query("SELECT * FROM connected_accounts WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAccounts(userId: String): List<ConnectedAccountEntity>

    @Query("SELECT * FROM connected_accounts WHERE id = :accountId AND userId = :userId LIMIT 1")
    suspend fun getById(userId: String, accountId: String): ConnectedAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: ConnectedAccountEntity)

    @Query("DELETE FROM connected_accounts WHERE id = :accountId AND userId = :userId")
    suspend fun delete(userId: String, accountId: String)

    @Query("DELETE FROM connected_accounts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_provider_configs WHERE userId = :userId ORDER BY providerType ASC")
    fun observeProviders(userId: String): Flow<List<AiProviderConfigEntity>>

    @Query("SELECT * FROM ai_provider_configs WHERE userId = :userId")
    suspend fun getProviders(userId: String): List<AiProviderConfigEntity>

    @Query("SELECT * FROM ai_provider_configs WHERE userId = :userId AND providerType = :providerType LIMIT 1")
    suspend fun getProvider(userId: String, providerType: String): AiProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(config: AiProviderConfigEntity)

    @Query("SELECT * FROM ai_models WHERE userId = :userId ORDER BY providerType ASC, displayName ASC")
    fun observeModels(userId: String): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE userId = :userId AND providerType = :providerType")
    suspend fun getModelsForProvider(userId: String, providerType: String): List<AiModelEntity>

    @Query("DELETE FROM ai_models WHERE userId = :userId AND providerType = :providerType")
    suspend fun clearModelsForProvider(userId: String, providerType: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AiModelEntity>)

    @Query("SELECT * FROM routing_rules WHERE userId = :userId ORDER BY taskType ASC")
    fun observeRoutingRules(userId: String): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM routing_rules WHERE userId = :userId AND taskType = :taskType LIMIT 1")
    suspend fun getRoutingRule(userId: String, taskType: String): RoutingRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutingRule(rule: RoutingRuleEntity)

    @Query("SELECT * FROM ai_usage_logs WHERE userId = :userId ORDER BY createdAt DESC LIMIT 200")
    fun observeUsageLogs(userId: String): Flow<List<AiUsageLogEntity>>

    @Query("SELECT COUNT(*) FROM ai_usage_logs WHERE userId = :userId AND providerType = :providerType AND createdAt >= :sinceMs")
    suspend fun countRequestsSince(userId: String, providerType: String, sinceMs: Long): Int

    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM ai_usage_logs WHERE userId = :userId AND providerType = :providerType AND createdAt >= :sinceMs")
    suspend fun sumTokensSince(userId: String, providerType: String, sinceMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageLog(log: AiUsageLogEntity)

    @Query("DELETE FROM ai_provider_configs WHERE userId = :userId")
    suspend fun deleteAllProviders(userId: String)

    @Query("DELETE FROM ai_models WHERE userId = :userId")
    suspend fun deleteAllModels(userId: String)

    @Query("DELETE FROM routing_rules WHERE userId = :userId")
    suspend fun deleteAllRoutingRules(userId: String)

    @Query("DELETE FROM ai_usage_logs WHERE userId = :userId")
    suspend fun deleteAllUsageLogs(userId: String)
}

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails WHERE userId = :userId AND isArchived = 0 ORDER BY receivedAt DESC")
    fun observeInbox(userId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE userId = :userId ORDER BY receivedAt DESC")
    fun observeAllEmails(userId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE userId = :userId ORDER BY receivedAt DESC")
    suspend fun getAllEmails(userId: String): List<EmailEntity>

    @Query("SELECT * FROM emails WHERE id = :emailId AND userId = :userId LIMIT 1")
    suspend fun getById(userId: String, emailId: String): EmailEntity?

    @Query("SELECT * FROM emails WHERE userId = :userId AND threadId = :threadId ORDER BY receivedAt ASC")
    fun observeThread(userId: String, threadId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE userId = :userId AND threadId = :threadId ORDER BY receivedAt ASC")
    suspend fun getThread(userId: String, threadId: String): List<EmailEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(email: EmailEntity)

    @Query("UPDATE emails SET isRead = :isRead WHERE id = :emailId AND userId = :userId")
    suspend fun setRead(userId: String, emailId: String, isRead: Boolean)

    @Query("UPDATE emails SET isArchived = :archived WHERE id = :emailId AND userId = :userId")
    suspend fun setArchived(userId: String, emailId: String, archived: Boolean)

    @Query("UPDATE emails SET category = :category WHERE id = :emailId AND userId = :userId")
    suspend fun setCategory(userId: String, emailId: String, category: String)

    @Query("UPDATE emails SET labelsCsv = :labelsCsv WHERE id = :emailId AND userId = :userId")
    suspend fun setLabels(userId: String, emailId: String, labelsCsv: String)

    @Query("DELETE FROM emails WHERE id = :emailId AND userId = :userId")
    suspend fun deleteById(userId: String, emailId: String)

    @Query("DELETE FROM emails WHERE accountId = :accountId AND userId = :userId")
    suspend fun deleteByAccount(userId: String, accountId: String)

    @Query("DELETE FROM emails WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT * FROM ai_summaries WHERE userId = :userId")
    fun observeSummaries(userId: String): Flow<List<AiSummaryEntity>>

    @Query("SELECT * FROM ai_summaries WHERE emailId = :emailId AND userId = :userId LIMIT 1")
    suspend fun getSummary(userId: String, emailId: String): AiSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: AiSummaryEntity)

    @Query("DELETE FROM ai_summaries WHERE userId = :userId")
    suspend fun deleteAllSummaries(userId: String)

    @Query("SELECT * FROM custom_categories WHERE userId = :userId ORDER BY name ASC")
    fun observeCustomCategories(userId: String): Flow<List<CustomCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomCategory(category: CustomCategoryEntity)

    @Query("DELETE FROM custom_categories WHERE id = :id AND userId = :userId")
    suspend fun deleteCustomCategory(userId: String, id: String)
}

@Dao
interface WorkspaceDao {
    // Drafts
    @Query("SELECT * FROM ai_drafts WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeDrafts(userId: String): Flow<List<AiDraftEntity>>

    @Query("SELECT * FROM ai_drafts WHERE id = :draftId AND userId = :userId LIMIT 1")
    suspend fun getDraftById(userId: String, draftId: String): AiDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: AiDraftEntity)

    @Query("DELETE FROM ai_drafts WHERE id = :draftId AND userId = :userId")
    suspend fun deleteDraft(userId: String, draftId: String)

    // Follow-up reminders
    @Query("SELECT * FROM followup_reminders WHERE userId = :userId ORDER BY dueAt ASC")
    fun observeReminders(userId: String): Flow<List<FollowUpReminderEntity>>

    @Query("SELECT * FROM followup_reminders WHERE userId = :userId AND status IN ('PENDING', 'SNOOZED') AND dueAt <= :nowMs AND notifiedAt = 0")
    suspend fun getDueUnnotifiedReminders(userId: String, nowMs: Long): List<FollowUpReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: FollowUpReminderEntity)

    @Query("UPDATE followup_reminders SET status = :status WHERE id = :id AND userId = :userId")
    suspend fun updateReminderStatus(userId: String, id: String, status: String)

    @Query("UPDATE followup_reminders SET dueAt = :newDueAt, status = 'SNOOZED', notifiedAt = 0 WHERE id = :id AND userId = :userId")
    suspend fun snoozeReminder(userId: String, id: String, newDueAt: Long)

    @Query("UPDATE followup_reminders SET notifiedAt = :notifiedAt WHERE id = :id AND userId = :userId")
    suspend fun markReminderNotified(userId: String, id: String, notifiedAt: Long)

    @Query("DELETE FROM followup_reminders WHERE id = :id AND userId = :userId")
    suspend fun deleteReminder(userId: String, id: String)

    // Automations
    @Query("SELECT * FROM automation_rules WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAutomationRules(userId: String): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE userId = :userId AND enabled = 1")
    suspend fun getEnabledRules(userId: String): List<AutomationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationRule(rule: AutomationRuleEntity)

    @Query("UPDATE automation_rules SET enabled = :enabled WHERE id = :id AND userId = :userId")
    suspend fun setAutomationRuleEnabled(userId: String, id: String, enabled: Boolean)

    @Query("DELETE FROM automation_rules WHERE id = :id AND userId = :userId")
    suspend fun deleteAutomationRule(userId: String, id: String)

    @Query("SELECT * FROM automation_executions WHERE userId = :userId ORDER BY executedAt DESC LIMIT 150")
    fun observeAutomationExecutions(userId: String): Flow<List<AutomationExecutionEntity>>

    @Query("SELECT * FROM automation_executions WHERE triggerEventKey = :key LIMIT 1")
    suspend fun getExecutionByTriggerKey(key: String): AutomationExecutionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExecutionIgnoreDuplicate(execution: AutomationExecutionEntity): Long

    @Query("UPDATE automation_executions SET status = :status, resultMessage = :message, executedAt = :executedAt WHERE id = :id AND userId = :userId")
    suspend fun updateExecutionStatus(userId: String, id: String, status: String, message: String, executedAt: Long)

    // Attachments
    @Query("SELECT * FROM attachments WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAttachments(userId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE userId = :userId AND emailId = :emailId")
    suspend fun getAttachmentsForEmail(userId: String, emailId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id AND userId = :userId")
    suspend fun deleteAttachment(userId: String, id: String)

    // Notifications
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC LIMIT 100")
    fun observeNotifications(userId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id AND userId = :userId")
    suspend fun markNotificationRead(userId: String, id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllNotificationsRead(userId: String)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun clearNotifications(userId: String)

    // Audit Logs
    @Query("SELECT * FROM audit_logs WHERE userId = :userId ORDER BY createdAt DESC LIMIT 200")
    fun observeAuditLogs(userId: String): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    // User Wipe
    @Query("DELETE FROM ai_drafts WHERE userId = :userId")
    suspend fun deleteAllDrafts(userId: String)

    @Query("DELETE FROM followup_reminders WHERE userId = :userId")
    suspend fun deleteAllReminders(userId: String)

    @Query("DELETE FROM automation_rules WHERE userId = :userId")
    suspend fun deleteAllRules(userId: String)

    @Query("DELETE FROM automation_executions WHERE userId = :userId")
    suspend fun deleteAllExecutions(userId: String)

    @Query("DELETE FROM attachments WHERE userId = :userId")
    suspend fun deleteAllAttachments(userId: String)

    @Query("DELETE FROM audit_logs WHERE userId = :userId")
    suspend fun deleteAllAuditLogs(userId: String)
}
