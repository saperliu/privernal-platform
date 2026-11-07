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

package com.privernal.dynamic.filter;

import com.privernal.common.web.domain.FilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * form的列的数据对象 dynamic_form_column
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description form的列的数据的过滤器
 */
@Tag(name = "DynamicFormColumnFilterDTO", description = "form的列的数据对应的查询过滤器")
@Data
public class DynamicFormColumnFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 所属部门
     */
    @Schema(description = "所属部门")
    private String departmentId;


    /**
     * 所属组织
     */
    @Schema(description = "所属组织")
    private String organizationId;


    /**
     * 数据标题
     */
    @Schema(description = "数据标题")
    private String title;


    /**
     * 状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消
     */
    @Schema(description = "状态;业务对象的状态,0:草稿,1审批完成或已提交,2:流程运行中,3:被取消")
    private Long status;


    /**
     * 表单ID
     */
    @Schema(description = "表单ID")
    private String formId;


    /**
     * 字段编码,数据库表的列表
     */
    @Schema(description = " 字段编码,数据库表的列表")
    private String filedCode;


    /**
     * 字段名称
     */
    @Schema(description = "字段名称")
    private String filedName;


    /**
     * 字段类型
     */
    @Schema(description = "字段类型")
    private String filedType;


    /**
     * 长度
     */
    @Schema(description = "长度")
    private Long length;


    /**
     * 小数点位数
     */
    @Schema(description = "小数点位数")
    private Long scale;


    /**
     * 排序
     */
    @Schema(description = "排序")
    private Long sort;


    /**
     * 默认值
     */
    @Schema(description = "默认值")
    private String defaultValue;


    /**
     * 列表是否显示
     */
    @Schema(description = "列表是否显示")
    private Long showList;


    /**
     * 查询是否显示
     */
    @Schema(description = "查询是否显示")
    private Long showQuery;

    /**
     * 敏感字段标记 Y/N
     */
    @Schema(description = "敏感字段标记")
    private String sensitive;

    /**
     * 业务唯一键标记 Y/N
     */
    @Schema(description = "业务唯一键标记")
    private String uniqueFlag;

    /**
     * 前端组件的ID
     */
    @Schema(description = "前端组件的ID")
    private String componentId;
}
