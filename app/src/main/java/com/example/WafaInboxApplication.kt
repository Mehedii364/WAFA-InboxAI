package com.example

import android.app.Application
import com.example.core.security.AndroidKeyStoreVault
import com.example.core.security.NetworkSecurity
import com.example.data.ai.AiProviderRegistry
import com.example.data.ai.IntelligentModelRouter
import com.example.data.email.GmailApiClient
import com.example.data.email.OAuthPkceManager
import com.example.data.email.OutlookGraphClient
import com.example.data.email.WafaBackendClient
import com.example.data.jobs.WafaNotificationHelper
import com.example.data.local.PreferencesStore
import com.example.data.local.WafaDatabase
import com.example.data.repository.WafaRepository
import com.example.domain.agents.AutomationEngine
import com.example.domain.agents.WafaAiAgents

class WafaInboxApplication : Application() {

    lateinit var repository: WafaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        WafaNotificationHelper.ensureChannels(this)

        val db = WafaDatabase.getInstance(this)
        val prefsStore = PreferencesStore(this)
        val vault = AndroidKeyStoreVault()
        val httpClient = NetworkSecurity.buildSafeHttpClient()
        val registry = AiProviderRegistry(httpClient)
        val router = IntelligentModelRouter(db.aiProviderDao(), vault, registry)
        val oauthManager = OAuthPkceManager(httpClient)
        val gmailClient = GmailApiClient(httpClient)
        val outlookClient = OutlookGraphClient(httpClient)
        val backendClient = WafaBackendClient(httpClient)
        val aiAgents = WafaAiAgents(router)
        val automationEngine = AutomationEngine(db.emailDao(), db.workspaceDao(), aiAgents)

        repository = WafaRepository(
            context = this,
            db = db,
            prefsStore = prefsStore,
            vault = vault,
            providerRegistry = registry,
            modelRouter = router,
            oauthManager = oauthManager,
            gmailClient = gmailClient,
            outlookClient = outlookClient,
            backendClient = backendClient,
            aiAgents = aiAgents,
            automationEngine = automationEngine
        )
    }
}
