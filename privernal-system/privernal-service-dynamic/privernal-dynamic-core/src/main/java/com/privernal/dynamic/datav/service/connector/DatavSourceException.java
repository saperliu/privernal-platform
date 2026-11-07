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

package com.privernal.dynamic.datav.service.connector;

import com.privernal.common.core.exception.ServiceException;

/**
 * 带分类的数据源访问异常（M3.9）。
 * <p>
 * 只给健康检查用：前端要按类别给不同的处置建议（「检查主机与端口」和「重新填凭据」
 * 完全不是一句话），而这些信息原本只存在于异常文案里。
 * <p>
 * 刻意继承 {@link ServiceException} 且**只走单参构造**：全局异常处理与 CommonResult 看到的
 * 还是普通 ServiceException，code 保持 null，不因为加了分类就改动既有错误契约。
 *
 * @author saperliu
 */
public class DatavSourceException extends ServiceException {

    private static final long serialVersionUID = 1L;

    /** 连不上：DNS、超时、拒连、TLS */
    public static final String NETWORK = "network";

    /** 凭据不对：账号密码错、该账号无权访问目标库表 */
    public static final String AUTH = "auth";

    /** 拿到的东西不是预期的形状：非 JSON、路径定位不到数组、响应不是合法结构 */
    public static final String PARSE = "parse";

    /** 触到硬上限：响应体过大、行数超限 */
    public static final String LIMIT = "limit";

    /** 配置本身不成立：缺字段、类型不支持、开关未打开、表/库名写错 */
    public static final String CONFIG = "config";

    /** 归不进上面任何一类：健康检查照实说「未知错误」，比硬套一个类别更容易排错 */
    public static final String UNKNOWN = "unknown";

    private final String category;

    public DatavSourceException(String category, String message) {
        super(message);
        this.category = category;
    }

    public String getCategory() {
        return this.category;
    }
}
