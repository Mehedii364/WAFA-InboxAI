# WAFA InboxAI

**Your Inbox. Your AI. Your Control.**  
**Developed by:** Mehedi364 (Md. Mehedi Hasan)

WAFA InboxAI is a native Android AI-powered email management platform built with **Kotlin, Jetpack Compose, Material 3, Room, DataStore, OkHttp, and Android Keystore AES-256-GCM**, paired with a modular **PHP 8.2+ & MySQL 8+ PDO backend** and automated **GitHub Actions** APK builds.

---

## Architecture Overview

| Layer | Technology | Responsibility |
|---|---|---|
| **Android App (`app/`)** | Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room, DataStore, OkHttp, JobScheduler | Native executive UI (all 22 screens), Android Keystore encrypted BYOK vault, 6 AI provider adapters, Intelligent Model Router, Gmail REST v1 & Microsoft Graph OAuth 2.0 PKCE clients, safe attachment & `.eml` inspector, Automation Studio, bilingual EN/বাংলা UI, Dark/Light/System themes. |
| **Companion Backend (`backend/`)** | PHP 8.2+, MySQL 8+ PDO, OpenSSL AES-256-GCM | HTTPS REST API, Argon2id user auth, server-side encrypted BYOK credential vault, 22-table normalized MySQL schema (`backend/migrations/001_initial_schema.sql`), locked background job runner (`backend/jobs/Runner.php`) for CLI cron & shared-hosting HTTP execution. |
| **CI/CD (`.github/workflows/`)** | GitHub Actions | Automated unit & Robolectric tests, real `assembleDebug` build, ZIP/APK structural validation, SHA-256 verification, artifact upload, and `[skip ci]` APK publication to `APK_DOWNLOAD/app-debug.apk`. |

---

## Core Implemented Features

- **6 Independent BYOK AI Provider Adapters** (`AIProviderInterface`):
  1. **Google Gemini API** (`generativelanguage.googleapis.com/v1beta`, `x-goog-api-key`)
  2. **Groq API** (`api.groq.com/openai/v1`, Bearer)
  3. **OpenRouter API** (`openrouter.ai/api/v1`, Bearer + pricing discovery)
  4. **Hugging Face Inference Providers** (`router.huggingface.co/v1`, Bearer)
  5. **Cloudflare Workers AI** (`api.cloudflare.com/client/v4/accounts/{accountId}/ai`, Bearer + Account ID)
  6. **XPIKI API** (`api.xpiki.com/v1`, adjustable endpoint & Bearer auth)
- **Intelligent Model Router**: Task-specific primary & fallback routing (`Classification`, `Summarization`, `Drafting`, `Document`, `Security`, `Research`, `Follow-up`), daily request/token usage caps, capped transient retry (no infinite loops), `allowPaidFallback` guard, and real token/latency logging.
- **Real Email Integrations**:
  - Official **Gmail REST API v1** + RFC 7636 OAuth 2.0 PKCE (`wafa.inboxai://oauth/callback`) with incremental `historyId` sync, MIME decoding, labels, drafts, and confirmation-gated send.
  - Official **Microsoft Graph v1.0** + OAuth 2.0 PKCE for Outlook / Microsoft 365 delta sync, archive, drafts, and confirmation-gated send.
  - **Yahoo Mail / Generic IMAP & SMTP** bridge config with explicit capability reporting.
  - Zero-permission **Storage Access Framework** `.eml` and document inspector (PDF, DOCX, XLSX, CSV, TXT, images; 15 MB cap; zero execution).
- **9 Modular AI Email Agents + Prompt-Injection Defense**: Inbox, Priority, Summary, Reply (EN & বাংলা), Research (*Ask Your Inbox* with clickable source citations), Follow-up, Attachment, Security (SPF/DKIM/DMARC/URL/spoofing heuristics + AI verdict), and Schedule agents.
- **3 AI Operation Modes & Automation Studio**: Copilot Mode, Approval Mode, Smart Automation Mode, visual rule builder (`Trigger → Conditions → AI → Approval → Action → Result`), unique `triggerEventKey` duplicate-execution lock, instant **Revoke All Permissions**, and immutable Audit Logs.

---

## Local Build & Testing

### Requirements
- JDK 17+
- Android SDK (API 36)
- Gradle 9.3.1 (or Android Studio Narwhal / Ladybug+)

### Build Debug APK & Run Tests
```bash
# Run local JVM unit & Robolectric tests
gradle :app:testDebugUnitTest

# Build real Debug APK
gradle :app:assembleDebug
```

Generated APK location:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## External Credentials & Manual Setup Guide

No private API keys or OAuth client secrets are bundled in this repository or APK.

### 1. AI Provider API Keys (BYOK — Entered In-App)
Open **Control → API Provider Manager** inside the installed app, add keys for any provider(s) you use, and tap **Test & Fetch** to verify real connectivity and populate live models.

### 2. Gmail OAuth 2.0 PKCE
1. Enable **Gmail API** in Google Cloud Console.
2. Create an OAuth 2.0 Client ID (Android / Mobile native client) and register redirect URI:
   ```text
   wafa.inboxai://oauth/callback
   ```
3. Enter the Client ID in **Control → Connected Accounts → Gmail**.

### 3. Microsoft Outlook Graph OAuth 2.0 PKCE
1. Register a Mobile/Desktop application in **Microsoft Entra ID**.
2. Add Redirect URI `wafa.inboxai://oauth/callback` and delegated permissions `Mail.Read` (optionally `Mail.ReadWrite`, `Mail.Send`, `Calendars.Read`).
3. Enter the Application (Client) ID in **Control → Connected Accounts → Outlook**.

### 4. PHP 8.2+ & MySQL Production Backend
1. Create a MySQL 8+ database and import `backend/migrations/001_initial_schema.sql`.
2. Copy `backend/.env.example` to `backend/.env` and configure `DB_*`, `APP_ENCRYPTION_KEY`, and `CRON_RUNNER_SECRET`.
3. Deploy `backend/` to a PHP 8.2+ HTTPS server (do **not** deploy PHP to GitHub Pages).
4. Schedule jobs via cron (`php backend/jobs/Runner.php`) or authenticated HTTP POST to `/api/jobs/run` with header `X-Cron-Token: <CRON_RUNNER_SECRET>`.

### 5. GitHub Actions Automated APK Build & Secrets
The workflow `.github/workflows/android-build.yml` runs automatically on pushes to `main`/`master` and manual `workflow_dispatch`:
- Executes unit & Robolectric tests
- Builds real `app-debug.apk`
- Validates ZIP/APK structure, size, and SHA-256 checksums
- Uploads artifact `wafa-inboxai-debug-apk`
- Publishes verified `APK_DOWNLOAD/app-debug.apk` and `.build-outputs/app-debug.apk` with `[skip ci]`

For optional signed release builds (`workflow_dispatch`), configure these GitHub Repository Secrets:
- `KEYSTORE_BASE64`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

---

## Security Policy

- All API keys and OAuth tokens are encrypted at rest with **AES-256-GCM** (Android Keystore on device; `APP_ENCRYPTION_KEY` outside DB on PHP backend).
- Cleartext HTTP traffic is blocked (`android:usesCleartextTraffic="false"`).
- Untrusted email bodies and attachments are isolated inside explicit boundary delimiters to resist prompt injection.
- Sending, forwarding, and deleting emails ALWAYS require explicit user confirmation.
