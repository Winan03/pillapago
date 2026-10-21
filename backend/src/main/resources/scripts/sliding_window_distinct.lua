-- sliding_window_distinct.lua
-- Cuenta entidades distintas activas en una ventana deslizante de tiempo.
-- KEYS[1]: Clave ZSET (t:{merchantId}:vel:dist:{metric}:{hmacIdentifier})
-- ARGV[1]: nowMs (Timestamp actual en milisegundos)
-- ARGV[2]: windowMs (Duración de la ventana en milisegundos)
-- ARGV[3]: targetEntity (Identificador seudonimizado de la entidad secundaria a contar, ej: hmacAccountId)
-- ARGV[4]: ttlSeconds (Tiempo de vida de la clave en segundos)

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local targetEntity = ARGV[3]
local ttl = tonumber(ARGV[4])

local threshold = now - window
redis.call('ZADD', key, now, targetEntity)
redis.call('ZREMRANGEBYSCORE', key, '-inf', threshold)
local distinctCount = redis.call('ZCARD', key)
redis.call('EXPIRE', key, ttl)

return distinctCount
