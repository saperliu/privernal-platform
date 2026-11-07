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

import com.privernal.common.core.constant.Constants;
import com.privernal.common.web.domain.CommonResult;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端请求上下文工具。本工程是 Spring MVC 单体，这里统一从
 * {@link RequestContextHolder} 取当前线程绑定的 request/response，
 * 并约定「脱离请求上下文调用时静默降级为 null」，方便定时任务、
 * 异步回调等无请求场景直接复用同一套取参代码。
 *
 * @author saperliu
 */
@Slf4j
public class ServletUtils
{
    // ===== 当前请求上下文 =====

    /**
     * 当前线程绑定的 Servlet 上下文；无请求（非 Web 线程）时返回 null。
     */
    public static ServletRequestAttributes getRequestAttributes()
    {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes attributes1 ? attributes1 : null;
    }

    public static HttpServletRequest getRequest()
    {
        ServletRequestAttributes attributes = getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    public static HttpServletResponse getResponse()
    {
        ServletRequestAttributes attributes = getRequestAttributes();
        return attributes == null ? null : attributes.getResponse();
    }

    public static HttpSession getSession()
    {
        return getRequest().getSession();
    }

    // ===== 参数取值（宽松语义）=====

    public static String getParameter(String name)
    {
        HttpServletRequest request = getRequest();
        return request == null ? null : request.getParameter(name);
    }

    public static String getParameter(String name, String defaultValue)
    {
        String value = getParameter(name);
        return value == null ? defaultValue : value;
    }

    /**
     * 请求参数都是字符串，这里刻意采用「转换失败返回默认值、不抛异常」的宽松语义；
     * 语义基线由 {@code ServletUtilsParameterTest} 锁定。
     */
    public static Integer getParameterToInt(String name)
    {
        return parseIntQuietly(getParameter(name), null);
    }

    public static Integer getParameterToInt(String name, Integer defaultValue)
    {
        return parseIntQuietly(getParameter(name), defaultValue);
    }

    public static Boolean getParameterToBool(String name)
    {
        return parseBoolQuietly(getParameter(name), null);
    }

    public static Boolean getParameterToBool(String name, Boolean defaultValue)
    {
        return parseBoolQuietly(getParameter(name), defaultValue);
    }

    private static Integer parseIntQuietly(String value, Integer defaultValue)
    {
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        try
        {
            return Integer.valueOf(value.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    /**
     * 认定 true 的字面量：true/yes/ok/1；认定 false：false/no/0；其余返回默认值。
     */
    private static Boolean parseBoolQuietly(String value, Boolean defaultValue)
    {
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase())
        {
            case "true", "yes", "ok", "1" -> true;
            case "false", "no", "0" -> false;
            default -> defaultValue;
        };
    }

    // ===== 响应写出 =====

    /**
     * 把一段 JSON 文本写回客户端（HTTP 200 + application/json）。
     * 供过滤器/异常出口在未进入控制器前直接应答。
     */
    public static String renderString(HttpServletResponse response, String string)
    {
        try
        {
            response.setStatus(200);
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().print(string);
        }
        catch (IOException e)
        {
            log.error("渲染响应内容失败", e);
        }
        return null;
    }

    /**
     * 汇总请求头为有序 Map（同名头取首个）。
     */
    public static Map<String, String> getHeaders(HttpServletRequest request)
    {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements())
        {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    public static String urlEncode(String str)
    {
        try
        {
            return URLEncoder.encode(str, Constants.UTF8);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    public static String urlDecode(String str)
    {
        try
        {
            return URLDecoder.decode(str, Constants.UTF8);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    // ===== WebFlux 出口（网关式应答，供少量响应式端点复用）=====

    public static Mono<Void> webFluxResponseWriter(ServerHttpResponse response, Object value)
    {
        return webFluxResponseWriter(response, HttpStatus.OK, value, CommonResult.FAIL);
    }

    public static Mono<Void> webFluxResponseWriter(ServerHttpResponse response, Object value, int code)
    {
        return webFluxResponseWriter(response, HttpStatus.OK, value, code);
    }

    public static Mono<Void> webFluxResponseWriter(ServerHttpResponse response, HttpStatus status, Object value, int code)
    {
        return webFluxResponseWriter(response, MediaType.APPLICATION_JSON_VALUE, status, value, code);
    }

    public static Mono<Void> webFluxResponseWriter(ServerHttpResponse response, String contentType, HttpStatus status, Object value, int code)
    {
        response.setStatusCode(status);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, contentType);
        CommonResult<?> body = CommonResult.fail(code, value.toString());
        DataBuffer buffer = response.bufferFactory().wrap(JSON.toJSONString(body).getBytes());
        return response.writeWith(Mono.just(buffer));
    }
}
