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

package com.privernal.dynamic.dbs.groovy;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;

/**
 * 表单后台脚本语法校验
 * <p>
 * 与 {@link GroovyScriptService} 使用一致的编译配置（含 {@link ThreadInterrupt} 中断检查转换），
 * 保证"校验通过"与实际执行环境一致。
 * </p>
 */
@Slf4j
public class ScriptValidator {

    private ScriptValidator() {
    }

    /**
     * 校验脚本语法
     *
     * @param script 脚本内容
     * @return 校验通过返回 null；失败返回错误详情（透出给前端便于定位语法问题）
     */
    public static String validateScript(String script) {
        if (script == null || script.isBlank()) {
            return null;
        }
        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass(Script.class.getName());
        config.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
        try {
            new GroovyShell(config).parse(script);
            return null;
        } catch (Exception e) {
            log.warn("脚本语法校验失败：{}", e.getMessage());
            return e.getMessage();
        }
    }
}
