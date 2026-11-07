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

package com.privernal.system.support;

import com.privernal.common.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * OpenAPI / Webhook / SSO 三类机读密钥的可逆存储。
 * <p>
 * ⚠️ 为什么不是 hash：HMAC 验签要求服务端用同一密钥重算签名，一旦只存 sha256 就无法还原。
 * 库里存的是 AES/GCM 密文，主密钥来自配置 openapi.secret-master-key（不落库、不进代码）。
 * 把 secret_cipher 改成 hash 列会让所有验签链路静默失效，改动前读 。
 * <p>
 * 实现下沉接口 {@link ISecretCipherService}（privernal-system-api），dynamic 等模块
 * 按接口注入复用，禁止复制第二套 AES。
 */
@Slf4j
@Component
public class MachineSecretCipher implements com.privernal.system.api.ISecretCipherService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec masterKey;

    public MachineSecretCipher(@Value("${openapi.secret-master-key:}") String masterKeyConfig) {
        if (masterKeyConfig == null || masterKeyConfig.trim().isEmpty()) {
            this.masterKey = null;
        } else {
            byte[] keyBytes = masterKeyConfig.trim().getBytes(StandardCharsets.UTF_8);
            this.masterKey = new SecretKeySpec(normalizeKey(keyBytes), "AES");
        }
    }

    /**
     * 主密钥未配置时返回 false，调用方应拒绝创建凭证而不是退化成明文存储
     */
    public boolean isConfigured() {
        return masterKey != null;
    }

    private void requireConfigured() {
        if (masterKey == null) {
            throw new ServiceException("未配置 openapi.secret-master-key，无法处理机读密钥");
        }
    }

    private static byte[] normalizeKey(byte[] raw) {
        byte[] digest = sha256(raw);
        byte[] key = new byte[32];
        System.arraycopy(digest, 0, key, 0, 32);
        return key;
    }

    /** 生成 32 字节随机密钥，URL 安全 base64（无填充） */
    public String generateSecret() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public String encrypt(String plainText) {
        requireConfigured();
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new ServiceException("密钥加密失败：" + e.getMessage());
        }
    }

    public String decrypt(String cipherTextBase64) {
        requireConfigured();
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            return null;
        }
        try {
            byte[] packed = Base64.getDecoder().decode(cipherTextBase64);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(packed, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] plain = cipher.doFinal(packed, IV_LENGTH, packed.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServiceException("密钥解密失败，主密钥可能已变更：" + e.getMessage());
        }
    }

    public static String sha256Hex(String text) {
        if (text == null) {
            return null;
        }
        return HexFormat.of().formatHex(sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 常量时间比对，避免验签比较被时序侧信道利用
     */
    public static boolean constantEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String prefix(String secret) {
        if (secret == null || secret.length() < 8) {
            return secret;
        }
        return secret.substring(0, 6);
    }

    public static String last4(String secret) {
        if (secret == null || secret.length() < 4) {
            return secret;
        }
        return secret.substring(secret.length() - 4);
    }
}
