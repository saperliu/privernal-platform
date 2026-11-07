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

package com.privernal.common.security.service;

import com.privernal.common.core.utils.PasswordDigestCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * F4 封存器（system 模块改密写点用）：把改密/重置链路拿到的线摘要封存成库内凭证。
 * 主密钥与 auth 侧 PasswordDigestService 同源（PASSWORD_DIGEST_MASTER_KEY），
 * AES-GCM 细节在 PasswordDigestCipher。
 *
 * @author saperliu
 */
@Service
public class PasswordDigestSealer {

    @Value("${PASSWORD_DIGEST_MASTER_KEY:}")
    private String masterKey;

    /** 挑战登录是否可用（主密钥已配置） */
    public boolean enabled() {
        return PasswordDigestCipher.enabled(masterKey);
    }

    /** 封存线摘要；未配主密钥时返回 null（库内 password_v2 留空，登录走 bcrypt 引导） */
    public String seal(String digest) {
        return PasswordDigestCipher.seal(digest, masterKey);
    }
}
