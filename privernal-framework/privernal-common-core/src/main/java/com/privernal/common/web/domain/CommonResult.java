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

package com.privernal.common.web.domain;

import com.privernal.common.core.constant.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.Serializable;

/**
 * 统一响应信封：所有 HTTP 接口的返回载体，JSON 形如
 * {@code {"code":200,"message":"...","data":...}}。
 *
 * <p>code 是与前端约定的<b>业务状态字</b>，不是 HTTP 状态码（HTTP 层恒为 200，由
 * code 驱动前端行为）。当前约定：</p>
 * <ul>
 *   <li>{@link #SUCCESS} 200 —— 成功</li>
 *   <li>{@link #FAIL} 500 —— 业务失败，message 为可展示文案</li>
 *   <li>{@link #AUTH_FAIL} 401 —— 未认证</li>
 *   <li>8888/8889、7777/7778、9999/9998/3333 等登出与令牌刷新字由前端
 *       {@code VITE_SERVICE_*} 环境变量消费，见前端 .env 与 {@code Constants}</li>
 * </ul>
 *
 * <p>静态工厂只覆盖常用形态；需要自定义 code 时使用
 * {@link #fail(int, String)}。字段名 {@code code/message/data} 是对前端的
 * 线格式契约，不可更名。</p>
 *
 * @author saperliu
 */
@Tag(name = "响应结果", description = "统一响应信封")
public class CommonResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务成功字 */
    public static final int SUCCESS = Constants.SUCCESS;

    /** 业务失败字 */
    public static final int FAIL = Constants.FAIL;

    /** 未认证字 */
    public static final int AUTH_FAIL = Constants.AUTH_FAIL;

    /** 业务状态字 */
    @Schema(description = "业务状态字：200 成功；其余取值见 Constants 与前端 VITE_SERVICE_* 约定")
    private int code;

    /** 可展示文案；成功时为「操作成功」类固定语，失败时为面向用户的错误说明 */
    @Schema(description = "提示消息")
    private String message;

    /** 业务载荷；失败时通常为 null */
    @Schema(description = "业务数据")
    private T data;

    /**
     * 附加标识，当前仅图形验证码接口使用：随验证码图片下发轮次 uuid，
     * 前端提交时原样回传用于比对。
     */
    @Schema(description = "附加标识（验证码轮次 uuid）")
    private String id;

    private CommonResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ===== 成功工厂 =====

    public static <T> CommonResult<T> ok() {
        return new CommonResult<>(SUCCESS, "操作成功", null);
    }

    public static <T> CommonResult<T> ok(T data) {
        return new CommonResult<>(SUCCESS, "操作成功", data);
    }

    public static <T> CommonResult<T> ok(T data, String message) {
        return new CommonResult<>(SUCCESS, message, data);
    }

    // ===== 失败工厂 =====

    public static <T> CommonResult<T> fail() {
        return new CommonResult<>(FAIL, "操作失败", null);
    }

    public static <T> CommonResult<T> fail(String message) {
        return new CommonResult<>(FAIL, message, null);
    }

    public static <T> CommonResult<T> fail(T data) {
        return new CommonResult<>(FAIL, "操作失败", data);
    }

    public static <T> CommonResult<T> fail(T data, String message) {
        return new CommonResult<>(FAIL, message, data);
    }

    public static <T> CommonResult<T> fail(int code, String message) {
        return new CommonResult<>(code, message, null);
    }

    // ===== 访问器（Jackson / fastjson 序列化契约）=====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
