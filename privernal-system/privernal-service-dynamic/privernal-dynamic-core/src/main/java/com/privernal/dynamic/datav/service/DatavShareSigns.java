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

package com.privernal.dynamic.datav.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 大屏外链签名与 referer 匹配（纯静态，可单测）。
 * <p>
 * sign = HMAC-SHA256(masterKey, "datav-share:" + shareCode)。固定 per-share 而不是把
 * 有效期编进签名：改期/吊销只改库里的行，不需要重新分发链接；比较用常量时间，
 * 防 timing 探测。主密钥未配置（空串）时 fail-closed——签名永远对不上，分享链路整体不可用。
 *
 * @author saperliu
 */
public final class DatavShareSigns {

    private DatavShareSigns() {
    }

    public static final String SIGN_MESSAGE_PREFIX = "datav-share:";

    /**
     * @return hex(HMAC-SHA256(masterKey, prefix + shareCode))；masterKey 为空返回 null（fail-closed）
     */
    public static String signOf(String masterKey, String shareCode) {
        if (masterKey == null || masterKey.isEmpty() || shareCode == null || shareCode.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((SIGN_MESSAGE_PREFIX + shareCode).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 常量时间比较：长度不同直接 false（长度本身不是秘密），同长逐字节累积差异 */
    public static boolean constantEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /**
     * referer 是否命中白名单。白名单 null/空 = 不限制；Referer 缺失且配置了白名单 = 拒绝
     * （配置白名单的意义就是限定来源，缺头的请求不应比带头的更宽）。
     * 匹配按 Referer 的 host：等于候选，或以 ".候选" 结尾——防 "evil-" + 授权域名 的拼接绕过。
     */
    public static boolean refererAllowed(String whitelist, String referer) {
        if (whitelist == null || whitelist.trim().isEmpty()) {
            return true;
        }
        if (referer == null || referer.trim().isEmpty()) {
            return false;
        }
        String host = hostOf(referer.trim());
        if (host.isEmpty()) {
            return false;
        }
        for (String raw : whitelist.split("[,，]")) {
            String candidate = raw.trim().toLowerCase();
            if (candidate.isEmpty()) {
                continue;
            }
            // 去掉调用方可能带上的协议前缀与端口，白名单里只该写裸域名
            candidate = hostOf(candidate.contains("://") ? candidate : "http://" + candidate);
            if (candidate.isEmpty()) {
                continue;
            }
            if (host.equals(candidate) || host.endsWith("." + candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 URL 里取小写 host；解析失败或含域名字符集之外的字符（空格、感叹号等）返回空串（按拒绝处理）。
     * 不做合法性校验的话，「not a host !!!」会被整段当 host 收进白名单，之后再无 Referer 能匹配上。
     */
    public static String hostOf(String url) {
        try {
            String rest = url;
            int scheme = rest.indexOf("://");
            if (scheme >= 0) {
                rest = rest.substring(scheme + 3);
            }
            int end = rest.indexOf('/');
            if (end >= 0) {
                rest = rest.substring(0, end);
            }
            int at = rest.lastIndexOf('@');
            if (at >= 0) {
                rest = rest.substring(at + 1);
            }
            int colon = rest.indexOf(':');
            if (colon >= 0) {
                rest = rest.substring(0, colon);
            }
            String host = rest.toLowerCase();
            return host.matches("^[a-z0-9.-]+$") ? host : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * referer 白名单归一：按中英文逗号拆开、剥协议与端口、小写去重；全空返回 null（不限制）。
     * 配置进库前必须走它——白名单里存 "https://A.com:8443" 这种原始形态，refererAllowed 永远匹配不上。
     */
    public static String normalizeWhitelist(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String item : raw.split("[,，]")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String host = hostOf(trimmed.contains("://") ? trimmed : "http://" + trimmed);
            if (!host.isEmpty()) {
                seen.add(host);
            }
        }
        return seen.isEmpty() ? null : String.join(",", seen);
    }

    /** SHA-256 工具（生成 shareCode 的随机性来自 UUID，不需要摘要；保留给需要的调用方） */
    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
