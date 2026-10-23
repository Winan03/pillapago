-- V7__transactions_and_cases_schema.sql
-- Tablas para persistencia de transacciones, evaluaciones y casos para analistas (HU-06c y HU-09)

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    external_transaction_id VARCHAR(255) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    decision VARCHAR(30) NOT NULL,
    risk_score INTEGER NOT NULL,
    degraded BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms BIGINT NOT NULL,
    customer_id_encrypted BYTEA,
    customer_email_encrypted BYTEA,
    ip_address_encrypted BYTEA,
    device_id_encrypted BYTEA,
    card_fingerprint_encrypted BYTEA,
    card_bin VARCHAR(10),
    card_last_four VARCHAR(10),
    payment_method VARCHAR(50),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_merchant_external_tx UNIQUE (merchant_id, external_transaction_id)
);

CREATE TABLE IF NOT EXISTS transaction_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    rule_name VARCHAR(255) NOT NULL,
    points INTEGER NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    priority VARCHAR(30) NOT NULL DEFAULT 'MEDIUM',
    assigned_to UUID REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transactions_merchant_created ON transactions(merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_external_id ON transactions(merchant_id, external_transaction_id);
CREATE INDEX IF NOT EXISTS idx_transaction_evaluations_tx ON transaction_evaluations(transaction_id);
CREATE INDEX IF NOT EXISTS idx_cases_merchant_status ON cases(merchant_id, status);
CREATE INDEX IF NOT EXISTS idx_cases_transaction ON cases(transaction_id);
