package com.example.domain.agents

import com.example.data.local.AiDraftEntity
import com.example.data.local.AiOperationMode
import com.example.data.local.AuditLogEntity
import com.example.data.local.AutomationExecutionEntity
import com.example.data.local.AutomationRuleEntity
import com.example.data.local.EmailDao
import com.example.data.local.EmailEntity
import com.example.data.local.FollowUpReminderEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.WafaPreferences
import com.example.data.local.WorkspaceDao
import java.util.UUID

/**
 * Workflow Agent & Automation Studio Execution Engine.
 * Pipeline: Trigger -> Conditions -> AI Analysis -> Approval -> Action -> Result
 * Enforces idempotency via unique triggerEventKey and respects Copilot / Approval / Smart Automation modes.
 */
class AutomationEngine(
    private val emailDao: EmailDao,
    private val workspaceDao: WorkspaceDao,
    private val aiAgents: WafaAiAgents
) {

    suspend fun evaluateEmailAgainstRules(
        userId: String,
        email: EmailEntity,
        preferences: WafaPreferences
    ): List<AutomationExecutionEntity> {
        val rules = workspaceDao.getEnabledRules(userId)
        val results = mutableListOf<AutomationExecutionEntity>()

        for (rule in rules) {
            if (!matchesTriggerAndCondition(rule, email)) continue

            // Idempotency lock: never execute the same workflow twice for the same email trigger event
            val triggerKey = "$userId:${rule.id}:${email.id}"
            val existing = workspaceDao.getExecutionByTriggerKey(triggerKey)
            if (existing != null) continue

            // Sensitive actions ALWAYS require explicit approval regardless of rule settings
            val isSensitiveAction = rule.actionType in setOf("SEND_EMAIL", "FORWARD_EMAIL", "DELETE_EMAIL")
            val permissionAllowed = isActionPermitted(rule.actionType, preferences)

            val executionId = UUID.randomUUID().toString()
            when {
                preferences.operationMode == AiOperationMode.COPILOT -> {
                    val exec = AutomationExecutionEntity(
                        id = executionId,
                        userId = userId,
                        ruleId = rule.id,
                        ruleName = rule.name,
                        emailId = email.id,
                        triggerEventKey = triggerKey,
                        status = "PENDING_APPROVAL",
                        resultMessage = "Copilot Mode active: workflow matched '${email.subject}' and awaits manual user approval."
                    )
                    if (workspaceDao.insertExecutionIgnoreDuplicate(exec) != -1L) {
                        results += exec
                        notifyApproval(userId, rule.name, email)
                    }
                }
                isSensitiveAction || rule.requiresApproval ||
                    preferences.operationMode == AiOperationMode.APPROVAL || !permissionAllowed -> {
                    val reason = when {
                        isSensitiveAction -> "Sensitive action requires explicit confirmation."
                        !permissionAllowed -> "Action permission disabled in Security Center; queued for approval."
                        else -> "Approval Mode: waiting for user confirmation."
                    }
                    val exec = AutomationExecutionEntity(
                        id = executionId,
                        userId = userId,
                        ruleId = rule.id,
                        ruleName = rule.name,
                        emailId = email.id,
                        triggerEventKey = triggerKey,
                        status = "PENDING_APPROVAL",
                        resultMessage = reason
                    )
                    if (workspaceDao.insertExecutionIgnoreDuplicate(exec) != -1L) {
                        results += exec
                        notifyApproval(userId, rule.name, email)
                    }
                }
                else -> {
                    // Smart Automation Mode + explicitly permitted low-risk action
                    val placeholder = AutomationExecutionEntity(
                        id = executionId,
                        userId = userId,
                        ruleId = rule.id,
                        ruleName = rule.name,
                        emailId = email.id,
                        triggerEventKey = triggerKey,
                        status = "EXECUTING",
                        resultMessage = "Executing workflow..."
                    )
                    if (workspaceDao.insertExecutionIgnoreDuplicate(placeholder) != -1L) {
                        val outcome = executeAction(userId, rule, email, preferences)
                        workspaceDao.updateExecutionStatus(
                            userId = userId,
                            id = executionId,
                            status = outcome.first,
                            message = outcome.second,
                            executedAt = System.currentTimeMillis()
                        )
                        results += placeholder.copy(status = outcome.first, resultMessage = outcome.second)
                    }
                }
            }
        }
        return results
    }

    suspend fun approveAndExecute(
        userId: String,
        execution: AutomationExecutionEntity,
        rule: AutomationRuleEntity,
        email: EmailEntity,
        preferences: WafaPreferences
    ): Pair<String, String> {
        val outcome = executeAction(userId, rule, email, preferences)
        workspaceDao.updateExecutionStatus(
            userId = userId,
            id = execution.id,
            status = outcome.first,
            message = outcome.second,
            executedAt = System.currentTimeMillis()
        )
        workspaceDao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                action = "WORKFLOW_APPROVED_AND_EXECUTED",
                actor = "USER",
                targetId = execution.id,
                details = "Rule '${rule.name}' -> ${outcome.first}: ${outcome.second}"
            )
        )
        return outcome
    }

    suspend fun rejectExecution(userId: String, executionId: String) {
        workspaceDao.updateExecutionStatus(
            userId = userId,
            id = executionId,
            status = "REJECTED",
            message = "Rejected by user.",
            executedAt = System.currentTimeMillis()
        )
    }

    private suspend fun executeAction(
        userId: String,
        rule: AutomationRuleEntity,
        email: EmailEntity,
        preferences: WafaPreferences
    ): Pair<String, String> {
        return try {
            when (rule.actionType) {
                "APPLY_CATEGORY" -> {
                    val cat = rule.actionParam.ifBlank { "Work" }
                    emailDao.setCategory(userId, email.id, cat)
                    "EXECUTED" to "Applied category '$cat' to '${email.subject}'."
                }
                "ADD_LABEL" -> {
                    val label = rule.actionParam.ifBlank { "Automated" }
                    val existing = email.labelsCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
                    existing += label
                    emailDao.setLabels(userId, email.id, existing.joinToString(","))
                    "EXECUTED" to "Added label '$label'."
                }
                "CREATE_REMINDER", "CREATE_TASK" -> {
                    val due = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
                    workspaceDao.upsertReminder(
                        FollowUpReminderEntity(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            emailId = email.id,
                            emailSubject = email.subject,
                            senderEmail = email.senderEmail,
                            followUpType = "NEEDS_MY_REPLY",
                            dueAt = due,
                            extractedDeadlineText = rule.actionParam.ifBlank { "Workflow follow-up (+24h)" }
                        )
                    )
                    "EXECUTED" to "Created follow-up reminder for '${email.subject}'."
                }
                "PREPARE_DRAFT" -> {
                    val aiRes = aiAgents.generateDraft(
                        userId = userId,
                        sourceEmail = email,
                        tone = rule.actionParam.ifBlank { "Professional" },
                        languageCode = preferences.language.code,
                        customInstructions = "Prepare a helpful reply draft for user review. Do not send.",
                        allowPaidFallback = preferences.allowPaidFallback
                    )
                    when (aiRes) {
                        is com.example.data.ai.AiResult.Success -> {
                            workspaceDao.upsertDraft(
                                AiDraftEntity(
                                    id = UUID.randomUUID().toString(),
                                    userId = userId,
                                    accountId = email.accountId,
                                    emailId = email.id,
                                    threadId = email.threadId,
                                    recipientEmail = email.senderEmail,
                                    subject = aiRes.data.subject,
                                    body = aiRes.data.body,
                                    tone = rule.actionParam.ifBlank { "Professional" },
                                    language = preferences.language.code,
                                    status = "PENDING_APPROVAL",
                                    providerUsed = aiRes.data.providerUsed.name,
                                    modelUsed = aiRes.data.modelUsed
                                )
                            )
                            "EXECUTED" to "Prepared AI draft via ${aiRes.data.providerUsed.displayName} (saved for review; not sent)."
                        }
                        is com.example.data.ai.AiResult.Failure ->
                            "FAILED" to "Draft generation failed: ${aiRes.error.message}"
                    }
                }
                "ARCHIVE" -> {
                    emailDao.setArchived(userId, email.id, true)
                    "EXECUTED" to "Archived '${email.subject}'."
                }
                "MARK_READ" -> {
                    emailDao.setRead(userId, email.id, true)
                    "EXECUTED" to "Marked '${email.subject}' as read."
                }
                "REQUEST_APPROVAL" -> {
                    notifyApproval(userId, rule.name, email)
                    "EXECUTED" to "Notification dispatched requesting review."
                }
                else -> "FAILED" to "Unsupported action type: ${rule.actionType}"
            }
        } catch (t: Throwable) {
            "FAILED" to "Execution error: ${t.message ?: "unknown error"}"
        }
    }

    fun matchesTriggerAndCondition(rule: AutomationRuleEntity, email: EmailEntity): Boolean {
        val cond = rule.conditionValue.trim().lowercase()
        return when (rule.triggerType) {
            "NEW_EMAIL" -> cond.isEmpty() ||
                email.subject.lowercase().contains(cond) ||
                email.bodyText.lowercase().contains(cond)
            "SPECIFIC_SENDER" -> cond.isNotEmpty() &&
                (email.senderEmail.lowercase().contains(cond) || email.senderName.lowercase().contains(cond))
            "SUBJECT_KEYWORD" -> cond.isNotEmpty() && email.subject.lowercase().contains(cond)
            "ATTACHMENT_RECEIVED" -> email.hasAttachments
            "IMPORTANT_DETECTED" -> email.priority in setOf("Urgent", "High") || email.category == "Important"
            "FOLLOWUP_DEADLINE" -> email.followUpState in setOf("HAS_DEADLINE", "NEEDS_MY_REPLY")
            else -> false
        }
    }

    private fun isActionPermitted(actionType: String, prefs: WafaPreferences): Boolean = when (actionType) {
        "APPLY_CATEGORY" -> prefs.allowAutoCategorize
        "ADD_LABEL" -> prefs.allowAutoLabel
        "CREATE_REMINDER", "CREATE_TASK" -> prefs.allowAutoReminder
        "PREPARE_DRAFT" -> prefs.allowAutoDraft
        "ARCHIVE" -> prefs.allowAutoArchive
        "MARK_READ" -> prefs.allowAutoMarkRead
        "REQUEST_APPROVAL" -> true
        else -> false
    }

    private suspend fun notifyApproval(userId: String, ruleName: String, email: EmailEntity) {
        workspaceDao.insertNotification(
            NotificationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = "Approval Needed: $ruleName",
                message = "Workflow matched '${email.subject}' from ${email.senderEmail}. Review in Automation Studio.",
                category = "APPROVAL",
                relatedEmailId = email.id
            )
        )
    }
}
