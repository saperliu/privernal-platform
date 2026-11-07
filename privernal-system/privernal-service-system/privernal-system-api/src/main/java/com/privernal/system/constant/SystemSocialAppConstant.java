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

package com.privernal.system.constant;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-02-13 11:38
 * @Description 系统的第三方应用
 */
public class SystemSocialAppConstant {

    /**
     * 企业绑定钉钉的应用的编码
     * 查询钉钉的key和密钥使用，在企业同步组织和人员时使用。
     * 固定配置，根据组织ID来区别不同的组织绑定的钉钉应用
     */
    public static final String SYSTEM_DING_APP_CODE = "perfect_app";

    /**
     * 企业绑定企业微信的应用的编码
     * 查询企业微信的key和密钥使用，在企业同步组织和人员时使用。
     * 固定配置，根据组织ID来区别不同的组织绑定的企业微信应用
     */
    public static final String SYSTEM_WORK_WEIXIN = "work_weixin";

}
