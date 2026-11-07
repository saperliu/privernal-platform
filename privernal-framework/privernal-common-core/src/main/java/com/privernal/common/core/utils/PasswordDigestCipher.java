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

package com.privernal.common.core.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Base64;

/**
 * F4 口令线摘要封存（静态工具）：AES-GCM(iv 前缀, "v2:" 前缀)。
 * 主密钥来源：PASSWORD_DIGEST_MASTER_KEY（32 字节 hex 直用，否则 SHA-256 派生）。
 * auth 的 PasswordDigestService（挑战存取）与 system 的改密写点共用本工具。
 *
 * @author saperliu
 */
public final class PasswordDigestCipher {

    public static final String V2_PREFIX = "v2:";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordDigestCipher() {
    }

    /** 主密钥是否可用（≥32 字符） */
    public static boolean enabled(String masterKey) {
        return masterKey != null && masterKey.length() >= 32;
    }

    /** 封存：不可用/入参为空 → null；失败 → null（调用方按"未封存"处理，登录可回退 bcrypt） */
    public static String seal(String digest, String masterKey) {
        if (!enabled(masterKey) || digest == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(masterKey), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(digest.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return V2_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解封：不可用/格式不符/密钥不匹配 → null */
    public static String unseal(String sealed, String masterKey) {
        if (!enabled(masterKey) || sealed == null || !sealed.startsWith(V2_PREFIX)) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(V2_PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(masterKey), new GCMParameterSpec(GCM_TAG_BITS, all, 0, GCM_IV_LEN));
            byte[] pt = cipher.doFinal(all, GCM_IV_LEN, all.length - GCM_IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 32 字节 hex 主密钥直用，其余 SHA-256 派生到 32 字节 */
    private static SecretKeySpec keySpec(String masterKey) {
        try {
            byte[] key;
            if (masterKey.length() == 64 && masterKey.matches("[0-9a-fA-F]{64}")) {
                key = HexFormat.of().parseHex(masterKey);
            } else {
                key = MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8));
            }
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("口令主密钥派生失败", e);
        }
    }
}
