-- ============================================================
-- V28: forgotten-password reset
--
-- RESET_PASSWORD joins the allowed one-time-code purposes. The code goes to
-- the account's email or phone (otp_attempts.phone holds the destination,
-- user_id the account), and POST /api/v1/auth/password/reset spends it to
-- set a new password. See docs/devlog/DEVLOG_PASSWORD_RESET.md.
-- ============================================================

ALTER TABLE otp_attempts DROP CONSTRAINT chk_otp_purpose;
ALTER TABLE otp_attempts ADD CONSTRAINT chk_otp_purpose
    CHECK (purpose IN ('SIGNUP', 'LOGIN', 'CHANGE_PHONE', 'CHANGE_EMAIL', 'RESET_PASSWORD'));
