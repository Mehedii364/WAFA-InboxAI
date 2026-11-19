-- WAFA InboxAI — Production Normalized MySQL 8.0+ Schema
-- Developer: Mehedi364 (Md. Mehedi Hasan)
-- Migration: 001_initial_schema.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS users (
  id CHAR(36) PRIMARY KEY,
  full_name VARCHAR(160) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_sessions (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  user_agent VARCHAR(255) DEFAULT NULL,
  ip_address VARCHAR(64) DEFAULT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sessions_user (user_id, expires_at),
  CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS connected_accounts (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  provider ENUM('GMAIL','OUTLOOK','YAHOO_IMAP','GENERIC_IMAP') NOT NULL,
  email_address VARCHAR(255) NOT NULL,
  display_name VARCHAR(160) DEFAULT NULL,
  imap_host VARCHAR(255) DEFAULT NULL,
  imap_port SMALLINT UNSIGNED DEFAULT 993,
  smtp_host VARCHAR(255) DEFAULT NULL,
  smtp_port SMALLINT UNSIGNED DEFAULT 587,
  status VARCHAR(40) NOT NULL DEFAULT 'CONNECTED',
  preferred_ai_provider VARCHAR(40) DEFAULT NULL,
  preferred_ai_model VARCHAR(160) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_provider_email (user_id, provider, email_address),
  INDEX idx_accounts_user (user_id),
  CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS oauth_tokens (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  account_id CHAR(36) NOT NULL UNIQUE,
  encrypted_access_token TEXT NOT NULL,
  encrypted_refresh_token TEXT DEFAULT NULL,
  iv_b64 VARCHAR(64) NOT NULL,
  tag_b64 VARCHAR(64) NOT NULL,
  scopes TEXT DEFAULT NULL,
  expires_at DATETIME DEFAULT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_oauth_user (user_id),
  CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_oauth_account FOREIGN KEY (account_id) REFERENCES connected_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_providers (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  provider_type ENUM('GEMINI','GROQ','OPENROUTER','HUGGINGFACE','CLOUDFLARE','XPIKI') NOT NULL,
  base_url VARCHAR(512) NOT NULL,
  account_identifier VARCHAR(128) DEFAULT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  is_paid_provider TINYINT(1) NOT NULL DEFAULT 0,
  default_model VARCHAR(191) DEFAULT NULL,
  daily_request_limit INT UNSIGNED NOT NULL DEFAULT 500,
  daily_token_limit INT UNSIGNED NOT NULL DEFAULT 250000,
  connection_verified TINYINT(1) NOT NULL DEFAULT 0,
  last_tested_at DATETIME DEFAULT NULL,
  last_latency_ms INT UNSIGNED DEFAULT 0,
  last_error TEXT DEFAULT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_provider (user_id, provider_type),
  CONSTRAINT fk_ai_providers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS encrypted_api_credentials (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  ai_provider_id CHAR(36) NOT NULL UNIQUE,
  ciphertext TEXT NOT NULL,
  iv_b64 VARCHAR(64) NOT NULL,
  auth_tag_b64 VARCHAR(64) NOT NULL,
  masked_preview VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_creds_user (user_id),
  CONSTRAINT fk_creds_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_creds_provider FOREIGN KEY (ai_provider_id) REFERENCES ai_providers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_models (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  provider_type VARCHAR(40) NOT NULL,
  model_id VARCHAR(191) NOT NULL,
  display_name VARCHAR(191) NOT NULL,
  supports_json TINYINT(1) NOT NULL DEFAULT 1,
  supports_vision TINYINT(1) NOT NULL DEFAULT 0,
  context_window INT UNSIGNED DEFAULT 0,
  prompt_cost_per_1k DECIMAL(12,6) DEFAULT NULL,
  completion_cost_per_1k DECIMAL(12,6) DEFAULT NULL,
  fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_provider_model (user_id, provider_type, model_id),
  CONSTRAINT fk_models_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_usage_logs (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  provider_type VARCHAR(40) NOT NULL,
  model_id VARCHAR(191) NOT NULL,
  task_type VARCHAR(40) NOT NULL,
  prompt_tokens INT UNSIGNED NOT NULL DEFAULT 0,
  completion_tokens INT UNSIGNED NOT NULL DEFAULT 0,
  total_tokens INT UNSIGNED NOT NULL DEFAULT 0,
  latency_ms INT UNSIGNED NOT NULL DEFAULT 0,
  estimated_cost_usd DECIMAL(12,6) DEFAULT NULL,
  success TINYINT(1) NOT NULL,
  error_code VARCHAR(64) DEFAULT NULL,
  fallback_used TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_usage_user_time (user_id, created_at),
  CONSTRAINT fk_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_sync_state (
  account_id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  sync_cursor TEXT DEFAULT NULL,
  last_synced_at DATETIME DEFAULT NULL,
  last_status VARCHAR(40) NOT NULL DEFAULT 'IDLE',
  last_error TEXT DEFAULT NULL,
  INDEX idx_sync_user (user_id),
  CONSTRAINT fk_sync_account FOREIGN KEY (account_id) REFERENCES connected_accounts(id) ON DELETE CASCADE,
  CONSTRAINT fk_sync_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_threads (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  account_id CHAR(36) NOT NULL,
  remote_thread_id VARCHAR(191) NOT NULL,
  subject VARCHAR(500) NOT NULL,
  last_message_at DATETIME NOT NULL,
  message_count INT UNSIGNED NOT NULL DEFAULT 1,
  UNIQUE KEY uq_thread (user_id, account_id, remote_thread_id),
  INDEX idx_threads_user_time (user_id, last_message_at),
  CONSTRAINT fk_threads_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_threads_account FOREIGN KEY (account_id) REFERENCES connected_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_categories (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  name VARCHAR(80) NOT NULL,
  is_custom TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_cat (user_id, name),
  CONSTRAINT fk_cats_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_labels (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  account_id CHAR(36) NOT NULL,
  remote_label_id VARCHAR(128) NOT NULL,
  label_name VARCHAR(128) NOT NULL,
  UNIQUE KEY uq_account_label (account_id, remote_label_id),
  CONSTRAINT fk_labels_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_labels_account FOREIGN KEY (account_id) REFERENCES connected_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_metadata (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  account_id CHAR(36) NOT NULL,
  thread_id CHAR(36) DEFAULT NULL,
  remote_message_id VARCHAR(191) NOT NULL,
  sender_name VARCHAR(255) DEFAULT NULL,
  sender_email VARCHAR(255) NOT NULL,
  subject VARCHAR(500) NOT NULL,
  snippet VARCHAR(512) DEFAULT NULL,
  category VARCHAR(80) NOT NULL DEFAULT 'Primary',
  priority ENUM('Urgent','High','Normal','Low') NOT NULL DEFAULT 'Normal',
  priority_reason TEXT DEFAULT NULL,
  follow_up_state ENUM('NONE','NEEDS_MY_REPLY','AWAITING_THEIR_REPLY','HAS_DEADLINE','INFORMATIONAL_ONLY') NOT NULL DEFAULT 'NONE',
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  is_archived TINYINT(1) NOT NULL DEFAULT 0,
  has_attachments TINYINT(1) NOT NULL DEFAULT 0,
  security_risk_level VARCHAR(24) NOT NULL DEFAULT 'SAFE',
  security_score TINYINT UNSIGNED NOT NULL DEFAULT 0,
  received_at DATETIME NOT NULL,
  synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_remote_msg (user_id, account_id, remote_message_id),
  INDEX idx_mail_user_received (user_id, received_at),
  CONSTRAINT fk_mail_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_mail_account FOREIGN KEY (account_id) REFERENCES connected_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_summaries (
  email_id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  short_summary TEXT NOT NULL,
  detailed_summary MEDIUMTEXT NOT NULL,
  action_items_json JSON DEFAULT NULL,
  deadlines_json JSON DEFAULT NULL,
  entities_json JSON DEFAULT NULL,
  financial_amounts_json JSON DEFAULT NULL,
  provider_used VARCHAR(40) NOT NULL,
  model_used VARCHAR(191) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_summaries_user (user_id),
  CONSTRAINT fk_summaries_email FOREIGN KEY (email_id) REFERENCES email_metadata(id) ON DELETE CASCADE,
  CONSTRAINT fk_summaries_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_drafts (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  account_id CHAR(36) DEFAULT NULL,
  email_id CHAR(36) DEFAULT NULL,
  recipient_email VARCHAR(255) NOT NULL,
  subject VARCHAR(500) NOT NULL,
  body MEDIUMTEXT NOT NULL,
  tone VARCHAR(60) NOT NULL DEFAULT 'Professional',
  language_code VARCHAR(8) NOT NULL DEFAULT 'en',
  status ENUM('DRAFT','PENDING_APPROVAL','SENT','DISCARDED') NOT NULL DEFAULT 'DRAFT',
  provider_used VARCHAR(40) DEFAULT NULL,
  model_used VARCHAR(191) DEFAULT NULL,
  remote_draft_id VARCHAR(191) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_drafts_user (user_id, updated_at),
  CONSTRAINT fk_drafts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS followup_reminders (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  email_id CHAR(36) DEFAULT NULL,
  follow_up_type VARCHAR(40) NOT NULL,
  due_at DATETIME NOT NULL,
  extracted_deadline_text VARCHAR(255) DEFAULT NULL,
  suggested_message TEXT DEFAULT NULL,
  status ENUM('PENDING','SNOOZED','COMPLETED','DISMISSED') NOT NULL DEFAULT 'PENDING',
  notified_at DATETIME DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_followup_user_due (user_id, due_at, status),
  CONSTRAINT fk_followup_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS automation_rules (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  name VARCHAR(160) NOT NULL,
  trigger_type VARCHAR(60) NOT NULL,
  condition_value VARCHAR(255) DEFAULT NULL,
  ai_analysis_task VARCHAR(40) NOT NULL DEFAULT 'NONE',
  requires_approval TINYINT(1) NOT NULL DEFAULT 1,
  action_type VARCHAR(60) NOT NULL,
  action_param VARCHAR(255) DEFAULT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_rules_user (user_id, enabled),
  CONSTRAINT fk_rules_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS automation_executions (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  rule_id CHAR(36) NOT NULL,
  email_id CHAR(36) NOT NULL,
  trigger_event_key VARCHAR(255) NOT NULL UNIQUE,
  status ENUM('PENDING_APPROVAL','EXECUTED','BLOCKED_MODE','REJECTED','FAILED') NOT NULL,
  result_message TEXT DEFAULT NULL,
  executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_exec_user (user_id, executed_at),
  CONSTRAINT fk_exec_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_exec_rule FOREIGN KEY (rule_id) REFERENCES automation_rules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  title VARCHAR(180) NOT NULL,
  message TEXT NOT NULL,
  category VARCHAR(40) NOT NULL,
  related_email_id CHAR(36) DEFAULT NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_notif_user (user_id, is_read, created_at),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  action VARCHAR(80) NOT NULL,
  actor ENUM('USER','AI_AGENT','AUTOMATION','SYSTEM') NOT NULL,
  target_id VARCHAR(191) DEFAULT NULL,
  details TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_user_time (user_id, created_at),
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_preferences (
  user_id CHAR(36) PRIMARY KEY,
  theme_mode ENUM('DARK','LIGHT','SYSTEM') NOT NULL DEFAULT 'DARK',
  language_code ENUM('en','bn') NOT NULL DEFAULT 'en',
  operation_mode ENUM('COPILOT','APPROVAL','SMART_AUTOMATION') NOT NULL DEFAULT 'COPILOT',
  permissions_json JSON NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_prefs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS background_jobs (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  job_type ENUM('EMAIL_SYNC','AI_CLASSIFY','AI_SUMMARY','REMINDER_EVAL','ATTACHMENT_PARSE','AUTOMATION_RUN') NOT NULL,
  payload_json JSON NOT NULL,
  idempotency_key VARCHAR(191) NOT NULL UNIQUE,
  status ENUM('QUEUED','LOCKED','COMPLETED','FAILED') NOT NULL DEFAULT 'QUEUED',
  attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
  max_attempts TINYINT UNSIGNED NOT NULL DEFAULT 3,
  locked_at DATETIME DEFAULT NULL,
  run_after DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_error TEXT DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_jobs_poll (status, run_after),
  CONSTRAINT fk_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
