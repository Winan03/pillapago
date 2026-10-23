-- V9__terms_and_conditions.sql
-- Trazabilidad y auditoría legal de consentimiento de Términos y Condiciones y Privacidad

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS terms_version VARCHAR(20) DEFAULT 'v1.0';

-- Para los comercios y usuarios existentes previamente creados en migraciones o seeds, establecemos fecha retroactiva
UPDATE users
SET terms_accepted_at = created_at
WHERE terms_accepted_at IS NULL;
