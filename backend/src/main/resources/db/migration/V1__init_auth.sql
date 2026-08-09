-- ============================================================
-- V1: Auth tables
-- users, otp_attempts, user_sessions
--
-- Rules enforced here:
--   - All PKs are UUID (gen_random_uuid())
--   - Timestamps stored in UTC (TIMESTAMPTZ)
--   - Soft deletes via is_active / status fields — nothing is hard-deleted
--   - BS dates NOT used here (auth domain uses UTC timestamps only)
-- ============================================================

-- ── users ──────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    phone               VARCHAR(20)     NOT NULL UNIQUE,
    name                VARCHAR(255),
    role                VARCHAR(50)     NOT NULL DEFAULT 'LANDLORD',  -- LANDLORD | TENANT | ADMIN
    kyc_status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',   -- PENDING | SUBMITTED | VERIFIED | REJECTED
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    fcm_token           VARCHAR(512),
    preferred_language  VARCHAR(10)     NOT NULL DEFAULT 'en',        -- 'en' | 'ne'
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone    ON users (phone);
CREATE INDEX idx_users_role     ON users (role);
CREATE INDEX idx_users_active   ON users (is_active);

-- ── otp_attempts ───────────────────────────────────────────────────────────────
-- One row per OTP sent. Lookup by phone + is_used + expiry, never by code directly.
-- Code is stored as bcrypt hash — never plain text.
CREATE TABLE otp_attempts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    phone           VARCHAR(20) NOT NULL,
    code_hash       VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    is_used         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_phone_active   ON otp_attempts (phone, is_used);
CREATE INDEX idx_otp_expires        ON otp_attempts (expires_at);

-- ── user_sessions ──────────────────────────────────────────────────────────────
-- One row per active device session. Token stored as SHA-256 hash.
-- revoked_at is set (not deleted) when a session is terminated.
CREATE TABLE user_sessions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    device_info JSONB,                         -- {platform, model, os_version, app_version}
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,                   -- NULL = still valid
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_user      ON user_sessions (user_id);
CREATE INDEX idx_sessions_token     ON user_sessions (token_hash);
CREATE INDEX idx_sessions_expires   ON user_sessions (expires_at);
CREATE INDEX idx_sessions_revoked   ON user_sessions (revoked_at) WHERE revoked_at IS NULL;
