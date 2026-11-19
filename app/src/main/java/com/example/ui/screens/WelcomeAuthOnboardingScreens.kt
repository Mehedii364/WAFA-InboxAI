package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.AiProviderType
import com.example.data.email.OAuthEmailProvider
import com.example.data.local.AiOperationMode
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalWafaStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalWafaColors
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.WafaUiState
import com.example.ui.viewmodel.WafaViewModel

/**
 * Page 1: Welcome Screen
 */
@Composable
fun WelcomeScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onContinueToAuth: () -> Unit,
    onContinueToOnboarding: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()) {
            // Top bar: Language & Theme switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = "v1.0 • BYOK",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = Icons.Outlined.Shield
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.preferences.language == AppLanguage.EN,
                        onClick = { viewModel.setLanguage(AppLanguage.EN) },
                        label = { Text("EN") },
                        modifier = Modifier.testTag("welcome_lang_en")
                    )
                    FilterChip(
                        selected = state.preferences.language == AppLanguage.BN,
                        onClick = { viewModel.setLanguage(AppLanguage.BN) },
                        label = { Text("বাংলা") },
                        modifier = Modifier.testTag("welcome_lang_bn")
                    )
                    IconButton(
                        onClick = {
                            val next = if (state.preferences.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                            viewModel.setThemeMode(next)
                        },
                        modifier = Modifier.testTag("welcome_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (wafa.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Toggle theme"
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Hero Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_welcome_hero),
                    contentDescription = "WAFA InboxAI command center artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                    Text(
                        text = strings.tagline,
                        style = MaterialTheme.typography.titleMedium,
                        color = wafa.accentBlue
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = strings.welcomeTitle,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.welcomeSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // Key Pillars
            val pillars = listOf(
                Triple(Icons.Outlined.Key, "6 BYOK AI Providers", "Gemini, Groq, OpenRouter, Hugging Face, Cloudflare Workers AI & XPIKI — encrypted via Android Keystore."),
                Triple(Icons.Outlined.MarkEmailRead, "Real Email Integrations", "Official Gmail OAuth 2.0 PKCE, Microsoft Graph Outlook, PHP IMAP Bridge & local .EML analysis."),
                Triple(Icons.Outlined.AdminPanelSettings, "Zero Silent Sends", "Copilot, Approval & Smart Automation modes with prompt-injection barriers and immutable audit logs.")
            )
            pillars.forEach { (icon, title, desc) ->
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = if (state.currentUser != null) onContinueToOnboarding else onContinueToAuth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("welcome_get_started_button")
            ) {
                Text(strings.getStarted, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = strings.developerCredit,
                style = MaterialTheme.typography.labelSmall,
                color = wafa.mutedText,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/**
 * Page 2: Authentication Screen (Secure Local PBKDF2 Account + Optional HTTPS PHP Backend Auth)
 */
@Composable
fun AuthenticationScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onAuthComplete: () -> Unit
) {
    val strings = LocalWafaStrings.current
    var isRegisterMode by remember { mutableStateOf(true) }
    var useBackendAuth by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var backendUrl by remember { mutableStateOf(state.preferences.backendBaseUrl) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Spacer(Modifier.height(12.dp))
            Text(strings.appName, style = MaterialTheme.typography.headlineLarge)
            Text(
                text = if (isRegisterMode) "Create your encrypted workspace" else "Sign in to your workspace",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = isRegisterMode,
                    onClick = { isRegisterMode = true; useBackendAuth = false; validationError = null },
                    label = { Text(strings.createAccount) },
                    modifier = Modifier.weight(1f).testTag("auth_tab_register")
                )
                FilterChip(
                    selected = !isRegisterMode,
                    onClick = { isRegisterMode = false; validationError = null },
                    label = { Text(strings.signIn) },
                    modifier = Modifier.weight(1f).testTag("auth_tab_signin")
                )
            }

            Spacer(Modifier.height(14.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                if (!isRegisterMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Authenticate with PHP Backend", style = MaterialTheme.typography.titleSmall)
                            Text("Optional HTTPS server login instead of local vault auth", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = useBackendAuth, onCheckedChange = { useBackendAuth = it })
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (useBackendAuth) {
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text("HTTPS Backend URL") },
                        placeholder = { Text("https://api.yourdomain.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_backend_url_input")
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(strings.fullName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_fullname_input")
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(strings.emailAddress) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.password) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = { Text("Minimum 8 characters • PBKDF2-HMAC-SHA256 salted") },
                    modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                )

                if (isRegisterMode) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(strings.confirmPassword) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_confirm_password_input")
                    )
                }

                validationError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        validationError = null
                        if (isRegisterMode) {
                            if (password != confirmPassword) {
                                validationError = "Passwords do not match."
                                return@Button
                            }
                            viewModel.registerLocal(fullName, email, password, onAuthComplete)
                        } else if (useBackendAuth) {
                            viewModel.signInBackend(backendUrl, email, password, onAuthComplete)
                        } else {
                            viewModel.signInLocal(email, password, onAuthComplete)
                        }
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("auth_submit_button")
                ) {
                    Text(if (isRegisterMode) strings.createAccount else strings.signIn)
                }
            }
        }
    }
}

