package com.example.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AiOperationMode
import com.example.ui.components.*
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalWafaStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalWafaColors
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.WafaUiState
import com.example.ui.viewmodel.WafaViewModel

/**
 * Control Center Hub connecting every operational page cleanly
 */
@Composable
fun ControlCenterScreen(
    state: WafaUiState,
    onNavigate: (String) -> Unit
) {
    val strings = LocalWafaStrings.current

    data class HubEntry(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

    val entries = listOf(
        HubEntry("providers", strings.apiProviderManager, "${state.configuredProviderCount}/6 BYOK providers active", Icons.Outlined.Key),
        HubEntry("model_router", strings.modelRouter, "${state.routingRules.size} task routing rules", Icons.Outlined.AltRoute),
        HubEntry("accounts", strings.connectedAccounts, "${state.accounts.size} account(s) connected", Icons.Outlined.ManageAccounts),
        HubEntry("drafts", strings.aiDrafts, "${state.drafts.size} draft(s) ready for review", Icons.Outlined.EditNote),
        HubEntry("attachments", strings.attachmentManager, "${state.attachments.size} inspected file(s)", Icons.Outlined.AttachFile),
        HubEntry("analytics", strings.usageAnalytics, "${state.usageLogs.size} recorded AI calls", Icons.Outlined.BarChart),
        HubEntry("notifications", strings.notificationCenter, "${state.unreadNotificationCount} unread alerts", Icons.Outlined.Notifications),
        HubEntry("security", strings.securityCenter, "${state.preferences.operationMode.labelEn} • ${state.auditLogs.size} audit entries", Icons.Outlined.Shield),
        HubEntry("profile", strings.userProfile, state.currentUser?.email ?: "Workspace identity", Icons.Outlined.Person),
        HubEntry("settings", strings.appSettings, "Theme, English/বাংলা, Backend URL, Categories", Icons.Outlined.Settings),
        HubEntry("help", strings.helpDocs, "OAuth, BYOK, PHP Backend & APK Guides", Icons.Outlined.HelpOutline)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(strings.navControl, style = MaterialTheme.typography.headlineLarge)
            Text(strings.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        items(entries, key = { it.route }) { item ->
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("hub_item_${item.route}"),
                onClick = { onNavigate(item.route) },
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(item.icon, contentDescription = item.title, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Page 18: Notification Center
 */
@Composable
fun NotificationCenterScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit,
    onOpenEmail: (String) -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = strings.notificationCenter,
                subtitle = "${state.unreadNotificationCount} unread",
                onBack = onBack,
                actions = {
                    if (state.notifications.isNotEmpty()) {
                        TextButton(onClick = { viewModel.markAllNotificationsRead() }) { Text("Read All") }
                        IconButton(onClick = { viewModel.clearNotifications() }) {
                            Icon(Icons.Outlined.ClearAll, contentDescription = "Clear notifications")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("All caught up — no notifications yet.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Follow-up due alerts, automation approval requests, and phishing warnings will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.notifications, key = { it.id }) { n ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.markNotificationRead(n.id)
                            if (n.relatedEmailId.isNotBlank()) onOpenEmail(n.relatedEmailId)
                        }
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall)
                            StatusPill(
                                text = n.category,
                                containerColor = if (n.category == "SECURITY") wafa.dangerContainer else wafa.accentBlueContainer,
                                contentColor = if (n.category == "SECURITY") wafa.danger else wafa.accentBlue
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(n.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTimestamp(n.createdAt), style = MaterialTheme.typography.labelSmall, color = wafa.mutedText)
                    }
                }
            }
        }
    }
}

/**
 * Page 19: Security Center (3 AI Modes, Granular Permissions, Revoke All, Prompt-Injection Shield, Audit Logs)
 */
@Composable
fun SecurityCenterScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var confirmWipe by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            WafaSubScreenTopBar(
                title = strings.securityCenter,
                subtitle = "Zero-trust AI governance & audit trail",
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
                    Text("Security Posture", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("• Vault: ${if (state.isVaultHardwareBacked) "Android Keystore AES-256-GCM" else "AES-256-GCM"}", style = MaterialTheme.typography.bodySmall)
                    Text("• Transport: Strict HTTPS enforced (cleartext disabled)", style = MaterialTheme.typography.bodySmall)
                    Text("• Prompt-Injection Shield: Active on all 9 AI agents", style = MaterialTheme.typography.bodySmall)
                    Text("• Sending/Forwarding/Deleting: Always requires explicit confirmation", style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("AI Operation Mode", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    AiOperationMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.preferences.operationMode == mode,
                                onClick = { viewModel.setOperationMode(mode) }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(mode.labelEn, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when (mode) {
                                        AiOperationMode.COPILOT -> "AI analyzes and recommends. User performs all actions."
                                        AiOperationMode.APPROVAL -> "AI prepares actions and requests confirmation."
                                        AiOperationMode.SMART_AUTOMATION -> "AI executes only authorized low-risk rules."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.revokeAllAiPermissions() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("revoke_all_permissions_btn")
                    ) {
                        Text(strings.revokeAllPermissions)
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Granular Automation Permissions", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    PermissionToggle("Auto-Categorize Emails", state.preferences.allowAutoCategorize) { viewModel.updatePermissions(allowAutoCategorize = it) }
                    PermissionToggle("Auto-Add Labels", state.preferences.allowAutoLabel) { viewModel.updatePermissions(allowAutoLabel = it) }
                    PermissionToggle("Auto-Create Reminders", state.preferences.allowAutoReminder) { viewModel.updatePermissions(allowAutoReminder = it) }
                    PermissionToggle("Auto-Prepare Drafts (Never Sent)", state.preferences.allowAutoDraft) { viewModel.updatePermissions(allowAutoDraft = it) }
                    PermissionToggle("Auto-Archive Matched Mail", state.preferences.allowAutoArchive) { viewModel.updatePermissions(allowAutoArchive = it) }
                    PermissionToggle("Auto-Mark as Read", state.preferences.allowAutoMarkRead) { viewModel.updatePermissions(allowAutoMarkRead = it) }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Account Data Deletion", style = MaterialTheme.typography.titleMedium, color = wafa.danger)
                    Spacer(Modifier.height(4.dp))
                    Text("Permanently wipe your encrypted API keys, OAuth tokens, synced emails, drafts, rules, and audit logs from this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { confirmWipe = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete Account & Wipe All Data") }
                }
            }

            item { SectionHeader(title = "Audit Log (${state.auditLogs.size})") }
            items(state.auditLogs.take(40), key = { it.id }) { log ->
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(log.action, style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily))
                        Text(log.actor, style = MaterialTheme.typography.labelSmall, color = wafa.accentBlue)
                    }
                    Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTimestamp(log.createdAt), style = MaterialTheme.typography.labelSmall, color = wafa.mutedText)
                }
            }
        }
    }

    if (confirmWipe) {
        ConfirmActionDialog(
            title = "Delete All Local Data?",
            body = "This irreversibly removes your local account, encrypted API keys, connected accounts, emails, and drafts from this device.",
            confirmLabel = "Wipe Everything",
            isDestructive = true,
            onConfirm = {
                confirmWipe = false
                viewModel.deleteAccountAndWipeData()
            },
            onDismiss = { confirmWipe = false }
        )
    }
}

