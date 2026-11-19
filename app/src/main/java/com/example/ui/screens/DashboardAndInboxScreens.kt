package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.EmailEntity
import com.example.ui.components.*
import com.example.ui.i18n.LocalWafaStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalWafaColors
import com.example.ui.viewmodel.WafaUiState
import com.example.ui.viewmodel.WafaViewModel

/**
 * Page 4: Executive Dashboard (Real metrics only, explicit freshness timestamp, provider health)
 */
@Composable
fun DashboardScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onNavigate: (String) -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = if (wafa.isDark) 0.16f else 0.08f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Executive Command Header
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primaryGlow, Color.Transparent),
                                center = Offset(size.width * 0.9f, 0f),
                                radius = size.width * 0.7f
                            )
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.currentUser?.fullName?.ifBlank { strings.appName } ?: strings.appName,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${strings.dataFreshness}: ${if (state.preferences.lastGlobalSyncAt > 0L) formatTimestamp(state.preferences.lastGlobalSyncAt) else strings.neverSynced}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { onNavigate("notifications") },
                            modifier = Modifier.testTag("dashboard_notifications_btn")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.unreadNotificationCount > 0) {
                                        Badge { Text(state.unreadNotificationCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.Notifications, contentDescription = strings.notificationCenter)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.syncAllAccounts() },
                            enabled = !state.isBusy,
                            modifier = Modifier.testTag("dashboard_sync_btn")
                        ) {
                            Icon(Icons.Outlined.Sync, contentDescription = strings.syncNow)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OperationModePill(
                        mode = state.preferences.operationMode,
                        language = state.preferences.language,
                        onClick = { onNavigate("security") }
                    )
                    if (state.pendingApprovalCount > 0) {
                        StatusPill(
                            text = "${state.pendingApprovalCount} Pending Approval",
                            containerColor = wafa.warningContainer,
                            contentColor = wafa.warning,
                            icon = Icons.Outlined.PendingActions,
                            modifier = Modifier.clickable { onNavigate("workflows_hub") }
                        )
                    }
                }
            }
        }

        // Core Real-Time KPI Grid (strictly computed from local Room records — zero fabricated numbers)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = strings.totalMessages,
                        value = state.emails.size.toString(),
                        icon = Icons.Outlined.MailOutline,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("inbox") }
                    )
                    MetricCard(
                        label = strings.unreadMessages,
                        value = state.unreadCount.toString(),
                        icon = Icons.Outlined.MarkEmailUnread,
                        accentColor = wafa.accentBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("inbox") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = strings.importantMessages,
                        value = state.importantCount.toString(),
                        icon = Icons.Outlined.PriorityHigh,
                        accentColor = wafa.danger,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("inbox") }
                    )
                    MetricCard(
                        label = strings.pendingReplies,
                        value = state.pendingReplyCount.toString(),
                        icon = Icons.Outlined.Reply,
                        accentColor = wafa.warning,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("workflows_hub") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = strings.followUps,
                        value = state.activeReminderCount.toString(),
                        icon = Icons.Outlined.EventRepeat,
                        accentColor = wafa.success,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("workflows_hub") }
                    )
                    MetricCard(
                        label = strings.aiDrafts,
                        value = state.drafts.size.toString(),
                        icon = Icons.Outlined.EditNote,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("drafts") }
                    )
                }
            }
        }

        // Provider Health & Connected Accounts Readiness
        item {
            SectionHeader(
                title = strings.providerHealth,
                subtitle = "${state.configuredProviderCount} of 6 AI providers enabled • ${state.accounts.size} account(s)",
                trailing = {
                    TextButton(onClick = { onNavigate("providers") }) { Text("Manage") }
                }
            )
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                state.providers.forEachIndexed { idx, p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = when {
                                    p.connectionVerified -> "Verified • ${p.defaultModel.ifBlank { "Pick model" }} • ${p.lastLatencyMs}ms"
                                    p.encryptedApiKey.isNotBlank() -> "Key saved (${p.maskedKey}) — tap Manage to test"
                                    else -> "No API key configured"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPill(
                            text = when {
                                p.connectionVerified && p.enabled -> "LIVE"
                                p.lastError.isNotBlank() -> "ERROR"
                                p.encryptedApiKey.isNotBlank() -> "SAVED"
                                else -> "IDLE"
                            },
                            containerColor = when {
                                p.connectionVerified && p.enabled -> wafa.successContainer
                                p.lastError.isNotBlank() -> wafa.dangerContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when {
                                p.connectionVerified && p.enabled -> wafa.success
                                p.lastError.isNotBlank() -> wafa.danger
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (idx < state.providers.lastIndex) {
                        HorizontalDivider(color = wafa.subtleDivider)
                    }
                }
            }
        }

        // Real API Usage Snapshot (never fabricated)
        item {
            val totalRequests = state.usageLogs.size
            val totalTokens = state.usageLogs.sumOf { it.totalTokens }
            val avgLatency = if (totalRequests > 0) state.usageLogs.map { it.latencyMs }.average().toLong() else 0L
            val knownCost = state.usageLogs.mapNotNull { it.estimatedCostUsd }.takeIf { it.isNotEmpty() }?.sum()

            SectionHeader(
                title = strings.apiUsage,
                trailing = { TextButton(onClick = { onNavigate("analytics") }) { Text("Analytics") } }
            )
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Requests", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(totalRequests.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily))
                    }
                    Column {
                        Text("Tokens", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(totalTokens.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily))
                    }
                    Column {
                        Text("Avg Latency", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${avgLatency}ms", style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily))
                    }
                    Column {
                        Text("Est. Cost", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = knownCost?.let { String.format("$%.4f", it) } ?: "N/A",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }
                }
            }
        }

        // Quick Workspace Launchers
        item {
            SectionHeader(title = "Quick Actions")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onNavigate("accounts") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Accounts")
                }
                OutlinedButton(
                    onClick = { onNavigate("attachments") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Files")
                }
                OutlinedButton(
                    onClick = { onNavigate("model_router") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Router")
                }
            }
        }
    }
}

