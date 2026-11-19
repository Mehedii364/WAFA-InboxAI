package com.example.data.email

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class RemoteAttachmentMeta(
    val remoteAttachmentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class RemoteEmailMessage(
    val remoteId: String,
    val threadId: String,
    val senderName: String,
    val senderEmail: String,
    val replyTo: String,
    val recipients: String,
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val authHeaders: String,
    val labels: List<String>,
    val isRead: Boolean,
    val isArchived: Boolean,
    val isStarred: Boolean,
    val receivedAt: Long,
    val attachments: List<RemoteAttachmentMeta>
)

data class EmailSyncBatch(
    val messages: List<RemoteEmailMessage>,
    val nextCursor: String
)

data class EmailProfile(
    val emailAddress: String,
    val displayName: String,
    val historyCursor: String = ""
)

/**
 * Genuine Gmail REST API v1 client supporting profile inspection, incremental history sync,
 * MIME multipart decoding, labels, read/archive mutations, draft creation, and user-approved sending.
 */
class GmailApiClient(private val httpClient: OkHttpClient) {

    private val baseUrl = "https://gmail.googleapis.com/gmail/v1/users/me"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun getProfile(accessToken: String): Result<EmailProfile> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/profile")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Gmail profile request failed (HTTP ${resp.code}): ${body.take(180)}")
                val obj = JSONObject(body)
                val email = obj.optString("emailAddress", "")
                require(email.isNotBlank()) { "Gmail profile missing emailAddress." }
                EmailProfile(
                    emailAddress = email,
                    displayName = email.substringBefore("@"),
                    historyCursor = obj.optString("historyId", "")
                )
            }
        }
    }

    suspend fun syncMessages(
        accessToken: String,
        historyCursor: String,
        maxResults: Int = 25
    ): Result<EmailSyncBatch> = withContext(Dispatchers.IO) {
        runCatching {
            val messageIds = linkedSetOf<String>()
            var latestHistoryId = historyCursor

            // Attempt incremental history sync if a cursor already exists
            if (historyCursor.isNotBlank()) {
                val histReq = Request.Builder()
                    .url("$baseUrl/history?startHistoryId=$historyCursor&historyTypes=messageAdded&maxResults=$maxResults")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                httpClient.newCall(histReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val root = JSONObject(body)
                        latestHistoryId = root.optString("historyId", latestHistoryId)
                        val historyArr = root.optJSONArray("history") ?: JSONArray()
                        for (i in 0 until historyArr.length()) {
                            val added = historyArr.optJSONObject(i)?.optJSONArray("messagesAdded") ?: continue
                            for (j in 0 until added.length()) {
                                val id = added.optJSONObject(j)?.optJSONObject("message")?.optString("id", "").orEmpty()
                                if (id.isNotBlank()) messageIds += id
                            }
                        }
                    } else if (resp.code == 404) {
                        // History ID expired; fall back to full list sync below
                        latestHistoryId = ""
                    } else {
                        error("Gmail history sync failed (HTTP ${resp.code}): ${body.take(180)}")
                    }
                }
            }

            if (latestHistoryId.isBlank()) {
                val listReq = Request.Builder()
                    .url("$baseUrl/messages?maxResults=$maxResults")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                httpClient.newCall(listReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Gmail list failed (HTTP ${resp.code}): ${body.take(180)}")
                    val arr = JSONObject(body).optJSONArray("messages") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val id = arr.optJSONObject(i)?.optString("id", "").orEmpty()
                        if (id.isNotBlank()) messageIds += id
                    }
                }
                getProfile(accessToken).getOrNull()?.let { latestHistoryId = it.historyCursor }
            }

            val parsed = mutableListOf<RemoteEmailMessage>()
            for (id in messageIds.take(maxResults)) {
                val msg = fetchMessage(accessToken, id).getOrNull()
                if (msg != null) parsed += msg
            }
            EmailSyncBatch(messages = parsed, nextCursor = latestHistoryId)
        }
    }

    suspend fun fetchMessage(accessToken: String, messageId: String): Result<RemoteEmailMessage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/messages/$messageId?format=full")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Gmail getMessage failed (HTTP ${resp.code})")
                    parseGmailMessage(JSONObject(body))
                }
            }
        }

    suspend fun modifyMessage(
        accessToken: String,
        messageId: String,
        addLabels: List<String> = emptyList(),
        removeLabels: List<String> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                if (addLabels.isNotEmpty()) put("addLabelIds", JSONArray(addLabels))
                if (removeLabels.isNotEmpty()) put("removeLabelIds", JSONArray(removeLabels))
            }
            val req = Request.Builder()
                .url("$baseUrl/messages/$messageId/modify")
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Gmail modify failed (HTTP ${resp.code}): ${resp.body?.string()?.take(180)}")
                }
            }
        }
    }

    suspend fun createDraft(
        accessToken: String,
        to: String,
        subject: String,
        body: String,
        threadId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rawMime = buildRfc2822Raw(to, subject, body)
            val msgObj = JSONObject().put("raw", rawMime).apply {
                if (threadId.isNotBlank()) put("threadId", threadId)
            }
            val payload = JSONObject().put("message", msgObj)
            val req = Request.Builder()
                .url("$baseUrl/drafts")
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Gmail createDraft failed (HTTP ${resp.code}): ${respBody.take(180)}")
                JSONObject(respBody).optString("id", "")
            }
        }
    }

    suspend fun sendMessage(
        accessToken: String,
        to: String,
        subject: String,
        body: String,
        threadId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rawMime = buildRfc2822Raw(to, subject, body)
            val payload = JSONObject().put("raw", rawMime).apply {
                if (threadId.isNotBlank()) put("threadId", threadId)
            }
            val req = Request.Builder()
                .url("$baseUrl/messages/send")
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Gmail send failed (HTTP ${resp.code}): ${respBody.take(180)}")
                JSONObject(respBody).optString("id", "")
            }
        }
    }

    suspend fun downloadAttachment(
        accessToken: String,
        messageId: String,
        attachmentId: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/messages/$messageId/attachments/$attachmentId")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Gmail attachment fetch failed (HTTP ${resp.code})")
                val data = JSONObject(body).optString("data", "")
                Base64.decode(data, Base64.URL_SAFE)
            }
        }
    }

    private fun parseGmailMessage(obj: JSONObject): RemoteEmailMessage {
        val id = obj.optString("id")
        val threadId = obj.optString("threadId", id)
        val snippet = obj.optString("snippet", "")
        val internalDate = obj.optString("internalDate").toLongOrNull() ?: System.currentTimeMillis()
        val labelsArr = obj.optJSONArray("labelIds") ?: JSONArray()
        val labels = (0 until labelsArr.length()).map { labelsArr.optString(it) }

        val payload = obj.optJSONObject("payload") ?: JSONObject()
        val headersArr = payload.optJSONArray("headers") ?: JSONArray()
        val headers = mutableMapOf<String, String>()
        for (i in 0 until headersArr.length()) {
            val h = headersArr.optJSONObject(i) ?: continue
            headers[h.optString("name").lowercase()] = h.optString("value")
        }

        val fromHeader = headers["from"].orEmpty()
        val (senderName, senderEmail) = parseEmailHeader(fromHeader)
        val replyTo = parseEmailHeader(headers["reply-to"].orEmpty()).second
        val subject = headers["subject"].orEmpty().ifBlank { "(No Subject)" }
        val authHeaders = listOfNotNull(
            headers["authentication-results"],
            headers["received-spf"]
        ).joinToString(" | ")

        val plainParts = mutableListOf<String>()
        val htmlParts = mutableListOf<String>()
        val attachments = mutableListOf<RemoteAttachmentMeta>()
        extractMimeParts(payload, plainParts, htmlParts, attachments)

        val bodyText = when {
            plainParts.isNotEmpty() -> plainParts.joinToString("\n")
            htmlParts.isNotEmpty() -> stripHtml(htmlParts.joinToString("\n"))
            else -> snippet
        }

        return RemoteEmailMessage(
            remoteId = id,
            threadId = threadId,
            senderName = senderName,
            senderEmail = senderEmail,
            replyTo = replyTo,
            recipients = headers["to"].orEmpty(),
            subject = subject,
            snippet = snippet,
            bodyText = bodyText.trim(),
            authHeaders = authHeaders,
            labels = labels,
            isRead = !labels.contains("UNREAD"),
            isArchived = !labels.contains("INBOX"),
            isStarred = labels.contains("STARRED"),
            receivedAt = internalDate,
            attachments = attachments
        )
    }

    private fun extractMimeParts(
        part: JSONObject,
        plainOut: MutableList<String>,
        htmlOut: MutableList<String>,
        attachmentsOut: MutableList<RemoteAttachmentMeta>
    ) {
        val mime = part.optString("mimeType", "").lowercase()
        val fileName = part.optString("filename", "")
        val body = part.optJSONObject("body")
        val attachmentId = body?.optString("attachmentId", "").orEmpty()
        if (fileName.isNotBlank() && attachmentId.isNotBlank()) {
            attachmentsOut += RemoteAttachmentMeta(
                remoteAttachmentId = attachmentId,
                fileName = fileName,
                mimeType = mime.ifBlank { "application/octet-stream" },
                sizeBytes = body?.optLong("size", 0L) ?: 0L
            )
        } else {
            val data = body?.optString("data", "").orEmpty()
            if (data.isNotBlank()) {
                val decoded = runCatching {
                    String(Base64.decode(data, Base64.URL_SAFE), Charsets.UTF_8)
                }.getOrDefault("")
                if (mime.contains("text/plain")) plainOut += decoded
                else if (mime.contains("text/html")) htmlOut += decoded
            }
        }
        val nested = part.optJSONArray("parts") ?: return
        for (i in 0 until nested.length()) {
            nested.optJSONObject(i)?.let { extractMimeParts(it, plainOut, htmlOut, attachmentsOut) }
        }
    }

    private fun buildRfc2822Raw(to: String, subject: String, body: String): String {
        val mime = buildString {
            append("To: ").append(to.trim()).append("\r\n")
            append("Subject: ").append(subject.trim()).append("\r\n")
            append("Content-Type: text/plain; charset=\"UTF-8\"\r\n")
            append("MIME-Version: 1.0\r\n\r\n")
            append(body)
        }
        return Base64.encodeToString(
            mime.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    companion object {
        fun parseEmailHeader(raw: String): Pair<String, String> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return "" to ""
            val angleMatch = Regex("^(.*?)<([^>]+)>").find(trimmed)
            return if (angleMatch != null) {
                val name = angleMatch.groupValues[1].trim().trim('"')
                val email = angleMatch.groupValues[2].trim()
                (name.ifBlank { email.substringBefore("@") }) to email
            } else {
                trimmed.substringBefore("@") to trimmed
            }
        }

        fun stripHtml(html: String): String = html
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>|</div>|</tr>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
