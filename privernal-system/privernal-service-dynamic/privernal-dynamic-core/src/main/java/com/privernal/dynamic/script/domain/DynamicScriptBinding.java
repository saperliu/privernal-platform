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

package com.privernal.dynamic.script.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 脚本挂点绑定（<b>独立表，绝不进 metaData</b>——
 * 设计器保存链会整体覆盖 metaData，写进 metaData 的绑定会在用户下一次保存表单时被冲掉且不留痕迹）。
 * <p>
 * 一行 = 某表单在某挂点引用某资产一个版本；param_mapping 把资产参数映射到表单字段、
 * 上下文变量（{{currentUser}} / {{now}}）或常量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dynamic_script_binding", autoResultMap = true)
public class DynamicScriptBinding extends BaseEntity {
    private static final long serialVersionUID = 1L;

    public static final String POINT_SAVE_VALIDATE = "SAVE_VALIDATE";
    public static final String POINT_BACK_SCRIPT = "BACK_SCRIPT";
    public static final String POINT_SCHEDULE = "SCHEDULE";
    public static final String POINT_LINKAGE_SCRIPT = "LINKAGE_SCRIPT";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String assetId;

    /** 钉住的资产版本，NULL = 跟随当前发布版 */
    private Integer assetVersion;

    private String formId;

    private String tableCode;

    /** SAVE_VALIDATE|BACK_SCRIPT|SCHEDULE|LINKAGE_SCRIPT */
    private String bindPoint;

    /**
     * 参数映射 JSON：{"资产参数名": "表单字段编码 | {{currentUser}} | {{now}} | 常量"}。
     * 解析顺序：{{…}} 上下文变量 → data 里存在的字段编码 → 常量字符串
     */
    private String paramMapping;

    /** 校验失败时回显给提交人的提示；空则用默认文案 */
    private String failMessage;

    /** 同一挂点多条绑定的执行顺序，小的先执行 */
    private Integer executionOrder;

    /** Y/N */
    private String enabled;

    private String organizationId;

    private String departmentId;
}
