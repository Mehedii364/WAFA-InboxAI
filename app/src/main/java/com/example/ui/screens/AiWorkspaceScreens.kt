package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AiDraftEntity
import com.example.data.local.AttachmentEntity
import com.example.ui.components.*
import com.example.ui.i18n.LocalWafaStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalWafaColors
import com.example.ui.viewmodel.WafaUiState
import com.example.ui.viewmodel.WafaViewModel

/**
 * Unified AI Hub tab holding Page 9 (Ask Your Inbox) and Page 8 (AI Assistant)
 */
@Composable
fun AiHubScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onOpenEmail: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val strings = LocalWafaStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.askYourInbox) },
                modifier = Modifier.testTag("ai_hub_tab_ask")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(strings.aiAssistant) },
                modifier = Modifier.testTag("ai_hub_tab_assistant")
            )
        }
        when (selectedTab) {
            0 -> AskYourInboxContent(state, viewModel, onOpenEmail, onNavigate)
            1 -> AiAssistantContent(state, viewModel, onNavigate)
        }
    }
}

/**
 * Page 9: Ask Your Inbox (Source-grounded retrieval Q&A with clickable email citations)
 */
@Composable
fun AskYourInboxContent(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onOpenEmail: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var question by remember { mutableStateOf("") }

    val quickQuestions = listOf(
        "Find my latest important emails.",
        "Which emails need my reply?",
        "Find emails containing an invoice or payment.",
        "What appointments or deadlines were mentioned?",
        "Summarize my recent work conversations."
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (state.configuredProviderCount == 0) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("AI Provider Required", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(strings.configureProviderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onNavigate("providers") }) { Text("Open API Provider Manager") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickQuestions.forEach { preset ->
                SuggestionChip(
                    onClick = {
                        question = preset
                        viewModel.askYourInbox(preset)
                    },
                    label = { Text(preset, maxLines = 1) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text("Ask anything about your synced emails...") },
                modifier = Modifier.weight(1f).testTag("ask_inbox_input"),
                singleLine = true
            )
            FilledIconButton(
                onClick = {
                    val q = question
                    question = ""
                    viewModel.askYourInbox(q)
                },
                enabled = question.isNotBlank() && !state.isBusy,
                modifier = Modifier.size(52.dp).testTag("ask_inbox_send_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask Inbox")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.askInboxHistory.isEmpty()) {
            EmptyWorkspaceCard(
                title = strings.askYourInbox,
                description = "Retrieves relevant messages from your real synced inbox and grounds every AI answer with clickable source email citations.",
                primaryActionLabel = if (state.emails.isEmpty()) "Connect Email Account" else "Search Important Mail",
                onPrimaryAction = {
                    if (state.emails.isEmpty()) onNavigate("accounts")
                    else viewModel.askYourInbox("Find my latest important emails.")
                },
                showIllustration = false
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.askInboxHistory, key = { it.id }) { item ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(item.question, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        if (item.error != null) {
                            Text(item.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        } else if (item.answer != null) {
                            Text(item.answer.answer, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Provider: ${item.answer.providerUsed.displayName} (${item.answer.modelUsed}) • ${item.answer.latencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = wafa.accentBlue
                            )
                            if (item.answer.citedEmailIds.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Source References:", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                item.answer.citedEmailIds.forEach { eid ->
                                    val citedEmail = state.emails.firstOrNull { it.id == eid }
                                    if (citedEmail != null) {
                                        TextButton(
                                            onClick = { onOpenEmail(citedEmail.id) },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Outlined.MailOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "${citedEmail.subject} — ${citedEmail.senderEmail}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Page 8: AI Assistant (Multi-Agent Email Secretary & Composer)
 */
@Composable
fun AiAssistantContent(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onNavigate: (String) -> Unit
) {
    var recipient by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf("Professional") }
    var language by remember { mutableStateOf(state.preferences.language.code) }
    var prompt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("AI Email Secretary & Writer", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Compose contextual emails, adjust tone, fix grammar, or translate between English and Bangla. Outputs are saved to AI Drafts for review — never auto-sent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("Recipient Email (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            val modes = listOf("Professional", "Friendly", "Formal", "Short Reply", "Detailed", "Grammar Correction", "Translate EN↔BN")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { m ->
                    FilterChip(selected = tone == m, onClick = { tone = m }, label = { Text(m) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language == "en", onClick = { language = "en" }, label = { Text("English") })
                FilterChip(selected = language == "bn", onClick = { language = "bn" }, label = { Text("বাংলা") })
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Instructions or draft text to refine") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("ai_writer_prompt_input")
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        viewModel.generateDraftWithAi(null, recipient, tone, language, prompt)
                    },
                    enabled = prompt.isNotBlank() && !state.isBusy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ai_writer_generate_btn")
                ) {
                    Text("Generate Draft")
                }
                OutlinedButton(
                    onClick = { onNavigate("drafts") },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Drafts (${state.drafts.size})")
                }
            }
        }

        // Modular AI Agents Roster
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("Active Modular AI Agents (9)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val roster = listOf(
                "Inbox Agent" to "Categorizes into 12 core + custom categories",
                "Priority Agent" to "Flags Urgent/High/Normal/Low with verifiable reasons",
                "Summary Agent" to "Short/detailed/thread summaries, action items & amounts",
                "Reply Agent" to "Contextual EN/BN writer with strict review-before-send",
                "Research Agent" to "Ask Your Inbox grounded search with source citations",
                "Follow-up Agent" to "Distinguishes Needs Reply vs Awaiting vs Deadline",
                "Attachment Agent" to "Safe PDF/DOCX/XLSX/CSV/TXT extraction & analysis",
                "Security Agent" to "Phishing, SPF/DKIM/DMARC & prompt-injection defense",
                "Schedule Agent" to "Extracts verifiable appointments & deadlines"
            )
            roster.forEach { (name, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Page 10: AI Drafts Screen (Edit, Remote Save, Explicit Send Confirmation Modal)
 */
@Composable
fun AiDraftsScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var draftToSend by remember { mutableStateOf<AiDraftEntity?>(null) }
    var draftToEdit by remember { mutableStateOf<AiDraftEntity?>(null) }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = strings.aiDrafts,
                subtitle = strings.sendRequiresConfirmation,
                onBack = onBack
            )
        }
    ) { padding ->
        if (state.drafts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                EmptyWorkspaceCard(
                    title = "No AI Drafts Yet",
                    description = "Generate contextual replies from any email detail page or the AI Assistant writer. Drafts are never sent without your explicit confirmation.",
                    primaryActionLabel = "Go Back",
                    onPrimaryAction = onBack,
                    showIllustration = false
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.drafts, key = { it.id }) { draft ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(draft.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusPill(
                                text = draft.status,
                                containerColor = if (draft.status == "SENT") wafa.successContainer else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (draft.status == "SENT") wafa.success else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("To: ${draft.recipientEmail.ifBlank { "(No recipient)" }} • ${draft.tone} (${draft.language.uppercase()})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (draft.providerUsed.isNotBlank()) {
                            Text("Generated via ${draft.providerUsed} (${draft.modelUsed})", style = MaterialTheme.typography.labelSmall, color = wafa.accentBlue)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(draft.body, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { draftToEdit = draft }) { Text("Edit") }
                            if (draft.status != "SENT") {
                                OutlinedButton(
                                    onClick = { viewModel.syncDraftToProvider(draft) },
                                    enabled = !state.isBusy
                                ) { Text("Save Remote") }
                                Button(
                                    onClick = { draftToSend = draft },
                                    enabled = draft.recipientEmail.contains("@") && !state.isBusy,
                                    modifier = Modifier.testTag("draft_send_btn")
                                ) { Text("Send") }
                            }
                            IconButton(onClick = { viewModel.deleteDraft(draft.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete draft")
                            }
                        }
                    }
                }
            }
        }
    }

    draftToSend?.let { d ->
        ConfirmActionDialog(
            title = "Confirm Sending Email",
            body = "Send this email via your connected provider?\n\nTo: ${d.recipientEmail}\nSubject: ${d.subject}\n\nWAFA InboxAI never sends without your explicit approval.",
            confirmLabel = "Confirm & Send",
            onConfirm = {
                draftToSend = null
                viewModel.sendDraftConfirmed(d)
            },
            onDismiss = { draftToSend = null }
        )
    }

    draftToEdit?.let { editing ->
        var toField by remember(editing.id) { mutableStateOf(editing.recipientEmail) }
        var subjField by remember(editing.id) { mutableStateOf(editing.subject) }
        var bodyField by remember(editing.id) { mutableStateOf(editing.body) }
        AlertDialog(
            onDismissRequest = { draftToEdit = null },
            title = { Text("Edit Draft") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = toField, onValueChange = { toField = it }, label = { Text("To") }, singleLine = true)
                    OutlinedTextField(value = subjField, onValueChange = { subjField = it }, label = { Text("Subject") }, singleLine = true)
                    OutlinedTextField(value = bodyField, onValueChange = { bodyField = it }, label = { Text("Body") }, minLines = 5)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateDraft(editing.copy(recipientEmail = toField.trim(), subject = subjField.trim(), body = bodyField))
                    draftToEdit = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { draftToEdit = null }) { Text("Cancel") } }
        )
    }
}

/**
 * Page 13: Attachment Manager (Safe SAF Document Picker, Extraction, AI Analysis, Size & MIME Safety)
 */
@Composable
fun AttachmentManagerScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var docQuestion by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importAttachmentUri(uri)
    }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = strings.attachmentManager,
                subtitle = "PDF, DOCX, XLSX, CSV, TXT, Images • 15 MB max • Zero execution",
                onBack = onBack,
                actions = {
                    FilledTonalButton(
                        onClick = {
                            filePicker.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/csv",
                                    "text/plain",
                                    "image/*"
                                )
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp).testTag("attachment_pick_btn")
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Inspect File")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = docQuestion,
                onValueChange = { docQuestion = it },
                label = { Text("Optional question for AI document analysis") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (state.attachments.isEmpty()) {
                EmptyWorkspaceCard(
                    title = "No Attachments Inspected Yet",
                    description = "Pick a PDF, DOCX, XLSX, CSV, TXT, or image safely via Android's zero-permission file picker, or sync emails with attachments. Files are validated and never executed.",
                    primaryActionLabel = "Pick Document to Inspect",
                    onPrimaryAction = { filePicker.launch(arrayOf("*/*")) },
                    showIllustration = false
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.attachments, key = { it.id }) { att: AttachmentEntity ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(att.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${att.mimeType} • ${formatFileSize(att.sizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    text = if (att.isSafe) "SAFE" else "WARNING",
                                    containerColor = if (att.isSafe) wafa.successContainer else wafa.dangerContainer,
                                    contentColor = if (att.isSafe) wafa.success else wafa.danger
                                )
                            }
                            if (att.safetyWarning.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(att.safetyWarning, style = MaterialTheme.typography.bodySmall, color = wafa.danger)
                            }
                            if (att.extractedText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Extracted Preview:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    att.extractedText.take(280),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (att.aiSummary.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("AI Analysis:", style = MaterialTheme.typography.labelLarge, color = wafa.accentBlue)
                                Text(att.aiSummary, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.summarizeAttachment(att, docQuestion) },
                                    enabled = att.isSafe && att.extractedText.isNotBlank() && !state.isBusy
                                ) { Text("Analyze with AI") }
                                IconButton(onClick = { viewModel.deleteAttachment(att.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove attachment record")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
