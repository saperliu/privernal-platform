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

package com.privernal.common.wx.constant;

public class WeiXinConstant {

    /**
     * 获取access_token url
     */
    public static final String GET_ACCESS_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";

    /**
     * 根据用户id获取用户详情 url
     */
    public static final String USER_GET_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/get";

    /**
     * 获取部门列表 url
     * 请求方式：GET（HTTPS）
     * 请求地址：https://qyapi.weixin.qq.com/cgi-bin/department/list?access_token=ACCESS_TOKEN&id=ID
     * {
     *    "errcode": 0,
     *    "errmsg": "ok",
     *    "department": [
     *        {
     *            "id": 2,
     *            "name": "广州研发中心",
     * 		   "name_en": "RDGZ",
     * 		   "department_leader":["zhangsan","lisi"],
     *            "parentid": 1,
     *            "order": 10
     *        },
     *        {
     *            "id": 3,
     *            "name": "邮箱产品部",
     * 		   "name_en": "mail",
     * 		   "department_leader":["lisi","wangwu"],
     *            "parentid": 2,
     *            "order": 40
     *        }
     *    ]
     * }
     */
    public static final String DEPT_GET_URL = "https://qyapi.weixin.qq.com/cgi-bin/department/list";

    /**
     * 获取部门下面的用户列表 url
     * 请求方式：GET（HTTPS）
     * 请求地址：https://qyapi.weixin.qq.com/cgi-bin/user/list?access_token=ACCESS_TOKEN&department_id=DEPARTMENT_ID
     * {
     * 	"errcode": 0,
     * 	"errmsg": "ok",
     * 	"userlist": [{
     * 		"userid": "zhangsan",
     * 		"name": "李四",
     * 		"department": [1, 2],
     * 		"order": [1, 2],
     * 		"position": "后台工程师",
     * 		"mobile": "13800000000",
     * 		"gender": "1",
     * 		"email": "zhangsan@gzdev.com",
     * 		"biz_mail":"zhangsan@qyycs2.wecom.work",
     * 		"is_leader_in_dept": [1, 0],
     * 		"direct_leader":["lisi"],
     * 		"avatar": "http://wx.qlogo.cn/mmopen/ajNVdqHZLLA3WJ6DSZUfiakYe37PKnQhBIeOQBO4czqrnZDS79FH5Wm5m4X69TBicnHFlhiafvDwklOpZeXYQQ2icg/0",
     * 		"thumb_avatar": "http://wx.qlogo.cn/mmopen/ajNVdqHZLLA3WJ6DSZUfiakYe37PKnQhBIeOQBO4czqrnZDS79FH5Wm5m4X69TBicnHFlhiafvDwklOpZeXYQQ2icg/100",
     * 		"telephone": "020-123456",
     * 		"alias": "jackzhang",
     * 		"status": 1,
     * 		"address": "广州市海珠区新港中路",
     * 		"english_name" : "jacky",
     * 		"open_userid": "xxxxxx",
     * 		"main_department": 1,
     * 		"extattr": {
     * 			"attrs": [
     * 				                {
     * 					"type": 0,
     * 					"name": "文本名称",
     * 					"text": {
     * 						"value": "文本"
     *                    }
     *                },
     *                {
     * 					"type": 1,
     * 					"name": "网页名称",
     * 					"web": {
     * 						"url": "http://www.test.com",
     * 						"title": "标题"
     *                    }
     *                }
     * 			]
     * 		},
     * 		"qr_code": "https://open.work.weixin.qq.com/wwopen/userQRCode?vcode=xxx",
     * 		"external_position": "产品经理",
     *        	}]
     * }
     */
    public static final String DEPT_USER_GET_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/list";


    /**
     * userid转openid
     * 请求方式：POST（HTTPS）
     * 请求地址： https://qyapi.weixin.qq.com/cgi-bin/user/convert_to_openid?access_token=ACCESS_TOKEN
     * {
     *    "userid": "zhangsan"
     * }
     * {
     *    "errcode": 0,
     *    "errmsg": "ok",
     *    "openid": "oDjGHs-1yCnGrRovBj2yHij5JAAA"
     * }
     */
    public static final String GET_OPENID_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/convert_to_openid";
}
