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

package com.privernal.dynamic.datav.service.connector;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.system.api.ISecretCipherService;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外部数据源凭据处理测试
 * <p>
 * 覆盖三条容易被忽略的路径：改无关字段时不能把密钥清了、主密钥缺失时必须失败而不是退回明文、
 * 出口既不下发明文也不下发密文。
 *
 * @author saperliu
 */
@DisplayName("外部数据源凭据密文与回显测试")
class DatavSourceSecretsTest {

    /** 可逆的假实现：够验证调用编排，不引入第二套加密 */
    private static final class FakeCipher implements ISecretCipherService {
        private final boolean configured;

        FakeCipher(boolean configured) {
            this.configured = configured;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String encrypt(String plainText) {
            if (!configured) {
                throw new IllegalStateException("no master key");
            }
            return "enc:" + plainText;
        }

        @Override
        public String decrypt(String cipherTextBase64) {
            if (!configured) {
                throw new IllegalStateException("no master key");
            }
            if (cipherTextBase64 == null || !cipherTextBase64.startsWith("enc:")) {
                throw new IllegalArgumentException("bad cipher");
            }
            return cipherTextBase64.substring(4);
        }
    }

    private final ISecretCipherService cipher = new FakeCipher(true);

    @Test
    @DisplayName("哪些键算凭据：URL/方法/数据路径都不算")
    void classifiesSecretKeys() {
        assertThat(DatavSourceSecrets.isSecretKey("password")).isTrue();
        assertThat(DatavSourceSecrets.isSecretKey("Authorization")).isTrue();
        assertThat(DatavSourceSecrets.isSecretKey("appSecret")).isTrue();
        assertThat(DatavSourceSecrets.isSecretKey("url")).isFalse();
        assertThat(DatavSourceSecrets.isSecretKey("resultPath")).isFalse();
        // 已经是密文键不再当作凭据，否则会被二次加密
        assertThat(DatavSourceSecrets.isSecretKey("passwordCipher")).isFalse();
    }

    @Test
    @DisplayName("保存时明文换成密文键，明文不落库")
    void sealsPlaintext() {
        JSONObject incoming = new JSONObject();
        incoming.put("url", "https://api.example.com/orders");
        incoming.put("password", "p@ss");

        JSONObject sealed = DatavSourceSecrets.seal(incoming, null, cipher);

        assertThat(sealed.getString("passwordCipher")).isEqualTo("enc:p@ss");
        assertThat(sealed.containsKey("password")).isFalse();
        assertThat(sealed.getString("url")).isEqualTo("https://api.example.com/orders");
    }

    @Test
    @DisplayName("表单回传掩码时沿用旧密文——改个超时不能把密钥清掉")
    void keepsOldCipherWhenMasked() {
        JSONObject existing = new JSONObject();
        existing.put("passwordCipher", "enc:old-secret");
        JSONObject incoming = new JSONObject();
        incoming.put("password", DatavSourceSecrets.MASK);
        incoming.put("timeoutMs", 3000);

        JSONObject sealed = DatavSourceSecrets.seal(incoming, existing, cipher);

        assertThat(sealed.getString("passwordCipher")).isEqualTo("enc:old-secret");
        assertThat(sealed.getString("timeoutMs")).isEqualTo("3000");
    }

    @Test
    @DisplayName("请求头里的凭据搬进密文桶，普通头原样保留")
    void sealsHeaders() {
        JSONObject headers = new JSONObject();
        headers.put("Authorization", "Bearer super-token");
        headers.put("X-Tenant", "acme");
        JSONObject incoming = new JSONObject();
        incoming.put("headers", headers);

        JSONObject sealed = DatavSourceSecrets.seal(incoming, null, cipher);

        assertThat(sealed.getJSONObject("headers").getString("X-Tenant")).isEqualTo("acme");
        assertThat(sealed.getJSONObject("headers").containsKey("Authorization")).isFalse();
        assertThat(sealed.getJSONObject(DatavSourceSecrets.HEADER_CIPHERS).getString("Authorization"))
                .isEqualTo("enc:Bearer super-token");
    }

