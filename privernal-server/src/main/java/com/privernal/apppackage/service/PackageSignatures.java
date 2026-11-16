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

package com.privernal.apppackage.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.privernal.apppackage.dto.AppPackageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * 应用包完整性（checksum）与来源可信（signature）校验，R2-14 模板市场的前置件。
 *
 * <p>canonical 定义：把包序列化为 JSON 树后，<b>递归按 key 排序</b>再紧凑序列化的结果——
 * 与字段顺序、空白、fastjson2 版本差异无关，手工重排版过的包仍可验。签名域
 * {@code "app-package-sign-v1\n" + canonical} 做域分离，防止同一主密钥下与其他 HMAC 用途串线。
 *
 * <p>密钥体系复用 {@code openapi.secret-master-key}（与 OpenAPI 机读密钥/外链分享同一主密钥）：
 * 签名密钥 = hex(hmac(masterKey, "app-package-sign-v1"))，随环境配置走、不进代码库不进包——
 * 因此跨环境包只能验完整性（checksum），验不了来源（signature 必然不符），这是设计使然而非缺陷。
 *
 * <p>导入端语义（fail-closed）：见 {@link #verify}。checksum 只防损坏不防篡改；
 * signature 存在且主密钥可用时不符必须显式豁免才能导入。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class PackageSignatures {

    /** 签名算法与域分离前缀，客户端/外部工具复算时必须与此一致 */
    public static final String ALGORITHM = "HMAC-SHA256";
    public static final String SIGN_DOMAIN = "app-package-sign-v1";

    private final String masterKey;

    public PackageSignatures(@Value("${openapi.secret-master-key:}") String masterKey) {
        this.masterKey = masterKey == null ? "" : masterKey.trim();
    }

    /** 主密钥是否已配置（未配置时导出不签名、导入对已签包降级为「无法校验」警告） */
    public boolean keyConfigured() {
        return !masterKey.isEmpty();
    }

    /** 导出端收口：重算并写入 checksum；主密钥已配置则同时写 signature/algorithm/signedAt */
    public void sign(AppPackageDTO pkg) {
        String canonical = canonical(pkg);
        pkg.setChecksum(sha256Hex(canonical));
        if (!keyConfigured()) {
            pkg.setSignature(null);
            pkg.setSignatureAlgorithm(null);
            pkg.setSignedAt(null);
            log.info("未配置 openapi.secret-master-key，应用包仅带校验和不签名（appCode={}）",
                    pkg.getApp() == null ? null : pkg.getApp().getCode());
            return;
        }
        pkg.setSignatureAlgorithm(ALGORITHM);
        pkg.setSignedAt(LocalDateTime.now().toString());
        pkg.setSignature(hmacSha256Hex(signKey(), SIGN_DOMAIN + "\n" + canonical));
    }

    /** 签名校验状态 */
    public enum Status {
        /** 包未携带签名（旧版本包或导出端未配主密钥） */
        NOT_SIGNED,
        /** 签名与重算值一致 */
        VERIFIED,
        /** 签名不符：包内容在签名后被改动过，或不是本主密钥体系签出的包 */
        MISMATCH,
        /** 包有签名但本环境未配主密钥，无法校验 */
        KEY_MISSING
    }

    /** 校验结果：status + 包内容是否被改动的判定 */
    public record Verification(Status status, boolean checksumMatch, String detail) {
    }

    /**
     * 导入端校验（零副作用，只读包）：
     * <ul>
     *   <li>checksum 恒重算比对（canonical 不含 checksum/signature，与是否签名无关）；</li>
     *   <li>无签名 → NOT_SIGNED（老包走兼容通道，报告提醒）；</li>
     *   <li>有签名且主密钥可用 → 恒定时间比对，不符 = MISMATCH（调用方必须拒绝或拿到显式豁免）；</li>
     *   <li>有签名但主密钥未配置 → KEY_MISSING（无法校验，报告提醒，不阻断——未配密钥=本环境未启用签名体系）。</li>
     * </ul>
     */
    public Verification verify(AppPackageDTO pkg) {
        String canonical = canonical(pkg);
        boolean checksumMatch = pkg.getChecksum() != null
                && MessageDigest.isEqual(pkg.getChecksum().getBytes(StandardCharsets.UTF_8), sha256Hex(canonical).getBytes(StandardCharsets.UTF_8));
        if (pkg.getSignature() == null || pkg.getSignature().isBlank()) {
            return new Verification(Status.NOT_SIGNED, checksumMatch, "应用包未签名（旧版本包或导出端未配置主密钥）");
        }
        if (!keyConfigured()) {
            return new Verification(Status.KEY_MISSING, checksumMatch,
                    "应用包已签名但本环境未配置 openapi.secret-master-key，无法校验签名");
        }
        boolean match = MessageDigest.isEqual(pkg.getSignature().getBytes(StandardCharsets.UTF_8),
                hmacSha256Hex(signKey(), SIGN_DOMAIN + "\n" + canonical).getBytes(StandardCharsets.UTF_8));
        return match
                ? new Verification(Status.VERIFIED, checksumMatch, "签名校验通过")
                : new Verification(Status.MISMATCH, checksumMatch, "签名校验失败：包内容在签名后被改动过，或不是本主密钥体系签出的包");
    }

    /** 签名密钥派生：域分离 hmac 一次，避免把主密钥直接当 HMAC key 复用在多个用途上 */
    private String signKey() {
        return hmacSha256Hex(masterKey, SIGN_DOMAIN);
    }

    /**
     * canonical：包 → JSON 树 → 剔除签名元数据自身（signature/checksum/signedAt/signatureAlgorithm）
     * → 递归 key 排序 → 紧凑序列化。入参先过一次 JSON 往返，保证与「从文件解析回来的包」走完全相同的树形。
     */
    public String canonical(AppPackageDTO pkg) {
        JSONObject tree = JSON.parseObject(JSON.toJSONString(pkg));
        tree.remove("signature");
        tree.remove("checksum");
        tree.remove("signedAt");
        tree.remove("signatureAlgorithm");
        return JSON.toJSONString(sortDeep(tree));
    }

    private Object sortDeep(Object value) {
        if (value instanceof JSONObject jsonObject) {
            JSONObject sorted = new JSONObject(new TreeMap<>());
            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                sorted.put(entry.getKey(), sortDeep(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof JSONArray array) {
            JSONArray kept = new JSONArray(array.size());
            for (Object item : array) {
                kept.add(sortDeep(item));
            }
            return kept;
        }
        return value;
    }

    private String hmacSha256Hex(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 供 dry-run 报告展示的简述 */
    public static String describe(Verification verification) {
        return switch (verification.status()) {
            case VERIFIED -> "已验签";
            case MISMATCH -> "验签失败";
            case NOT_SIGNED -> "未签名";
            case KEY_MISSING -> "无法验签（未配主密钥）";
        };
    }
}
