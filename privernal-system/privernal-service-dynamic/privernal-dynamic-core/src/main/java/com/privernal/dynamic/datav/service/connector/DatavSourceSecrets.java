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
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.api.ISecretCipherService;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

/**
 * 外部数据源配置里的凭据处理（M3.3 剩余口径）。
 * <p>
 * 三条硬规则：
 * <ul>
 *   <li><b>不落明文</b>：保存时把 {@code password}/{@code token}/{@code Authorization} 这类键
 *       换成 {@code xxxCipher}，用 {@link ISecretCipherService} 加密；主密钥未配置就直接报错，
 *       绝不退化成明文或摘要（那是接口注释里的 fail-closed 约束）。</li>
 *   <li><b>出口不回显</b>：列表与详情接口只给掩码。密文也不下发——下发密文等于把
 *       「能不能离线爆破主密钥」这件事交给每一个有查询权限的人。</li>
 *   <li><b>掩码回写不许覆盖真值</b>：表单把掩码原样提交回来时，沿用库里已有的密文，
 *       否则用户改个超时时间就把密钥清成了 {@code ********}。</li>
 * </ul>
 * 静态方法都不碰 Spring，可直接单测；加解密由调用方把 cipher 传进来。
 *
 * @author saperliu
 */
public final class DatavSourceSecrets {

    /** 界面上统一显示的掩码，也是「这项没改」的哨兵 */
    public static final String MASK = "********";

    /** 密文键后缀：password → passwordCipher */
    public static final String CIPHER_SUFFIX = "Cipher";

    /** 请求头里的凭据另存一处，头名本身可能任意，不能靠固定后缀表达 */
    public static final String HEADER_CIPHERS = "headerCiphers";

    private static final List<String> SECRET_KEYS = List.of(
            "password", "passwd", "token", "secret", "authorization", "apikey", "api-key",
            "accesskey", "access_key", "appsecret", "app_secret", "refreshtoken", "clientsecret"
    );

    private DatavSourceSecrets() {
    }

