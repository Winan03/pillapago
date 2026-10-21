-- sliding_window_frequency.lua
-- Registra un evento en una ventana deslizante de tiempo y retorna la frecuencia atómicamente.
-- KEYS[1]: Clave ZSET (t:{merchantId}:vel:freq:{metric}:{hmacIdentifier})
-- ARGV[1]: nowMs (Timestamp actual en milisegundos)
-- ARGV[2]: windowMs (Duración de la ventana en milisegundos)
-- ARGV[3]: memberId (Identificador único del evento, ej: txnId:nowMs)
-- ARGV[4]: ttlSeconds (Tiempo de vida de la clave en segundos)

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local member = ARGV[3]
local ttl = tonumber(ARGV[4])

local threshold = now - window
redis.call('ZREMRANGEBYSCORE', key, '-inf', threshold)
redis.call('ZADD', key, now, member)
local count = redis.call('ZCARD', key)
redis.call('EXPIRE', key, ttl)

return count
