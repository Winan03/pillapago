-- V6__email_verification.sql
-- Tabla para almacenar los códigos de verificación de correo con expiración e intentos (HU-01)

CREATE TABLE IF NOT EXISTS email_verification_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evc_user_id ON email_verification_codes(user_id);
CREATE INDEX IF NOT EXISTS idx_evc_code_hash ON email_verification_codes(code_hash);
CREATE INDEX IF NOT EXISTS idx_evc_active ON email_verification_codes(user_id, used, expires_at);
