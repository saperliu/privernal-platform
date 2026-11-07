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

package com.privernal.dynamic.datav.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.privernal.common.mybatis.type.JSONObjectTypeHandler;
import com.privernal.dynamic.dba.annotation.Column;
import com.privernal.dynamic.dba.annotation.Table;
import com.privernal.dynamic.dba.constants.MySqlTypeConstant;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 大屏历史版本 dynamic_datav_version
 * <p>
 * 只存快照不回滚依赖：回滚动作是把某个版本的 schema 覆盖回主表，版本表本身不可变。
 *
 * @author saperliu
 */
@Table(name = "dynamic_datav_version")
@TableName(value = "dynamic_datav_version", autoResultMap = true)
@Data
public class DynamicDatavVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Column(name = "id", type = MySqlTypeConstant.VARCHAR, isKey = true, length = 36)
    private String id;

    @Column(name = "organization_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String organizationId;

    @Column(name = "screen_code", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String screenCode;

    /**
     * 同一屏内递增
     */
    @Column(name = "version_no", type = MySqlTypeConstant.INT)
    private Integer versionNo;

    @TableField(typeHandler = JSONObjectTypeHandler.class)
    @Column(name = "schema_json", type = MySqlTypeConstant.MEDIUMTEXT)
    private JSONObject schemaJson;

    @Column(name = "screen_name", type = MySqlTypeConstant.VARCHAR, length = 100)
    private String screenName;
}
