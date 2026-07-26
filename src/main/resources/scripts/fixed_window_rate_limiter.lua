-- Fixed-window counter: atomic INCR + conditional EXPIRE in one round trip.
-- Without this, a crash between a separate INCR and EXPIRE call could leave a
-- key with no TTL, rate-limiting that key forever until manually cleared.
-- KEYS[1] = counter key
-- ARGV[1] = window length in seconds
local current = redis.call('INCR', KEYS[1])
if tonumber(current) == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current
