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

package com.privernal.common.core.utils.ip;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.net.NetUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.privernal.common.core.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 的提取与内网判定。
 *
 * <p>提取结果会进登录日志与操作日志的来源字段，因此只接受形如合法 IPv4/IPv6 的值：
 * 代理头里的任意字符串一律丢弃并回落到 {@code getRemoteAddr()}，
 * 避免客户端自行构造 {@code X-Forwarded-For} 来污染审计字段。</p>
 *
 * <p>需要说明的是，任何基于请求头的取法都无法真正防伪造——只有把服务部署在可信反向代理之后，
 * 并由该代理覆写 {@code X-Forwarded-For} 时，这里的值才可信。</p>
 *
 * @author saperliu
 */
public class IpUtils
{
    /**
     * 代理头的探测顺序，沿用平台既有约定（X-Forwarded-For 优先，其次各家中间件）。
     */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "X-Real-IP"
    };

    /** IPv6 回环地址的展开写法 */
    private static final String IPV6_LOOPBACK = "0:0:0:0:0:0:0:1";

    private IpUtils()
    {
    }

    /**
     * 取客户端 IP：按 {@link #IP_HEADERS} 顺序探测，多层代理时取逗号分隔的第一段；
     * 探测结果不是合法 IP 时回落 {@code getRemoteAddr()}，IPv6 回环归一化为 {@code 127.0.0.1}。
     *
     * @return 客户端 IP；request 为 null 时返回 null
     */
    public static String getIpAddr(HttpServletRequest request)
    {
        if (request == null)
        {
            return null;
        }
        String ip = JakartaServletUtil.getClientIP(request, IP_HEADERS);
        if (!isIp(ip))
        {
            ip = request.getRemoteAddr();
        }
        return IPV6_LOOPBACK.equals(ip) ? "127.0.0.1" : ip;
    }

    /**
     * 是否内网/回环地址：RFC 1918（10/8、172.16/12、192.168/16）与 127/8。
     *
     * @return 非法或空值一律返回 false，不再被当作内网
     */
    public static boolean internalIp(String ip)
    {
        if (!isIp(ip))
        {
            return false;
        }
        return NetUtil.isInnerIP(ip) || ip.startsWith("127.");
    }

    /**
     * 是否为字面上合法的 IPv4 或 IPv6 地址。
     */
    public static boolean isIp(String ip)
    {
        return StringUtils.isNotEmpty(ip) && (Validator.isIpv4(ip) || Validator.isIpv6(ip));
    }
}
