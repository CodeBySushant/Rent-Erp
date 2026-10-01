-- ============================================================
-- V13: Authentication + Hindi
--
-- users          : email + password login, verified-at stamps, password lockout
-- otp_attempts   : purpose, verification token for sign-up, owner of a change
-- user_sessions  : last-used stamp (refresh token hash stays in token_hash)
-- languages      : 'hi' allowed next to 'en' and 'ne'
--
-- Additive only. Existing rows keep working: email / password are optional at
-- the column level (users created before this migration have neither), and
-- every existing otp_attempts row becomes purpose LOGIN.
-- ============================================================

-- ── users ──────────────────────────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN email                  VARCHAR(254),
    ADD COLUMN password_hash          VARCHAR(100),
    ADD COLUMN phone_verified_at      TIMESTAMPTZ,
    ADD COLUMN email_verified_at      TIMESTAMPTZ,
    ADD COLUMN failed_login_attempts  INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN login_locked_until     TIMESTAMPTZ;

-- Case-insensitive uniqueness; rows without an email are not constrained.
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email)) WHERE email IS NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT chk_users_preferred_language CHECK (preferred_language IN ('en','hi','ne')),
    ADD CONSTRAINT chk_users_role CHECK (role IN ('LANDLORD','TENANT','ADMIN')),
    ADD CONSTRAINT chk_users_failed_login_attempts CHECK (failed_login_attempts >= 0);

-- ── otp_attempts ───────────────────────────────────────────────────────────────
-- purpose            : what the code is for; codes are never valid across purposes
-- verified_at        : set when a SIGNUP code is checked successfully
-- verification_token_hash / token_used_at : the single-use proof handed back
--                      after a SIGNUP verify and spent by /auth/register
-- user_id            : the account requesting a CHANGE_PHONE code
ALTER TABLE otp_attempts
    ADD COLUMN purpose                  VARCHAR(20) NOT NULL DEFAULT 'LOGIN',
    ADD COLUMN verified_at              TIMESTAMPTZ,
    ADD COLUMN verification_token_hash  VARCHAR(64),
    ADD COLUMN token_used_at            TIMESTAMPTZ,
    ADD COLUMN user_id                  UUID REFERENCES users (id);

ALTER TABLE otp_attempts
    ADD CONSTRAINT chk_otp_purpose CHECK (purpose IN ('SIGNUP','LOGIN','CHANGE_PHONE')),
    ADD CONSTRAINT chk_otp_attempt_count CHECK (attempt_count >= 0);

CREATE INDEX idx_otp_phone_purpose_created ON otp_attempts (phone, purpose, created_at DESC);
CREATE UNIQUE INDEX uq_otp_verification_token ON otp_attempts (verification_token_hash)
    WHERE verification_token_hash IS NOT NULL;

-- ── user_sessions ──────────────────────────────────────────────────────────────
-- token_hash already holds the SHA-256 of the refresh token (rotated on refresh).
ALTER TABLE user_sessions
    ADD COLUMN last_used_at TIMESTAMPTZ;

-- ── tenant_profiles: allow Hindi ───────────────────────────────────────────────
ALTER TABLE tenant_profiles DROP CONSTRAINT chk_preferred_language;
ALTER TABLE tenant_profiles
    ADD CONSTRAINT chk_preferred_language CHECK (preferred_language IN ('en','hi','ne'));