/**
 * Page 5: Smart Inbox (Multi-account switcher, category filters, search, real sync & SAF .EML import)
 */
@Composable
fun SmartInboxScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onOpenEmail: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showArchived by remember { mutableStateOf(false) }

    val emlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importEmlUri(uri)
    }

    val filteredEmails = remember(state.emails, state.preferences.selectedAccountId, query, selectedCategory, showArchived) {
        state.emails.filter { email ->
            val archiveMatch = if (showArchived) email.isArchived else !email.isArchived
            val accountMatch = state.preferences.selectedAccountId.isBlank() || email.accountId == state.preferences.selectedAccountId
            val categoryMatch = selectedCategory == "All" || email.category.equals(selectedCategory, ignoreCase = true)
            val q = query.trim().lowercase()
            val queryMatch = q.isEmpty() ||
                email.subject.lowercase().contains(q) ||
                email.senderName.lowercase().contains(q) ||
                email.senderEmail.lowercase().contains(q) ||
                email.bodyText.lowercase().contains(q) ||
                email.labelsCsv.lowercase().contains(q)
            archiveMatch && accountMatch && categoryMatch && queryMatch
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.navInbox, style = MaterialTheme.typography.headlineLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { emlPicker.launch(arrayOf("message/rfc822", "text/plain", "*/*")) },
                    modifier = Modifier.heightIn(min = 40.dp).testTag("inbox_import_eml_btn")
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.importEml)
                }
                FilledTonalIconButton(
                    onClick = { viewModel.syncAllAccounts() },
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("inbox_sync_btn")
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = strings.syncNow)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(strings.searchEmails) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("inbox_search_input")
        )

        // Account Filter Strip
        if (state.accounts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.preferences.selectedAccountId.isBlank(),
                    onClick = { viewModel.setSelectedAccountFilter("") },
                    label = { Text("All Accounts") }
                )
                state.accounts.forEach { acc ->
                    FilterChip(
                        selected = state.preferences.selectedAccountId == acc.id,
                        onClick = { viewModel.setSelectedAccountFilter(acc.id) },
                        label = { Text(acc.emailAddress, maxLines = 1) }
                    )
                }
            }
        }

        // Category Filter Chips
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == "All" && !showArchived,
                onClick = { selectedCategory = "All"; showArchived = false },
                label = { Text("All") }
            )
            FilterChip(
                selected = showArchived,
                onClick = { showArchived = !showArchived },
                label = { Text("Archived") }
            )
            state.allCategories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat && !showArchived,
                    onClick = { selectedCategory = cat; showArchived = false },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (filteredEmails.isEmpty()) {
            EmptyWorkspaceCard(
                title = strings.noDataYet,
                description = strings.connectAccountHint,
                primaryActionLabel = "Connect Email Account",
                onPrimaryAction = { onNavigate("accounts") },
                secondaryActionLabel = strings.importEml,
                onSecondaryAction = { emlPicker.launch(arrayOf("message/rfc822", "text/plain", "*/*")) }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredEmails, key = { it.id }) { email ->
                    EmailListCard(
                        email = email,
                        onClick = { onOpenEmail(email.id) },
                        onClassify = { viewModel.classifyEmailWithAi(email) },
                        wafa = wafa
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailListCard(
    email: EmailEntity,
    onClick: () -> Unit,
    onClassify: () -> Unit,
    wafa: com.example.ui.theme.WafaExtendedColors
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().testTag("email_card_${email.remoteMessageId}"),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SenderAvatar(name = email.senderName.ifBlank { email.senderEmail })
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = email.senderName.ifBlank { email.senderEmail },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (email.isRead) FontWeight.Medium else FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTimestamp(email.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = email.subject,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = email.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityBadge(priority = email.priority)
                    StatusPill(
                        text = email.category,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (email.securityScore >= 50) {
                        StatusPill(
                            text = "Risk ${email.securityRiskLevel}",
                            containerColor = wafa.dangerContainer,
                            contentColor = wafa.danger,
                            icon = Icons.Outlined.GppMaybe
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onClassify,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = "Run AI Classification",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page 6: Email Detail Screen (Full AI Intelligence Suite + Contextual Reply Writer + Safety Controls)
 */
@Composable
fun EmailDetailScreen(
    emailId: String,
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDrafts: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    val email = state.emails.firstOrNull { it.id == emailId }
    val summary = state.summaries[emailId]
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedTone by remember { mutableStateOf("Professional") }
    var customReplyNote by remember { mutableStateOf("") }
    var replyLang by remember { mutableStateOf(state.preferences.language.code) }

    LaunchedEffect(emailId) {
        if (email != null && !email.isRead) {
            viewModel.toggleRead(email)
        }
    }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = "Email Intelligence",
                subtitle = email?.senderEmail,
                onBack = onBack,
                actions = {
                    if (email != null) {
                        IconButton(onClick = { onOpenConversation(email.threadId) }) {
                            Icon(Icons.Outlined.Forum, contentDescription = "Conversation View")
                        }
                        IconButton(onClick = { viewModel.archiveEmail(email); onBack() }) {
                            Icon(Icons.Outlined.Archive, contentDescription = "Archive")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (email == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Email not found.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Metadata Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(email.subject, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SenderAvatar(email.senderName.ifBlank { email.senderEmail })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(email.senderName.ifBlank { email.senderEmail }, style = MaterialTheme.typography.titleSmall)
                        Text(email.senderEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatTimestamp(email.receivedAt), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PriorityBadge(email.priority)
                    StatusPill(email.category, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    if (email.followUpState != "NONE") {
                        StatusPill(email.followUpState, wafa.warningContainer, wafa.warning)
                    }
                    StatusPill(
                        text = "Security: ${email.securityRiskLevel} (${email.securityScore})",
                        containerColor = if (email.securityScore >= 50) wafa.dangerContainer else wafa.successContainer,
                        contentColor = if (email.securityScore >= 50) wafa.danger else wafa.success
                    )
                }
                if (email.priorityReason.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Priority rationale (advisory): ${email.priorityReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = wafa.accentBlue
                    )
                }
            }

            // AI Agent Action Strip
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { viewModel.summarizeEmailWithAi(email) }, enabled = !state.isBusy) {
                    Icon(Icons.Outlined.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Summarize")
                }
                FilledTonalButton(onClick = { viewModel.classifyEmailWithAi(email) }, enabled = !state.isBusy) {
                    Icon(Icons.Outlined.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Classify")
                }
                FilledTonalButton(onClick = { viewModel.runFollowUpAgent(email) }, enabled = !state.isBusy) {
                    Icon(Icons.Outlined.EventRepeat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Follow-up")
                }
                FilledTonalButton(onClick = { viewModel.runSecurityAudit(email) }, enabled = !state.isBusy) {
                    Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Security Scan")
                }
                FilledTonalButton(onClick = { viewModel.runScheduleAgent(email) }, enabled = !state.isBusy) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Extract Dates")
                }
            }

            // AI Summary Card (with source citation & provider transparency)
            if (summary != null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("AI Email Summary", style = MaterialTheme.typography.titleMedium)
                        Text("${summary.providerUsed} • ${summary.modelUsed}", style = MaterialTheme.typography.labelSmall, color = wafa.accentBlue)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(summary.shortSummary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (summary.detailedSummary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(summary.detailedSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (summary.actionItemsCsv.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Action Items:", style = MaterialTheme.typography.labelLarge)
                        Text(summary.actionItemsCsv, style = MaterialTheme.typography.bodySmall)
                    }
                    if (summary.deadlinesCsv.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Deadlines (extracted verbatim): ${summary.deadlinesCsv}", style = MaterialTheme.typography.bodySmall, color = wafa.warning)
                    }
                    if (summary.financialAmountsCsv.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Financial Amounts: ${summary.financialAmountsCsv}", style = MaterialTheme.typography.bodySmall, color = wafa.success)
                    }
                    if (summary.entitiesCsv.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("People & Organizations: ${summary.entitiesCsv}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Source: ${summary.sourceReference} • ${strings.aiRecommendationNotice}", style = MaterialTheme.typography.labelSmall, color = wafa.mutedText)
                }
            }

            // Security Audit Report if flags exist or scan ran
            val deepSecurity = state.lastSecurityAudit?.takeIf { it.first == email.id }?.second
            if (email.securityFlagsCsv.isNotBlank() || deepSecurity != null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Security Agent Report", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    if (deepSecurity != null) {
                        Text("Verdict: ${deepSecurity.aiVerdict}", style = MaterialTheme.typography.titleSmall, color = wafa.warning)
                        Text(deepSecurity.aiExplanation, style = MaterialTheme.typography.bodySmall)
                    }
                    if (email.securityFlagsCsv.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(email.securityFlagsCsv, style = MaterialTheme.typography.bodySmall, color = wafa.danger)
                    }
                }
            }

            // Schedule Extraction Result
            val scheduleRes = state.lastScheduleResult?.takeIf { it.first == email.id }?.second
            if (scheduleRes != null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Schedule Agent Extractions", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Appointments: ${scheduleRes.appointments.joinToString("; ").ifBlank { "None found" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Deadlines: ${scheduleRes.deadlines.joinToString("; ").ifBlank { "None found" }}", style = MaterialTheme.typography.bodySmall)
                    if (scheduleRes.sourceQuotes.isNotEmpty()) {
                        Text("Quotes: ${scheduleRes.sourceQuotes.joinToString(" | ")}", style = MaterialTheme.typography.labelSmall, color = wafa.mutedText)
                    }
                }
            }

            // Email Body
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Message Content", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(email.bodyText, style = MaterialTheme.typography.bodyMedium)
            }

            // Contextual AI Email Writer
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("AI Reply Writer (Draft Only — Never Auto-Sent)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val tones = listOf("Professional", "Friendly", "Formal", "Short Reply", "Detailed Reply", "Grammar Fix", "Translate")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tones.forEach { t ->
                        FilterChip(selected = selectedTone == t, onClick = { selectedTone = t }, label = { Text(t) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = replyLang == "en", onClick = { replyLang = "en" }, label = { Text("English") })
                    FilterChip(selected = replyLang == "bn", onClick = { replyLang = "bn" }, label = { Text("বাংলা") })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customReplyNote,
                    onValueChange = { customReplyNote = it },
                    label = { Text("Custom instructions (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            viewModel.generateDraftWithAi(email, email.senderEmail, selectedTone, replyLang, customReplyNote)
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Generate Draft")
                    }
                    OutlinedButton(
                        onClick = onOpenDrafts,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("View Drafts")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && email != null) {
        ConfirmActionDialog(
            title = "Remove Email?",
            body = "Remove '${email.subject}' from local WAFA InboxAI storage?",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteEmail(email)
                onBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

/**
 * Page 7: Conversation Thread View
 */
@Composable
fun ConversationViewScreen(
    threadId: String,
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit,
    onOpenEmail: (String) -> Unit
) {
    val threadMessages = remember(state.emails, threadId) {
        state.emails.filter { it.threadId == threadId }.sortedBy { it.receivedAt }
    }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = "Conversation Thread",
                subtitle = "${threadMessages.size} message(s)",
                onBack = onBack,
                actions = {
                    if (threadMessages.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.summarizeEmailWithAi(threadMessages.last()) },
                            enabled = !state.isBusy
                        ) {
                            Text("Summarize Thread")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(threadMessages, key = { it.id }) { msg ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenEmail(msg.id) }
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(msg.senderName.ifBlank { msg.senderEmail }, style = MaterialTheme.typography.titleSmall)
                        Text(formatTimestamp(msg.receivedAt), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(msg.subject, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(msg.bodyText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
