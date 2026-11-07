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

package com.privernal.common.wx.service;

import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.wx.dto.MiniProgramDTO;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

public interface WorkWxService {

    /**
     * 获取第三方的登录标识
     *
     * @param appKey    key
     * @param appSecret 密钥
     * @return
     */
    public String getAccessToken(String appKey, String appSecret);

    /**
     * 获取用户信息
     *
     * @param accessToken 授权码
     * @return 用户信息
     */
    public List<JSONObject> getDeptList(String accessToken);

    /**
     * 查询部门下的用户列表
     *
     * @param accessToken 授权码
     * @return
     */
    public List<JSONObject> findDeptUserList(String deptId, String accessToken);

    /**
     * 通过userid获取用户的openid
     *
     * @param accessToken 授权码
     * @return
     */
    public String findOpenIdByUserId(String userId, String accessToken);

    /**
     * 通过userid获取用户详情
     *
     * @param accessToken 授权码
     * @return
     */
    public JSONObject findUserDetailByUserId(String userId, String accessToken);

    /**
     * 发送微信模板消息
     *
     * @param appId         微信应用ID
     * @param appSecret     微信应用密钥
     * @param templateId    模板ID
     * @param miniProgramDTO 小程序跳转配置
     * @param openIds       接收者openId列表
     * @param jsonData      模板消息数据
     * @return 发送结果
     */
    CommonResult<Object> sendWxMessage(String appId, String appSecret, String templateId,
                                       MiniProgramDTO miniProgramDTO, List<String> openIds,
                                       JSONObject jsonData);
}