-- User: Dima Vizelman
-- Date: 18/09/17

local inFlightKey = KEYS[1]
local resubmitQueueKey = KEYS[2]
local queuesKey = KEYS[3]
local now = tonumber(ARGV[1])
local delay = tonumber(ARGV[2])
local resubmitQueueName = ARGV[3]
local inflightQueuePattern = "inflight%-queue"


local inFlights = redis.call('KEYS', inFlightKey.."*")

for _,key in ipairs(inFlights) do
    local job = redis.call('LPOP',key);

    redis.call('SADD', queuesKey,resubmitQueueName)

    if delay == 0 then
        redis.call('RPUSH',resubmitQueueKey,job)
    else
        redis.call('ZADD',resubmitQueueKey,tostring(now+delay),job)
    end

end