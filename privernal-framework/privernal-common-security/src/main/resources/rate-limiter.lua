-- @RateLimiter 固定窗口计数：INCR + 首次 EXPIRE，原子执行
-- KEYS[1]=计数键  ARGV[1]=窗口秒数  返回当前计数
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
