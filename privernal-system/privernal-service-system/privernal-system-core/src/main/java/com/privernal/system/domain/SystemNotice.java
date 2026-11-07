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

package com.privernal.system.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 通知对象 system_notice
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 通知的实体
 */
@Data
public class SystemNotice extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 公告标题
     */
    private String noticeTitle;
    /**
     * 公告类型
     */
    private String noticeType;
    /**
     * 内容简介
     */
    private String content;
    /**
     * 状态;（0正常 1停止）
     */
    private Long status;
    /**
     * 详情链接
     */
    private String detailUrl;
    /**
     * 组织
     */
    private String organizationId;
    /**
     * 部门
     */
    private String departmentId;

}
