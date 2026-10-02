-- ============================================================
-- V17: property join code
--
-- Tenants join a property with a short code the owner shares (QR or text),
-- e.g. SR-KTM-7842. It is not the internal id and reveals nothing about it.
-- New properties get a code from PropertyService (name and city initials +
-- four digits); existing rows are back-filled here with a random code of the
-- same shape. Unique across all properties.
-- ============================================================

ALTER TABLE properties ADD COLUMN join_code VARCHAR(16);

UPDATE properties
SET join_code = 'RL-' || upper(translate(substr(md5(random()::text || id::text), 1, 3), '0123456789', 'GHJKMNPQRS'))
                || '-' || lpad((floor(random() * 10000))::int::text, 4, '0')
WHERE join_code IS NULL;

ALTER TABLE properties ALTER COLUMN join_code SET NOT NULL;
ALTER TABLE properties ADD CONSTRAINT uq_properties_join_code UNIQUE (join_code);
ALTER TABLE properties ADD CONSTRAINT chk_properties_join_code
    CHECK (join_code ~ '^[A-Z]{2}-[A-Z]{3}-[0-9]{4}$');
