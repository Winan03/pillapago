-- idempotency_check_and_reserve.lua
-- Verifica o adquiere el bloqueo de idempotencia para una transacción.
-- KEYS[1]: Clave Hash (t:{merchantId}:idemp:{transactionId})
-- ARGV[1]: incomingPayloadHash (Hash SHA-256 canónico del payload entrante)
-- ARGV[2]: ttlSeconds (Tiempo de retención de idempotencia en segundos, default 86400)

local key = KEYS[1]
local incomingHash = ARGV[1]
local ttl = tonumber(ARGV[2])

if redis.call('EXISTS', key) == 1 then
    local storedHash = redis.call('HGET', key, 'payloadHash')
    if storedHash ~= incomingHash then
        return 'CONFLICT'
    end
    local status = redis.call('HGET', key, 'status')
    if status == 'COMPLETED' then
        local cachedResponse = redis.call('HGET', key, 'responseJson')
        if cachedResponse then
            return 'COMPLETED:' .. cachedResponse
        else
            return 'COMPLETED:'
        end
    else
        return 'IN_PROGRESS'
    end
else
    redis.call('HSET', key, 'payloadHash', incomingHash, 'status', 'IN_PROGRESS')
    redis.call('EXPIRE', key, ttl)
    return 'ACQUIRED'
end
