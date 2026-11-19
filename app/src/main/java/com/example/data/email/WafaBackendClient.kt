package com.example.data.email

import com.example.core.security.NetworkSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class BackendHealthResult(
    val healthy: Boolean,
    val version: String,
    val message: String,
    val latencyMs: Long
)

data class BackendAuthResult(
    val userId: String,
    val fullName: String,
    val email: String,
    val sessionToken: String
)

/**
 * HTTPS client connecting WAFA InboxAI Android to the companion PHP 8.2+ & MySQL backend
 * for cloud auth, encrypted server-side credential sync, Yahoo/Generic IMAP/SMTP bridge,
 * and authenticated background task execution.
 */
class WafaBackendClient(private val httpClient: OkHttpClient) {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(baseUrl: String): BackendHealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val validated = NetworkSecurity.validateHttpsUrl(baseUrl).getOrElse {
            return@withContext BackendHealthResult(
                healthy = false,
                version = "",
                message = it.message ?: "Invalid HTTPS URL.",
                latencyMs = System.currentTimeMillis() - start
            )
        }
        val req = Request.Builder().url("$validated/api/health").get().build()
        try {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext BackendHealthResult(
                        healthy = false,
                        version = "",
                        message = "Backend responded with HTTP ${resp.code}",
                        latencyMs = System.currentTimeMillis() - start
                    )
                }
                val json = runCatching { JSONObject(body) }.getOrNull()
                BackendHealthResult(
                    healthy = true,
                    version = json?.optString("version", "1.0") ?: "1.0",
                    message = json?.optString("message", "WAFA InboxAI backend reachable over HTTPS.")
                        ?: "Connected.",
                    latencyMs = System.currentTimeMillis() - start
                )
            }
        } catch (t: Throwable) {
            BackendHealthResult(
                healthy = false,
                version = "",
                message = "Could not reach backend: ${t.message ?: "connection error"}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    suspend fun login(baseUrl: String, email: String, password: String): Result<BackendAuthResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val validated = NetworkSecurity.validateHttpsUrl(baseUrl).getOrThrow()
                val payload = JSONObject().put("email", email.trim()).put("password", password)
                val req = Request.Builder()
                    .url("$validated/api/auth/login")
                    .post(payload.toString().toRequestBody(jsonType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Backend login failed (HTTP ${resp.code}): ${body.take(180)}")
                    val obj = JSONObject(body)
                    val userObj = obj.optJSONObject("user") ?: obj
                    BackendAuthResult(
                        userId = userObj.optString("id"),
                        fullName = userObj.optString("full_name", email.substringBefore("@")),
                        email = userObj.optString("email", email),
                        sessionToken = obj.optString("token")
                    )
                }
            }
        }

    suspend fun syncImapBridge(
        baseUrl: String,
        sessionToken: String,
        accountId: String
    ): Result<List<RemoteEmailMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            val validated = NetworkSecurity.validateHttpsUrl(baseUrl).getOrThrow()
            require(sessionToken.isNotBlank()) { "Authenticate with your PHP backend first to sync IMAP accounts." }
            val req = Request.Builder()
                .url("$validated/api/email/imap/sync?account_id=$accountId")
                .header("Authorization", "Bearer $sessionToken")
                .post("{}".toRequestBody(jsonType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("IMAP bridge sync failed (HTTP ${resp.code}): ${body.take(180)}")
                val arr = JSONObject(body).optJSONArray("messages") ?: JSONArray()
                val result = mutableListOf<RemoteEmailMessage>()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    result += RemoteEmailMessage(
                        remoteId = m.optString("id"),
                        threadId = m.optString("thread_id", m.optString("id")),
                        senderName = m.optString("sender_name"),
                        senderEmail = m.optString("sender_email"),
                        replyTo = m.optString("reply_to"),
                        recipients = m.optString("recipients"),
                        subject = m.optString("subject", "(No Subject)"),
                        snippet = m.optString("snippet"),
                        bodyText = m.optString("body"),
                        authHeaders = m.optString("auth_headers"),
                        labels = listOf("INBOX"),
                        isRead = m.optBoolean("is_read", false),
                        isArchived = m.optBoolean("is_archived", false),
                        isStarred = m.optBoolean("is_starred", false),
                        receivedAt = m.optLong("received_at", System.currentTimeMillis()),
                        attachments = emptyList()
                    )
                }
                result
            }
        }
    }
}
