-- V4__tenant_data_keys.sql
-- Tabla para almacenar las claves de datos por comercio (DEK), protegidas mediante
-- cifrado de sobre (Envelope Encryption) con la clave maestra de KMS (RNF-31, RNF-32).

CREATE TABLE IF NOT EXISTS tenant_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    key_version INTEGER NOT NULL DEFAULT 1,
    encrypted_dek_bytes BYTEA NOT NULL,
    blind_index_key_bytes BYTEA NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_tenant_keys_merchant_version UNIQUE (merchant_id, key_version)
);

CREATE INDEX IF NOT EXISTS idx_tenant_keys_merchant_active ON tenant_keys(merchant_id, active);
