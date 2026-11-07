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

package com.privernal.common.mybatis.type;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * SQLite 时间列统一编解码。
 * <p>
 * sqlite-jdbc 对 {@code setTimestamp/setObject(LocalDateTime)} 的默认写出格式与
 * SQL 侧 {@code strftime}/迁移数据（空格分隔）字典序不可比（ISO 的 'T' &gt; ' '），
 * 会破坏租约比较与排序；而 {@code setTimestamp} 默认落成 epoch 毫秒、TEXT 列下
 * {@code getTimestamp} 反而解析失败。因此所有 Java 侧时间写入统一为
 * {@code yyyy-MM-dd HH:mm:ss} 字符串，读取端做宽解析，两种口径都能读回。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
final class SqliteDateTimeCodec {

    /** 与 SQL 侧 datetime('now','localtime') / strftime('%Y-%m-%d %H:%M:%f') 同一字典序口径 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private SqliteDateTimeCodec() {
    }

    /**
     * 时间对象 → 存储字符串（毫秒截断；秒级精度已满足平台全部比较语义）。
     */
    static String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    /**
     * LocalDateTime 宽解析：空格/ISO 'T' 两种分隔、可带毫秒、纯日期、epoch 毫秒兜底。
     */
    static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String value = raw.trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(value, DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_MILLIS);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value.substring(0, 10)).atStartOfDay();
        } catch (Exception ignored) {
        }
        // 兼容历史 epoch 毫秒残留
        if (value.chars().allMatch(Character::isDigit)) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), ZoneId.systemDefault());
        }
        throw new IllegalArgumentException("无法解析为 LocalDateTime：" + raw);
    }

    /**
     * {@link java.util.Date} 宽解析，口径同 {@link #parseDateTime(String)}。
     */
    static java.util.Date parseDate(String raw) {
        LocalDateTime parsed = parseDateTime(raw);
        return parsed == null ? null : java.util.Date.from(parsed.atZone(ZoneId.systemDefault()).toInstant());
    }
}
