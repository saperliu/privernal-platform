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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP 提取与内网判定的行为基线。
 *
 * <p>这两个方法直接决定登录日志与审计里的「来源 IP」是什么，
 * 所以断言的是应有语义：RFC 1918 才算内网、非法输入不得当成内网、
 * 代理头里的垃圾值不得污染最终结果。</p>
 */
class IpUtilsTest
{
    @Test
    @DisplayName("RFC 1918 与回环地址判为内网")
    void privateRangesAreInternal()
    {
        assertTrue(IpUtils.internalIp("10.0.0.1"));
        assertTrue(IpUtils.internalIp("10.42.7.13"));
        assertTrue(IpUtils.internalIp("172.16.0.1"));
        assertTrue(IpUtils.internalIp("172.31.255.255"));
        assertTrue(IpUtils.internalIp("192.168.1.10"));
        assertTrue(IpUtils.internalIp("127.0.0.1"));
    }

    @Test
    @DisplayName("公网地址判为非内网，含 172.168（不属于 172.16/12）")
    void publicRangesAreNotInternal()
    {
        assertFalse(IpUtils.internalIp("8.8.8.8"), "8.8.8.8 是公网");
        assertFalse(IpUtils.internalIp("223.5.5.5"), "223.5.5.5 是公网");
        assertFalse(IpUtils.internalIp("172.168.1.1"), "172.168 超出 172.16.0.0/12，不是内网");
        assertFalse(IpUtils.internalIp("172.32.0.1"), "172.32 超出 172.16.0.0/12，不是内网");
    }

    @Test
    @DisplayName("空值与非法地址不得判为内网")
    void invalidInputIsNotInternal()
    {
        assertFalse(IpUtils.internalIp("not-an-ip"), "非法字符串不应被当成内网");
        assertFalse(IpUtils.internalIp(""));
        assertFalse(IpUtils.internalIp(null));
        assertFalse(IpUtils.internalIp("999.999.999.999"));
    }

    @Test
    @DisplayName("无代理头时取 remoteAddr")
    void fallsBackToRemoteAddr()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        assertEquals("203.0.113.9", IpUtils.getIpAddr(request));
    }

    @Test
    @DisplayName("X-Forwarded-For 最优先，且多层代理取第一段")
    void forwardedForWinsAndSplits()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2, 192.168.1.1");
        assertEquals("203.0.113.7", IpUtils.getIpAddr(request));
    }

    @Test
    @DisplayName("代理头为 unknown 或空时继续向后找，最后回落 remoteAddr")
    void unknownHeaderIsSkipped()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "203.0.113.9");
        assertEquals("203.0.113.9", IpUtils.getIpAddr(request));

        MockHttpServletRequest onlyUnknown = new MockHttpServletRequest();
        onlyUnknown.setRemoteAddr("203.0.113.10");
        onlyUnknown.addHeader("X-Forwarded-For", "unknown");
        assertEquals("203.0.113.10", IpUtils.getIpAddr(onlyUnknown));
    }

    @Test
    @DisplayName("代理头里的垃圾值不得作为客户端 IP 返回，必须回落 remoteAddr")
    void forgedHeaderDoesNotPolluteResult()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.11");
        request.addHeader("X-Forwarded-For", "<script>or-arbitrary-string</script>");
        assertEquals("203.0.113.11", IpUtils.getIpAddr(request), "非法的代理头值不能进审计字段");
    }

    @Test
    @DisplayName("IPv6 回环归一化为 127.0.0.1")
    void ipv6LoopbackNormalized()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");
        assertEquals("127.0.0.1", IpUtils.getIpAddr(request));
    }

    @Test
    @DisplayName("request 为 null 时返回 null，不抛异常")
    void nullRequestReturnsNull()
    {
        assertNull(IpUtils.getIpAddr(null));
    }
}
