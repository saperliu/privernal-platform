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

package com.privernal.dynamic.advanced.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 联动幂等键与外链凭据工具。
 * <ul>
 *   <li>eventId 生成口径（§7.5.1）：由发布点生成、稳定可重放，同一事件多重投递同一值；</li>
 *   <li>外链 token：32 位加密随机串，库中只存 SHA-256 摘要（§5.3-1/§5.3-7）。</li>
 * </ul>
 */
public final class LinkageIdemUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkageIdemUtils() {
    }

    /** CREATE/UPDATE/DELETE：sha1(sourceTag|database|tableCode|dataId|写入毫秒) */
    public static String dataEventId(String sourceTag, String database, String tableCode, String dataId, long writeMillis) {
        return sha1(String.join("|",
                nullSafe(sourceTag), nullSafe(database), nullSafe(tableCode), nullSafe(dataId), String.valueOf(writeMillis)));
    }

    /** EFFECTIVE：sha1(orgId|tableCode|dataId|fromStatus|toStatus|回写时间)（§7.5.1 v1.2 更正口径） */
    public static String effectiveEventId(String orgId, String tableCode, String dataId,
                                          String fromStatus, String toStatus, String writeTime) {
        return sha1(String.join("|",
                nullSafe(orgId), nullSafe(tableCode), nullSafe(dataId),
                nullSafe(fromStatus), nullSafe(toStatus), nullSafe(writeTime)));
    }

    public static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 32 位加密随机 token（16 字节 hex），原文只在创建时一次性返回 */
    public static String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 回执码：20 位易读大写串（去易混淆字符），原文只在提交成功时一次性返回 */
    public static String randomReceiptCode() {
        String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            if (i == 4 || i == 9 || i == 14) {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
