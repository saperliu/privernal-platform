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

package com.privernal.job.security;

import com.privernal.dynamic.dbs.groovy.GroovySandboxConfig;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.stereotype.Component;

/**
 * Groovy 脚本白名单沙箱校验（——新建能力，非复用）。
 *
 * <p>现状 ScriptValidator 只有 GroovyShell.parse 语法校验，"能编译就能跑"，
 * 把 DESIGNER/OPENAPI 来源的脚本当安全能力用等于开放平台级 RCE。本类在注册时
 * 与执行前双重把关，规则来自 {@link GroovySandboxConfig}（与执行侧编译配置同源，
 * P1-1：原实现只有黑名单 + 空 ImportCustomizer，声明的"白名单"名不副实）。</p>
 *
 * <p>拦截目标：Runtime/ProcessBuilder（命令执行）、System（exit/env）、文件与网络
 * （File/Socket/URL/Files/HttpClient）、反射（Class.forName/setAccessible/.class/.metaClass）、
 * GroovyShell/ScriptEngine（二次求值）、execute() 字符串方法。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class GroovySandboxValidator {

    private final GroovyShell sandboxShell;

    public GroovySandboxValidator() {
        CompilerConfiguration configuration = GroovySandboxConfig.sandboxed();
        this.sandboxShell = new GroovyShell(GroovySandboxConfig.class.getClassLoader(),
                new groovy.lang.Binding(), configuration);
    }

    /**
     * 校验脚本可安全执行：语法 + 白名单（import / 类型 / 方法名 / 属性名 / 方法指针）。
     *
     * @throws IllegalArgumentException 脚本非法或命中白名单外（注册即拒绝，§8.1）
     */
    public void validate(String script) {
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("脚本内容为空");
        }
        try {
            sandboxShell.parse(script);
        } catch (CompilationFailedException e) {
            // SecureASTCustomizer 命中白名单外时以编译异常抛出，真实原因在 cause 里
            throw new IllegalArgumentException("脚本未通过安全校验: " + rootMessage(e), e);
        } catch (IllegalArgumentException forbidden) {
            throw forbidden;
        } catch (Exception e) {
            throw new IllegalArgumentException("脚本语法错误: " + e.getMessage(), e);
        }
    }

    /** 供执行侧复用同一套编译配置（GroovyScriptService 的沙箱 shell） */
    public CompilerConfiguration sandboxedConfiguration() {
        return GroovySandboxConfig.sandboxed();
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? t.getClass().getSimpleName() : message;
    }
}
