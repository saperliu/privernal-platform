/*
 * Copyright 2025-2026 saperliu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.privernal.common.security.aspect;

import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.exception.RateLimiterException;
import com.privernal.common.core.utils.ServletUtils;
import com.privernal.common.core.utils.ip.IpUtils;
import com.privernal.common.security.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 接口限流切面：Redis 固定窗口计数（INCR+EXPIRE 原子化）。
 *
 * 开关：privernal.security.rate-limiter.enabled（默认 true，置 false 仅建议在本地排障时）。
 * 注意 Redis 不可用时限流放行（fail-open）——限流是纵深防御层，
 * 不能因为缓存抖动把正常业务全部打挂；网关层限流不受此影响。
 *
 * @author saperliu
 */
@Slf4j
@Aspect
@Component
public class RateLimiterAspect {

    private static final String DEFAULT_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 总开关：privernal.security.rate-limiter.enabled，默认 true */
    @Value("${privernal.security.rate-limiter.enabled:true}")
    private boolean enabled;

    private final DefaultRedisScript<Long> script = buildScript();

    private static DefaultRedisScript<Long> buildScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        // 内置脚本与 classpath 的 rate-limiter.lua 同内容（lua 文件供运维审计比对）
        s.setScriptText(DEFAULT_SCRIPT);
        s.setResultType(Long.class);
        return s;
    }

    @Around("@annotation(com.privernal.common.security.annotation.RateLimiter)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (!enabled) {
            return point.proceed();
        }
        RateLimiter rateLimiter = ((MethodSignature) point.getSignature()).getMethod()
                .getAnnotation(RateLimiter.class);
        if (rateLimiter == null) {
            return point.proceed();
        }

        String name = rateLimiter.key().isEmpty()
                ? point.getSignature().getDeclaringTypeName() + "." + point.getSignature().getName()
                : rateLimiter.key();
        String dimension = rateLimiter.type() == RateLimiter.LimitType.IP
                ? IpUtils.getIpAddr(ServletUtils.getRequest())
                : "global";
        String redisKey = Constants.RATE_LIMITER_PREFIX + name + ":" + dimension;

        try {
            List<String> keys = Collections.singletonList(redisKey);
            Long count = stringRedisTemplate.execute(script, keys, String.valueOf(rateLimiter.period()));
            if (count != null && count > rateLimiter.max()) {
                log.warn("接口限流触发：key={} count={} max={}/{}s", redisKey, count, rateLimiter.max(), rateLimiter.period());
                throw new RateLimiterException();
            }
        } catch (RateLimiterException e) {
            throw e;
        } catch (Exception e) {
            // fail-open：Redis 异常不拦截业务（见类注释）
            log.error("限流器 Redis 异常，本请求放行：{}", e.getMessage());
        }
        return point.proceed();
    }
}
