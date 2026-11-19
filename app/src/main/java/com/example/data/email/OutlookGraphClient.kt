package com.example.data.email

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Genuine Microsoft Graph v1.0 Mail client for Outlook / Microsoft 365 accounts.
 */
class OutlookGraphClient(private val httpClient: OkHttpClient) {

    private val baseUrl = "https://graph.microsoft.com/v1.0/me"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun getProfile(accessToken: String): Result<EmailProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl?\$select=displayName,mail,userPrincipalName")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Microsoft Graph /me failed (HTTP ${resp.code}): ${body.take(180)}")
                val obj = JSONObject(body)
                val email = obj.optString("mail").ifBlank { obj.optString("userPrincipalName") }
                require(email.isNotBlank()) { "Microsoft profile missing email address." }
                EmailProfile(
                    emailAddress = email,
                    displayName = obj.optString("displayName", email.substringBefore("@"))
                )
            }
        }
    }

    suspend fun syncMessages(
        accessToken: String,
        deltaCursor: String,
        top: Int = 25
    ): Result<EmailSyncBatch> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (deltaCursor.startsWith("https://graph.microsoft.com/")) {
                deltaCursor
            } else {
                "$baseUrl/mailFolders/inbox/messages/delta?\$top=$top&\$select=id,conversationId,subject,bodyPreview,body,from,replyTo,toRecipients,isRead,receivedDateTime,hasAttachments,flag"
            }
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Prefer", "outlook.body-content-type=\"text\"")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Microsoft Graph sync failed (HTTP ${resp.code}): ${body.take(180)}")
                val root = JSONObject(body)
                val nextCursor = root.optString("@odata.deltaLink", deltaCursor)
                val arr = root.optJSONArray("value") ?: JSONArray()
                val messages = mutableListOf<RemoteEmailMessage>()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("id", "")
                    if (id.isBlank()) continue
                    val fromObj = m.optJSONObject("from")?.optJSONObject("emailAddress")
                    val senderEmail = fromObj?.optString("address", "").orEmpty()
                    val senderName = fromObj?.optString("name", senderEmail.substringBefore("@")).orEmpty()
                    val replyTo = m.optJSONArray("replyTo")
                        ?.optJSONObject(0)
                        ?.optJSONObject("emailAddress")
                        ?.optString("address", "")
                        .orEmpty()
                    val bodyObj = m.optJSONObject("body")
                    val rawContent = bodyObj?.optString("content", "").orEmpty()
                    val contentType = bodyObj?.optString("contentType", "text").orEmpty()
                    val bodyText = if (contentType.equals("html", true)) {
                        GmailApiClient.stripHtml(rawContent)
                    } else rawContent
                    val receivedMs = runCatching {
                        Instant.parse(m.optString("receivedDateTime")).toEpochMilli()
                    }.getOrDefault(System.currentTimeMillis())
                    val flagged = m.optJSONObject("flag")?.optString("flagStatus") == "flagged"

                    messages += RemoteEmailMessage(
                        remoteId = id,
                        threadId = m.optString("conversationId", id),
                        senderName = senderName,
                        senderEmail = senderEmail,
                        replyTo = replyTo,
                        recipients = "",
                        subject = m.optString("subject", "(No Subject)"),
                        snippet = m.optString("bodyPreview", ""),
                        bodyText = bodyText.ifBlank { m.optString("bodyPreview", "") },
                        authHeaders = "",
                        labels = listOf("INBOX"),
                        isRead = m.optBoolean("isRead", false),
                        isArchived = false,
                        isStarred = flagged,
                        receivedAt = receivedMs,
                        attachments = emptyList()
                    )
                }
                EmailSyncBatch(messages = messages, nextCursor = nextCursor)
            }
        }
    }

    suspend fun markRead(accessToken: String, messageId: String, isRead: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().put("isRead", isRead)
                val req = Request.Builder()
                    .url("$baseUrl/messages/$messageId")
                    .header("Authorization", "Bearer $accessToken")
                    .patch(payload.toString().toRequestBody(jsonType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Outlook markRead failed (HTTP ${resp.code})")
                }
            }
        }

    suspend fun archiveMessage(accessToken: String, messageId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().put("destinationId", "archive")
                val req = Request.Builder()
                    .url("$baseUrl/messages/$messageId/move")
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toString().toRequestBody(jsonType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Outlook archive failed (HTTP ${resp.code})")
                }
            }
        }

    suspend fun createDraft(
        accessToken: String,
        to: String,
        subject: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildGraphMessage(to, subject, body)
            val req = Request.Builder()
                .url("$baseUrl/messages")
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Outlook draft creation failed (HTTP ${resp.code}): ${respBody.take(180)}")
                JSONObject(respBody).optString("id", "")
            }
        }
    }

    suspend fun sendMail(
        accessToken: String,
        to: String,
        subject: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("message", buildGraphMessage(to, subject, body))
                .put("saveToSentItems", true)
            val req = Request.Builder()
                .url("$baseUrl/sendMail")
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Outlook sendMail failed (HTTP ${resp.code}): ${resp.body?.string()?.take(180)}")
                }
            }
        }
    }

    private fun buildGraphMessage(to: String, subject: String, body: String): JSONObject =
        JSONObject()
            .put("subject", subject.trim())
            .put("body", JSONObject().put("contentType", "Text").put("content", body))
            .put(
                "toRecipients",
                JSONArray().put(
                    JSONObject().put("emailAddress", JSONObject().put("address", to.trim()))
                )
            )
}
