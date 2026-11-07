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
 * AI 生成物台账：会话造了什么、逐个撤销的依据。
 * 撤销语义按对象分：表单 → 改名释放唯一索引并逻辑删；报表/仪表盘 → 删图表并刷数据集；
 * 流程 → 仅删未发布版本；应用 → 仅删空应用。平台尚无 AI 运行时，
 * sessionId 先取来源标识（编排层接入后为会话 id，人工操作记 manual）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "system_ai_artifact", autoResultMap = true)
public class AiArtifactLedger extends BaseEntity {
    private static final long serialVersionUID = 1L;

    public static final String TYPE_FORM = "FORM";
    public static final String TYPE_REPORT = "REPORT";
    public static final String TYPE_DASHBOARD = "DASHBOARD";
    public static final String TYPE_FLOW = "FLOW";
    public static final String TYPE_APP = "APP";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 生成会话标识（AI 编排层会话 id；人工操作记 manual） */
    private String sessionId;

    /** FORM|REPORT|DASHBOARD|FLOW|APP */
    private String objectType;

    /** 生成物主键（dynamic_form.id / report.code 等） */
    private String objectId;

    /** 生成物编码（table_code/report_code 等，展示与定位用） */
    private String code;

    /** Y/N：已撤销 */
    private String rolledBack;

    /** 撤销结果摘要（撤销失败原因/跳过原因，人工处理提示） */
    private String rollbackNote;

    private String organizationId;
}
