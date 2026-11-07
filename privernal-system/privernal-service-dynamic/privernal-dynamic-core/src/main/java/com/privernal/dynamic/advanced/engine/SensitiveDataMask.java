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

import com.privernal.common.core.utils.StringUtils;

import java.util.regex.Pattern;

/**
 * 敏感数据脱敏：导出/打印/外链三条链路统一执行，
 * 策略来自 io_config.sensitive_policy（MASK 脱敏 / HIDE 不出列），载体是 dynamic_form_column.sensitive。
 * 服务端脱敏，不信任前端隐藏。
 */
public final class SensitiveDataMask {

    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern ID_CARD = Pattern.compile("^\\d{17}[0-9Xx]$");
    private static final Pattern BANK_CARD = Pattern.compile("^\\d{13,19}$");

    private SensitiveDataMask() {
    }

    /** 是否需要脱敏（空值不脱） */
    public static boolean needMask(String sensitiveFlag) {
        return "Y".equalsIgnoreCase(sensitiveFlag);
    }

    /**
     * 按值形态自适应脱敏：手机号 138****1234；证件号保前 4 后 2；银行卡号保前 4 后 4；
     * 其它文本保前 1 后 1（长度≤2 全遮）。数据库数值列回读可能是科学计数法/带小数，
     * 先规整为纯数字串再匹配形态。
     */
    public static String mask(String value) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        String text = value.trim();
        if (text.matches("^-?\\d+(\\.\\d+)?([Ee]-?\\d+)?$")) {
            try {
                text = new java.math.BigDecimal(text).toPlainString();
            } catch (NumberFormatException ignored) {
                // 非数值形态按原文处理
            }
        }
        if (PHONE.matcher(text).matches()) {
            return text.substring(0, 3) + "****" + text.substring(7);
        }
        if (ID_CARD.matcher(text).matches()) {
            return text.substring(0, 4) + "**********" + text.substring(text.length() - 2);
        }
        if (BANK_CARD.matcher(text).matches()) {
            return text.substring(0, 4) + "********" + text.substring(text.length() - 4);
        }
        if (text.length() <= 2) {
            return "**";
        }
        return text.charAt(0) + repeat(text.length() - 2) + text.charAt(text.length() - 1);
    }

    private static String repeat(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append('*');
        }
        return sb.toString();
    }
}
