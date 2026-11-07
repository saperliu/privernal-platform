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

package com.privernal.common.dingtalk.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.dingtalk.constant.DingTalkConstant;
import com.privernal.common.dingtalk.service.DingTalkService;
import com.privernal.common.dingtalk.util.AccessTokenUtil;
import com.alibaba.fastjson2.JSON;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponseBody;
import com.aliyun.dingtalkworkflow_1_0.models.GetProcessInstanceResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.*;
import com.dingtalk.api.response.*;
import com.taobao.api.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理
 */
@Slf4j
@Service
public class DingTalkServiceImpl implements DingTalkService {


    @Override
    public OapiV2UserGetResponse.UserGetResponse getUserInfo(String authCode,String appKey,String appSecret) {

        // 1. 获取access_token
        String accessToken = AccessTokenUtil.getAccessToken(appKey, appSecret);

        // 2. 获取用户ID
        String userId = getUserId(authCode, accessToken);

        // 2. 根据用户ID获取用户详情
        return getOapiV2UserGetResponseByUserId(userId, accessToken);

    }

    /**
     * 根据authCode获取用户ID
     *
     * @param authCode
     * @param accessToken
     * @return
     */
    private String getUserId(String authCode, String accessToken) {
        DingTalkClient client = new DefaultDingTalkClient(DingTalkConstant.GET_USER_INFO_URL);
        OapiV2UserGetuserinfoRequest req = new OapiV2UserGetuserinfoRequest();
        req.setCode(authCode);
        OapiV2UserGetuserinfoResponse oapiV2UserGetuserinfoResponse;
        try {
            oapiV2UserGetuserinfoResponse = client.execute(req, accessToken);
            if (oapiV2UserGetuserinfoResponse.isSuccess()) {
                OapiV2UserGetuserinfoResponse.UserGetByCodeResponse userGetByCodeResponse = oapiV2UserGetuserinfoResponse.getResult();
                return userGetByCodeResponse.getUserid();
            } else {
                throw new ServiceException(oapiV2UserGetuserinfoResponse.getErrorCode(), oapiV2UserGetuserinfoResponse.getErrmsg());
            }

        } catch (ApiException e) {
            log.error("调用钉钉接口失败", e);
            throw new ServiceException(e.getErrCode(), e.getErrMsg());
        }
    }

    /**
     * 根据用户ID获取用户详情
     *
     * @param userId
     * @param accessToken
     * @return
     */
    private OapiV2UserGetResponse.UserGetResponse getOapiV2UserGetResponseByUserId(String userId, String accessToken) {
        DingTalkClient client = new DefaultDingTalkClient(DingTalkConstant.USER_GET_URL);
        OapiV2UserGetRequest req = new OapiV2UserGetRequest();
        req.setUserid(userId);
        req.setLanguage("zh_CN");

        try {
            OapiV2UserGetResponse oapiV2UserGetResponse = client.execute(req, accessToken);
            if (oapiV2UserGetResponse.isSuccess()) {
                return oapiV2UserGetResponse.getResult();
            } else {
                throw new ServiceException(oapiV2UserGetResponse.getErrorCode(), oapiV2UserGetResponse.getErrmsg());
            }
        } catch (ApiException e) {
            log.error("调用钉钉接口失败", e);
            throw new ServiceException(e.getErrCode(), e.getErrMsg());
        }
    }

