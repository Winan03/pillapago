-- V10__rule_set_versions_schema.sql
-- Versionado inmutable de conjuntos de reglas antifraude con soporte de rollback (HU-08 / RF-11)

CREATE TABLE IF NOT EXISTS rule_set_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    version_name VARCHAR(50) NOT NULL,
    version_number INTEGER NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    rules_snapshot TEXT NOT NULL,
    rules_count INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rsv_merchant_version_name ON rule_set_versions(merchant_id, version_name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rsv_merchant_version_number ON rule_set_versions(merchant_id, version_number);
CREATE INDEX IF NOT EXISTS idx_rsv_merchant_status ON rule_set_versions(merchant_id, status);

-- Cada decisión guarda la versión de reglas con la que se evaluó (Criterio 6 HU-08)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS rule_set_version VARCHAR(50) DEFAULT 'v1';
