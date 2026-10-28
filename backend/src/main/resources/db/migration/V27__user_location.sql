-- ============================================================
-- V27: the account's location (free text, e.g. "Biratnagar, Morang"),
-- edited on the app's Edit Profile screen with PUT /api/v1/users/{id}.
-- See docs/devlog/DEVLOG_PROFILE_PHOTO.md ("Edit Profile save").
-- ============================================================

ALTER TABLE users ADD COLUMN location VARCHAR(120);
