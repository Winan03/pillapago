-- =========================================================================================
-- V12: Esquema para Listas de Bloqueo y Confianza (HU-13 / RF-09 / RF-20 / RNF-15 / RNF-32)
-- Permite almacenar entradas de listas negras (BLOCK) y blancas (ALLOW) por comercio.
-- Utiliza indices ciegos HMAC-SHA256 para busqueda O(1) y cifrado Tink AES-256-GCM para visualizacion.
-- =========================================================================================

CREATE TABLE IF NOT EXISTS list_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    list_type VARCHAR(20) NOT NULL, -- 'BLOCK', 'ALLOW'
    entry_type VARCHAR(30) NOT NULL, -- 'DEVICE', 'IP', 'EMAIL', 'CARD_FINGERPRINT'
    hashed_value VARCHAR(64) NOT NULL, -- Indice ciego HMAC-SHA256 para busqueda rapida O(1)
    encrypted_value TEXT NOT NULL, -- Valor original cifrado con Tink AES-256-GCM
    value_masked VARCHAR(100), -- Representacion enmascarada para analistas (ej. c****@correo.com)
    reason VARCHAR(500) NOT NULL, -- Justificacion obligatoria de la inclusion
    expires_at TIMESTAMP WITH TIME ZONE, -- Vencimiento opcional (NULL = Permanente)
    created_by UUID REFERENCES users(id) ON DELETE SET NULL, -- Analista que creo la entrada
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_merchant_list_entry UNIQUE (merchant_id, list_type, entry_type, hashed_value)
);

-- Indices para acelerar consultas filtradas por comercio y vigencia
CREATE INDEX IF NOT EXISTS idx_list_entries_merchant_lookup ON list_entries(merchant_id, list_type, entry_type, hashed_value);
CREATE INDEX IF NOT EXISTS idx_list_entries_expiration ON list_entries(merchant_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_list_entries_created_at ON list_entries(merchant_id, created_at DESC);
