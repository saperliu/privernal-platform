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
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 模型配置（system_ai_config，公网模型口径）。
 * <p>
 * <b>公网模型为默认支持的形态</b>：baseUrl 指向公网 LLM 服务商 API；内网模型是内网部署的可选形态
 * （intranet_flag=Y），不再是硬性要求。原"是否允许出网"fail-closed 开关已随 v1.4 作废——
 * 管理员显式填写公网 baseUrl 即视为同意该数据流向，合规靠配置页明示数据流向（{@link #DATA_FLOW_NOTICE}）。
 * apiKey 只存 AES/GCM 密文（{@code ISecretCipherService}，主密钥 openapi.secret-master-key），
 * 查询接口只回 prefix/last4 掩码，绝不回明文或密文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "system_ai_config", autoResultMap = true)
public class SystemAiConfig extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 配置页/接口固定携带的数据流向明示文案（v1.4：配置即授权，须向管理员展示） */
    public static final String DATA_FLOW_NOTICE =
            "使用公网模型时，需求文本与业务数据将传输至所配模型服务商；"
                    + "仅当配置内网模型（intranet_flag=Y）时数据不出内网。保存配置即视为同意上述数据流向。";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 组织（每组织一份生效配置） */
    private String organizationId;

    /** 服务商标识（如 ZHIPU/OPENAI/CUSTOM，仅作展示与路由提示） */
    private String provider;

    /** 模型 API 地址：公网模型填公网 API，内网模型填内网推理地址 */
    private String baseUrl;

    /** apiKey 的 AES/GCM 密文（ISecretCipherService 加密），查询接口不回 */
    private String apiKeyCipher;

    /** 掩码展示用：明文前 6 位 */
    private String apiKeyPrefix;

    /** 掩码展示用：明文后 4 位 */
    private String apiKeyLast4;

    private String model;

    private java.math.BigDecimal temperature;

    /** 单会话 token 上限（超限告警/截断，NULL=不限） */
    private Integer sessionTokenLimit;

    /** 是否内网模型 Y/N（Y=数据不出内网，交付话术可如实承诺） */
    private String intranetFlag;

    /** Y/N（本组织 AI 能力总开关） */
    private String enabled;
}
