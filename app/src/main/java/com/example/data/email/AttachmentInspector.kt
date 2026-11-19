package com.example.data.email

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

data class InspectedAttachment(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isSafe: Boolean,
    val safetyWarning: String,
    val extractedText: String
)

data class ParsedEmlMessage(
    val senderName: String,
    val senderEmail: String,
    val replyTo: String,
    val recipients: String,
    val subject: String,
    val bodyText: String,
    val authHeaders: String,
    val receivedAt: Long
)

/**
 * Safe zero-execution attachment & RFC 822 .eml inspector.
 * Enforces strict 15 MB size limit, MIME allowlist, and parses TXT, CSV, DOCX, XLSX, PDF text, and EML.
 */
object AttachmentInspector {

    const val MAX_ATTACHMENT_BYTES: Long = 15L * 1024L * 1024L // 15 MB safety limit

    private val allowedExtensions = setOf(
        "pdf", "docx", "xlsx", "csv", "txt", "eml", "png", "jpg", "jpeg", "webp"
    )

    private val dangerousExtensions = setOf(
        "exe", "apk", "jar", "dex", "so", "bat", "cmd", "sh", "ps1", "js", "vbs", "msi", "scr", "dll"
    )

    fun inspectUri(context: Context, uri: Uri): Result<InspectedAttachment> = runCatching {
        val resolver = context.contentResolver
        var fileName = "document"
        var sizeBytes = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
            }
        }
        val mimeType = resolver.getType(uri) ?: guessMimeFromName(fileName)
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val limited = input.readNBytes((MAX_ATTACHMENT_BYTES + 1).toInt())
            if (sizeBytes <= 0L) sizeBytes = limited.size.toLong()
            limited
        } ?: error("Unable to read selected file.")

        inspectBytes(fileName, mimeType, bytes, sizeBytes)
    }

    fun inspectBytes(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        declaredSizeBytes: Long = bytes.size.toLong()
    ): InspectedAttachment {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val warnings = mutableListOf<String>()

        if (declaredSizeBytes > MAX_ATTACHMENT_BYTES || bytes.size > MAX_ATTACHMENT_BYTES) {
            warnings += "File exceeds 15 MB safety limit; full extraction skipped."
        }
        if (ext in dangerousExtensions) {
            warnings += "Blocked executable or script file extension (.$ext). Never executed."
            return InspectedAttachment(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = declaredSizeBytes,
                isSafe = false,
                safetyWarning = warnings.joinToString(" "),
                extractedText = ""
            )
        }
        if (ext.isNotEmpty() && ext !in allowedExtensions) {
            warnings += "Unsupported format (.$ext). Only PDF, DOCX, XLSX, CSV, TXT, EML, and standard images are supported."
        }

        val extracted = if (declaredSizeBytes <= MAX_ATTACHMENT_BYTES) {
            when (ext) {
                "txt", "csv", "eml" -> String(bytes, Charsets.UTF_8).take(24_000)
                "docx" -> extractXmlFromZip(bytes, "word/document.xml").take(24_000)
                "xlsx" -> extractXmlFromZip(bytes, "xl/sharedStrings.xml").take(24_000)
                "pdf" -> extractPrintablePdfText(bytes).take(24_000)
                "png", "jpg", "jpeg", "webp" -> "[Image asset: $fileName, $mimeType, ${declaredSizeBytes / 1024} KB — ready for metadata inspection]"
                else -> ""
            }
        } else ""

        return InspectedAttachment(
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = declaredSizeBytes,
            isSafe = warnings.isEmpty(),
            safetyWarning = warnings.joinToString(" "),
            extractedText = extracted.trim()
        )
    }

    fun parseEml(rawEml: String): ParsedEmlMessage {
        val normalized = rawEml.replace("\r\n", "\n")
        val splitIndex = normalized.indexOf("\n\n")
        val headerSection = if (splitIndex >= 0) normalized.substring(0, splitIndex) else normalized
        val bodySection = if (splitIndex >= 0) normalized.substring(splitIndex + 2) else ""

        val headers = mutableMapOf<String, String>()
        var currentKey = ""
        for (line in headerSection.lines()) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && currentKey.isNotEmpty()) {
                headers[currentKey] = (headers[currentKey].orEmpty() + " " + line.trim()).trim()
            } else {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    currentKey = line.substring(0, colon).trim().lowercase()
                    headers[currentKey] = line.substring(colon + 1).trim()
                }
            }
        }

        val (senderName, senderEmail) = GmailApiClient.parseEmailHeader(headers["from"].orEmpty())
        val replyTo = GmailApiClient.parseEmailHeader(headers["reply-to"].orEmpty()).second
        val subject = headers["subject"].orEmpty().ifBlank { "(Imported Message)" }
        val authHeaders = listOfNotNull(
            headers["authentication-results"],
            headers["received-spf"]
        ).joinToString(" | ")

        return ParsedEmlMessage(
            senderName = senderName.ifBlank { "Unknown Sender" },
            senderEmail = senderEmail.ifBlank { "unknown@imported.eml" },
            replyTo = replyTo,
            recipients = headers["to"].orEmpty(),
            subject = subject,
            bodyText = bodySection.trim().ifBlank { "(Empty body)" },
            authHeaders = authHeaders,
            receivedAt = System.currentTimeMillis()
        )
    }

    private fun extractXmlFromZip(bytes: ByteArray, entryPath: String): String = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.equals(entryPath, ignoreCase = true)) {
                    val xml = String(zis.readBytes(), Charsets.UTF_8)
                    return xml
                        .replace(Regex("</w:p>|</t>|</si>"), "\n")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("[ \\t]+"), " ")
                        .replace(Regex("\\n{3,}"), "\n\n")
                        .trim()
                }
                entry = zis.nextEntry
            }
        }
        ""
    }.getOrDefault("")

    private fun extractPrintablePdfText(bytes: ByteArray): String {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val matches = Regex("\\(([^()]{2,240})\\)\\s*Tj").findAll(raw)
            .map { it.groupValues[1].replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\") }
            .filter { str -> str.count { it.code in 32..126 } > str.length * 0.7 }
            .toList()
        return if (matches.isNotEmpty()) {
            matches.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        } else {
            "[PDF binary detected (${bytes.size / 1024} KB). Compressed stream PDFs can be summarized via your PHP backend parser or text export.]"
        }
    }

    private fun guessMimeFromName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "csv" -> "text/csv"
        "txt" -> "text/plain"
        "eml" -> "message/rfc822"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
