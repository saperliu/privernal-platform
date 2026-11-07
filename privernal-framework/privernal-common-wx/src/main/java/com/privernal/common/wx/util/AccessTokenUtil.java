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

package com.privernal.common.wx.util;


import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.html.HttpClient;
import com.privernal.common.wx.constant.WeiXinConstant;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

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
     * {
     * "errcode": 0,
     * "errmsg": "ok",
     * "access_token": "accesstoken000001",
     * "expires_in": 7200
     * }
     *
     * @param corpid
     * @param corpsecret
     * @return
     */
    public static String getAccessToken(String corpid, String corpsecret) {

        try {
            HashMap<String, Object> params = new HashMap<>();
            params.put("corpid", corpid);
            params.put("corpsecret", corpsecret);

            log.info("getAccessToken, corpid={}", corpid);


            String result = HttpClient.get(WeiXinConstant.GET_ACCESS_TOKEN_URL, params);

            log.info("getAccessToken  result: {}  ", result);

            JSONObject jsonObject = JSONObject.parseObject(result);
            if (jsonObject.getIntValue("errcode") != 0) {
                log.error("获取access_token失败：{}", jsonObject.getString("errmsg"));
                return null;
            }
            return jsonObject.getString("access_token");

        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }
}
