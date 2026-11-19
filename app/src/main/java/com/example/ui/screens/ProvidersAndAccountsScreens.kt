package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiProviderType
import com.example.data.ai.AiTaskType
import com.example.data.email.OAuthEmailProvider
import com.example.data.local.AiProviderConfigEntity
import com.example.data.local.RoutingRuleEntity
import com.example.ui.components.*
import com.example.ui.i18n.LocalWafaStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalWafaColors
import com.example.ui.viewmodel.WafaUiState
import com.example.ui.viewmodel.WafaViewModel

/**
 * Page 14: Connected Accounts (Gmail OAuth 2.0 PKCE, Outlook Graph OAuth 2.0 PKCE, Yahoo/IMAP Bridge)
 */
@Composable
fun ConnectedAccountsScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var clientId by remember { mutableStateOf("") }
    var includeWriteSend by remember { mutableStateOf(false) }
    var directAccessToken by remember { mutableStateOf("") }
    var directRefreshToken by remember { mutableStateOf("") }

    var imapProvider by remember { mutableStateOf("YAHOO_IMAP") }
    var imapEmail by remember { mutableStateOf("") }
    var imapHost by remember { mutableStateOf("imap.mail.yahoo.com") }
    var smtpHost by remember { mutableStateOf("smtp.mail.yahoo.com") }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = strings.connectedAccounts,
                subtitle = "${state.accounts.size} connected • Real OAuth 2.0 PKCE & HTTPS Bridge",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.syncAllAccounts() }, enabled = !state.isBusy) {
                        Icon(Icons.Outlined.Sync, contentDescription = strings.syncNow)
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
            // Existing Connected Accounts
            if (state.accounts.isNotEmpty()) {
                item { SectionHeader(title = "Active Accounts (${state.accounts.size})") }
                items(state.accounts, key = { it.id }) { acc ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.emailAddress, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${acc.provider} • Last sync: ${if (acc.lastSyncedAt > 0) formatTimestamp(acc.lastSyncedAt) else "Never"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (acc.lastError.isNotBlank()) {
                                    Text(acc.lastError, style = MaterialTheme.typography.bodySmall, color = wafa.danger)
                                }
                            }
                            StatusPill(
                                text = acc.status,
                                containerColor = if (acc.status == "CONNECTED") wafa.successContainer else wafa.warningContainer,
                                contentColor = if (acc.status == "CONNECTED") wafa.success else wafa.warning
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.syncAllAccounts() }, enabled = !state.isBusy) { Text("Sync") }
                            TextButton(
                                onClick = { viewModel.disconnectAccount(acc.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Disconnect & Wipe") }
                        }
                    }
                }
            }

            // Connect New Provider Tabs
            item {
                SectionHeader(title = "Connect Real Email Provider")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("Gmail") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("Outlook") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = selectedTab == 2, onClick = { selectedTab = 2 }, label = { Text("Yahoo/IMAP") }, modifier = Modifier.weight(1f))
                }
            }

            item {
                when (selectedTab) {
                    0, 1 -> {
                        val provider = if (selectedTab == 0) OAuthEmailProvider.GMAIL else OAuthEmailProvider.OUTLOOK
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (provider == OAuthEmailProvider.GMAIL) "Official Gmail API (OAuth 2.0 PKCE)"
                                else "Official Microsoft Graph Outlook (OAuth 2.0 PKCE)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Redirect URI: wafa.inboxai://oauth/callback\nMinimal scopes requested by default (read-only). Enable write/send scopes only when needed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = { Text("${provider.name} OAuth Client ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("oauth_client_id_input")
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeWriteSend, onCheckedChange = { includeWriteSend = it })
                                Text("Request draft, modify & send scopes (requires explicit confirmation)", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.launchOAuthPkce(provider, clientId, includeWriteSend) },
                                enabled = clientId.isNotBlank() && !state.isBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("launch_oauth_btn")
                            ) {
                                Text("Launch Official ${provider.name} OAuth Sign-In")
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = wafa.subtleDivider)

                            Text("Or Verify OAuth Access Token Directly", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Verified live against ${if (provider == OAuthEmailProvider.GMAIL) "gmail.googleapis.com" else "graph.microsoft.com"} before saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = directAccessToken,
                                onValueChange = { directAccessToken = it },
                                label = { Text("OAuth 2.0 Access Token") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = directRefreshToken,
                                onValueChange = { directRefreshToken = it },
                                label = { Text("Refresh Token (optional)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.connectOAuthTokenDirectly(
                                        provider = provider,
                                        accessToken = directAccessToken,
                                        refreshToken = directRefreshToken,
                                        clientId = clientId,
                                        scopes = if (includeWriteSend) provider.fullMailScopes else provider.defaultReadScope
                                    )
                                    directAccessToken = ""
                                    directRefreshToken = ""
                                },
                                enabled = directAccessToken.isNotBlank() && !state.isBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text("Verify Token & Connect Live")
                            }
                        }
                    }

                    2 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Yahoo Mail & Generic IMAP/SMTP Adapter", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Capability Notice: Direct raw TCP IMAP/SMTP requires your deployed HTTPS PHP 8.2+ backend bridge (never plaintext passwords on device). Available via bridge: Inbox sync, Reading, Search. Unavailable without bridge: Push history sync.",
                                style = MaterialTheme.typography.bodySmall,
                                color = wafa.warning
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = imapProvider == "YAHOO_IMAP",
                                    onClick = { imapProvider = "YAHOO_IMAP"; imapHost = "imap.mail.yahoo.com"; smtpHost = "smtp.mail.yahoo.com" },
                                    label = { Text("Yahoo Mail") }
                                )
                                FilterChip(
                                    selected = imapProvider == "GENERIC_IMAP",
                                    onClick = { imapProvider = "GENERIC_IMAP"; imapHost = ""; smtpHost = "" },
                                    label = { Text("Generic IMAP/SMTP") }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = imapEmail, onValueChange = { imapEmail = it }, label = { Text("Email Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = imapHost, onValueChange = { imapHost = it }, label = { Text("IMAP TLS Host (Port 993)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = smtpHost, onValueChange = { smtpHost = it }, label = { Text("SMTP STARTTLS Host (Port 587)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.saveImapBridgeAccount(imapProvider, imapEmail, imapHost, 993, smtpHost, 587)
                                },
                                enabled = imapEmail.contains("@") && imapHost.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text("Save IMAP Bridge Configuration")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Page 15: API Provider Manager (All 6 BYOK Providers — Encrypted Key Vault, Live Test, Live Model Discovery)
 */
@Composable
fun ApiProviderManagerScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit,
    onOpenRouter: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var editingConfig by remember { mutableStateOf<AiProviderConfigEntity?>(null) }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = strings.apiProviderManager,
                subtitle = "BYOK Encrypted Vault • 6 Independent Providers",
                onBack = onBack,
                actions = {
                    TextButton(onClick = onOpenRouter) { Text("Router") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = wafa.success)
                        Text(
                            "Zero keys are bundled in this APK. Every API key you enter is encrypted with AES-256-GCM, displayed masked only, and never written to logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(state.providers, key = { it.id }) { cfg ->
                val pType = AiProviderType.valueOf(cfg.providerType)
                val providerModels = state.models.filter { it.providerType == cfg.providerType }

                GlassCard(modifier = Modifier.fillMaxWidth().testTag("provider_card_${cfg.providerType}")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cfg.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(pType.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(
                            text = when {
                                cfg.connectionVerified && cfg.enabled -> "VERIFIED"
                                cfg.encryptedApiKey.isNotBlank() -> "CONFIGURED"
                                else -> "NOT SET"
                            },
                            containerColor = when {
                                cfg.connectionVerified && cfg.enabled -> wafa.successContainer
                                cfg.encryptedApiKey.isNotBlank() -> wafa.warningContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when {
                                cfg.connectionVerified && cfg.enabled -> wafa.success
                                cfg.encryptedApiKey.isNotBlank() -> wafa.warning
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Key: ${cfg.maskedKey.ifBlank { "Not configured" }} • Endpoint: ${cfg.baseUrl}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Model: ${cfg.defaultModel.ifBlank { "None selected" }} • Models fetched: ${providerModels.size} • Limits: ${cfg.dailyRequestLimit} req / ${cfg.dailyTokenLimit} tok/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = wafa.accentBlue
                    )
                    if (cfg.lastError.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Last Error: ${cfg.lastError}", style = MaterialTheme.typography.bodySmall, color = wafa.danger)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { editingConfig = cfg },
                            modifier = Modifier.testTag("configure_provider_${cfg.providerType}")
                        ) {
                            Text(if (cfg.encryptedApiKey.isBlank()) "Add Key" else "Configure")
                        }
                        OutlinedButton(
                            onClick = { viewModel.testAndFetchModels(pType) },
                            enabled = cfg.encryptedApiKey.isNotBlank() && !state.isBusy,
                            modifier = Modifier.testTag("test_provider_${cfg.providerType}")
                        ) {
                            Text("Test & Fetch")
                        }
                        if (cfg.encryptedApiKey.isNotBlank()) {
                            IconButton(onClick = { viewModel.deleteProviderKey(pType) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Revoke key")
                            }
                        }
                    }
                }
            }
        }
    }

    editingConfig?.let { cfg ->
        val pType = AiProviderType.valueOf(cfg.providerType)
        val availableModels = state.models.filter { it.providerType == cfg.providerType }
        var newApiKey by remember(cfg.id) { mutableStateOf("") }
        var baseUrl by remember(cfg.id) { mutableStateOf(cfg.baseUrl) }
        var accountId by remember(cfg.id) { mutableStateOf(cfg.accountId) }
        var defaultModel by remember(cfg.id) { mutableStateOf(cfg.defaultModel) }
        var enabled by remember(cfg.id) { mutableStateOf(cfg.enabled || cfg.encryptedApiKey.isBlank()) }
        var isPaid by remember(cfg.id) { mutableStateOf(cfg.isPaidProvider) }
        var dailyReq by remember(cfg.id) { mutableStateOf(cfg.dailyRequestLimit.toString()) }
        var dailyTok by remember(cfg.id) { mutableStateOf(cfg.dailyTokenLimit.toString()) }

        AlertDialog(
            onDismissRequest = { editingConfig = null },
            title = { Text(cfg.displayName) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = newApiKey,
                        onValueChange = { newApiKey = it },
                        label = { Text(if (cfg.maskedKey.isNotBlank()) "Replace API Key (kept if blank)" else "API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("provider_key_dialog_input")
                    )
                    if (pType.requiresAccountId) {
                        OutlinedTextField(
                            value = accountId,
                            onValueChange = { accountId = it },
                            label = { Text("Cloudflare Account ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("HTTPS Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = defaultModel,
                        onValueChange = { defaultModel = it },
                        label = { Text("Default Model ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (availableModels.isNotEmpty()) {
                        Text("Discovered Models (tap to select):", style = MaterialTheme.typography.labelMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            availableModels.take(15).forEach { m ->
                                FilterChip(
                                    selected = defaultModel == m.modelId,
                                    onClick = { defaultModel = m.modelId },
                                    label = { Text(m.modelId, maxLines = 1) }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = dailyReq, onValueChange = { dailyReq = it }, label = { Text("Daily Req Cap") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = dailyTok, onValueChange = { dailyTok = it }, label = { Text("Daily Tok Cap") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                        Text("Enable provider", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                        Text("Mark as paid / billed provider (blocks silent fallback)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveProviderConfig(
                            providerType = pType,
                            rawApiKey = newApiKey.ifBlank { null },
                            baseUrl = baseUrl,
                            accountId = accountId,
                            enabled = enabled,
                            isPaidProvider = isPaid,
                            defaultModel = defaultModel,
                            dailyRequestLimit = dailyReq.toIntOrNull() ?: 500,
                            dailyTokenLimit = dailyTok.toIntOrNull() ?: 250_000
                        )
                        editingConfig = null
                    },
                    modifier = Modifier.testTag("provider_save_dialog_btn")
                ) { Text("Encrypt & Save") }
            },
            dismissButton = { TextButton(onClick = { editingConfig = null }) { Text("Cancel") } }
        )
    }
}

/**
 * Page 16: Model Selection & Intelligent Router Configuration
 */
@Composable
fun ModelRouterScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var editingRule by remember { mutableStateOf<RoutingRuleEntity?>(null) }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = strings.modelRouter,
                subtitle = "Task-specific routing, retries & paid-fallback protection",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow Automatic Paid Fallback", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "When OFF, the router will never silently fall back to a provider marked as paid/billed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.preferences.allowPaidFallback,
                            onCheckedChange = { viewModel.updatePermissions(allowPaidFallback = it) }
                        )
                    }
                }
            }

            items(state.routingRules, key = { it.id }) { rule ->
                val taskLabel = runCatching { AiTaskType.valueOf(rule.taskType).label }.getOrDefault(rule.taskType)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { editingRule = rule }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(taskLabel, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Primary: ${rule.primaryProvider} (${rule.primaryModel.ifBlank { "default" }})",
                                style = MaterialTheme.typography.bodySmall,
                                color = wafa.accentBlue
                            )
                            Text(
                                "Fallback: ${rule.fallbackProvider.ifBlank { "None" }} (${rule.fallbackModel.ifBlank { "default" }}) • Timeout: ${rule.timeoutSeconds}s • Max retries: ${rule.maxRetries}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Outlined.Tune, contentDescription = "Edit rule")
                    }
                }
            }
        }
    }

    editingRule?.let { rule ->
        var primaryProvider by remember(rule.id) { mutableStateOf(rule.primaryProvider) }
        var primaryModel by remember(rule.id) { mutableStateOf(rule.primaryModel) }
        var fallbackProvider by remember(rule.id) { mutableStateOf(rule.fallbackProvider) }
        var fallbackModel by remember(rule.id) { mutableStateOf(rule.fallbackModel) }
        var timeoutSec by remember(rule.id) { mutableStateOf(rule.timeoutSeconds.toString()) }
        var maxRetries by remember(rule.id) { mutableStateOf(rule.maxRetries.toString()) }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = { Text("Routing: ${rule.taskType}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Primary Provider", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AiProviderType.entries.forEach { p ->
                            FilterChip(selected = primaryProvider == p.name, onClick = { primaryProvider = p.name }, label = { Text(p.name) })
                        }
                    }
                    OutlinedTextField(value = primaryModel, onValueChange = { primaryModel = it }, label = { Text("Primary Model (blank = provider default)") }, singleLine = true)

                    Text("Fallback Provider", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = fallbackProvider.isBlank(), onClick = { fallbackProvider = "" }, label = { Text("NONE") })
                        AiProviderType.entries.forEach { p ->
                            FilterChip(selected = fallbackProvider == p.name, onClick = { fallbackProvider = p.name }, label = { Text(p.name) })
                        }
                    }
                    OutlinedTextField(value = fallbackModel, onValueChange = { fallbackModel = it }, label = { Text("Fallback Model (blank = provider default)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = timeoutSec, onValueChange = { timeoutSec = it }, label = { Text("Timeout (s)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = maxRetries, onValueChange = { maxRetries = it }, label = { Text("Retries (0-3)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveRoutingRule(
                        rule.copy(
                            primaryProvider = primaryProvider,
                            primaryModel = primaryModel.trim(),
                            fallbackProvider = fallbackProvider,
                            fallbackModel = fallbackModel.trim(),
                            timeoutSeconds = (timeoutSec.toIntOrNull() ?: 45).coerceIn(10, 120),
                            maxRetries = (maxRetries.toIntOrNull() ?: 2).coerceIn(0, 3)
                        )
                    )
                    editingRule = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingRule = null }) { Text("Cancel") } }
        )
    }
}

/**
 * Page 17: AI Usage Analytics (Real Logged Telemetry — Zero Fabricated Numbers)
 */
@Composable
fun AiUsageAnalyticsScreen(
    state: WafaUiState,
    onBack: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    val totalReqs = state.usageLogs.size
    val successReqs = state.usageLogs.count { it.success }
    val totalTokens = state.usageLogs.sumOf { it.totalTokens }
    val avgLatency = if (totalReqs > 0) state.usageLogs.map { it.latencyMs }.average().toLong() else 0L
    val costSum = state.usageLogs.mapNotNull { it.estimatedCostUsd }.takeIf { it.isNotEmpty() }?.sum()

    val byProvider = remember(state.usageLogs) {
        state.usageLogs.groupBy { it.providerType }
    }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = strings.usageAnalytics,
                subtitle = "Verified request telemetry • Real counts only",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("Total Calls", totalReqs.toString(), Icons.Outlined.QueryStats, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricCard("Success", "$successReqs/$totalReqs", Icons.Outlined.CheckCircle, wafa.success, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("Total Tokens", totalTokens.toString(), Icons.Outlined.DataUsage, wafa.accentBlue, Modifier.weight(1f))
                    MetricCard("Avg Latency", "${avgLatency}ms", Icons.Outlined.Speed, wafa.warning, Modifier.weight(1f))
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Estimated AI Cost (where pricing metadata exists)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = costSum?.let { String.format("$%.5f USD", it) } ?: "Pricing data unavailable for used models (never fabricated).",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = wafa.accentBlue
                    )
                }
            }

            if (byProvider.isNotEmpty()) {
                item { SectionHeader(title = "By Provider") }
                items(byProvider.entries.toList(), key = { it.key }) { (provider, logs) ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Text(provider, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Calls: ${logs.size} (${logs.count { it.success }} ok) • Tokens: ${logs.sumOf { it.totalTokens }} • Avg latency: ${logs.map { it.latencyMs }.average().toLong()}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionHeader(title = "Recent Executions") }
            if (state.usageLogs.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("No AI requests recorded yet. Configure an AI provider and summarize or classify an email to record real metrics.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(state.usageLogs.take(40), key = { it.id }) { log ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("${log.providerType} • ${log.taskType}", style = MaterialTheme.typography.titleSmall)
                            StatusPill(
                                text = if (log.success) "${log.latencyMs}ms" else log.errorCode.ifBlank { "ERROR" },
                                containerColor = if (log.success) wafa.successContainer else wafa.dangerContainer,
                                contentColor = if (log.success) wafa.success else wafa.danger
                            )
                        }
                        Text(
                            "Model: ${log.modelId} • Tokens: ${log.totalTokens}${if (log.fallbackUsed) " • Fallback" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (log.errorMessage.isNotBlank()) {
                            Text(log.errorMessage, style = MaterialTheme.typography.bodySmall, color = wafa.danger)
                        }
                    }
                }
            }
        }
    }
}
