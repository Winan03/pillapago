CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    uuid VARCHAR(255) UNIQUE NOT NULL,
    review_threshold INTEGER DEFAULT 50,
    reject_threshold INTEGER DEFAULT 80,
    fail_policy VARCHAR(20) DEFAULT 'FAIL_CLOSED',
    data_retention_months INTEGER DEFAULT 13,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    username VARCHAR(255) NOT NULL,
    email_hash VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'MERCHANT_ADMIN',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    mfa_enabled BOOLEAN DEFAULT FALSE,
    mfa_recovery_codes TEXT[],
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_users_merchant_email UNIQUE (merchant_id, email_hash)
);

CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(32) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_merchant_active_key UNIQUE (merchant_id, key_prefix)
);

-- Índices de rendimiento
CREATE INDEX idx_merchants_uuid ON merchants(uuid);
CREATE INDEX idx_users_merchant ON users(merchant_id);
CREATE INDEX idx_api_keys_merchant ON api_keys(merchant_id);
CREATE INDEX idx_users_email_hash ON users(email_hash);