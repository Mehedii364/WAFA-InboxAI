package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.SoftwareAesGcmVault
import com.example.data.ai.*
import com.example.data.local.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.security.SecureRandom

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiProvidersAndRouterTest {

    private lateinit var db: WafaDatabase
    private lateinit var vault: SoftwareAesGcmVault

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WafaDatabase::class.java
        ).allowMainThreadQueries().build()
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        vault = SoftwareAesGcmVault(key)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun fakeClient(handler: (Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain -> handler(chain.request()) }
            .build()
    }

    private fun jsonResponse(request: Request, code: Int, json: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERR")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `gemini provider discovers models and parses generateContent usage`() = runTest {
        val client = fakeClient { req ->
            assertEquals("test-gemini-key", req.header("x-goog-api-key"))
            if (req.url.encodedPath.endsWith("/models")) {
                jsonResponse(req, 200, """{"models":[{"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","supportedGenerationMethods":["generateContent"],"inputTokenLimit":1048576}]}""")
            } else {
                jsonResponse(req, 200, """{"candidates":[{"content":{"parts":[{"text":"Summary OK"}]}}],"usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":5,"totalTokenCount":17}}""")
            }
        }
        val provider = GeminiProvider(client)
        val creds = ProviderCredentials(AiProviderType.GEMINI, "test-gemini-key", AiProviderType.GEMINI.defaultBaseUrl)

        val models = provider.listModels(creds)
        assertTrue(models is AiResult.Success)
        assertEquals("gemini-2.5-flash", (models as AiResult.Success).data.first().modelId)

        val gen = provider.generateText(creds, AiRequest("gemini-2.5-flash", "sys", "hello"))
        assertTrue(gen is AiResult.Success)
        val resp = (gen as AiResult.Success).data
        assertEquals("Summary OK", resp.text)
        assertEquals(17, resp.usage.totalTokens)
    }

    @Test
    fun `cloudflare and xpiki adapters format provider-specific requests cleanly`() = runTest {
        val client = fakeClient { req ->
            when {
                req.url.host == "api.cloudflare.com" -> {
                    assertTrue(req.url.encodedPath.contains("/accounts/acct-123/ai/"))
                    jsonResponse(req, 200, """{"result":{"response":"Cloudflare reply","usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14}}}""")
                }
                req.url.host == "api.xpiki.com" -> {
                    assertEquals("Bearer xpiki-key", req.header("Authorization"))
                    jsonResponse(req, 200, """{"model":"xpiki-pro","choices":[{"message":{"content":"XPIKI reply"}}],"usage":{"prompt_tokens":8,"completion_tokens":3,"total_tokens":11}}""")
                }
                else -> jsonResponse(req, 404, "{}")
            }
        }
        val cf = CloudflareProvider(client)
        val cfCreds = ProviderCredentials(AiProviderType.CLOUDFLARE, "cf-token", AiProviderType.CLOUDFLARE.defaultBaseUrl, accountId = "acct-123")
        val cfRes = cf.generateText(cfCreds, AiRequest("@cf/meta/llama-3.1-8b-instruct", "", "hi"))
        assertEquals("Cloudflare reply", (cfRes as AiResult.Success).data.text)

        val xpiki = XpikiProvider(client)
        val xpikiCreds = ProviderCredentials(AiProviderType.XPIKI, "xpiki-key", AiProviderType.XPIKI.defaultBaseUrl)
        val xpRes = xpiki.generateText(xpikiCreds, AiRequest("xpiki-pro", "", "hi"))
        assertEquals("XPIKI reply", (xpRes as AiResult.Success).data.text)
    }

    @Test
    fun `router caps retries on timeout, blocks unapproved paid fallback, and succeeds on free fallback`() = runTest {
        var groqCalls = 0
        var openRouterCalls = 0
        var geminiCalls = 0

        val client = fakeClient { req ->
            when (req.url.host) {
                "api.groq.com" -> {
                    groqCalls++
                    throw SocketTimeoutException("Simulated timeout")
                }
                "openrouter.ai" -> {
                    openRouterCalls++
                    jsonResponse(req, 200, """{"choices":[{"message":{"content":"Paid fallback"}}]}""")
                }
                "generativelanguage.googleapis.com" -> {
                    geminiCalls++
                    jsonResponse(req, 200, """{"candidates":[{"content":{"parts":[{"text":"Free fallback OK"}]}}],"usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":3,"totalTokenCount":8}}""")
                }
                else -> jsonResponse(req, 500, "{}")
            }
        }

        val userId = "user-router-test"
        db.aiProviderDao().upsertProvider(
            AiProviderConfigEntity(
                "$userId:GROQ", userId, AiProviderType.GROQ.name, "Groq",
                AiProviderType.GROQ.defaultBaseUrl, encryptedApiKey = vault.encrypt("gsk_test"),
                maskedKey = "gsk_••••", enabled = true, isPaidProvider = false, defaultModel = "llama-3.3-70b"
            )
        )
        db.aiProviderDao().upsertProvider(
            AiProviderConfigEntity(
                "$userId:OPENROUTER", userId, AiProviderType.OPENROUTER.name, "OpenRouter",
                AiProviderType.OPENROUTER.defaultBaseUrl, encryptedApiKey = vault.encrypt("sk-or-test"),
                maskedKey = "sk-o••••", enabled = true, isPaidProvider = true, defaultModel = "openai/gpt-4o-mini"
            )
        )
        db.aiProviderDao().upsertProvider(
            AiProviderConfigEntity(
                "$userId:GEMINI", userId, AiProviderType.GEMINI.name, "Gemini",
                AiProviderType.GEMINI.defaultBaseUrl, encryptedApiKey = vault.encrypt("AIza_test"),
                maskedKey = "AIza••••", enabled = true, isPaidProvider = false, defaultModel = "gemini-2.5-flash"
            )
        )
        db.aiProviderDao().upsertRoutingRule(
            RoutingRuleEntity(
                id = "$userId:CLASSIFICATION",
                userId = userId,
                taskType = AiTaskType.CLASSIFICATION.name,
                primaryProvider = AiProviderType.GROQ.name,
                primaryModel = "llama-3.3-70b",
                fallbackProvider = AiProviderType.OPENROUTER.name,
                fallbackModel = "openai/gpt-4o-mini",
                allowPaidFallback = false,
                timeoutSeconds = 10,
                maxRetries = 1
            )
        )

        val router = IntelligentModelRouter(
            providerDao = db.aiProviderDao(),
            vault = vault,
            registry = AiProviderRegistry(client),
            backoffDelayMs = {}
        )

        val res = router.routeAndExecute(
            userId = userId,
            taskType = AiTaskType.CLASSIFICATION,
            systemPrompt = "sys",
            userPrompt = "classify",
            globalAllowPaidFallback = false
        )

        assertTrue(res is AiResult.Success)
        val success = res as AiResult.Success
        assertEquals("Free fallback OK", success.data.text)
        assertEquals(AiProviderType.GEMINI, success.data.providerUsed)
        assertTrue(success.data.fallbackUsed)
        assertEquals("Groq must attempt initial + 1 capped retry only", 2, groqCalls)
        assertEquals("Paid OpenRouter fallback must NOT be called when allowPaidFallback=false", 0, openRouterCalls)
        assertEquals(1, geminiCalls)
    }
}
