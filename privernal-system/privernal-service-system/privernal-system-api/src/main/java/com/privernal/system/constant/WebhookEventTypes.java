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

import java.util.Arrays;
import java.util.List;

/**
 * Webhook 事件类型码。订阅校验与事件发布共用同一份，避免两侧各写字符串。
 */
public class WebhookEventTypes {

    public static final String DATA_CREATED = "dynamic.data.created";
    public static final String DATA_UPDATED = "dynamic.data.updated";
    public static final String DATA_DELETED = "dynamic.data.deleted";
    public static final String FLOW_STARTED = "flow.instance.started";
    public static final String FLOW_FINISHED = "flow.instance.finished";
    public static final String FLOW_REJECTED = "flow.instance.rejected";

    private static final List<String> ALL = Arrays.asList(
            DATA_CREATED, DATA_UPDATED, DATA_DELETED, FLOW_STARTED, FLOW_FINISHED, FLOW_REJECTED);

    private WebhookEventTypes() {
    }

    public static List<String> all() {
        return ALL;
    }

    public static boolean isKnown(String eventType) {
        return eventType != null && ALL.contains(eventType);
    }
}
