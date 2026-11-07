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

package com.privernal.common.wx.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.html.HttpClient;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.wx.constant.WeiXinConstant;
import com.privernal.common.wx.dto.MiniProgramDTO;
import com.privernal.common.wx.service.WorkWxService;
import com.privernal.common.wx.util.AccessTokenUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
public class WorkWxServiceImpl implements WorkWxService {

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String MSG_SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send";
    private static final long TOKEN_EXPIRE_MS = 7_100_000L;
    private static final String ERR_TOKEN_INVALID = "40014";
    private static final String ERR_TOKEN_EXPIRED = "42001";
    private static final String ERR_SUCCESS = "0";

    private volatile String token;
    private volatile long tokenTimestamp;

    @Override
    public CommonResult<Object> sendWxMessage(String appId, String appSecret, String templateId,
                                              MiniProgramDTO miniProgramDTO, List<String> openIds,
                                              JSONObject jsonData) {
        String currentToken = getOrRefreshToken(appId, appSecret);

        JSONObject msgTemplate = buildMessageTemplate(templateId, miniProgramDTO, jsonData);

        boolean allSuccess = true;
        String lastCode = "";
        for (String openId : openIds) {
            JSONObject msgBody = new JSONObject(msgTemplate);
            msgBody.put("touser", openId);

            JSONObject response = sendSingleMessage(currentToken, msgBody);
            lastCode = response.getString("errcode");

            if (!ERR_SUCCESS.equals(lastCode)) {
                if (isTokenError(lastCode)) {
                    currentToken = refreshToken(appId, appSecret);
                    response = sendSingleMessage(currentToken, msgBody);
                    lastCode = response.getString("errcode");
                    if (!ERR_SUCCESS.equals(lastCode)) {
                        allSuccess = false;
                    }
                } else {
                    allSuccess = false;
                }
            }
        }

        return allSuccess ? CommonResult.ok(lastCode, "成功") : CommonResult.fail(lastCode, "失败");
    }

    private String getOrRefreshToken(String appId, String appSecret) {
        long now = System.currentTimeMillis();
        if (!StringUtils.hasLength(token) || now - tokenTimestamp > TOKEN_EXPIRE_MS) {
            token = refreshToken(appId, appSecret);
            tokenTimestamp = System.currentTimeMillis();
        }
        return token;
    }

    private synchronized String refreshToken(String appId, String appSecret) {
        String url = TOKEN_URL + "?grant_type=client_credential&appid=" + appId + "&secret=" + appSecret;
        String result = HttpUtil.get(url);
        log.info("get token result: {}", result);
        JSONObject obj = JSONObject.parseObject(result);
        String newToken = obj.getString("access_token");
        this.token = newToken;
        this.tokenTimestamp = System.currentTimeMillis();
        return newToken;
    }

    private JSONObject buildMessageTemplate(String templateId, MiniProgramDTO miniProgramDTO, JSONObject jsonData) {
        JSONObject msg = new JSONObject();
        msg.put("template_id", templateId);
        msg.put("data", jsonData);
        if (miniProgramDTO != null && StringUtils.hasLength(miniProgramDTO.getAppid())) {
            msg.put("miniprogram", miniProgramDTO);
        }
        return msg;
    }

    private JSONObject sendSingleMessage(String currentToken, JSONObject msgBody) {
        String responseMsg = HttpRequest.post(MSG_SEND_URL + "?access_token=" + currentToken)
                .body(JSONObject.toJSONString(msgBody))
                .execute()
                .body();
        JSONObject reMsg = JSONObject.parseObject(responseMsg);
        log.info("post result params: {} {} {}", currentToken, reMsg, msgBody);
        return reMsg;
    }

    private boolean isTokenError(String code) {
        return ERR_TOKEN_INVALID.equals(code) || ERR_TOKEN_EXPIRED.equals(code);
    }

    @Override
    public List<JSONObject> getDeptList(String accessToken) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        HashMap<String, Object> params = new HashMap<>();
        params.put("access_token", accessToken);
        try {
            String result = HttpClient.getWithHeaders(WeiXinConstant.DEPT_GET_URL, params, headers);
            log.info("getDeptList result: {}", result);
            JSONObject jsonObject = JSON.parseObject(result);
            if (jsonObject.getIntValue("errcode") == 0) {
                return jsonObject.getJSONArray("department").toList(JSONObject.class);
            }
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
        return List.of();
    }

    @Override
    public List<JSONObject> findDeptUserList(String deptId, String accessToken) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        HashMap<String, Object> params = new HashMap<>();
        params.put("access_token", accessToken);
        params.put("department_id", deptId);
        try {
            String result = HttpClient.getWithHeaders(WeiXinConstant.DEPT_USER_GET_URL, params, headers);
            log.info("findDeptUserList result: {}", result);
            JSONObject jsonObject = JSON.parseObject(result);
            if (jsonObject.getIntValue("errcode") == 0) {
                return jsonObject.getJSONArray("userlist").toList(JSONObject.class);
            }
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
        return List.of();
    }

    @Override
    public String getAccessToken(String appKey, String appSecret) {
        try {
            return AccessTokenUtil.getAccessToken(appKey, appSecret);
        } catch (Exception err) {
            log.error("getAccessToken error: {}", err.getMessage());
        }
        return null;
    }

    @Override
    public String findOpenIdByUserId(String userId, String accessToken) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("userid", userId);
        try {
            String result = HttpClient.post(WeiXinConstant.GET_OPENID_URL + "?access_token=" + accessToken, params);
            log.info("findOpenIdByUserId result: {}", result);
            JSONObject jsonObject = JSON.parseObject(result);
            if (jsonObject.getIntValue("errcode") == 0) {
                return jsonObject.getString("openid");
            }
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
        return null;
    }

    @Override
    public JSONObject findUserDetailByUserId(String userId, String accessToken) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        HashMap<String, Object> params = new HashMap<>();
        params.put("access_token", accessToken);
        params.put("userid", userId);
        try {
            String result = HttpClient.getWithHeaders(WeiXinConstant.USER_GET_URL, params, headers);
            log.info("findUserDetailByUserId result: {}", result);
            JSONObject jsonObject = JSON.parseObject(result);
            if (jsonObject.getIntValue("errcode") == 0) {
                return jsonObject;
            }
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
        return null;
    }
}