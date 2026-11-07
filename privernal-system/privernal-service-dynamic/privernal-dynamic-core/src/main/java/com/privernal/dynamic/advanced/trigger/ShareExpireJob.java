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

package com.privernal.dynamic.advanced.trigger;

import com.privernal.dynamic.advanced.mapper.DynamicFormShareMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 外链到期自动停用每日作业（「有效期：过期自动停用」+ T11）：
 * 到期外链置 enabled='N'（停用即 404）。每日 00:20 执行；UPDATE 幂等，多实例重复执行无害。
 * 项目调度池 size=1 已被既有任务占用——本作业每日一次、毫秒级完成，占用可忽略（§2 口径）。
 * <p>
 * 时间全部走绑定参数（与列存储同一 'yyyy-MM-dd HH:mm:ss' 口径）：NOW()/datetime('now')
 * 是方言函数，JdbcTemplate 直连 SQL 无法按 databaseId 分支；字符串字面量在 MySQL 下
 * 隐式转型为 datetime、在 SQLite 下 TEXT 字典序比较，两库语义一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareExpireJob {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 20 0 * * ?")
    public void disableExpiredShares() {
        try {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            int rows = jdbcTemplate.update(
                    "UPDATE dynamic_form_share SET enabled = 'N', updated_time = ? "
                            + "WHERE deleted_flag = 'N' AND enabled = 'Y' AND expire_time IS NOT NULL AND expire_time < ?",
                    now, now);
            if (rows > 0) {
                log.info("外链到期自动停用 {} 条", rows);
            }
        } catch (Exception e) {
            log.error("外链到期自动停用作业失败", e);
        }
    }
}
