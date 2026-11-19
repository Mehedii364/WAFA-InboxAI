package com.example.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.i18n.LocalWafaStrings
import com.example.ui.screens.*
import com.example.ui.viewmodel.WafaUiState
import com.example.ui.viewmodel.WafaViewModel

object WafaRoutes {
    const val WELCOME = "welcome"
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val INBOX = "inbox"
    const val AI_HUB = "ai_hub"
    const val WORKFLOWS_HUB = "workflows_hub"
    const val CONTROL_CENTER = "control_center"
    const val EMAIL_DETAIL = "email_detail/{emailId}"
    const val CONVERSATION = "conversation/{threadId}"
    const val DRAFTS = "drafts"
    const val ATTACHMENTS = "attachments"
    const val ACCOUNTS = "accounts"
    const val PROVIDERS = "providers"
    const val MODEL_ROUTER = "model_router"
    const val ANALYTICS = "analytics"
    const val NOTIFICATIONS = "notifications"
    const val SECURITY = "security"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val HELP = "help"

    fun emailDetail(emailId: String) = "email_detail/${Uri.encode(emailId)}"
    fun conversation(threadId: String) = "conversation/${Uri.encode(threadId)}"
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun WafaAppShell(
    state: WafaUiState,
    viewModel: WafaViewModel
) {
    val context = LocalContext.current
    val strings = LocalWafaStrings.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Launch external HTTPS browser for real OAuth 2.0 PKCE when triggered
    LaunchedEffect(state.pendingOAuthUri) {
        val uri = state.pendingOAuthUri ?: return@LaunchedEffect
        viewModel.consumePendingOAuthUri()
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            viewModel.showMessage("No browser available to complete OAuth sign-in.", isError = true)
        }
    }

    // Surface error & status messages cleanly
    LaunchedEffect(state.bannerMessage) {
        val msg = state.bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        viewModel.dismissBanner()
    }

    val startDestination = remember(state.preferences.activeUserId, state.preferences.onboardingCompleted) {
        when {
            state.preferences.activeUserId.isBlank() -> WafaRoutes.WELCOME
            !state.preferences.onboardingCompleted -> WafaRoutes.ONBOARDING
            else -> WafaRoutes.DASHBOARD
        }
    }

