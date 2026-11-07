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

package com.privernal.message.service.impl;

import com.privernal.message.service.AppMessageService;
import com.alibaba.fastjson2.JSON;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.push.model.v20160801.PushRequest;
import com.aliyuncs.push.model.v20160801.PushResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * APP消息的Service接口
 *
 * @author saperliu
 * @date 2023-07-07
 * @Description 系统消息的Service接口
 */
@Slf4j
@Service
public class AppMessageServiceImpl implements AppMessageService {

    protected static DefaultAcsClient client;

    @Value("${app.ios.app-key}")
    private Long iosAppKey;

    @Value("${app.android.app-key}")
    private Long androidAppKey;

    @Value("${app.env}")
    private String env;


    @Override
    public List<PushResponse> pushAppMessage(String title, String subTitle, String body, String[] accountList) {
        PushResponse pushResponse = pushIOSAppMessage(title, subTitle, body, accountList);
        PushResponse pushResponse1 = pushAndroidAppMessage(title, subTitle, body, accountList);
        List<PushResponse> list = Arrays.asList(pushResponse, pushResponse1);
        return list;
    }

    @Override
    public PushResponse pushIOSAppMessage(String title, String subTitle, String body, String[] accountList) {
        PushRequest pushRequest = new PushRequest();
        // 推送目标
        pushRequest.setAppKey(iosAppKey);
        /**
         * 推送目标。可取值：
         *
         * DEVICE：根据设备推送。
         * ACCOUNT：根据账号推送。
         * ALIAS：根据别名推送。
         * TAG：根据标签推送。
         * ALL：推送给全部设备（同一种DeviceType的两次全推的间隔至少为1秒）。
         * 说明对iOS设备全推，会推送给24个月内活跃过但未卸载的设备，一旦APNs（苹果推送服务）接收到推送请求且未返回错误信息即为送达，导致活跃设备数暴增，从而产生大量费用，请您酌情使用。
         * TBD：初始化持续推送，推送目标由后续的ContinuouslyPush接口指定。
         */
        pushRequest.setTarget("ACCOUNT");

        /**
         * 根据Target来设定，多个值使用逗号分隔，超过限制需要分多次推送。
         *
         * Target=DEVICE，值如deviceid1,deviceid2（最多支持1000个）。
         * Target=ACCOUNT，值如account1,account2（最多支持1000个）。
         * Target=ALIAS，值如alias1,alias2（最多支持1000个）。
         * Target=TAG，支持单Tag和多Tag，格式请参见标签格式。
         * Target=ALL，值为ALL。推送给全部已经注册的设备ID
         * Target=TBD，值为TBD。
         */
        pushRequest.setTargetValue(Arrays.toString(accountList));
        // 消息类型 MESSAGE NOTICE
        pushRequest.setPushType("NOTICE");
        // 设备类型 ANDROID iOS ALL.
        pushRequest.setDeviceType("ALL");

        // 推送配置
        pushRequest.setTitle(title); // 消息的标题
        pushRequest.setBody(body); // 消息的内容


        // 推送配置: iOS
        pushRequest.setIOSBadgeAutoIncrement(true);
//        pushRequest.setIOSBadge(5); // iOS应用图标右上角角标
        pushRequest.setIOSSilentNotification(false);//开启静默通知
//        pushRequest.setIOSMusic("default"); // iOS通知声音
        pushRequest.setIOSSubtitle(subTitle);//iOS10通知副标题的内容
        pushRequest.setIOSRemindBody(body);//iOS消息转通知时使用的iOS通知内容，仅当iOSApnsEnv=PRODUCT && iOSRemind为true时有效
//        pushRequest.setIOSNotificationCategory("iOS10 Notification Category");//指定iOS10通知Category
        pushRequest.setIOSMutableContent(true);//是否允许扩展iOS通知内容
        pushRequest.setIOSApnsEnv(env);//iOS的通知是通过APNs中心来发送的，需要填写对应的环境信息。"DEV" : 表示开发环境 "PRODUCT" : 表示生产环境
        pushRequest.setIOSRemind(true); // 消息推送时设备不在线（既与移动推送的服务端的长连接通道不通），则这条推送会做为通知，通过苹果的APNs通道送达一次。注意：离线消息转通知仅适用于生产环境
        pushRequest.setIOSExtParameters("{\"_ENV_\":\"DEV\",\"k2\":\"v2\"}"); //通知的扩展属性(注意 : 该参数要以json map的格式传入,否则会解析出错)
        // 推送配置: Android
//        pushRequest.setAndroidNotifyType("NONE");//通知的提醒方式 "VIBRATE" : 震动 "SOUND" : 声音 "BOTH" : 声音和震动 NONE : 静音
//        pushRequest.setAndroidNotificationBarType(1);//通知栏自定义样式0-100
//        pushRequest.setAndroidNotificationBarPriority(1);//通知栏自定义样式0-100
//        pushRequest.setAndroidOpenType("URL"); //点击通知后动作 "APPLICATION" : 打开应用 "ACTIVITY" : 打开AndroidActivity "URL" : 打开URL "NONE" : 无跳转
//        pushRequest.setAndroidOpenUrl("http://www.aliyun.com"); //Android收到推送后打开对应的url,仅当AndroidOpenType="URL"有效
//        pushRequest.setAndroidActivity("com.alibaba.push2.demo.XiaoMiPushActivity"); // 设定通知打开的activity，仅当AndroidOpenType="Activity"有效

        //设定通知的扩展属性。(注意 : 该参数要以 json map 的格式传入,否则会解析出错)
//        pushRequest.setAndroidExtParameters("{\"k1\":\"android\",\"k2\":\"v2\"}");


//        // 推送控制
//        Date pushDate = new Date(System.currentTimeMillis()); // 30秒之间的时间点, 也可以设置成你指定固定时间
//        String pushTime = ParameterHelper.getISO8601Time(pushDate);
//        pushRequest.setPushTime(pushTime); // 延后推送。可选，如果不设置表示立即推送
//        String expireTime = ParameterHelper.getISO8601Time(new Date(System.currentTimeMillis() + 12 * 3600 * 1000)); // 12小时后消息失效, 不会再发送
//        pushRequest.setExpireTime(expireTime);
        // 离线消息是否保存,若保存, 在推送时候，用户即使不在线，下一次上线则会收到
//        pushRequest.setStoreOffline(true);


        PushResponse pushResponse = null;
        try {
            pushResponse = client.getAcsResponse(pushRequest);
        } catch (ClientException e) {
            log.error(" push message client exception ", e);
        }
        log.info("推送结果：{}  accountList :{}  RequestId: {} ", title, Arrays.toString(accountList), JSON.toJSONString(pushResponse));
        return pushResponse;
    }