    /**
     * <b>description</b> :
     * <p>使用 Token 初始化账号Client</p>
     * @return Client
     *
     * @throws Exception
     */
    public static com.aliyun.dingtalkworkflow_1_0.Client createWorkflowClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkworkflow_1_0.Client(config);
    }

    @Override
    public GetProcessInstanceResponse findApproveFormInfo(String processInstanceId, String accessToken) {

        try {
            com.aliyun.dingtalkworkflow_1_0.Client client = DingTalkServiceImpl.createWorkflowClient();
            com.aliyun.dingtalkworkflow_1_0.models.GetProcessInstanceHeaders getProcessInstanceHeaders = new com.aliyun.dingtalkworkflow_1_0.models.GetProcessInstanceHeaders();
            getProcessInstanceHeaders.xAcsDingtalkAccessToken = accessToken;
            com.aliyun.dingtalkworkflow_1_0.models.GetProcessInstanceRequest getProcessInstanceRequest = new com.aliyun.dingtalkworkflow_1_0.models.GetProcessInstanceRequest()
                    .setProcessInstanceId(processInstanceId);
            GetProcessInstanceResponse processInstanceResponse = client.getProcessInstanceWithOptions(getProcessInstanceRequest, getProcessInstanceHeaders, new com.aliyun.teautil.models.RuntimeOptions());

            return processInstanceResponse;
        } catch (TeaException err) {
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                log.error(" TeaException error  {}.  {} ", err.code, err.message);
            }
            log.error(" findApproveFormInfo error  {} ", JSON.toJSONString(err));
        } catch (Exception _err) {
            TeaException err = new TeaException(_err.getMessage(), _err);
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                log.error(" Exception error  {}.  {} ", err.code, err.message);
            }
            log.error(" findApproveFormInfo error  {} ", err.message);
        }
        return null;
    }




    /**
     * 使用 Token 初始化账号Client
     *
     * @return Client
     * @throws Exception
     */
    public static com.aliyun.dingtalkoauth2_1_0.Client createClient() throws Exception {
        Config config = new Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkoauth2_1_0.Client(config);
    }


    public String getToken(String appKey, String appSecret) {
        try {
            DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/gettoken");
            OapiGettokenRequest request = new OapiGettokenRequest();
            request.setAppkey(appKey);
            request.setAppsecret(appSecret);
            request.setHttpMethod("GET");
            OapiGettokenResponse response = client.execute(request);
            return response.getAccessToken();
        } catch (Exception e) {
            log.error(" get token ", e);
        }
        return null;
    }

    @Override
    public GetAccessTokenResponseBody getAccessToken(String appKey, String appSecret) {

        try {
            com.aliyun.dingtalkoauth2_1_0.Client client = DingTalkServiceImpl.createClient();
            com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest getAccessTokenRequest = new com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest()
                    .setAppKey(appKey)
                    .setAppSecret(appSecret);
            GetAccessTokenResponse response = client.getAccessToken(getAccessTokenRequest);
            return response.getBody();
        } catch (TeaException err) {
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                throw new ServiceException(err.message, err.code);
            }
            log.error(" getAccessToken error  {} ", err.message);
        } catch (Exception _err) {
            TeaException err = new TeaException(_err.getMessage(), _err);
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                throw new ServiceException(err.message, err.code);
            }
            log.error(" getAccessToken error  {} ", err.message);
        }
        return null;
    }

    @Override
    public OapiV2UserListResponse.PageResult findDeptUserList(OapiV2UserListRequest req, String accessToken) {

        try {
            DingTalkClient userClient = new DefaultDingTalkClient(DingTalkConstant.DEPT_USER_GET_URL);
            OapiV2UserListResponse rsp = userClient.execute(req, accessToken);
            return rsp.getResult();
        }catch (Exception e){
            log.error(" findDeptUserList error  {} ", e.getMessage());
        }
        return null;
    }

    @Override
    public List<OapiV2DepartmentListsubResponse.DeptBaseResponse> findDeptList(OapiV2DepartmentListsubRequest req, String accessToken) {

        try {
            DingTalkClient deptClient = new DefaultDingTalkClient(DingTalkConstant.DEPT_GET_URL);
            OapiV2DepartmentListsubResponse rsp = deptClient.execute(req, accessToken);
            return rsp.getResult();
        }catch (Exception e){
            log.error(" findDeptList error  {} ", e.getMessage());
        }
        return List.of();
    }
}
