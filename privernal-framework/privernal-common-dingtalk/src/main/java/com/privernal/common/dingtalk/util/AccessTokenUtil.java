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

package com.privernal.common.dingtalk.util;


import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.dingtalk.constant.DingTalkConstant;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.taobao.api.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

/**
 * 获取access_token工具类
 */
@Slf4j
public class AccessTokenUtil {
    /**
     * 在使用accessToken时，请注意：
     * accessToken的有效期为7200秒（2小时），有效期内重复获取会返回相同结果并自动续期，过期后获取会返回新的accessToken。
     * 开发者需要缓存accessToken，用于后续接口的调用。因为每个应用的accessToken是彼此独立的，所以进行缓存时需要区分应用来进行存储。
     * 不能频繁调用接口，否则会受到频率拦截。
     *
     * @param appKey
     * @param appSecret
     * @return
     */
    public static String getAccessToken(String appKey, String appSecret) {

        DefaultDingTalkClient client = new DefaultDingTalkClient(DingTalkConstant.GET_ACCESS_TOKEN_URL);
        OapiGettokenRequest request = new OapiGettokenRequest();

        request.setAppkey(appKey);
        request.setAppsecret(appSecret);
        request.setHttpMethod(HttpMethod.GET.name());

        try {
            OapiGettokenResponse response = client.execute(request);
            if (response.isSuccess()) {
                return response.getAccessToken();
            } else {
                throw new ServiceException(response.getErrorCode(), response.getErrmsg());
            }
        } catch (ApiException e) {
            log.error("调用钉钉获取access_token接口失败", e);
            throw new ServiceException(e.getErrCode(), e.getErrMsg());
        }
    }
}
