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
 * 字典类型对象 system_dictionary_type
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 字典类型的实体
 */
@Data
public class SystemDictionaryType extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 字典主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 字典名称
     */
    private String dictName;
    /**
     * 字典类型
     */
    private String dictType;
    /**
     * 状态（0正常 1停用）
     */
    private Long status;
    /**
     * 组织
     */
    private String organizationId;
    /**
     * 部门
     */
    private String departmentId;

}