    @Test
    @DisplayName("回传的掩码密文键必须还原成库里真值，否则一次保存就毁了凭据")
    void restoresMaskedCipherKeys() {
        JSONObject previous = new JSONObject();
        previous.put("passwordCipher", "enc:real");
        JSONObject previousHeaders = new JSONObject();
        previousHeaders.put("Authorization", "enc:Bearer real");
        previous.put(DatavSourceSecrets.HEADER_CIPHERS, previousHeaders);

        JSONObject incoming = new JSONObject();
        incoming.put("url", "https://api.example.com");
        incoming.put("passwordCipher", DatavSourceSecrets.MASK);
        incoming.put(DatavSourceSecrets.HEADER_CIPHERS, maskedCopy(previousHeaders));

        JSONObject sealed = DatavSourceSecrets.seal(incoming, previous, cipher);

        assertThat(sealed.getString("passwordCipher")).isEqualTo("enc:real");
        assertThat(sealed.getJSONObject(DatavSourceSecrets.HEADER_CIPHERS).getString("Authorization"))
                .isEqualTo("enc:Bearer real");
    }

    @Test
    @DisplayName("库里没有对应密文时，掩码键直接丢掉，不留一个假值在里面")
    void dropsMaskedCipherWithoutHistory() {
        JSONObject incoming = new JSONObject();
        incoming.put("passwordCipher", DatavSourceSecrets.MASK);

        JSONObject sealed = DatavSourceSecrets.seal(incoming, null, cipher);

        assertThat(sealed.containsKey("passwordCipher")).isFalse();
    }

    private static JSONObject maskedCopy(JSONObject source) {
        JSONObject copy = new JSONObject();
        source.keySet().forEach(k -> copy.put(k, DatavSourceSecrets.MASK));
        return copy;
    }

    @Test
    @DisplayName("表单不带凭据字段时，库里已有的密文必须原样留下")
    void carriesUntouchedCredentials() {
        JSONObject previous = new JSONObject();
        previous.put("passwordCipher", "enc:real");
        JSONObject headerCiphers = new JSONObject();
        headerCiphers.put("Authorization", "enc:Bearer real");
        previous.put(DatavSourceSecrets.HEADER_CIPHERS, headerCiphers);

        JSONObject incoming = new JSONObject();
        incoming.put("url", "https://api.example.com");
        incoming.put("timeoutMs", 4000);

        JSONObject sealed = DatavSourceSecrets.seal(incoming, previous, cipher);

        assertThat(sealed.getString("passwordCipher")).isEqualTo("enc:real");
        assertThat(sealed.getJSONObject(DatavSourceSecrets.HEADER_CIPHERS).getString("Authorization"))
                .isEqualTo("enc:Bearer real");
    }

