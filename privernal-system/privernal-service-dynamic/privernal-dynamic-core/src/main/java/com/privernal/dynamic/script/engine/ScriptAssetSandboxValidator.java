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

package com.privernal.dynamic.script.engine;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dbs.groovy.GroovySandboxConfig;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.stereotype.Component;

/**
 * 脚本资产注册/保存期沙箱校验（与 job 侧 GroovySandboxValidator 同源同规则——
 * 编译配置都取 {@link GroovySandboxConfig#sandboxed()}，保证"校验通过的脚本"
 * 与"实际编译出的字节码"是同一套白名单）。
 */
@Component
public class ScriptAssetSandboxValidator {

    private final GroovyShell sandboxShell;

    public ScriptAssetSandboxValidator() {
        CompilerConfiguration configuration = GroovySandboxConfig.sandboxed();
        this.sandboxShell = new GroovyShell(GroovySandboxConfig.class.getClassLoader(),
                new groovy.lang.Binding(), configuration);
    }

    /** 校验脚本可安全运行：语法 + 白名单（import/类型/方法名/属性名/方法指针），命中白名单外即抛业务异常 */
    public void validate(String script) {
        if (StringUtils.isEmpty(script)) {
            throw new ServiceException("脚本内容为空");
        }
        try {
            sandboxShell.parse(script);
        } catch (CompilationFailedException e) {
            throw new ServiceException("脚本未通过安全校验: " + rootMessage(e));
        } catch (IllegalArgumentException forbidden) {
            throw new ServiceException("脚本未通过安全校验: " + forbidden.getMessage());
        } catch (Exception e) {
            throw new ServiceException("脚本语法错误: " + e.getMessage());
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? t.getClass().getSimpleName() : root.getMessage();
    }
}
