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

package com.privernal.flow.constant;

/**
 * 流程表单配置常量
 *
 * @author saperliu
 */
public class FlowFormConstants {

    private FlowFormConstants() {
    }

    /**
     * 全局（非节点级）配置的节点编码，对齐旧 wfs 的 workflow_instance_form_field/btn 全局表语义。
     * 节点未配置权限明细时回退到该节点的配置。
     */
    public static final String GLOBAL_NODE_CODE = "global";

    /**
     * 发布状态：草稿
     */
    public static final int PUBLISH_STATUS_DRAFT = 0;

    /**
     * 发布状态：已发布
     */
    public static final int PUBLISH_STATUS_PUBLISHED = 1;

    /**
     * 当前生效发布版标记：是
     */
    public static final String CURRENT_FLAG_YES = "Y";

    /**
     * 当前生效发布版标记：否
     */
    public static final String CURRENT_FLAG_NO = "N";
}