    @Test
    @DisplayName("主密钥没配就拒绝保存凭据，绝不退化成明文")
    void failsClosedWithoutMasterKey() {
        JSONObject incoming = new JSONObject();
        incoming.put("password", "p@ss");

        assertThatThrownBy(() -> DatavSourceSecrets.seal(incoming, null, new FakeCipher(false)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("主密钥未配置");
    }

    @Test
    @DisplayName("出口只给掩码，且不改原对象")
    void masksEverySecretOnTheWayOut() {
        JSONObject stored = new JSONObject();
        stored.put("url", "https://api.example.com");
        stored.put("passwordCipher", "enc:p@ss");
        JSONObject headers = new JSONObject();
        headers.put("Authorization", "enc:Bearer x");
        stored.put("headers", headers);

        JSONObject masked = DatavSourceSecrets.mask(stored);

        assertThat(masked.getString("passwordCipher")).isEqualTo(DatavSourceSecrets.MASK);
        assertThat(masked.getJSONObject("headers").getString("Authorization")).isEqualTo(DatavSourceSecrets.MASK);
        assertThat(masked.getString("url")).isEqualTo("https://api.example.com");
        // 原对象必须完好：它是取数链路正在用的那份配置
        assertThat(stored.getString("passwordCipher")).isEqualTo("enc:p@ss");
    }

    @Test
    @DisplayName("取用前还原明文，包括头里的凭据")
    void opensForUse() {
        JSONObject stored = new JSONObject();
        stored.put("url", "https://api.example.com");
        stored.put("passwordCipher", "enc:p@ss");
        JSONObject headerCiphers = new JSONObject();
        headerCiphers.put("Authorization", "enc:Bearer t");
        stored.put(DatavSourceSecrets.HEADER_CIPHERS, headerCiphers);

        JSONObject opened = DatavSourceSecrets.open(stored, cipher);

        assertThat(opened.getString("password")).isEqualTo("p@ss");
        assertThat(opened.containsKey("passwordCipher")).isFalse();
        assertThat(opened.getJSONObject("headers").getString("Authorization")).isEqualTo("Bearer t");
        assertThat(opened.containsKey(DatavSourceSecrets.HEADER_CIPHERS)).isFalse();
    }

    @Test
    @DisplayName("解密失败不泄漏密文，只提示重新填写")
    void decryptFailureDoesNotLeakCipher() {
        JSONObject stored = new JSONObject();
        stored.put("passwordCipher", "garbage-not-a-cipher");

        assertThatThrownBy(() -> DatavSourceSecrets.open(stored, cipher))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("重新填写")
                .hasMessageNotContaining("garbage-not-a-cipher");
    }

    @Test
    @DisplayName("是否有凭据判定，用于「未配置主密钥就别碰这个源」")
    void detectsSecretPresence() {
        assertThat(DatavSourceSecrets.hasSecrets(null)).isFalse();
        JSONObject plain = new JSONObject();
        plain.put("url", "https://x");
        assertThat(DatavSourceSecrets.hasSecrets(plain)).isFalse();
        JSONObject withHeader = new JSONObject();
        JSONObject headers = new JSONObject();
        headers.put("Authorization", "Bearer x");
        withHeader.put("headers", headers);
        assertThat(DatavSourceSecrets.hasSecrets(withHeader)).isTrue();
    }

    @Test
    @DisplayName("空白凭据表示「这个源没有密码」，不该触发 fail-closed")
    void blankSecretMeansNoCredential() {
        // 外部库的空密码实例是真实形态；前端总会带 password 键，
        // 若把它当凭据去加密，未配主密钥的部署连一次保存都做不了
        JSONObject incoming = new JSONObject();
        incoming.put("host", "10.0.0.1");
        incoming.put("password", "");

        JSONObject sealed = DatavSourceSecrets.seal(incoming, new JSONObject(), new FakeCipher(false));

        assertThat(sealed.getString("host")).isEqualTo("10.0.0.1");
        assertThat(sealed).doesNotContainKey("password");
        assertThat(sealed).doesNotContainKey("passwordCipher");
    }

    @Test
    @DisplayName("显式清空密码不会被掩码还原循环填回旧密文")
    void clearedSecretStaysCleared() {
        JSONObject previous = new JSONObject();
        previous.put("passwordCipher", "old-real-cipher");
        JSONObject incoming = new JSONObject();
        // 编辑态表单同时回传掩码后的密文键与用户清空后的空密码
        incoming.put("password", "");
        incoming.put("passwordCipher", DatavSourceSecrets.MASK);

        JSONObject sealed = DatavSourceSecrets.seal(incoming, previous, cipher);

        assertThat(sealed).doesNotContainKey("passwordCipher");
        assertThat(sealed).doesNotContainKey("password");
    }

    @Test
    @DisplayName("没动密码时掩码回传仍沿用库里密文，与「清空」区分开")
    void maskedEchoKeepsExistingCipher() {
        JSONObject previous = new JSONObject();
        previous.put("passwordCipher", "old-real-cipher");
        JSONObject incoming = new JSONObject();
        incoming.put("host", "10.0.0.1");
        incoming.put("password", DatavSourceSecrets.MASK);

        JSONObject sealed = DatavSourceSecrets.seal(incoming, previous, cipher);

        assertThat(sealed.getString("passwordCipher")).isEqualTo("old-real-cipher");
    }
}
