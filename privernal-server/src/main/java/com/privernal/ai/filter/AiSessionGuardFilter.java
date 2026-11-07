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

package com.privernal.ai.filter;

import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.domain.SystemAiSessionLog;
import com.privernal.system.service.SystemAiSessionLogService;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * AI 会话守门 Filter（发布权兜底 = 服务端拒绝，不是"等角色授权"）。
 * <p>
 * 识别 X-AI-Session 请求头（编排层/MCP 的全部请求都会带）：命中的请求按路径规则拒绝
 * 二档（publish/rollback/编辑生效定义/terminate/rotate）与三档（删除/角色/用户/菜单/组织/凭证）
 * 类接口，返回结构化 403 并留 DENIED 审计；其余放行并留 ALLOW 审计。
 * 未带该头的请求（人走 UI）零影响。
 * <p>
 * 依据（为什么必须服务端兜底，四条代码证据见 skill §0.2.2）：组织管理员持 *:*:*、
 * 发布链自动授菜单按钮权限、流程发布链无授权环节、DDL 隐式提交不可回滚——
 * 角色授权只解决可见性，不构成发布闸门。
 */
@Slf4j
public class AiSessionGuardFilter implements Filter {

    public static final String HEADER_SESSION = "X-AI-Session";

    private final SystemAiSessionLogService sessionLogService;

    /** 二档：发布/回滚/生效定义编辑（含脚本资产发布——发布纪律归人） */
    private static final Rule[] DENY_RULES = {
            new Rule("PUT", "^/back/dynamic/form/edit$", "编辑生效定义（二档）：请由人在界面操作"),
            new Rule(null, "^/back/dynamic/form/publish/[^/]+$", "发布表单版本（二档）：请由人在会话产物面板点发布"),
            new Rule(null, "^/back/dynamic/form/rollback/[^/]+$", "回滚表单版本（二档）：请由人在界面操作"),
            new Rule(null, "^/back/flow/definition/(publish|unpublish|active|unactive)/[^/]+$", "流程定义发布/启停（二档）：请由人在流程设计器操作"),
            new Rule(null, "^/back/flow/form/version/(publish|rollback|migrate-from-legacy)", "流程表单版本发布/回滚（二档，会切 currentFlag 影响在办实例）：请由人在界面操作"),
            // 兜底：任何其余 publish/rollback 形态（未来新增控制器不再逐个登记）
            new Rule(null, "/publish(/[^/]+)?$", "发布类操作（二档）对 AI 会话关闭：请由人在界面操作"),
            new Rule(null, "/rollback(/[^/]+)?$", "回滚类操作（二档）对 AI 会话关闭：请由人在界面操作"),
            new Rule(null, "/terminate(/|$)", "终止实例/任务（二档）：请由人在界面操作"),
            new Rule(null, "/rotate(/|$)", "凭证/外链轮换（二档）：请由人在界面操作"),
            // 三档：删除类（全部 DELETE + 显式 delete 路径）
            new Rule("DELETE", "^/(back|dbs)/", "删除类接口（三档）对 AI 会话关闭"),
            new Rule(null, "/delete(/|$)", "删除类接口（三档）对 AI 会话关闭"),
            new Rule(null, "^/back/dynamic/(form|dataset)/[^/]*/(delete|remove)$", "删除（三档）对 AI 会话关闭"),
            // 三档：管理面写操作（角色/用户/菜单/组织/应用编辑/凭证/数据源）
            // 路径按控制器实际拼写登记：userGroup/roleGroup 是驼峰，旧规则写的 user-group/role-group
            // 打不中，等于这两个管理面对 AI 会话意外放行（2026-10-08 校正）
            new Rule(null, "^/back/system/(role|menu|organization|position|userGroup|roleGroup)(/.*)?$", "组织管理面（三档）对 AI 会话关闭"),
            new Rule(null, "^/back/system/user/(save|edit|remove|add|update|import|reset)", "用户管理（三档）对 AI 会话关闭"),
            new Rule(null, "^/back/system/appInfo/(edit|remove|save)", "应用管理写操作（三档）对 AI 会话关闭"),
            new Rule(null, "^/back/system/appPackage/import", "应用包导入（三档）对 AI 会话关闭"),
            new Rule(null, "^/back/system/(openapi|webhook|sso|oauth)", "集成凭证面（三档）对 AI 会话关闭"),
            new Rule(null, "^/back/dynamic/database/", "数据源管理（三档）对 AI 会话关闭"),
            new Rule(null, "^/back/(dbs/open/(database|table)/(add|update))", "库表直管（三档）对 AI 会话关闭"),
    };

    /** 工具面白名单：生成物台账撤销是 B7 工具面的合法能力（rollback_artifact），不受 rollback 兜底规则影响 */
    private static final Pattern ALLOW_ARTIFACT_ROLLBACK =
            Pattern.compile("^/back/dynamic/ai/artifact/[^/]+/rollback$");

    /**
     * 一档只读白名单：流程生成必须把办理人落到真实角色（skill §4.4 的 role:{roleId}），
     * 而 role 控制器整体登记在三档名单里——只放行读语义（list / list/dto），写操作仍拦。
     * 拿不到 roleId 的后果不是报错而是「节点无人可办、流程空跑」，所以这条通道是流程生成的前置。
     */
    private static final Pattern ALLOW_ROLE_READ =
            Pattern.compile("^/back/system/role/(list|list/dto)$");

    /** 白名单集合：命中即跳过 DENY 规则（留 ALLOW 审计） */
    private static final Pattern[] ALLOW_RULES = {ALLOW_ARTIFACT_ROLLBACK, ALLOW_ROLE_READ};

    public AiSessionGuardFilter(SystemAiSessionLogService sessionLogService) {
        this.sessionLogService = sessionLogService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        String sessionId = httpRequest.getHeader(HEADER_SESSION);
        if (sessionId == null || sessionId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();
        String denyReason = matchDeny(method, uri);
        if (denyReason != null) {
            log.warn("AI 会话请求被拒绝 session={} {} {} : {}", sessionId, method, uri, denyReason);
            sessionLogService.logRequest(sessionId, SystemAiSessionLog.ACTION_DENIED, method, uri, denyReason);
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write(JSON.toJSONString(
                    CommonResult.fail(HttpServletResponse.SC_FORBIDDEN, "AI 会话无权执行该操作：" + denyReason)));
            return;
        }
        sessionLogService.logRequest(sessionId, SystemAiSessionLog.ACTION_ALLOW, method, uri, null);
        chain.doFilter(request, response);
    }

    private String matchDeny(String method, String uri) {
        for (Pattern allow : ALLOW_RULES) {
            if (allow.matcher(uri).matches()) {
                return null;
            }
        }
        for (Rule rule : DENY_RULES) {
            if (rule.method != null && !rule.method.equalsIgnoreCase(method)) {
                continue;
            }
            if (rule.pattern.matcher(uri).find()) {
                return rule.reason;
            }
        }
        return null;
    }

    private record Rule(String method, Pattern pattern, String reason) {
        Rule(String method, String regex, String reason) {
            this(method, Pattern.compile(regex), reason);
        }
    }
}
