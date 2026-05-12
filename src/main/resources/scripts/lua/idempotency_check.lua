-- idempotency_check.lua
-- Atomic check-and-set for idempotency in Redis.
-- KEYS[1] = idempotency key (e.g. idempotency:{merchantId}:{key})
-- ARGV[1] = request hash (SHA-256)
-- ARGV[2] = response JSON (to cache)
-- ARGV[3] = TTL in seconds
-- Returns: "HIT" + cached response | "MISS" | "CONFLICT" + cached hash

local existing = redis.call('GET', KEYS[1])
if existing then
    local data = cjson.decode(existing)
    if data.requestHash == ARGV[1] then
        return {'HIT', existing}
    else
        return {'CONFLICT', data.requestHash}
    end
end

if ARGV[2] ~= '' then
    local entry = cjson.encode({
        requestHash = ARGV[1],
        response = ARGV[2]
    })
    redis.call('SETEX', KEYS[1], ARGV[3], entry)
end

return {'MISS'}
