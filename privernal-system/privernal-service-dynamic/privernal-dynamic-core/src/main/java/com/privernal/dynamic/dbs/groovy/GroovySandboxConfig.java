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

import groovy.transform.ThreadInterrupt;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Groovy 脚本沙箱编译配置（task-upgrade P1-1）。
 *
 * <p>注册校验（job 侧 GroovySandboxValidator）与编译执行（GroovyScriptService 的沙箱 shell）
 * 共用本类，保证"校验通过的脚本"与"实际编译出的字节码"是同一套规则——此前只有静态关，
 * 执行侧无任何 AST 限制，属"能过校验≠被沙箱约束"。</p>
 *
 * <p>分层口径（诚实说明，勿再当纯白名单宣传）：
 * ① import 走白名单（allowedImports/allowedStarImports，未列出的包不可显式 import）；
 * ② 全限定名走包白名单（indirectImportCheck：{@code java.net.URL} 这类点号路径必须在
 * 白名单包前缀内）；
 * ③ {@code java.lang.*} 是 Groovy 默认隐式导入，无法用 import 白名单挡住 {@code Runtime.getRuntime()}，
 * 故对 java.lang 危险类用 disallowedImports + 构造器/方法名/属性名检查器兜住；
 * ④ 反射逃逸（{@code "x".class.forName}、{@code obj.metaClass}、{@code obj.&method}）由
 * 属性名/方法名/方法指针检查器拦住，不依赖接收者类型（接收者黑名单对动态链无效）。</p>
 */
public final class GroovySandboxConfig {

    private GroovySandboxConfig() {
    }

    /** 允许显式 import 的具体类（脚本内构造业务对象基本不需要，保留 JSON/日期工具） */
    private static final List<String> ALLOWED_IMPORTS = Collections.unmodifiableList(Arrays.asList(
            "java.math.BigDecimal", "java.math.BigInteger",
            "cn.hutool.json.JSONUtil", "cn.hutool.json.JSONObject", "cn.hutool.json.JSONArray",
            "cn.hutool.core.util.StrUtil", "cn.hutool.core.date.DateUtil"));

    /**
     * 允许 import / 全限定引用的包前缀（P1-1：白名单，未列出的 java.io/java.net/java.nio/
     * java.lang.reflect/javax.script/groovy.lang/org.codehaus.groovy 等一律拒绝）。
     * java.lang 必须在列（Groovy 隐式默认导入，缺席会让普通脚本编译失败），
     * 其中的危险类 Runtime/System/Thread/Class 由表达式检查器按名字拦。
     */
    private static final List<String> ALLOWED_STAR_IMPORTS = Collections.unmodifiableList(Arrays.asList(
            "java.lang", "java.util", "java.time", "java.math", "java.text",
            "cn.hutool.json", "cn.hutool.core", "com.alibaba.fastjson2"));

    // 说明：原实现在此维护 DISALLOWED_IMPORTS / DISALLOWED_STAR_IMPORTS 两套黑名单，
    // P1-1 改为纯白名单后删除——Groovy 4 不允许 allowed 与 disallowed 同时设置，
    // 且白名单已把 java.io/java.net/java.nio/java.lang.reflect/javax.script/groovy.lang 整包关死。
    /** 危险方法名（动态调用链的收口层：不依赖接收者类型） */
    private static final List<String> BLOCKED_METHODS = Collections.unmodifiableList(Arrays.asList(
            "forName", "getClass", "getClassLoader", "loadClass", "newInstance", "invoke",
            "setAccessible", "getDeclaredMethod", "getDeclaredMethods", "getDeclaredField",
            "getDeclaredFields", "getConstructor", "getConstructors", "getMethod", "getMethods",
            "execute", "executeWaitFor", "getRuntime", "exit", "halt", "getenv", "setProperty",
            "getProperty", "runFinalizers", "eval", "evaluate", "parseClass", "createScript",
            "defineClass", "setMetaClass", "getMetaClassInfo", "transform", "transformObject",
            "createClassLoader", "dump", "inspectObject", "withStreams", "redirectErrorStream",
            "startBackground", "waitFor", "destroy", "destroyForcibly", "identityHashCode"));

