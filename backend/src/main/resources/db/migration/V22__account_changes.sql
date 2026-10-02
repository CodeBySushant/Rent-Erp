-- ============================================================
-- V22: account self-service
--
-- otp_attempts.phone now holds the destination of the code: a phone number
-- or, for CHANGE_EMAIL, the new email address (so it is widened).
-- CHANGE_EMAIL joins the allowed purposes; a code is never valid across
-- purposes.
-- ============================================================

ALTER TABLE otp_attempts ALTER COLUMN phone TYPE VARCHAR(254);

ALTER TABLE otp_attempts DROP CONSTRAINT chk_otp_purpose;
ALTER TABLE otp_attempts ADD CONSTRAINT chk_otp_purpose
    CHECK (purpose IN ('SIGNUP', 'LOGIN', 'CHANGE_PHONE', 'CHANGE_EMAIL'));
