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

package com.privernal.auth.service;

import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.utils.PasswordDigestCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * F4 口令摘要改造：挑战-应答协议的服务端部件。
 *
 * 背景（ROADMAP 1.11）：旧协议线路上传无盐 SHA512(明文口令)，摘要即等价口令、可跨部署重放。
 * 新协议：
 *   1) 客户端取一次性 nonce（本服务签发，60 秒/单次，读取即作废）；
 *   2) 线路上传 SHA512(nonce + X)，X=SHA512(明文口令)——被截获的值换一次 nonce 即失效；
 *   3) 服务端用 AES-GCM 封存的 X（主密钥在环境变量，不落库明文）还原 X 后比对。
 * 过渡期双口径：未迁移用户（无封存值）走 bcrypt 引导一次（客户端自动降级重试），成功即迁移。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class PasswordDigestService {

    private static final String V2_PREFIX = "v2:";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final long CHALLENGE_TTL_SECONDS = 60;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 主密钥（32 字节 hex 或任意 ≥32 字符口令短语）；留空=挑战登录关闭，全走 bcrypt 引导 */
    @Value("${PASSWORD_DIGEST_MASTER_KEY:}")
    private String masterKey;

    /** 过渡期口径：true=已迁移用户拒绝旧口径登录（全量客户端上线后切），默认 false */
    @Value("${privernal.security.password-digest.require-challenge:false}")
    private boolean requireChallenge;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 挑战登录是否可用（主密钥已配置） */
    public boolean challengeEnabled() {
        return masterKey != null && masterKey.length() >= 32;
    }

    public boolean isRequireChallenge() {
        return requireChallenge;
    }

    /**
     * 签发一次性挑战：{nonceId} 入 Redis、nonce 直接返回给客户端；
     * 校验时必须用 takeChallenge 原子消费，防同一 nonce 重放。
     */
    public Challenge issue(String loginName) {
        String nonce = randomHex(32);
        String nonceId = UUID.randomUUID().toString().replace("-", "");
        // loginName 只进 key 不进值，避免把账号写进公共可枚举空间；键 60 秒过期
        stringRedisTemplate.opsForValue().set(Constants.PWD_CHALLENGE_PREFIX + nonceId,
                nonce + "|" + loginName, Duration.ofSeconds(CHALLENGE_TTL_SECONDS));
        return new Challenge(nonceId, nonce, CHALLENGE_TTL_SECONDS);
    }

    /**
     * 原子消费挑战并校验：SHA512(nonce + X) 是否等于客户端上传值。
     * 返回 null=挑战无效/已消费/不匹配（不区分原因，避免给枚举者额外信号）。
     */
    public String verifyChallenge(String nonceId, String loginName, String sealedDigest, String clientDigest) {
        if (nonceId == null || nonceId.isBlank() || !challengeEnabled()) {
            return null;
        }
        String key = Constants.PWD_CHALLENGE_PREFIX + nonceId;
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (stored == null) {
            return null;
        }
        // 读取即作废（单次行权）
        stringRedisTemplate.delete(key);
        int sep = stored.lastIndexOf('|');
        String nonce = stored.substring(0, sep);
        String boundLoginName = stored.substring(sep + 1);
        if (!loginName.equals(boundLoginName)) {
            return null;
        }
        String x = unseal(sealedDigest);
        if (x == null) {
            return null;
        }
        // 协议口径：客户端上传 SHA512(nonce + X)
        String expected = sha512Hex(nonce + x);
        // 恒时比较
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                (clientDigest == null ? "" : clientDigest).getBytes(StandardCharsets.UTF_8)) ? x : null;
    }

    /** AES-GCM 封存线摘要 X；主密钥未配置时返回 null。实现在 PasswordDigestCipher（common-core） */
    public String seal(String digest) {
        String sealed = PasswordDigestCipher.seal(digest, masterKey);
        if (sealed == null && digest != null) {
            log.error("口令摘要封存失败（主密钥未配置或加密异常）");
        }
        return sealed;
    }

    /** 解封；格式不符/密钥不匹配/未配置 → null。实现在 PasswordDigestCipher（common-core） */
    public String unseal(String sealed) {
        String x = PasswordDigestCipher.unseal(sealed, masterKey);
        if (x == null && sealed != null && sealed.startsWith(PasswordDigestCipher.V2_PREFIX)) {
            log.warn("口令摘要解封失败（密钥轮换或数据损坏）");
        }
        return x;
    }

    /** 密钥派生：hex(32字节) 直接用；其余 SHA-256 到 32 字节 */
    private SecretKeySpec keySpec() {
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

    private String randomHex(int chars) {
        byte[] buf = new byte[chars / 2];
        secureRandom.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private String sha512Hex(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-512").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record Challenge(String nonceId, String nonce, long ttlSeconds) {
    }
}
