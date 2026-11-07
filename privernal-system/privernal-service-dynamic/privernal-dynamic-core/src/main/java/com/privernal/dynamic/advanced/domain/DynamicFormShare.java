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

import java.util.Date;

/**
 * 表单外链配置。token 只存 SHA-256 摘要，原文仅创建时一次性返回（-1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dynamic_form_share", autoResultMap = true)
public class DynamicFormShare extends BaseEntity {
    private static final long serialVersionUID = 1L;

    public static final String TYPE_FILL = "FILL";
    public static final String TYPE_VIEW = "VIEW";

    public static final String SCOPE_ANON_PUBLIC = "ANON_PUBLIC";
    public static final String SCOPE_LOGIN_ALL = "LOGIN_ALL";
    public static final String SCOPE_LOGIN_DEPT = "LOGIN_DEPT";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String departmentId;

    private String organizationId;

    /** 关联 dynamic_form.id */
    private String formId;

    /** FILL填写/VIEW查看 */
    private String shareType;

    /** SHA-256(token) 摘要，原文不落库 */
    private String tokenDigest;

    /** 展示用尾号 */
    private String tokenTail;

    /** 填写链固定 ANON_PUBLIC；查看链 LOGIN_ALL/LOGIN_DEPT/ANON_PUBLIC */
    private String scopeType;

    /** LOGIN_DEPT 时部门 id 数组 JSON */
    private String scopeDeptIds;

    /** 访问密码 bcrypt，可空 */
    private String password;

    /** 有效期截止，NULL 永久（前后端口径 yyyy-MM-dd HH:mm:ss） */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expireTime;

    /** 提交总次数上限，NULL 不限 */
    private Integer submitLimit;

    /** 每人限填次数（匿名按 IP+UA 指纹，尽力防重） */
    private Integer perPersonLimit;

    /** 行为验证码 Y/N（P0 预留未接） */
    private String captcha;

    /** 提交后允许凭回执码修改 Y/N */
    private String editableAfterSubmit;

    /** 外链提交代理用户，空=表单创建人 */
    private String proxyUserId;

    /** 启用 Y/N（停用即 404） */
    private String enabled;

    /** 累计提交数（填写链） */
    private Integer submitCount;

    /**
     * 完整 token 的 AES/GCM 密文（链接回显，密钥复用 openapi.secret-master-key）。
     * 存量旧行/密钥未配置时为空 → 取链直接报错并提示 rotate（不迁移不兼容）。
     */
    private String linkCipher;

    /**
     * 免登预填白名单：字段编码 JSON 数组。
     * 免登 URL 的 ?p_{field}= 参数仅白名单字段接受，其余忽略（防篡改面）。
     */
    private String prefillFields;
}