    /** 危险属性名（{@code obj.class} / {@code obj.metaClass} / {@code obj.owner} 这类逃逸入口） */
    private static final List<String> BLOCKED_PROPERTIES = Collections.unmodifiableList(Arrays.asList(
            "class", "classLoader", "metaClass", "owners", "owner", "delegate", "binding",
            "thisObject", "outerClass", "declaringClass", "superClass", "enclosingMethod",
            "contextClassLoader", "protectedClassLoader"));

    /** 无包名前缀时的危险简单名（构造器/静态调用都以简单名出现，隐式 java.lang 挡不住） */
    private static final List<String> BLOCKED_SIMPLE_TYPES = Collections.unmodifiableList(Arrays.asList(
            "Runtime", "ProcessBuilder", "Process", "System", "Thread", "ThreadGroup", "ThreadDeath",
            "ClassLoader", "Class", "Module", "Compiler", "Shutdown", "SecurityManager",
            "File", "FileInputStream", "FileOutputStream", "RandomAccessFile", "ObjectInputStream",
            "Socket", "ServerSocket", "URL", "URI", "HttpClient", "Files", "Paths", "Path",
            "Method", "Field", "Constructor", "Proxy", "InvocationHandler", "AccessibleObject",
            "MethodHandles", "MethodHandle", "ScriptEngine", "ScriptEngineManager", "InitialContext",
            "DriverManager", "Statement", "GroovyShell", "GroovyClassLoader", "Binding",
            "InvokerHelper", "Executors", "ExecutorService", "RuntimePermission"));

    /** 异常类允许裸名构造（脚本抛业务错误的常规写法） */
    private static final List<String> ALLOWED_EXCEPTION_NAMES = Arrays.asList(
            "Exception", "RuntimeException", "IllegalArgumentException", "IllegalStateException",
            "NullPointerException", "UnsupportedOperationException");

