package com.example.data.email

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

enum class OAuthEmailProvider(
    val authEndpoint: String,
    val tokenEndpoint: String,
    val defaultReadScope: String,
    val fullMailScopes: String
) {
    GMAIL(
        authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenEndpoint = "https://oauth2.googleapis.com/token",
        defaultReadScope = "https://www.googleapis.com/auth/gmail.readonly email profile",
        fullMailScopes = "https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.modify https://www.googleapis.com/auth/gmail.compose https://www.googleapis.com/auth/gmail.send email profile"
    ),
    OUTLOOK(
        authEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        defaultReadScope = "offline_access openid profile User.Read Mail.Read",
        fullMailScopes = "offline_access openid profile User.Read Mail.Read Mail.ReadWrite Mail.Send Calendars.Read"
    )
}

data class PendingPkceSession(
    val provider: OAuthEmailProvider,
    val clientId: String,
    val state: String,
    val codeVerifier: String,
    val scopes: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
    val scope: String
)

/**
 * RFC 7636 OAuth 2.0 Authorization Code + PKCE manager with cryptographic state validation
 * for official Gmail API and Microsoft Graph integrations.
 */
class OAuthPkceManager(private val httpClient: OkHttpClient) {

    @Volatile
    private var pendingSession: PendingPkceSession? = null

    fun createAuthorizationUrl(
        provider: OAuthEmailProvider,
        clientId: String,
        requestWriteAndSendScopes: Boolean
    ): Result<Uri> {
        val cleanClientId = clientId.trim()
        if (cleanClientId.isEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "OAuth Client ID is required. Register a native Android/Mobile OAuth Client ID with redirect URI '$REDIRECT_URI'."
                )
            )
        }
        val verifier = randomUrlSafeString(64)
        val challenge = sha256Base64Url(verifier)
        val state = randomUrlSafeString(32)
        val scopes = if (requestWriteAndSendScopes) provider.fullMailScopes else provider.defaultReadScope

        pendingSession = PendingPkceSession(
            provider = provider,
            clientId = cleanClientId,
            state = state,
            codeVerifier = verifier,
            scopes = scopes
        )

        val builder = Uri.parse(provider.authEndpoint).buildUpon()
            .appendQueryParameter("client_id", cleanClientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")

        if (provider == OAuthEmailProvider.GMAIL) {
            builder.appendQueryParameter("access_type", "offline")
            builder.appendQueryParameter("prompt", "consent")
        }
        return Result.success(builder.build())
    }

    suspend fun handleCallbackUri(callbackUri: Uri): Result<Pair<PendingPkceSession, OAuthTokenResponse>> =
        withContext(Dispatchers.IO) {
            val session = pendingSession
                ?: return@withContext Result.failure(IllegalStateException("No active OAuth session found."))
            val error = callbackUri.getQueryParameter("error")
            if (!error.isNullOrBlank()) {
                val desc = callbackUri.getQueryParameter("error_description") ?: error
                pendingSession = null
                return@withContext Result.failure(IllegalStateException("OAuth authorization declined: $desc"))
            }
            val returnedState = callbackUri.getQueryParameter("state")
            if (returnedState != session.state) {
                pendingSession = null
                return@withContext Result.failure(SecurityException("OAuth state validation failed (CSRF protection)."))
            }
            val code = callbackUri.getQueryParameter("code")
            if (code.isNullOrBlank()) {
                pendingSession = null
                return@withContext Result.failure(IllegalStateException("Authorization code missing in OAuth callback."))
            }

            val form = FormBody.Builder()
                .add("client_id", session.clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", session.codeVerifier)
                .build()

            val request = Request.Builder()
                .url(session.provider.tokenEndpoint)
                .post(form)
                .build()

            try {
                httpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    pendingSession = null
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Token exchange failed (HTTP ${resp.code}): ${extractOAuthError(body)}")
                        )
                    }
                    val json = JSONObject(body)
                    val accessToken = json.optString("access_token", "")
                    if (accessToken.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Token endpoint returned no access_token."))
                    }
                    Result.success(
                        session to OAuthTokenResponse(
                            accessToken = accessToken,
                            refreshToken = json.optString("refresh_token", ""),
                            expiresInSec = json.optLong("expires_in", 3600L),
                            scope = json.optString("scope", session.scopes)
                        )
                    )
                }
            } catch (t: Throwable) {
                pendingSession = null
                Result.failure(t)
            }
        }

    suspend fun refreshAccessToken(
        provider: OAuthEmailProvider,
        clientId: String,
        refreshToken: String
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Client ID and refresh token required."))
        }
        val form = FormBody.Builder()
            .add("client_id", clientId.trim())
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken.trim())
            .build()
        val request = Request.Builder().url(provider.tokenEndpoint).post(form).build()
        try {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Token refresh failed (HTTP ${resp.code}): ${extractOAuthError(body)}")
                    )
                }
                val json = JSONObject(body)
                val access = json.optString("access_token", "")
                if (access.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Empty access_token in refresh response."))
                }
                Result.success(
                    OAuthTokenResponse(
                        accessToken = access,
                        refreshToken = json.optString("refresh_token", refreshToken),
                        expiresInSec = json.optLong("expires_in", 3600L),
                        scope = json.optString("scope", "")
                    )
                )
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun extractOAuthError(body: String): String = runCatching {
        val obj = JSONObject(body)
        obj.optString("error_description", obj.optString("error", body.take(180)))
    }.getOrElse { body.take(180) }

    private fun randomUrlSafeString(byteLen: Int): String {
        val bytes = ByteArray(byteLen).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sha256Base64Url(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val REDIRECT_URI = "wafa.inboxai://oauth/callback"
    }
}
