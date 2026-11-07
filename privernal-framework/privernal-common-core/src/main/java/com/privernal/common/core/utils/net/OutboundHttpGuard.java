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

package com.privernal.common.core.utils.net;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 出站 HTTP 通用闸门（OutboundHttpGuard）：Webhook 投递、OPENAPI 回调、定时任务 HTTP_CALL
 * 等一切"平台替用户发起"的出站请求，在连接前必须过这里——协议白名单 + IP 字面量否决 +
 * DNS 逐地址否决（内网/保留段/链路本地/云元数据）。
 *
 * <p>实现口径与 datav 的 {@code HttpSourceConnector} 一致并共用判定（connector 的
 * {@code isBlocked}/{@code literalAddress} 即委托本类），两处各写一遍迟早漂移成一个能绕过的版本。
 * 注意：java.net.http.HttpClient 无法挂 DNS 回调，本类在发送前解析并否决；
 * 「这里解析一次、连接层再解析一次」理论上存在 DNS rebinding 窗口，
 * datav 连接器通过 OkHttp Dns 钩子消除该窗口，其余出站方接受此残余风险。</p>
 *
 * @author saperliu
 */
public final class OutboundHttpGuard {

    private OutboundHttpGuard() {
    }

    /** IPv4 字面量判定；只匹配四段数字，故意不接受前导零以外的宽松形式 */
    private static final String IPV4_LITERAL = "^(?:\\d{1,3}\\.){3}\\d{1,3}$";

    /**
     * 出站前总闸：解析 URL、校验协议与主机名、否决 IP 字面量与内网解析结果。
     *
     * @param rawUrl  目标地址
     * @param purpose 报错用途说明（如 "Webhook 回调"）
     */
    public static void check(String rawUrl, String purpose) {
        if (StringUtils.isBlank(rawUrl)) {
            throw new ServiceException(purpose + "地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(purpose + "地址无法解析：" + rawUrl);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ServiceException(purpose + "只允许 http/https 协议");
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            throw new ServiceException(purpose + "地址缺少主机名：" + rawUrl);
        }
        // IP 字面量在解析前单独拦：URL 里直接写 127.0.0.1 / 169.254.169.254 是最常见的两种填法
        vetoLiteralAddress(host, purpose);
        vetoResolvedAddresses(host, purpose);
    }

    /** 把「配置里写的本来就是 IP」解析出来；绝不触发 DNS——非字面量一律返回 null */
    public static InetAddress literalAddress(String host) {
        String text = StringUtils.trimToEmpty(host);
        if (text.length() > 2 && text.charAt(0) == '[' && text.charAt(text.length() - 1) == ']') {
            text = text.substring(1, text.length() - 1);
        }
        if (text.isEmpty() || (text.indexOf(':') < 0 && !text.matches(IPV4_LITERAL))) {
            return null;
        }
        try {
            return InetAddress.getByName(text);
        } catch (UnknownHostException e) {
            // 畸形 IP（如 999.1.1.1）不当内网处理，交给后续连接阶段报「解析失败」
            return null;
        }
    }

    public static void vetoLiteralAddress(String host, String purpose) {
        InetAddress address = literalAddress(host);
        if (address != null && isBlocked(address)) {
            throw new ServiceException(purpose + "禁止访问内网/保留地址：" + host);
        }
    }

    /** 解析主机名的全部地址并逐个否决；存在任一内网地址即拒绝（不做部分放行，出站没有重试别的地址的语义） */
    public static void vetoResolvedAddresses(String hostname, String purpose) {
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(hostname);
        } catch (UnknownHostException e) {
            throw new ServiceException(purpose + "地址解析失败：" + hostname);
        }
        List<String> blocked = new ArrayList<>(2);
        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                blocked.add(address.getHostAddress());
            }
        }
        if (!blocked.isEmpty()) {
            throw new ServiceException(purpose + "禁止访问内网/保留地址：" + hostname + " -> " + String.join(", ", blocked));
        }
    }

    /** 内网/保留段/链路本地/云元数据判定（与 datav 连接器共用口径） */
    public static boolean isBlocked(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || isUniqueLocal(address)
                || isMetadata(address);
    }

    private static boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFF) == 0xFC;
    }

    private static boolean isMetadata(InetAddress address) {
        byte[] bytes = address.getAddress();
        // 169.254.169.254 与 100.64.0.0/10（运营商级 NAT，常被云内网复用）
        return bytes.length == 4
                && ((bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254 && (bytes[2] & 0xFF) == 169 && (bytes[3] & 0xFF) == 254
                || (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xFF) >= 64 && (bytes[1] & 0xFF) <= 127);
    }
}