    /** 全限名允许出现在这些包前缀下才允许构造 */
    private static final List<String> ALLOWED_CONSTRUCTOR_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "java.util", "java.time", "java.math", "java.text", "cn.hutool", "com.alibaba.fastjson2"));

    /** 沙箱 shell 共用的编译配置（含 ThreadInterrupt，超时中断依赖它） */
    public static CompilerConfiguration sandboxed() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setSourceEncoding("UTF-8");
        configuration.addCompilationCustomizers(secureCustomizer(),
                new ASTTransformationCustomizer(ThreadInterrupt.class));
        return configuration;
    }

    /** 白名单 AST 守卫（注册校验与执行编译共用） */
    public static SecureASTCustomizer secureCustomizer() {
        SecureASTCustomizer secure = new SecureASTCustomizer();
        // P1-1 实测：indirectImportCheck=true 会把一切点号链当包名去比对白名单，
        // `println a` / `daoService.selectList("t")` / `obj.a.b` 全部误杀（PoC 已复现）。
        // 全限名的收口改由 rejectForbiddenOwner 按"首段是包根"的判定负责，效果等价且不误伤。
        secure.setIndirectImportCheckEnabled(false);
        secure.setPackageAllowed(false);
        // 同一维度只能二选一（Groovy 4 会抛 "not allowed to set both an allowed list and a
        // disallowed list"），故 import 走纯白名单：未列入的包/类一律拒绝，
        // 危险类（java.lang.Runtime 等隐式导入）由表达式检查器按名字收口
        secure.setAllowedImports(ALLOWED_IMPORTS);
        secure.setAllowedStarImports(ALLOWED_STAR_IMPORTS);
        secure.setAllowedStaticImports(Collections.<String>emptyList());
        secure.setAllowedStaticStarImports(Collections.<String>emptyList());
        // 接收者黑名单只作纵深：动态链（"x".class / 元类派发）不经过接收者判定，
        // 真正的收口在下面的表达式检查器（按名字拦，不看类型）
        secure.setReceiversBlackList(Arrays.asList("java.lang.Runtime", "java.lang.ProcessBuilder",
                "java.lang.System", "java.io.File", "java.net.Socket", "java.net.URL", "java.lang.Class",
                "groovy.lang.GroovyShell", "javax.script.ScriptEngineManager"));
        secure.addExpressionCheckers(GroovySandboxConfig::isExpressionAllowed);
        return secure;
    }

    /**
     * 表达式级收口：方法名 / 属性名 / 构造类型 / 方法指针。返回 false 会被 SecureASTCustomizer
     * 记为"表达式不允许"，这里直接抛带原因的异常，让注册失败信息可定位。
     */
    private static boolean isExpressionAllowed(final Expression expression) {
        if (expression instanceof MethodPointerExpression) {
            throw new IllegalArgumentException("脚本使用了被禁止的方法引用（obj.&method，§8.1 白名单外）");
        }
        if (expression instanceof MethodCallExpression call) {
            // S9-2/3：方法名必须是字面量——GString/括号动态名（obj."${'execute'}"()）不落
            // 黑名单比对（getMethodAsString() 返回 null），直接拒绝，业务侧没有动态名场景
            if (!(call.getMethod() instanceof ConstantExpression)) {
                throw new IllegalArgumentException("脚本使用了动态方法名（沙箱只允许字面量方法名，§8.1）");
            }
            String name = call.getMethodAsString();
            if (name != null && BLOCKED_METHODS.contains(name)) {
                throw new IllegalArgumentException("脚本调用了被禁止的方法：" + name + "（沙箱白名单外，§8.1）");
            }
            rejectForbiddenOwner(call.getObjectExpression());
            return true;
        }
        if (expression instanceof StaticMethodCallExpression staticCall) {
            String name = staticCall.getMethod();
            String owner = staticCall.getOwnerType().getName();
            if (name != null && BLOCKED_METHODS.contains(name)) {
                throw new IllegalArgumentException("脚本调用了被禁止的静态方法：" + owner + "." + name);
            }
            requireTypeAllowed(owner);
            return true;
        }
        if (expression instanceof PropertyExpression property) {
            // S9-2/3：属性名必须是字面量——`obj.("${'class'}")` 的属性名是 GStringExpression，
            // getText() 为 null 会整体绕过黑名单比对（AttributeExpression 同理，是其子类）
            if (!(property.getProperty() instanceof ConstantExpression)) {
                throw new IllegalArgumentException("脚本使用了动态属性名（沙箱只允许字面量属性名，§8.1）");
            }
            String name = property.getProperty().getText();
            if (name != null && BLOCKED_PROPERTIES.contains(name)) {
                throw new IllegalArgumentException("脚本访问了被禁止的属性：" + name + "（反射/元类逃逸面）");
            }
            rejectForbiddenOwner(property.getObjectExpression());
            return true;
        }
        if (expression instanceof ConstructorCallExpression constructor) {
            requireTypeAllowed(constructor.getType().getName());
            return true;
        }
        // 变量声明/强转/instanceof 里的类型引用（如 `java.lang.reflect.Method m = null`）：
        // 这里只按危险类型名收口，不要求包前缀——`def x = 1` 的类型是 java.lang.Object，
        // 加前缀白名单会把所有普通声明误杀（PoC 实测）
        if (expression instanceof DeclarationExpression declaration) {
            rejectForbiddenTypeName(declaration.getType().getName());
            // 实测：限定名声明（`java.lang.System s = null`）在本阶段 ClassNode 仍是 java.lang.Object，
            // 真实类型只在声明文本里，故对"= 左侧"的包路径限定名再收一次
            String text = declaration.getText();
            int eq = text.indexOf('=');
            rejectQualifiedNamesIn(eq < 0 ? text : text.substring(0, eq));
            return true;
        }
        if (expression instanceof CastExpression cast) {
            rejectForbiddenTypeName(cast.getType().getName());
            return true;
        }
        if (expression instanceof ClassExpression classRef) {
            // `x instanceof java.lang.Runtime` 的类型侧是 ClassExpression（AST 无 InstanceOfExpression）
            rejectForbiddenTypeName(classRef.getType().getName());
            return true;
        }
        return true;
    }

    /** 危险类型名判定（不看大小写前缀，只看最后一段简单名） */
    private static void rejectForbiddenTypeName(final String typeName) {
        if (typeName == null || typeName.isEmpty() || typeName.startsWith("[")) {
            return;
        }
        String simple = simpleNameOf(typeName);
        if (BLOCKED_SIMPLE_TYPES.contains(simple)) {
            throw new IllegalArgumentException("脚本引用了被禁止的类型：" + typeName);
        }
    }

    /** 限定名是否落在 import 白名单包内（语义分析阶段声明文本会被重写成全限名，故这里用包白名单而非构造器前缀） */
    private static void rejectForeignQualifiedType(final String name) {
        if (BLOCKED_SIMPLE_TYPES.contains(simpleNameOf(name))) {
            throw new IllegalArgumentException("脚本引用了被禁止的类型：" + name);
        }
        for (String prefix : ALLOWED_STAR_IMPORTS) {
            if (name.equals(prefix) || name.startsWith(prefix + ".")) {
                return;
            }
        }
        throw new IllegalArgumentException("脚本引用了白名单包外的类型：" + name + "（可用包：" + ALLOWED_STAR_IMPORTS + "）");
    }

    /** 从一段声明文本里挑出"包根.类名"形式的限定名并按包白名单判定 */
    private static void rejectQualifiedNamesIn(final String text) {
        if (text == null || !text.contains(".")) {
            return;
        }
        for (String segment : text.split("[\\s,<>\\[\\](){}]+")) {
            if (segment.indexOf('.') < 1) {
                continue;
            }
            if (!segment.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) {
                continue;
            }
            if (isPackageRoot(segment.substring(0, segment.indexOf('.')))) {
                rejectForeignQualifiedType(segment);
            }
        }
    }

    /**
     * 接收者收口：裸危险名（{@code System.exit} / {@code Runtime.getRuntime}）与
     * 包路径限定名（{@code java.net.http.HttpClient.newHttpClient()}）都拦住；
     * 普通变量链（{@code daoService.selectList} / {@code requestData.header.code}）放行。
     */
    private static void rejectForbiddenOwner(final Expression owner) {
        if (owner == null) {
            return;
        }
        String text = owner.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        if (BLOCKED_SIMPLE_TYPES.contains(text)) {
            throw new IllegalArgumentException("脚本引用了被禁止的类型：" + text);
        }
        // 只在"整串是标识符点号链且首段是包根"时按包白名单判定，避免把
        // new java.text.SimpleDateFormat(...) / map.a.b 这类合法写法误杀
        if (text.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
                && isPackageRoot(text.substring(0, text.indexOf('.')))) {
            rejectForeignQualifiedType(text);
        }
    }

    /** 首段是否像包根（java/javax/jdk/sun/groovy/org/cn/com/net/io 等顶级域） */
    private static boolean isPackageRoot(final String first) {
        switch (first) {
            case "java":
            case "javax":
            case "jdk":
            case "sun":
            case "groovy":
            case "org":
            case "cn":
            case "com":
            case "net":
            case "io":
                return true;
            default:
                return false;
        }
    }

    /** 类型名是否在沙箱允许范围内（隐式 java.lang 的裸名按危险简单名黑名单收口） */
    private static void requireTypeAllowed(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return;
        }
        if (!typeName.contains(".")) {
            if (BLOCKED_SIMPLE_TYPES.contains(typeName)) {
                throw new IllegalArgumentException("脚本引用了被禁止的类型：" + typeName);
            }
            return;
        }
        if (BLOCKED_SIMPLE_TYPES.contains(simpleNameOf(typeName))) {
            throw new IllegalArgumentException("脚本引用了被禁止的类型：" + typeName);
        }
        for (String prefix : ALLOWED_CONSTRUCTOR_PREFIXES) {
            if (typeName.startsWith(prefix)) {
                return;
            }
        }
        if (ALLOWED_EXCEPTION_NAMES.contains(simpleNameOf(typeName))) {
            return;
        }
        throw new IllegalArgumentException("脚本引用了白名单外的类型：" + typeName
                + "（可用包前缀：" + ALLOWED_CONSTRUCTOR_PREFIXES + "）");
    }

    private static String simpleNameOf(String typeName) {
        int idx = typeName.lastIndexOf('.');
        return idx < 0 ? typeName : typeName.substring(idx + 1);
    }

    /** 供单测/自检使用：命中白名单外即抛异常 */
    public static List<String> allowedConstructorPrefixes() {
        return ALLOWED_CONSTRUCTOR_PREFIXES;
    }
}
