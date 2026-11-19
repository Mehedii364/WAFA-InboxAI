package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wafaDataStore by preferencesDataStore(name = "wafa_inboxai_prefs")

enum class AiOperationMode(val labelEn: String, val labelBn: String) {
    COPILOT("Copilot Mode", "কোপাইলট মোড"),
    APPROVAL("Approval Mode", "অ্যাপ্রুভাল মোড"),
    SMART_AUTOMATION("Smart Automation", "স্মার্ট অটোমেশন")
}

data class WafaPreferences(
    val activeUserId: String = "",
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.EN,
    val backendBaseUrl: String = "",
    val operationMode: AiOperationMode = AiOperationMode.COPILOT,
    val allowAutoCategorize: Boolean = true,
    val allowAutoLabel: Boolean = true,
    val allowAutoReminder: Boolean = true,
    val allowAutoDraft: Boolean = true,
    val allowAutoArchive: Boolean = false,
    val allowAutoMarkRead: Boolean = false,
    val allowPaidFallback: Boolean = false,
    val syncIntervalMinutes: Int = 30,
    val lastGlobalSyncAt: Long = 0L,
    val selectedAccountId: String = "" // empty means All Accounts
)

class PreferencesStore(private val context: Context) {

    private object Keys {
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val OPERATION_MODE = stringPreferencesKey("operation_mode")
        val ALLOW_AUTO_CATEGORIZE = booleanPreferencesKey("allow_auto_categorize")
        val ALLOW_AUTO_LABEL = booleanPreferencesKey("allow_auto_label")
        val ALLOW_AUTO_REMINDER = booleanPreferencesKey("allow_auto_reminder")
        val ALLOW_AUTO_DRAFT = booleanPreferencesKey("allow_auto_draft")
        val ALLOW_AUTO_ARCHIVE = booleanPreferencesKey("allow_auto_archive")
        val ALLOW_AUTO_MARK_READ = booleanPreferencesKey("allow_auto_mark_read")
        val ALLOW_PAID_FALLBACK = booleanPreferencesKey("allow_paid_fallback")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val LAST_GLOBAL_SYNC_AT = longPreferencesKey("last_global_sync_at")
        val SELECTED_ACCOUNT_ID = stringPreferencesKey("selected_account_id")
    }

    val preferencesFlow: Flow<WafaPreferences> = context.wafaDataStore.data.map { prefs: Preferences ->
        WafaPreferences(
            activeUserId = prefs[Keys.ACTIVE_USER_ID] ?: "",
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
            }.getOrDefault(ThemeMode.DARK),
            language = runCatching {
                AppLanguage.valueOf(prefs[Keys.LANGUAGE] ?: AppLanguage.EN.name)
            }.getOrDefault(AppLanguage.EN),
            backendBaseUrl = prefs[Keys.BACKEND_BASE_URL] ?: "",
            operationMode = runCatching {
                AiOperationMode.valueOf(prefs[Keys.OPERATION_MODE] ?: AiOperationMode.COPILOT.name)
            }.getOrDefault(AiOperationMode.COPILOT),
            allowAutoCategorize = prefs[Keys.ALLOW_AUTO_CATEGORIZE] ?: true,
            allowAutoLabel = prefs[Keys.ALLOW_AUTO_LABEL] ?: true,
            allowAutoReminder = prefs[Keys.ALLOW_AUTO_REMINDER] ?: true,
            allowAutoDraft = prefs[Keys.ALLOW_AUTO_DRAFT] ?: true,
            allowAutoArchive = prefs[Keys.ALLOW_AUTO_ARCHIVE] ?: false,
            allowAutoMarkRead = prefs[Keys.ALLOW_AUTO_MARK_READ] ?: false,
            allowPaidFallback = prefs[Keys.ALLOW_PAID_FALLBACK] ?: false,
            syncIntervalMinutes = prefs[Keys.SYNC_INTERVAL_MINUTES] ?: 30,
            lastGlobalSyncAt = prefs[Keys.LAST_GLOBAL_SYNC_AT] ?: 0L,
            selectedAccountId = prefs[Keys.SELECTED_ACCOUNT_ID] ?: ""
        )
    }

    suspend fun setActiveUser(userId: String) {
        context.wafaDataStore.edit { it[Keys.ACTIVE_USER_ID] = userId }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.wafaDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.wafaDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.wafaDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setBackendBaseUrl(url: String) {
        context.wafaDataStore.edit { it[Keys.BACKEND_BASE_URL] = url }
    }

    suspend fun setOperationMode(mode: AiOperationMode) {
        context.wafaDataStore.edit { it[Keys.OPERATION_MODE] = mode.name }
    }

    suspend fun updatePermissions(
        allowAutoCategorize: Boolean? = null,
        allowAutoLabel: Boolean? = null,
        allowAutoReminder: Boolean? = null,
        allowAutoDraft: Boolean? = null,
        allowAutoArchive: Boolean? = null,
        allowAutoMarkRead: Boolean? = null,
        allowPaidFallback: Boolean? = null
    ) {
        context.wafaDataStore.edit { prefs ->
            allowAutoCategorize?.let { prefs[Keys.ALLOW_AUTO_CATEGORIZE] = it }
            allowAutoLabel?.let { prefs[Keys.ALLOW_AUTO_LABEL] = it }
            allowAutoReminder?.let { prefs[Keys.ALLOW_AUTO_REMINDER] = it }
            allowAutoDraft?.let { prefs[Keys.ALLOW_AUTO_DRAFT] = it }
            allowAutoArchive?.let { prefs[Keys.ALLOW_AUTO_ARCHIVE] = it }
            allowAutoMarkRead?.let { prefs[Keys.ALLOW_AUTO_MARK_READ] = it }
            allowPaidFallback?.let { prefs[Keys.ALLOW_PAID_FALLBACK] = it }
        }
    }

    suspend fun revokeAllAiPermissions() {
        context.wafaDataStore.edit { prefs ->
            prefs[Keys.OPERATION_MODE] = AiOperationMode.COPILOT.name
            prefs[Keys.ALLOW_AUTO_CATEGORIZE] = false
            prefs[Keys.ALLOW_AUTO_LABEL] = false
            prefs[Keys.ALLOW_AUTO_REMINDER] = false
            prefs[Keys.ALLOW_AUTO_DRAFT] = false
            prefs[Keys.ALLOW_AUTO_ARCHIVE] = false
            prefs[Keys.ALLOW_AUTO_MARK_READ] = false
            prefs[Keys.ALLOW_PAID_FALLBACK] = false
        }
    }

    suspend fun setSyncInterval(minutes: Int) {
        context.wafaDataStore.edit { it[Keys.SYNC_INTERVAL_MINUTES] = minutes.coerceIn(15, 720) }
    }

    suspend fun setLastGlobalSyncAt(timestampMs: Long) {
        context.wafaDataStore.edit { it[Keys.LAST_GLOBAL_SYNC_AT] = timestampMs }
    }

    suspend fun setSelectedAccountId(accountId: String) {
        context.wafaDataStore.edit { it[Keys.SELECTED_ACCOUNT_ID] = accountId }
    }

    suspend fun clearSession() {
        context.wafaDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_USER_ID] = ""
            prefs[Keys.SELECTED_ACCOUNT_ID] = ""
        }
    }
}
