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

package com.privernal.dynamic.advanced.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外链回执码摘要 → 数据行映射（§5.3-7）。
 * 「提交后允许修改」的回执码是每条提交各一份的凭据，故不能放 dynamic_form_share 单列；
 * 服务端只存摘要，按 (shareId, receiptDigest) 定位可改的数据行，禁止可枚举 ID 作回执码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dynamic_share_receipt", autoResultMap = true)
public class DynamicShareReceipt extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String departmentId;

    private String organizationId;

    /** 关联 dynamic_form_share.id */
    private String shareId;

    /** 回执码 SHA-256 摘要，原文不落库 */
    private String receiptDigest;

    /** 回执码对应的数据行 ID */
    private String dataId;
}