/**
 * Page 3: Complete 8-Step First-Launch Onboarding Wizard
 * Step 1: Welcome
 * Step 2: Account Verified
 * Step 3: Optional Backend HTTPS Connection
 * Step 4: Configure AI Providers (BYOK, optional skip)
 * Step 5: Test Configured Providers
 * Step 6: Connect Gmail / Outlook / IMAP (optional skip)
 * Step 7: Configure Permissions & Operation Mode
 * Step 8: Finish Setup
 */
@Composable
fun OnboardingWizardScreen(
    state: WafaUiState,
    viewModel: WafaViewModel,
    onFinish: () -> Unit
) {
    val strings = LocalWafaStrings.current
    val wafa = LocalWafaColors.current
    var step by remember { mutableIntStateOf(1) }

    var backendUrl by remember { mutableStateOf(state.preferences.backendBaseUrl) }
    var selectedProvider by remember { mutableStateOf(AiProviderType.GEMINI) }
    var apiKeyInput by remember { mutableStateOf("") }
    var accountIdInput by remember { mutableStateOf("") }
    var customBaseUrl by remember { mutableStateOf(AiProviderType.GEMINI.defaultBaseUrl) }

    var oauthClientId by remember { mutableStateOf("") }
    var selectedMailProvider by remember { mutableStateOf(OAuthEmailProvider.GMAIL) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.showMessage(
            if (granted) "Notification permission granted for follow-ups & security alerts."
            else "Notifications declined — alerts will still appear in Notification Center."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxSize()) {
            // Step Progress Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.onboardingTitle, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Step $step / 8",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { step / 8f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp))
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    1 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("1. Welcome to WAFA InboxAI", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "This guided setup lets you connect your optional PHP backend, add one or more AI API keys, verify real connectivity, link Gmail or Outlook, and set strict AI permission boundaries.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Every external integration is optional during setup and can be updated anytime in Control Center.",
                                style = MaterialTheme.typography.bodySmall,
                                color = wafa.accentBlue
                            )
                        }
                    }

                    2 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("2. Workspace Account Ready", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Signed in as: ${state.currentUser?.fullName ?: "Active User"}", style = MaterialTheme.typography.titleMedium)
                            Text(state.currentUser?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            StatusPill(
                                text = if (state.isVaultHardwareBacked) "Android Keystore AES-256-GCM Active" else "AES-256-GCM Encrypted Vault Active",
                                containerColor = wafa.successContainer,
                                contentColor = wafa.success,
                                icon = Icons.Outlined.Lock
                            )
                        }
                    }

                    3 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("3. Backend Server Connection (Optional)", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Connect your deployed PHP 8.2+ & MySQL backend over HTTPS for cloud sync and IMAP/SMTP bridging, or skip to run directly on Android.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = backendUrl,
                                onValueChange = { backendUrl = it },
                                label = { Text("Production HTTPS Backend URL") },
                                placeholder = { Text("https://api.yourdomain.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("onboarding_backend_url")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.checkBackendHealth(backendUrl) },
                                enabled = backendUrl.isNotBlank() && !state.isBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text("Verify HTTPS Backend Health")
                            }
                            state.lastBackendHealth?.let { health ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${health.message} (${health.latencyMs}ms)",
                                    color = if (health.healthy) wafa.success else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    4 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("4. Configure AI Provider (BYOK)", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "You only need 1 provider to start (or skip and add later). Keys are encrypted at rest and never hardcoded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            AiProviderType.entries.chunked(2).forEach { rowProviders ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    rowProviders.forEach { p ->
                                        val cfg = state.providers.firstOrNull { it.providerType == p.name }
                                        FilterChip(
                                            selected = selectedProvider == p,
                                            onClick = {
                                                selectedProvider = p
                                                customBaseUrl = cfg?.baseUrl ?: p.defaultBaseUrl
                                                accountIdInput = cfg?.accountId ?: ""
                                                apiKeyInput = ""
                                            },
                                            label = { Text(p.name, maxLines = 1) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(selectedProvider.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(selectedProvider.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("${selectedProvider.displayName} API Key") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("onboarding_api_key_input")
                            )
                            if (selectedProvider.requiresAccountId) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = accountIdInput,
                                    onValueChange = { accountIdInput = it },
                                    label = { Text("Cloudflare Account ID") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customBaseUrl,
                                onValueChange = { customBaseUrl = it },
                                label = { Text("HTTPS Base URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.saveProviderConfig(
                                        providerType = selectedProvider,
                                        rawApiKey = apiKeyInput,
                                        baseUrl = customBaseUrl,
                                        accountId = accountIdInput,
                                        enabled = true,
                                        isPaidProvider = selectedProvider == AiProviderType.OPENROUTER,
                                        defaultModel = "",
                                        dailyRequestLimit = 500,
                                        dailyTokenLimit = 250_000
                                    )
                                    apiKeyInput = ""
                                },
                                enabled = apiKeyInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text("Encrypt & Save Key")
                            }
                        }
                    }

                    5 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("5. Test Configured AI Providers", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            val configured = state.providers.filter { it.encryptedApiKey.isNotBlank() }
                            if (configured.isEmpty()) {
                                Text(
                                    "No AI keys added yet. You can skip this step and configure keys anytime in API Provider Manager.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                configured.forEach { cfg ->
                                    val pType = AiProviderType.valueOf(cfg.providerType)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cfg.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = if (cfg.connectionVerified) "Verified (${cfg.lastLatencyMs}ms)" else cfg.lastError.ifBlank { "Not tested yet" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (cfg.connectionVerified) wafa.success else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.testAndFetchModels(pType) },
                                            enabled = !state.isBusy
                                        ) {
                                            Text(strings.testConnection)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    6 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("6. Connect Gmail or Outlook (Optional)", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Connect an official OAuth 2.0 PKCE inbox now, or skip to connect later / import .eml files directly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedMailProvider == OAuthEmailProvider.GMAIL,
                                    onClick = { selectedMailProvider = OAuthEmailProvider.GMAIL },
                                    label = { Text("Gmail OAuth") }
                                )
                                FilterChip(
                                    selected = selectedMailProvider == OAuthEmailProvider.OUTLOOK,
                                    onClick = { selectedMailProvider = OAuthEmailProvider.OUTLOOK },
                                    label = { Text("Outlook Graph") }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = oauthClientId,
                                onValueChange = { oauthClientId = it },
                                label = { Text("${selectedMailProvider.name} OAuth Client ID") },
                                supportingText = { Text("Redirect URI: wafa.inboxai://oauth/callback") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.launchOAuthPkce(selectedMailProvider, oauthClientId, false) },
                                enabled = oauthClientId.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text("Authorize ${selectedMailProvider.name} (Read-Only Scope)")
                            }
                            if (state.accounts.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Connected: ${state.accounts.joinToString { it.emailAddress }}", color = wafa.success, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    7 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("7. AI Operation Mode & Permissions", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            AiOperationMode.entries.forEach { mode ->
                                val desc = when (mode) {
                                    AiOperationMode.COPILOT -> "AI analyzes and recommends only. You execute every action manually."
                                    AiOperationMode.APPROVAL -> "AI prepares drafts & actions in an Approval Queue requiring confirmation."
                                    AiOperationMode.SMART_AUTOMATION -> "Auto-runs low-risk authorized rules only. Sending/deleting ALWAYS asks first."
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = state.preferences.operationMode == mode,
                                        onClick = { viewModel.setOperationMode(mode) }
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(mode.labelEn, style = MaterialTheme.typography.titleSmall)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Text("Enable Follow-up & Security Notifications")
                                }
                            }
                        }
                    }

                    8 -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("8. Setup Complete", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Workspace summary:", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text("• Account: ${state.currentUser?.email ?: "Local User"}", style = MaterialTheme.typography.bodyMedium)
                            Text("• Active AI Providers: ${state.configuredProviderCount} / 6", style = MaterialTheme.typography.bodyMedium)
                            Text("• Connected Email Accounts: ${state.accounts.size}", style = MaterialTheme.typography.bodyMedium)
                            Text("• AI Operation Mode: ${state.preferences.operationMode.labelEn}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "You are in full control. Launch your workspace now and adjust any setting later in Control Center.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text(strings.back)
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.setOnboardingCompleted(true)
                            onFinish()
                        },
                        modifier = Modifier.weight(1f).height(50.dp).testTag("onboarding_skip_all")
                    ) {
                        Text(strings.skipForNow)
                    }
                }
                Button(
                    onClick = {
                        if (step < 8) step++
                        else {
                            viewModel.setOnboardingCompleted(true)
                            onFinish()
                        }
                    },
                    modifier = Modifier.weight(1.4f).height(50.dp).testTag("onboarding_next_button")
                ) {
                    Text(if (step == 8) strings.finishSetup else strings.nextStep)
                }
            }
        }
    }
}