    @Override
    public PushResponse pushAndroidAppMessage(String title, String subTitle, String body, String[] accountList) {
        PushRequest pushRequest = new PushRequest();

        // 推送目标
        pushRequest.setAppKey(androidAppKey);
        /**
         * 推送目标。可取值：
         *
         * DEVICE：根据设备推送。
         * ACCOUNT：根据账号推送。
         * ALIAS：根据别名推送。
         * TAG：根据标签推送。
         * ALL：推送给全部设备（同一种DeviceType的两次全推的间隔至少为1秒）。
         * 说明对iOS设备全推，会推送给24个月内活跃过但未卸载的设备，一旦APNs（苹果推送服务）接收到推送请求且未返回错误信息即为送达，导致活跃设备数暴增，从而产生大量费用，请您酌情使用。
         * TBD：初始化持续推送，推送目标由后续的ContinuouslyPush接口指定。
         */
        pushRequest.setTarget("ACCOUNT");

        /**
         * 根据Target来设定，多个值使用逗号分隔，超过限制需要分多次推送。
         *
         * Target=DEVICE，值如deviceid1,deviceid2（最多支持1000个）。
         * Target=ACCOUNT，值如account1,account2（最多支持1000个）。
         * Target=ALIAS，值如alias1,alias2（最多支持1000个）。
         * Target=TAG，支持单Tag和多Tag，格式请参见标签格式。
         * Target=ALL，值为ALL。推送给全部已经注册的设备ID
         * Target=TBD，值为TBD。
         */
        pushRequest.setTargetValue(Arrays.toString(accountList));
        // 消息类型 MESSAGE NOTICE
        pushRequest.setPushType("NOTICE");
        // 设备类型 ANDROID iOS ALL.
        pushRequest.setDeviceType("ALL");

        // 推送配置
        pushRequest.setTitle(title); // 消息的标题
        pushRequest.setBody(body); // 消息的内容


        // 推送配置: iOS
////        pushRequest.setIOSBadge(5); // iOS应用图标右上角角标
//        pushRequest.setIOSSilentNotification(false);//开启静默通知
////        pushRequest.setIOSMusic("default"); // iOS通知声音
//        pushRequest.setIOSSubtitle(subTitle);//iOS10通知副标题的内容
//        pushRequest.setIOSRemindBody(body);//iOS消息转通知时使用的iOS通知内容，仅当iOSApnsEnv=PRODUCT && iOSRemind为true时有效
////        pushRequest.setIOSNotificationCategory("iOS10 Notification Category");//指定iOS10通知Category
//        pushRequest.setIOSMutableContent(true);//是否允许扩展iOS通知内容
//        pushRequest.setIOSApnsEnv(env);//iOS的通知是通过APNs中心来发送的，需要填写对应的环境信息。"DEV" : 表示开发环境 "PRODUCT" : 表示生产环境
//        pushRequest.setIOSRemind(true); // 消息推送时设备不在线（既与移动推送的服务端的长连接通道不通），则这条推送会做为通知，通过苹果的APNs通道送达一次。注意：离线消息转通知仅适用于生产环境
//        pushRequest.setIOSExtParameters("{\"_ENV_\":\"DEV\",\"k2\":\"v2\"}"); //通知的扩展属性(注意 : 该参数要以json map的格式传入,否则会解析出错)
        // 推送配置: Android
        pushRequest.setAndroidNotifyType("BOTH");//通知的提醒方式 "VIBRATE" : 震动 "SOUND" : 声音 "BOTH" : 声音和震动 NONE : 静音
        pushRequest.setAndroidNotificationBarType(2);//通知栏自定义样式0-100
        pushRequest.setAndroidNotificationBarPriority(1);//通Android 通知在通知栏展示时排列位置的优先级。可取值： -2，-1，0，1，2。
        pushRequest.setAndroidOpenType("APPLICATION"); //点击通知后动作 "APPLICATION" : 打开应用 "ACTIVITY" : 打开AndroidActivity "URL" : 打开URL "NONE" : 无跳转
//        pushRequest.setAndroidOpenUrl("http://www.aliyun.com"); //Android收到推送后打开对应的url,仅当AndroidOpenType="URL"有效
//        pushRequest.setAndroidActivity("com.alibaba.push2.demo.XiaoMiPushActivity"); // 设定通知打开的activity，仅当AndroidOpenType="Activity"有效

        pushRequest.setAndroidNotificationChannel("1");
        //设定通知的扩展属性。(注意 : 该参数要以 json map 的格式传入,否则会解析出错)
        pushRequest.setAndroidExtParameters("{\"k1\":\"android\",\"k2\":\"v2\"}");


//        // 推送控制
//        Date pushDate = new Date(System.currentTimeMillis()); // 30秒之间的时间点, 也可以设置成你指定固定时间
//        String pushTime = ParameterHelper.getISO8601Time(pushDate);
//        pushRequest.setPushTime(pushTime); // 延后推送。可选，如果不设置表示立即推送
//        String expireTime = ParameterHelper.getISO8601Time(new Date(System.currentTimeMillis() + 12 * 3600 * 1000)); // 12小时后消息失效, 不会再发送
//        pushRequest.setExpireTime(expireTime);
        // 离线消息是否保存,若保存, 在推送时候，用户即使不在线，下一次上线则会收到
//        pushRequest.setStoreOffline(true);


        PushResponse pushResponse = null;
        try {
            pushResponse = client.getAcsResponse(pushRequest);
        } catch (ClientException e) {
            log.error(" push message client exception ", e);
        }
        log.info("推送结果：{}  accountList :{}  RequestId: {} ", title, Arrays.toString(accountList), JSON.toJSONString(pushResponse));
        return pushResponse;
    }
}
