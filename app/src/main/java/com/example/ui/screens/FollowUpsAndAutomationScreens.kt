package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.i18n.LocalWafaStrings
import com.example.ui.theme.LocalWafaColors
import com.example.ui.viewmodel.WafaUiState
import com.example.ui.viewmodel.WafaViewModel

/**
 * Combined Flows Hub holding Page 11 (Follow-ups) & Page 12 (Automation Studio)
 */
@Composable
fun WorkflowsHubScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onOpenDrafts: () -> Unit,
    onOpenEmail: (String) -> Unit
) {
    val strings = LocalWafaStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("${strings.followUps} (${state.activeReminderCount})") },
                modifier = Modifier.testTag("flows_tab_followups")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("${strings.automationStudio} (${state.pendingApprovalCount})") },
                modifier = Modifier.testTag("flows_tab_automation")
            )
        }
        when (selectedTab) {
            0 -> FollowUpsContent(state, viewModel, onOpenEmail, onOpenDrafts)
            1 -> AutomationStudioContent(state, viewModel)
        }
    }
}

/**
 * Page 11: Follow-ups & Reminder System
 */
@Composable
fun FollowUpsContent(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onOpenEmail: (String) -> Unit,
    onOpenDrafts: () -> Unit
) {
    val wafa = LocalWafaColors.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Follow-ups & Deadlines", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDrafts) { Text("Drafts") }
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_reminder_btn")
                ) {
                    Icon(Icons.Outlined.AddAlarm, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.reminders.isEmpty()) {
            EmptyWorkspaceCard(
                title = "No Follow-up Reminders",
                description = "Run the Follow-up Agent on any email to distinguish Needs Reply vs Awaiting Response vs Real Deadlines, or schedule a manual follow-up.",
                primaryActionLabel = "Schedule Follow-up",
                onPrimaryAction = { showCreateDialog = true },
                showIllustration = false
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.reminders, key = { it.id }) { rem ->
                    val overdue = rem.status != "COMPLETED" && rem.dueAt < System.currentTimeMillis()
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(rem.emailSubject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusPill(
                                text = if (overdue) "OVERDUE" else rem.status,
                                containerColor = when {
                                    overdue -> wafa.dangerContainer
                                    rem.status == "COMPLETED" -> wafa.successContainer
                                    else -> wafa.warningContainer
                                },
                                contentColor = when {
                                    overdue -> wafa.danger
                                    rem.status == "COMPLETED" -> wafa.success
                                    else -> wafa.warning
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${rem.followUpType} • Due: ${formatTimestamp(rem.dueAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (rem.extractedDeadlineText.isNotBlank()) {
                            Text("Source deadline: ${rem.extractedDeadlineText}", style = MaterialTheme.typography.bodySmall, color = wafa.accentBlue)
                        }
                        if (rem.suggestedMessage.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Suggested follow-up: ${rem.suggestedMessage}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (rem.status != "COMPLETED") {
                                Button(onClick = { viewModel.completeReminder(rem.id) }) { Text("Done") }
                                OutlinedButton(onClick = { viewModel.snoozeReminder(rem.id, 24) }) { Text("Snooze 24h") }
                            }
                            if (rem.emailId.isNotBlank()) {
                                TextButton(onClick = { onOpenEmail(rem.emailId) }) { Text("Open Mail") }
                            }
                            IconButton(onClick = { viewModel.deleteReminder(rem.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete reminder")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var subject by remember { mutableStateOf("") }
        var sender by remember { mutableStateOf("") }
        var followUpType by remember { mutableStateOf("NEEDS_MY_REPLY") }
        var hoursText by remember { mutableStateOf("24") }
        var note by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Schedule Follow-up Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject / Topic") }, singleLine = true)
                    OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("Contact Email") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("NEEDS_MY_REPLY", "AWAITING_THEIR_REPLY", "HAS_DEADLINE").forEach { t ->
                            FilterChip(selected = followUpType == t, onClick = { followUpType = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(value = hoursText, onValueChange = { hoursText = it }, label = { Text("Due in (hours)") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Deadline note") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createReminder(null, subject, sender, followUpType, hoursText.toIntOrNull() ?: 24, note)
                    showCreateDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Page 12: Automation Studio (Visual Workflow Builder, Approval Queue, Duplicate-Protected History)
 */
@Composable
fun AutomationStudioContent(
    state: WafaUiState,
    viewModel: WafaViewModel
) {
    val wafa = LocalWafaColors.current
    var showBuilder by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Trigger → Conditions → AI → Approval → Action → Result", style = MaterialTheme.typography.titleSmall, color = wafa.accentBlue)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Current mode: ${state.preferences.operationMode.labelEn}. Duplicate executions for the same trigger event are strictly blocked. Sending/deleting always require confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showBuilder = true },
                        modifier = Modifier.weight(1f).testTag("automation_new_rule_btn")
                    ) { Text("Create Workflow") }
                    OutlinedButton(
                        onClick = { viewModel.runAutomationsOnInbox() },
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1f).testTag("automation_evaluate_btn")
                    ) { Text("Evaluate Inbox") }
                }
            }
        }

        // Pending Approval Queue
        val pending = state.automationExecutions.filter { it.status == "PENDING_APPROVAL" }
        if (pending.isNotEmpty()) {
            item { SectionHeader(title = "Approval Queue (${pending.size})") }
            items(pending, key = { it.id }) { exec ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(exec.ruleName, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(exec.resultMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.approveExecution(exec) }, enabled = !state.isBusy) { Text("Approve & Run") }
                        OutlinedButton(onClick = { viewModel.rejectExecution(exec.id) }) { Text("Reject") }
                    }
                }
            }
        }

        // Configured Workflows
        item { SectionHeader(title = "Workflows (${state.automationRules.size})") }
        if (state.automationRules.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No workflows configured yet. Tap 'Create Workflow' to build an approval-protected rule.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(state.automationRules, key = { it.id }) { rule ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${rule.triggerType} (${rule.conditionValue.ifBlank { "any" }}) → ${rule.actionType} (${rule.actionParam.ifBlank { "default" }})",
                                style = MaterialTheme.typography.bodySmall,
                                color = wafa.accentBlue
                            )
                            Text(
                                "Approval required: ${if (rule.requiresApproval) "Yes" else "Follows Mode"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { viewModel.toggleAutomationRule(rule.id, it) }
                        )
                        IconButton(onClick = { viewModel.deleteAutomationRule(rule.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete workflow")
                        }
                    }
                }
            }
        }

        // Execution History & Audit Trail
        val history = state.automationExecutions.filter { it.status != "PENDING_APPROVAL" }
        if (history.isNotEmpty()) {
            item { SectionHeader(title = "Execution History (${history.size})") }
            items(history.take(25), key = { it.id }) { exec ->
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(exec.ruleName, style = MaterialTheme.typography.titleSmall)
                        StatusPill(
                            text = exec.status,
                            containerColor = if (exec.status == "EXECUTED") wafa.successContainer else wafa.dangerContainer,
                            contentColor = if (exec.status == "EXECUTED") wafa.success else wafa.danger
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(exec.resultMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTimestamp(exec.executedAt), style = MaterialTheme.typography.labelSmall, color = wafa.mutedText)
                }
            }
        }
    }

    if (showBuilder) {
        var name by remember { mutableStateOf("") }
        var trigger by remember { mutableStateOf("SUBJECT_KEYWORD") }
        var condition by remember { mutableStateOf("invoice") }
        var action by remember { mutableStateOf("APPLY_CATEGORY") }
        var actionParam by remember { mutableStateOf("Finance") }
        var requiresApproval by remember { mutableStateOf(true) }

        val triggers = listOf("NEW_EMAIL", "SPECIFIC_SENDER", "SUBJECT_KEYWORD", "ATTACHMENT_RECEIVED", "IMPORTANT_DETECTED", "FOLLOWUP_DEADLINE")
        val actions = listOf("APPLY_CATEGORY", "ADD_LABEL", "CREATE_REMINDER", "PREPARE_DRAFT", "ARCHIVE", "MARK_READ", "CREATE_TASK", "REQUEST_APPROVAL")

        AlertDialog(
            onDismissRequest = { showBuilder = false },
            title = { Text("New Automation Workflow") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Workflow Name") }, singleLine = true)
                    Text("Trigger", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        triggers.forEach { t ->
                            FilterChip(selected = trigger == t, onClick = { trigger = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(value = condition, onValueChange = { condition = it }, label = { Text("Condition Keyword / Sender") }, singleLine = true)
                    Text("Action", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        actions.forEach { a ->
                            FilterChip(selected = action == a, onClick = { action = a }, label = { Text(a) })
                        }
                    }
                    OutlinedTextField(value = actionParam, onValueChange = { actionParam = it }, label = { Text("Action Parameter (category/label/tone)") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = requiresApproval, onCheckedChange = { requiresApproval = it })
                        Text("Require manual approval before executing", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createAutomationRule(name, "", trigger, condition, "NONE", requiresApproval, action, actionParam)
                    showBuilder = false
                }) { Text("Save Rule") }
            },
            dismissButton = { TextButton(onClick = { showBuilder = false }) { Text("Cancel") } }
        )
    }
}
