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

package com.privernal.flow.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 流程表单运行时权限（兼容旧 wfs 的 form-command 前端契约）
 *
 * @author saperliu
 */
@Data
public class FlowFormPermissionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 绑定记录id */
    private String id;

    /** 动态表单编码 */
    private String tableCode;

    /** 表单名称 */
    private String tableName;

    /** 部门ID */
    private String departmentId;

    /** 所属组织 */
    private String organizationId;

    /** 当前用户是否是当前节点审批人 */
    private Boolean haveUser;

    /** 节点字段权限列表 */
    private List<FieldItem> fieldDTOList;

    /** 节点按钮权限列表 */
    private List<BtnItem> btnDTOList;

    /**
     * 字段权限项（命名对齐旧契约：fieldValue/isShow/isEdit/isRequired 为 0/1）
     */
    @Data
    public static class FieldItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 字段编码 */
        private String fieldValue;

        /** 字段名称 */
        private String fieldName;

        /** 是否显示 0否 1是 */
        private Integer isShow;

        /** 是否可编辑 0否 1是 */
        private Integer isEdit;

        /** 是否必填 0否 1是 */
        private Integer isRequired;

        /** 默认值 */
        private String defaultValue;

        /** 字典编码 */
        private String dict;
    }

    /**
     * 按钮权限项
     */
    @Data
    public static class BtnItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 按钮编码（approve/reject/transfer/save 等） */
        private String fieldValue;

        /** 按钮名称 */
        private String fieldName;

        /** 是否显示 0否 1是 */
        private Integer isShow;

        /** 按钮状态（提交前/提交后显示等，预留） */
        private Integer status;

        /** 是否弹窗确认 0否 1是 */
        private Integer showDialog;

        /** 按钮颜色 */
        private String color;
    }
}
