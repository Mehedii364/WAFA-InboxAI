package com.example.core.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URI
import java.util.concurrent.TimeUnit

object NetworkSecurity {

    fun validateHttpsUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("URL cannot be empty."))
        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https") {
                "HTTPS is required for all external endpoints (got '${scheme ?: "none"}')."
            }
            require(!uri.host.isNullOrBlank()) { "Valid hostname is required." }
            trimmed
        }
    }

    fun buildSafeHttpClient(
        connectTimeoutSec: Long = 15,
        readTimeoutSec: Long = 45,
        writeTimeoutSec: Long = 30
    ): OkHttpClient {
        val redactingLogger = HttpLoggingInterceptor { message ->
            // Only log non-sensitive metadata; never log headers or tokens.
            KeyMasker.redactSecrets(message)
        }.apply {
            level = HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("x-goog-api-key")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSec, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(redactingLogger)
            .build()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
