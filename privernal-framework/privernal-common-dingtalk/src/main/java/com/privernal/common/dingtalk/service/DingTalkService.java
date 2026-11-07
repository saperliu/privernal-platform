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

package com.privernal.common.dingtalk.service;

import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponseBody;
import com.aliyun.dingtalkworkflow_1_0.models.GetProcessInstanceResponse;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.request.OapiV2UserListRequest;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse;
import com.dingtalk.api.response.OapiV2UserGetResponse;
import com.dingtalk.api.response.OapiV2UserListResponse;

import java.util.List;

public interface DingTalkService {

    /**
     * 获取第三方的登录标识
     *
     * @param appKey    key
     * @param appSecret 密钥
     * @return
     */
    public GetAccessTokenResponseBody getAccessToken(String appKey, String appSecret);

    /**
     * 获取用户信息
     * @param authCode 授权码
     * @return 用户信息
     */
    public OapiV2UserGetResponse.UserGetResponse getUserInfo(String authCode,String appKey, String appSecret);


    /**
     * 查询审批表单信息
     * @param processInstanceId 实例ID
     * @param accessToken 令牌
     * @throws Exception
     */
    public GetProcessInstanceResponse findApproveFormInfo(String processInstanceId, String accessToken);


    /**
     * 查询部门下的用户列表
     * @param accessToken 授权码
     * @return
     */
    public OapiV2UserListResponse.PageResult findDeptUserList(OapiV2UserListRequest req, String accessToken);

    /**
     * 查询部门
     * @param accessToken 授权码
     * @return
     */
    public List<OapiV2DepartmentListsubResponse.DeptBaseResponse> findDeptList(OapiV2DepartmentListsubRequest req, String accessToken);
}
