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

package com.privernal.auth.constant;

public class LoginConstant {

    // client_id 与 Team ID 属于部署方凭据，已改为配置项：
    // privernal.apple.client-id / privernal.apple.team-id（见 SystemLoginService）

    //AUD(固定的)
    public static final String REVOKE_AUD = "https://appleid.apple.com";

    //生成或刷新token的URL
    public static final String AUTH_TOKEN_URL = "https://appleid.apple.com/auth/token";

    //撤销用户token请求url
    public static final String AUTH_REVOKE_URL = "https://appleid.apple.com/auth/revoke";

    //获取公共密钥的请求url
    public static final String AUTH_KEYS_URL = "https://appleid.apple.com/auth/keys";

}