    // Redirect cleanly to Welcome when signed out
    LaunchedEffect(state.preferences.activeUserId) {
        val current = navController.currentDestination?.route
        if (state.preferences.activeUserId.isBlank() &&
            current != null &&
            current != WafaRoutes.WELCOME &&
            current != WafaRoutes.AUTH
        ) {
            navController.navigate(WafaRoutes.WELCOME) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val tabs = listOf(
        TopLevelTab(WafaRoutes.DASHBOARD, strings.navDashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        TopLevelTab(WafaRoutes.INBOX, strings.navInbox, Icons.Filled.Inbox, Icons.Outlined.Inbox),
        TopLevelTab(WafaRoutes.AI_HUB, strings.navAiHub, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
        TopLevelTab(WafaRoutes.WORKFLOWS_HUB, strings.navWorkflows, Icons.Filled.AccountTree, Icons.Outlined.AccountTree),
        TopLevelTab(WafaRoutes.CONTROL_CENTER, strings.navControl, Icons.Filled.Tune, Icons.Outlined.Tune)
    )
    val topLevelRoutes = remember { tabs.map { it.route }.toSet() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showMainChrome = currentRoute in topLevelRoutes

    val navigateTab: (String) -> Unit = { route ->
        if (route in topLevelRoutes) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Adaptive NavigationRail for Tablets, Foldables & ChromeOS
            if (showMainChrome && isWideScreen) {
                NavigationRail(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(Modifier.height(12.dp))
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label, maxLines = 1) },
                            modifier = Modifier.testTag("nav_rail_${tab.route}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (showMainChrome && !isWideScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_bar_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(WafaRoutes.WELCOME) {
                            WelcomeScreen(
                                state = state,
                                viewModel = viewModel,
                                onContinueToAuth = { navController.navigate(WafaRoutes.AUTH) },
                                onContinueToOnboarding = { navController.navigate(WafaRoutes.ONBOARDING) }
                            )
                        }
                        composable(WafaRoutes.AUTH) {
                            AuthenticationScreen(
                                state = state,
                                viewModel = viewModel,
                                onAuthComplete = {
                                    val dest = if (state.preferences.onboardingCompleted) WafaRoutes.DASHBOARD else WafaRoutes.ONBOARDING
                                    navController.navigate(dest) {
                                        popUpTo(WafaRoutes.WELCOME) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(WafaRoutes.ONBOARDING) {
                            OnboardingWizardScreen(
                                state = state,
                                viewModel = viewModel,
                                onFinish = {
                                    navController.navigate(WafaRoutes.DASHBOARD) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(WafaRoutes.DASHBOARD) {
                            DashboardScreen(state = state, viewModel = viewModel, onNavigate = navigateTab)
                        }
                        composable(WafaRoutes.INBOX) {
                            SmartInboxScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenEmail = { id -> navController.navigate(WafaRoutes.emailDetail(id)) },
                                onNavigate = navigateTab
                            )
                        }
                        composable(WafaRoutes.AI_HUB) {
                            AiHubScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenEmail = { id -> navController.navigate(WafaRoutes.emailDetail(id)) },
                                onNavigate = navigateTab
                            )
                        }
                        composable(WafaRoutes.WORKFLOWS_HUB) {
                            WorkflowsHubScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenDrafts = { navController.navigate(WafaRoutes.DRAFTS) },
                                onOpenEmail = { id -> navController.navigate(WafaRoutes.emailDetail(id)) }
                            )
                        }
                        composable(WafaRoutes.CONTROL_CENTER) {
                            ControlCenterScreen(state = state, onNavigate = navigateTab)
                        }
                        composable(
                            route = WafaRoutes.EMAIL_DETAIL,
                            arguments = listOf(navArgument("emailId") { type = NavType.StringType })
                        ) { backStack ->
                            val emailId = Uri.decode(backStack.arguments?.getString("emailId").orEmpty())
                            EmailDetailScreen(
                                emailId = emailId,
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenConversation = { tid -> navController.navigate(WafaRoutes.conversation(tid)) },
                                onOpenDrafts = { navController.navigate(WafaRoutes.DRAFTS) }
                            )
                        }
                        composable(
                            route = WafaRoutes.CONVERSATION,
                            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
                        ) { backStack ->
                            val threadId = Uri.decode(backStack.arguments?.getString("threadId").orEmpty())
                            ConversationViewScreen(
                                threadId = threadId,
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenEmail = { id -> navController.navigate(WafaRoutes.emailDetail(id)) }
                            )
                        }
                        composable(WafaRoutes.DRAFTS) {
                            AiDraftsScreen(state = state, viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.ATTACHMENTS) {
                            AttachmentManagerScreen(state = state, viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.ACCOUNTS) {
                            ConnectedAccountsScreen(state = state, viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.PROVIDERS) {
                            ApiProviderManagerScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenRouter = { navController.navigate(WafaRoutes.MODEL_ROUTER) }
                            )
                        }
                        composable(WafaRoutes.MODEL_ROUTER) {
                            ModelRouterScreen(state = state, viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.ANALYTICS) {
                            AiUsageAnalyticsScreen(state = state, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.NOTIFICATIONS) {
                            NotificationCenterScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenEmail = { id -> navController.navigate(WafaRoutes.emailDetail(id)) }
                            )
                        }
                        composable(WafaRoutes.SECURITY) {
                            SecurityCenterScreen(state = state, viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.PROFILE) {
                            UserProfileScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onReopenOnboarding = { navController.navigate(WafaRoutes.ONBOARDING) }
                            )
                        }
                        composable(WafaRoutes.SETTINGS) {
                            ApplicationSettingsScreen(state = state, viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.HELP) {
                            HelpDocsScreen(onBack = { navController.popBackStack() })
                        }
                    }

                    // Non-blocking top progress strip during live network/AI calls
                    if (state.isBusy) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                            tonalElevation = 4.dp
                        ) {
                            Column {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                if (state.busyMessage.isNotBlank()) {
                                    Text(
                                        text = state.busyMessage,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
