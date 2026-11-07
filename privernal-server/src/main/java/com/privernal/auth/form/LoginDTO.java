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

package com.privernal.auth.form;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.Serializable;

/**
 * 系统基础服务
 *
 * @author saperliu
 * @date 2022-04-10
 * @description 系统基础服务
 */
@Tag(name = "登录信息", description = "登录信息DTO")
public class LoginDTO implements Serializable
{
    /**
     * 用户名
     */
    @Schema(description = "用户名")
    private String loginName;

    /**
     * 手机号
     */
    @Schema(description = "手机号")
    private String phone;

    /**
     * 用户密码
     */
    @Schema(description = "用户密码")
    private String password;

    /**
     * F4 挑战登录：/auth/login/challenge 下发的一次性 nonce 编号；携带时密码字段=SHA512(nonce+线摘要)
     */
    @Schema(description = "挑战登录 nonce 编号")
    private String nonceId;

    /**
     * 验证码登录时的验证码
     */
    @Schema(description = "验证码")
    private String phoneCode;

    // 用户登录的小程序的编码
    @Schema(description = "用户登录的小程序的编码不能为空")
    private String productCode;


    // 用户登录凭证（有效期五分钟）。开发者需要在开发者服务器后台调用 auth.code2Session，使用 code 换取 openid 和 session_key 等信息
    @Schema(description = "微信小程序登录 code不能为空")
    private String code;


    // 加密算法的初始向量，详见 用户数据的签名验证和加解密
    @Schema(description = "微信小程序登录 iv不能为空")
    private String iv;


    // 包括敏感数据在内的完整用户信息的加密数据，详见 用户数据的签名验证和加解密
    @Schema(description = "微信小程序登录 encryptedData不能为空")
    private String encryptedData;

    /**
     * APP登录
     * 1.APP登录
     * 2.PC端登录
     */
    @Schema(description = "APP登录")
    private int loginType;


    /**
     * 验证码的ID
     */
    @Schema(description = "验证码的ID")
    private String id;

    /**
     * APP登录
     */
    public static final int LOGIN_TYPE_APP = 1;
    /**
     * PC端登录
     */
    public static final int LOGIN_TYPE_PC = 2;

    public String getLoginName()
    {
        return loginName;
    }

    public void setLoginName(String loginName)
    {
        this.loginName = loginName;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getNonceId()
    {
        return nonceId;
    }

    public void setNonceId(String nonceId)
    {
        this.nonceId = nonceId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhoneCode() {
        return phoneCode;
    }

    public void setPhoneCode(String phoneCode) {
        this.phoneCode = phoneCode;
    }

    public int getLoginType() {
        return loginType;
    }

    public void setLoginType(int loginType) {
        this.loginType = loginType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
