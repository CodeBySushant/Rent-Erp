-- ============================================================
-- V26: profile photos
--
-- users.photo_file_id points at the account's current photo: an upload with
-- purpose PROFILE_PHOTO owned by that user (POST /api/v1/files), set with
-- PUT /api/v1/me/photo and cleared with DELETE /api/v1/me/photo.
-- See docs/devlog/DEVLOG_PROFILE_PHOTO.md.
-- ============================================================

ALTER TABLE users ADD COLUMN photo_file_id UUID REFERENCES stored_files (id);
