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

package com.privernal.system.api;

/**
 * 机读密钥可逆加解密服务（接口下沉到 privernal-system-api）。
 * <p>
 * 实现方是 privernal-system-core 的 {@code MachineSecretCipher}（AES/GCM，主密钥
 * {@code openapi.secret-master-key}）。跨模块（dynamic 等只依赖 system-api 的模块）
 * 一律按本接口注入，<b>禁止各模块复制第二套 AES 实现</b>。
 * <p>
 * 语义约束：{@link #isConfigured()} 为 false 或加解密失败时，调用方必须显式报错，
 * <b>不得降级为明文或摘要</b>（fail-closed）。
 */
public interface ISecretCipherService {

    /** 主密钥未配置时返回 false，调用方应拒绝涉及密文的功能而不是退化成明文 */
    boolean isConfigured();

    /** 明文 → AES/GCM 密文（base64）；未配置主密钥时抛异常 */
    String encrypt(String plainText);

    /** AES/GCM 密文（base64） → 明文；未配置或密钥变更时抛异常 */
    String decrypt(String cipherTextBase64);
}
