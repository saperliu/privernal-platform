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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Groovy 沙箱白名单离线回归（task-upgrade P1-1 验收用例，建议收进 用例 20）。
 *
 * <p>不依赖 Spring/数据库，可离线跑。REJECT 组是审计点名的绕过面（命令执行、反射逃逸、
 * 文件与网络、二次求值）；ACCEPT 组是业务脚本的常见写法——两侧同时守，
 * 因为白名单沙箱最容易坏在"谁都用不了"。</p>
 */
class GroovySandboxBypassTest {

    private final GroovyShell shell = new GroovyShell(GroovySandboxConfig.sandboxed());

    /** 必须被拒的绕过用例 */
    static final List<String> REJECT = List.of(
            "\"x\".execute()",
            "\"\".class.forName(\"java.lang.Runtime\")",
            "\"\".getClass().getClassLoader()",
            "Class.forName(\"java.lang.Runtime\")",
            "new URL(\"http://127.0.0.1\")",
            "new java.net.URL(\"http://127.0.0.1\")",
            "java.net.http.HttpClient.newHttpClient()",
            "java.nio.file.Files.readAllLines(java.nio.file.Paths.get(\"/etc/passwd\"))",
            "Runtime.getRuntime().exec(\"ls\")",
            "System.exit(0)",
            "System.getenv(\"PATH\")",
            "java.lang.System s = null",
            "java.lang.Runtime rt = null",
            "java.io.File f = null",
            "java.lang.reflect.Method m = null",
            "def f = (java.io.File) null",
            "if (x instanceof java.lang.Runtime) { println 1 }",
            "def s = new GroovyShell(); s.evaluate(\"1+1\")",
            "new GroovyShell().parse(\"1+1\")",
            "def file = new File(\"/tmp/x\"); file.text",
            "new ProcessBuilder(\"ls\").start()",
            "\"\".metaClass.invokeMethod(\"execute\", \"ls\")",
            "def x = 1; x.&toString",
            "Thread.start { println 1 }",
            "new javax.script.ScriptEngineManager().getEngineByName(\"groovy\")",
            "\"\".class.classLoader.loadClass(\"java.lang.Runtime\").newInstance()",
            "import java.io.File; File.listRoots()",
            "import javax.script.ScriptEngineManager\nnew ScriptEngineManager()",
            "\"\".class.declaredMethods",
            "new java.net.Socket(\"127.0.0.1\", 80)",
            // S9-2/3 PoC（p6① 动态名/引号属性证伪用例）：动态名不落黑名单比对，
            // 修复口径是"方法/属性名必须是字面量"
            "\"\".\"${'execute'}\"()",
            "\"\".getClass().\"${'forName'}\"(\"java.lang.Runtime\")",
            "def m = 'getClass'; 1.\"$m\"()",
            "1.(\"${'class'}\")",
            "\"\".@\"${'metaClass'}\"");

    /** 必须放行的业务脚本写法 */
    static final List<String> ACCEPT = List.of(
            "def a = 1 + 2\nprintln a",
            "daoService.selectList(\"t\")",
            "import java.util.Date\ndef d = new Date()\nprintln d.time",
            "def x = [1, 2, 3].collect { it * 2 }\nprintln x",
            "if (!requestData) { throw new IllegalArgumentException(\"bad\") }",
            "import cn.hutool.json.JSONUtil\nprintln JSONUtil.toJsonStr([a: 1])",
            "def t = java.time.LocalDate.now().toString()\nprintln t",
            "println requestData?.get(\"id\")",
            "def s = \"abc\".toUpperCase()\nprintln s",
            "new BigDecimal(\"1.5\").multiply(new BigDecimal(\"2\"))",
            "import java.text.SimpleDateFormat\nprintln new SimpleDateFormat(\"yyyy-MM-dd\").format(new java.util.Date())",
            "import java.math.RoundingMode\nprintln RoundingMode.HALF_UP",
            "for (int i = 0; i < 3; i++) { println i }",
            "def map = [:]\nmap.k = daoService.count(\"t\")\nprintln map",
            "def v = requestData?.header?.code\nprintln v",
            "println new java.text.SimpleDateFormat(\"yyyy\").format(new java.util.Date())",
            "def list = daoService.selectList(\"t\")\nlist.each { it.name = it.name?.trim() }\nprintln list.size()",
            "java.time.LocalDate d = java.time.LocalDate.now()\nprintln d",
            "String k = daoService.count(\"t\") + \"\"\nprintln k");

    @Test
    @DisplayName("沙箱拒绝命令执行/反射/文件网络/二次求值等绕过面")
    void rejectsEscapes() {
        for (String script : REJECT) {
            Exception error = assertThrows(Exception.class, () -> shell.parse(script),
                    "应被沙箱拒绝: " + script.replace('\n', ' '));
            if (!hasForbiddenCause(error)) {
                throw new AssertionError("应被沙箱拒绝: " + script + " —— 实际是其他编译错误: "
                        + error.getMessage(), error);
            }
        }
    }

    @Test
    @DisplayName("沙箱放行常规业务脚本（白名单不得过窄）")
    void acceptsNormalScripts() {
        for (String script : ACCEPT) {
            assertDoesNotThrow(() -> shell.parse(script), "应能通过沙箱: " + script.replace('\n', ' '));
        }
    }

    @Test
    @DisplayName("整包拒绝面覆盖 java.io/java.net/java.nio/java.lang.reflect 等")
    void blocksDangerousPackages() {
        for (String pkg : List.of("java.io.FileInputStream", "java.net.Socket", "java.nio.ByteBuffer",
                "java.lang.reflect.Proxy", "javax.naming.InitialContext", "java.sql.DriverManager")) {
            assertThrows(Exception.class, () -> shell.parse("import " + pkg + "\nprintln 1"),
                    "整包应被拒: " + pkg);
        }
    }

    /** 只有"沙箱判定"（白名单/危险名/危险包）才算命中，语法错误一类的失败不能蒙混过关 */
    private static boolean hasForbiddenCause(Throwable t) {
        for (Throwable current = t; current != null; current = current.getCause() == current ? null : current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            if (message.contains("被禁止") || message.contains("白名单") || message.contains("not allowed")
                    || message.contains("只允许字面量")) {
                return true;
            }
        }
        return false;
    }
}