@Composable
private fun PermissionToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

/**
 * Page 20: User Profile Screen
 */
@Composable
fun UserProfileScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit,
    onReopenOnboarding: () -> Unit
) {
    val strings = LocalWafaStrings.current
    Scaffold(
        topBar = { WafaSubScreenTopBar(title = strings.userProfile, onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SenderAvatar(state.currentUser?.fullName ?: "W")
                    Column {
                        Text(state.currentUser?.fullName ?: "Workspace User", style = MaterialTheme.typography.headlineSmall)
                        Text(state.currentUser?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Last Sign-in: ${formatTimestamp(state.currentUser?.lastLoginAt ?: 0L)}", style = MaterialTheme.typography.bodySmall)
                Text("Connected Backend: ${state.preferences.backendBaseUrl.ifBlank { "Standalone Android Vault" }}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onReopenOnboarding, modifier = Modifier.weight(1f)) { Text("Run Setup Wizard") }
                    Button(
                        onClick = { viewModel.signOut() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).testTag("profile_sign_out_btn")
                    ) { Text(strings.signOut) }
                }
            }
        }
    }
}

/**
 * Page 21: Application Settings Screen (Theme, Language EN/BN, Backend URL, Custom Categories, Sync Interval)
 */
@Composable
fun ApplicationSettingsScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onBack: () -> Unit
) {
    val strings = LocalWafaStrings.current
    var backendUrl by remember(state.preferences.backendBaseUrl) { mutableStateOf(state.preferences.backendBaseUrl) }
    var newCatName by remember { mutableStateOf("") }
    var newCatDesc by remember { mutableStateOf("") }

    Scaffold(
        topBar = { WafaSubScreenTopBar(title = strings.appSettings, onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Theme Mode
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = state.preferences.themeMode == ThemeMode.DARK, onClick = { viewModel.setThemeMode(ThemeMode.DARK) }, label = { Text(strings.themeDark) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = state.preferences.themeMode == ThemeMode.LIGHT, onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }, label = { Text(strings.themeLight) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = state.preferences.themeMode == ThemeMode.SYSTEM, onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) }, label = { Text(strings.themeSystem) }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = state.preferences.language == AppLanguage.EN, onClick = { viewModel.setLanguage(AppLanguage.EN) }, label = { Text("English") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = state.preferences.language == AppLanguage.BN, onClick = { viewModel.setLanguage(AppLanguage.BN) }, label = { Text("বাংলা") }, modifier = Modifier.weight(1f))
                }
            }

            // Production PHP Backend HTTPS URL
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Production PHP 8.2+ Backend", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    label = { Text("HTTPS Backend URL") },
                    placeholder = { Text("https://api.yourdomain.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.checkBackendHealth(backendUrl) },
                    enabled = backendUrl.isNotBlank() && !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Verify & Save Backend URL") }
            }

            // Custom Email Categories
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Custom Email Categories", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newCatName, onValueChange = { newCatName = it }, label = { Text("Category Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            viewModel.addCustomCategory(newCatName, newCatDesc)
                            newCatName = ""
                            newCatDesc = ""
                        },
                        enabled = newCatName.isNotBlank(),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) { Text("Add") }
                }
                if (state.customCategories.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    state.customCategories.forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { viewModel.deleteCustomCategory(cat.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete category")
                            }
                        }
                    }
                }
            }

            // Background Sync Interval
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Background Sync Interval (${state.preferences.syncIntervalMinutes} min)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 180).forEach { mins ->
                        FilterChip(
                            selected = state.preferences.syncIntervalMinutes == mins,
                            onClick = { viewModel.setSyncInterval(mins) },
                            label = { Text("${mins}m") }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page 22: Help & Documentation Screen
 */
@Composable
fun HelpDocsScreen(onBack: () -> Unit) {
    val strings = LocalWafaStrings.current
    Scaffold(
        topBar = { WafaSubScreenTopBar(title = strings.helpDocs, onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("WAFA InboxAI — Architecture & Credits", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("${strings.tagline}\n${strings.developerCredit}", style = MaterialTheme.typography.bodyMedium)
            }
            val guides = listOf(
                "1. Bring Your Own AI Key (BYOK)" to
                    "Open Control → API Provider Manager to enter keys for Google Gemini, Groq, OpenRouter, Hugging Face Inference Providers, Cloudflare Workers AI (requires Account ID + Token), or XPIKI. Tap 'Test & Fetch' to verify real connectivity and populate live models.",
                "2. Gmail OAuth 2.0 PKCE Setup" to
                    "Create a Native/Android OAuth 2.0 Client ID in Google Cloud Console with Gmail API enabled and custom redirect URI 'wafa.inboxai://oauth/callback'. Enter the Client ID in Connected Accounts → Gmail.",
                "3. Microsoft Outlook Graph Setup" to
                    "Register a Mobile/Desktop app in Microsoft Entra ID with redirect URI 'wafa.inboxai://oauth/callback' and delegated permissions Mail.Read (plus Mail.ReadWrite & Mail.Send when desired).",
                "4. PHP 8.2+ & MySQL Production Backend" to
                    "Deploy the /backend directory to a PHP 8.2+ HTTPS host, import backend/migrations/001_initial_schema.sql into MySQL, set APP_ENCRYPTION_KEY and CRON_TOKEN in backend/.env, and enter the HTTPS URL in Settings.",
                "5. Security & Safety Guarantees" to
                    "Email bodies and attachments are wrapped inside untrusted-data boundaries to defeat prompt injection. Sending, forwarding, or deleting emails always requires explicit user confirmation."
            )
            guides.forEach { (title, body) ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
