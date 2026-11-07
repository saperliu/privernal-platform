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

package com.privernal.common.rocketmq.constant;

/**
 * @author saperliu
 * @version 1.0
 * @date 2022/01/09 11:42
 * @return
 */
public class RocketMQConstants {
    /**
     * 时间
     */
    public static final String DELAY_TIMES = "times";

    /**
     * 原始的topic
     */
    public static final String ORIGINAL_TOPIC = "original_topic";

    /**
     * 原始的tag
     */
    public static final String ORIGINAL_TAG = "original_tag";

    /**
     * 原始的key
     */
    public static final String ORIGINAL_KEY = "original_key";

    /**
     * 唯一ID
     */
    public static final String ORIGINAL_UUID = "original_UUID";

    /**
     * 代理的topic
     */
    public static final String PROXY_TOPIC          = "DELAY_MESSAGE_TOPIC";

    /**
     * 超时时间
     */
    public static final Long   TIME_OUT             = 60L;
}