    public static boolean isSecretKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(CIPHER_SUFFIX.toLowerCase(Locale.ROOT))) {
            // 已经是密文键，本身不该再被当成凭据加密一遍
            return false;
        }
        return SECRET_KEYS.stream().anyMatch(lower::contains);
    }

    public static boolean isMasked(String value) {
        return MASK.equals(StringUtils.trimToEmpty(value));
    }

    /**
     * 给接口下发用：所有凭据（含密文）一律换成掩码，返回新对象不改原值。
     */
    public static JSONObject mask(JSONObject sourceConfig) {
        if (sourceConfig == null) {
            return null;
        }
        JSONObject masked = JSONObject.parseObject(sourceConfig.toJSONString());
        for (Map.Entry<String, Object> entry : masked.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSecretKey(key) || key.endsWith(CIPHER_SUFFIX)) {
                masked.put(key, MASK);
                continue;
            }
            if (HEADER_CIPHERS.equals(key) && value instanceof JSONObject) {
                ((JSONObject) value).keySet().forEach(k -> masked.getJSONObject(HEADER_CIPHERS).put(k, MASK));
                continue;
            }
            if ("headers".equals(key) && value instanceof JSONObject) {
                JSONObject headers = (JSONObject) value;
                for (String header : new ArrayList<>(headers.keySet())) {
                    if (isSecretKey(header)) {
                        headers.put(header, MASK);
                    }
                }
            }
        }
        return masked;
    }

    /**
     * 保存前：明文凭据换成密文键，并保留提交里没有真值的项（掩码或空缺）所对应的旧密文。
     *
     * @param incoming 表单提交上来的配置
     * @param existing 库里已有的配置，可为 null（新建）
     */
    public static JSONObject seal(JSONObject incoming, JSONObject existing, ISecretCipherService cipher) {
        if (incoming == null) {
            return null;
        }
        JSONObject sealed = JSONObject.parseObject(incoming.toJSONString());
        JSONObject previous = existing == null ? new JSONObject() : existing;
        // 本次被显式清空的密文键：后面的掩码还原循环不能再把库里的旧密文填回来
        Set<String> clearedCipherKeys = new HashSet<>();

        for (String key : new ArrayList<>(sealed.keySet())) {
            if (!isSecretKey(key)) {
                continue;
            }
            Object plain = sealed.remove(key);
            String cipherKey = key + CIPHER_SUFFIX;
            if (plain == null || StringUtils.isBlank(String.valueOf(plain))) {
                // 空白 = 「这个源没有凭据」（外部库的空密码实例是真实存在的形态），直接丢键。
                // 不丢的话，未配置主密钥的部署连一次保存都做不了——因为 fail-closed 会拒。
                sealed.remove(cipherKey);
                clearedCipherKeys.add(cipherKey);
                continue;
            }
            if (isMasked(String.valueOf(plain))) {
                // 没改：沿用库里那份密文，丢了就是连不上而只表现为「数据源突然报错」
                Object old = previous.get(cipherKey);
                if (old != null) {
                    sealed.put(cipherKey, old);
                }
                continue;
            }
            sealed.put(cipherKey, requireEncrypt(cipher, String.valueOf(plain), key));
        }

        // 出口把密文键也掩码了（见 mask），表单原样回传时若不在此还原，
        // 真密文就会被 "********" 覆盖——而下一次失败只会表现为「数据源连不上」
        for (String key : new ArrayList<>(sealed.keySet())) {
            if (!key.endsWith(CIPHER_SUFFIX) || !isMasked(sealed.getString(key))) {
                continue;
            }
            if (clearedCipherKeys.contains(key)) {
                continue;
            }
            Object old = previous.get(key);
            if (old == null) {
                sealed.remove(key);
            } else {
                sealed.put(key, old);
            }
        }

        // headerCiphers 也可能被整体掩码回传，且不一定伴随 headers，必须在无条件路径上还原
        JSONObject carriedHeaderCiphers = sealed.getJSONObject(HEADER_CIPHERS);
        if (carriedHeaderCiphers != null) {
            JSONObject previousHeaderCiphers = previous.getJSONObject(HEADER_CIPHERS);
            for (String header : new ArrayList<>(carriedHeaderCiphers.keySet())) {
                if (!isMasked(carriedHeaderCiphers.getString(header))) {
                    continue;
                }
                Object old = previousHeaderCiphers == null ? null : previousHeaderCiphers.get(header);
                if (old == null) {
                    carriedHeaderCiphers.remove(header);
                } else {
                    carriedHeaderCiphers.put(header, old);
                }
            }
            if (carriedHeaderCiphers.isEmpty()) {
                sealed.remove(HEADER_CIPHERS);
            }
        }

        // 表单常常不携带凭据字段（HTTP 配置面板只提交 url/method/headers/超时）。
        // 此时不能理解为「把凭据删掉」——否则用户改一次超时时间就把密钥弄丢了，
        // 而且症状是下一次取数才报连不上。只有显式回传掩码或空白才表示要动它，前面已处理。
        for (Map.Entry<String, Object> entry : previous.entrySet()) {
            String key = entry.getKey();
            if (sealed.containsKey(key) || !(key.endsWith(CIPHER_SUFFIX) || HEADER_CIPHERS.equals(key))) {
                continue;
            }
            // 这条「表单没带就沿用」的路径与上面的「显式清空」只隔一个键是否留在 sealed 里：
            // 清空时那个键已被移出，所以必须在这里也跳过，否则旧密文会被原样搬回来
            if (clearedCipherKeys.contains(key)) {
                continue;
            }
            sealed.put(key, entry.getValue());
        }

        JSONObject headers = sealed.getJSONObject("headers");
        if (headers != null) {
            JSONObject headerCiphers = sealed.getJSONObject(HEADER_CIPHERS);
            if (headerCiphers == null) {
                headerCiphers = new JSONObject();
                JSONObject oldHeaderCiphers = previous.getJSONObject(HEADER_CIPHERS);
                if (oldHeaderCiphers != null) {
                    headerCiphers.putAll(oldHeaderCiphers);
                }
                sealed.put(HEADER_CIPHERS, headerCiphers);
            }
            for (String header : new ArrayList<>(headers.keySet())) {
                if (!isSecretKey(header)) {
                    continue;
                }
                String value = headers.getString(header);
                if (isMasked(value)) {
                    // 掩码表示不动，其余值一律搬进密文桶
                    headers.remove(header);
                    continue;
                }
                headerCiphers.put(header, requireEncrypt(cipher, value, header));
                headers.remove(header);
            }
            // 同理：库里已有但这次没提交的头凭据要保留
            JSONObject previousHeaderCiphers = previous.getJSONObject(HEADER_CIPHERS);
            if (previousHeaderCiphers != null) {
                for (String header : previousHeaderCiphers.keySet()) {
                    if (!headerCiphers.containsKey(header)) {
                        headerCiphers.put(header, previousHeaderCiphers.get(header));
                    }
                }
            }
            if (headerCiphers.isEmpty()) {
                sealed.remove(HEADER_CIPHERS);
            }
        }
        return sealed;
    }

    /**
     * 连接器实际发请求前用：把密文还原成明文并放回原来的位置。
     * <p>
     * 返回值只在内存里存活，禁止落库、禁止进日志——明文一旦进日志就等于把加密白做了一半。
     */
    public static JSONObject open(JSONObject stored, ISecretCipherService cipher) {
        if (stored == null) {
            return null;
        }
        JSONObject opened = JSONObject.parseObject(stored.toJSONString());
        for (String key : new ArrayList<>(opened.keySet())) {
            if (!key.endsWith(CIPHER_SUFFIX) || isSecretKey(key)) {
                continue;
            }
            String plainName = key.substring(0, key.length() - CIPHER_SUFFIX.length());
            opened.put(plainName, requireDecrypt(cipher, opened.getString(key), plainName));
            opened.remove(key);
        }
        JSONObject headerCiphers = opened.getJSONObject(HEADER_CIPHERS);
        if (headerCiphers != null) {
            JSONObject headers = opened.getJSONObject("headers");
            if (headers == null) {
                headers = new JSONObject();
                opened.put("headers", headers);
            }
            for (Map.Entry<String, Object> entry : headerCiphers.entrySet()) {
                headers.put(entry.getKey(), requireDecrypt(cipher, String.valueOf(entry.getValue()), entry.getKey()));
            }
            opened.remove(HEADER_CIPHERS);
        }
        return opened;
    }

    /** 库里是否存在任何凭据（用于「主密钥没配就别碰这个源」的前置判断） */
    public static boolean hasSecrets(JSONObject sourceConfig) {
        if (sourceConfig == null) {
            return false;
        }
        if (sourceConfig.keySet().stream().anyMatch(k -> k.endsWith(CIPHER_SUFFIX) || isSecretKey(k))) {
            return true;
        }
        JSONObject headers = sourceConfig.getJSONObject("headers");
        return headers != null && headers.keySet().stream().anyMatch(DatavSourceSecrets::isSecretKey);
    }

    private static String requireEncrypt(ISecretCipherService cipher, String plain, String what) {
        if (cipher == null || !cipher.isConfigured()) {
            throw new ServiceException("凭据主密钥未配置，无法保存「" + what + "」：请配置 openapi.secret-master-key");
        }
        return cipher.encrypt(plain);
    }

    private static String requireDecrypt(ISecretCipherService cipher, String text, String what) {
        if (cipher == null || !cipher.isConfigured()) {
            throw new ServiceException("凭据主密钥未配置，无法使用「" + what + "」");
        }
        try {
            return cipher.decrypt(text);
        } catch (Exception e) {
            // 主密钥轮换过或数据被挪库时会走到这里；不回带密文，避免日志里出现可离线爆破的材料
            throw new ServiceException("凭据解密失败，请重新填写「" + what + "」");
        }
    }
}
