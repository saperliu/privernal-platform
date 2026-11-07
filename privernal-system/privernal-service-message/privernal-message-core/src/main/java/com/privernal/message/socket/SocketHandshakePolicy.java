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

package com.privernal.message.socket;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.HandshakeData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.BiPredicate;

/**
 * socket.io 的握手准入策略。
 * <p>
 * <b>为什么必须卡在握手期而不是 {@code @OnConnect}</b>：netty-socketio 的 {@code @OnConnect}
 * 回调发生在连接已经建立之后。原来的实现只打了一行日志就把鉴权失败的客户端留在原地，
 * 于是任何人都能用假 token 反复连上来，每条连接都占着一个 worker 会话与登记表里的一格——
 * 想堆死这个端口不需要别的攻击。握手期返回 false 会让 HTTP 升级直接失败，连接根本不建立。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class SocketHandshakePolicy {

    /** 与前端 useWebsocket 的 query 参数名同一份口径 */
    public static final String USER_ID_PARAM = "userId";

    public static final String TOKEN_PARAM = "token";

    /**
     * 纯函数形式的判定：把「怎么算合法」与 sa-token、HandshakeData 这些全局/框架依赖分开，
     * 否则这条安全判据就只能靠起服务去验，而安全判据恰恰最需要被测试钉住。
     */
    public static boolean permits(String userId, String token, BiPredicate<String, String> loginCheck) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(token)) {
            return false;
        }
        return loginCheck.test(userId, token);
    }

    /**
     * 握手是否放行。
     * <p>
     * 只调一次 {@code checkTokenLogin} 就够：它比的是「这个 token 现在解析出来的登录号
     * 是不是这个 userId」，已经隐含了 token 有效与账号在线两件事，再叠一个 {@code isLogin}
     * 只是多一次 Redis 往返。
     */
    public AuthorizationResult authorize(HandshakeData data) {
        String userId = data.getSingleUrlParam(USER_ID_PARAM);
        String token = data.getSingleUrlParam(TOKEN_PARAM);
        boolean allowed = permits(userId, token, SecurityUtils::checkTokenLogin);
        if (!allowed) {
            // 不打 token：日志里出现凭据是另一类事故
            log.warn("socket 握手被拒：userId={} 缺失或与 token 不匹配", userId);
            return AuthorizationResult.FAILED_AUTHORIZATION;
        }
        return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;
    }
}
