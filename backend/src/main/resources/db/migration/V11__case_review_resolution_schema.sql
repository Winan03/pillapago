-- V11__case_review_resolution_schema.sql
-- Ampliación de esquema para soporte de HU-09: Resolución de casos con bloqueo optimista (JPA @Version)

-- 1. Agregar columna de control de concurrencia optimista (version)
ALTER TABLE cases ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- 2. Columnas de auditoría y resolución del caso
ALTER TABLE cases ADD COLUMN IF NOT EXISTS resolution VARCHAR(30);
ALTER TABLE cases ADD COLUMN IF NOT EXISTS resolution_notes TEXT;
ALTER TABLE cases ADD COLUMN IF NOT EXISTS resolved_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE cases ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP WITH TIME ZONE;

-- 3. Índices optimizados para filtros de cola de casos (antigüedad, monto y estado)
CREATE INDEX IF NOT EXISTS idx_cases_merchant_status_created ON cases(merchant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cases_created_at ON cases(created_at DESC);
