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

/**
 * 用户对象 system_user
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的实体
 */
@Data
@TableName(value = "system_user_base", autoResultMap = true)
public class SystemUser extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 登录名
     */
    private String loginName;
    /**
     * 昵称
     */
    private String nickName;
    /**
     * 用户类型（sys 系统用户）
     */
    private String userType;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 手机
     */
    private String phone;
    /**
     * 密码
     */
    private String password;
    /**
     * F4 挑战-应答凭证：AES-GCM 封存的线摘要（SHA512(明文口令)），主密钥在环境变量，
     * 库里不再留可重放明文摘要；挑战登录用。旧 bcrypt 列过渡期保留双写
     */
    private String passwordV2;
    /**
     * 性别;用户性别（0男 1女 2未知）
     */
    private String gender;
    /**
     * 头像
     */
    private String avatar;
    /**
     * 状态;状态（0正常 1停用）
     */
    private Long status;
    /**
     * 身份证号;身份证号
     */
    private String identityNum;
    /**
     * 所属部门ID
     */
    private String organizationId;
    /**
     * 所属部门ID;所属部门ID[主部门:必须存在]
     */
    private String departmentId;

    /**
     * 保存用户的服务组织时传参使用
     */
    private transient Long dataScope;
}
