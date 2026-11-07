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

package com.privernal.system.controller;

import com.privernal.common.redis.service.RedisService;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 流水号生成
 * 规则：前缀 + 日期(可配格式) + "-" + 序列（按周期重置，Redis 原子递增保证唯一），
 * 如 SN20250929-0001；序列基于 RedisService.getAutoResetIncrementNum（key 带周期，
 * 到期自动清零），多实例并发安全
 *
 * @author saperliu
 */
@Slf4j
@Tag(name = "流水号生成接口")
@RequestMapping("/back/system/serialNumber")
@RestController
public class SystemSerialNumberController extends BaseController {

    @Autowired
    private RedisService redisService;

    /**
     * 生成流水号
     *
     * @param prefix 前缀（如 SN）
     * @param pattern 日期格式（默认 yyyyMMdd）
     * @param digits 序列位数（不足左补 0，默认 4）
     * @param cycle  序列重置周期：day 每日 / month 每月 / never 不重置
     */
    @Operation(summary = "生成流水号", description = "前缀+日期+序列，序列按周期重置（Redis 原子递增）")
    @GetMapping("/generate")
    public CommonResult<Object> generate(
            @RequestParam(value = "prefix", required = false, defaultValue = "SN") String prefix,
            @RequestParam(value = "pattern", required = false, defaultValue = "yyyyMMdd") String pattern,
            @RequestParam(value = "digits", required = false, defaultValue = "4") int digits,
            @RequestParam(value = "cycle", required = false, defaultValue = "day") String cycle) {
        try {
            String dateStr;
            try {
                dateStr = DateTimeFormatter.ofPattern(pattern).format(LocalDate.now());
            } catch (Exception e) {
                dateStr = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now());
            }
            String organizationId = SecurityUtils.getOrganizationId();
            // 组织隔离：不同组织的序列互不干扰
            String redisKey = "serial_number:" + organizationId + ":" + prefix + ":"
                    + ("never".equals(cycle) ? "all" : dateStr);
            long timeout = resolveTimeoutSeconds(cycle);
            Long seq = redisService.getAutoResetIncrementNum(redisKey, 0, timeout);
            int width = digits > 0 && digits <= 10 ? digits : 4;
            String serialNumber = prefix + dateStr + "-" + String.format("%0" + width + "d", seq);
            return CommonResult.ok(serialNumber);
        } catch (Exception e) {
            log.error("生成流水号失败", e);
            return CommonResult.fail("生成流水号失败：" + e.getMessage());
        }
    }

    /**
     * 序列 key 的过期时间：day=当日末，month=当月末，never 不过期（-1）；
     * 多加 1 小时缓冲，避免周期交界处的临界重置
     */
    private long resolveTimeoutSeconds(String cycle) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt;
        if ("month".equals(cycle)) {
            expireAt = YearMonth.now().atEndOfMonth().atTime(LocalTime.MAX);
        } else if ("never".equals(cycle)) {
            return -1;
        } else {
            expireAt = LocalDate.now().plusDays(1).atStartOfDay();
        }
        long seconds = ChronoUnit.SECONDS.between(now, expireAt) + 3600;
        return Math.max(seconds, 3600);
    }
}
