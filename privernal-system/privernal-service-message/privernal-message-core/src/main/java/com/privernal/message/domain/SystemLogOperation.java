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

package com.privernal.message.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 操作日志对象 system_log_operation
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 操作日志的实体
 */
@TableName(value = "system_log_operation", autoResultMap = true)
@Data
public class SystemLogOperation extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 模块名称
     */
    private String name;
    /**
     * 操作类型;操作类型（0其它 1新增 2修改 3删除）
     */
    private String operationType;
    /**
     * 方法名称
     */
    private String method;
    /**
     * 请求方式
     */
    private String requestMethod;
    /**
     * 业务类型;0其它 1后台用户 2手机端用户
     */
    private String businessType;
    /**
     * 请求URL
     */
    private String operationUrl;
    /**
     * 主机地址
     */
    private String operationIp;
    /**
     * 操作地点
     */
    private String operationLocation;
    /**
     * 请求参数
     */
    private String operationParam;
    /**
     * 返回参数
     */
    private String operationResult;
    /**
     * 操作状态;操作状态（0正常 1异常）
     */
    private Long status;
    /**
     * 操作人员;操作人名称
     */
    private String operationPerson;
    /**
     * 操作消息
     */
    private String message;
    /**
     * 组织ID
     */
    private String organizationId;
    /**
     * 部门
     */
    private String departmentId;

}
