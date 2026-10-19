-- Migración V3: Optimización para lookup O(1) de API keys por hash SHA-256 en autenticación
CREATE INDEX IF NOT EXISTS idx_api_keys_hash ON api_keys(key_hash);
