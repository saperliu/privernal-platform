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
import com.privernal.dynamic.dba.annotation.Column;
import com.privernal.dynamic.dba.annotation.Table;
import com.privernal.dynamic.dba.constants.MySqlTypeConstant;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 大屏外链分享 dynamic_datav_share
 * <p>
 * 链接 = {@code /datav-share?sc={shareCode}&sign={hmac}}。签名固定 per-share：
 * HMAC-SHA256(openapi.secret-master-key, "datav-share:" + shareCode)。
 * 有效期与 referer 白名单存在行里，改期/吊销改行即可，不需要重新分发链接。
 * <p>
 * 免登取数以 {@code organizationId} 定位业务库并 bypass 部门行级范围
 * （与 OpenApi 机器调用同口径），因此数据范围 = 分享所属组织的全部数据。
 *
 * @author saperliu
 */
@Table(name = "dynamic_datav_share")
@TableName("dynamic_datav_share")
@Data
public class DynamicDatavShare extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Column(name = "id", type = MySqlTypeConstant.VARCHAR, isKey = true, length = 36)
    private String id;

    @Column(name = "department_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String departmentId;

    @Column(name = "organization_id", type = MySqlTypeConstant.VARCHAR, length = 36)
    private String organizationId;

    /**
     * 128bit 随机不透明值，对外即链接标识；可以安全进日志
     */
    @Column(name = "share_code", type = MySqlTypeConstant.VARCHAR, length = 64)
    private String shareCode;

    /**
     * 被分享的大屏编码（创建时要求已发布；schema 端点返回发布快照而非实时草稿）
     */
    @Column(name = "screen_code", type = MySqlTypeConstant.VARCHAR, length = 64)
    private String screenCode;

    /**
     * 失效时间；NULL 表示长期有效
     */
    @Column(name = "expire_time", type = "datetime")
    private Date expireTime;

    /**
     * 允许嵌入/访问的来源域名白名单（逗号分隔，按域名后缀匹配）；NULL/空表示不限制
     */
    @Column(name = "referer_whitelist", type = MySqlTypeConstant.VARCHAR, length = 900)
    private String refererWhitelist;

    /**
     * Y=有效 N=已禁用。禁用即吊销；不删行以便审计
     */
    @Column(name = "enabled", type = MySqlTypeConstant.CHAR, length = 1)
    private String enabled;
}
