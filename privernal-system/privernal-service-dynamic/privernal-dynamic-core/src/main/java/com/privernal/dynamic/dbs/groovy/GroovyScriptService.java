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


import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dbs.groovy.env.DaoService;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import jakarta.annotation.PreDestroy;
import groovy.transform.ThreadInterrupt;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 表单后台脚本执行服务
 * <p>
 * 脚本按内容缓存编译结果（LRU），避免每次提交都重新编译；
 * 通过 {@link ThreadInterrupt} 在循环处插入中断检查，
 * 配合共享线程池与超时取消，防止死循环脚本长期占用请求线程。
 * </p>
 */
@Service
@Slf4j
public class GroovyScriptService {

    /**
     * 编译缓存上限，超过后按访问顺序淘汰最久未使用的脚本
     */
    private static final int MAX_CACHED_SCRIPTS = 500;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    DaoService daoService;

    @Autowired
    private ScriptConcurrencyGuard concurrencyGuard;

    /**
     * 脚本执行超时时间（秒），可通过 privernal.dynamic.script.timeout-seconds 配置
     */
    @Value("${privernal.dynamic.script.timeout-seconds:10}")
    private long timeoutSeconds;

    private final GroovyShell shell;

    /**
     * P1-1：调度/高级设置等"平台外来源"脚本走沙箱 shell——编译期即套 {@link GroovySandboxConfig}
     * 白名单，与注册校验同一套规则；表单后台脚本沿用无沙箱 shell（既有行为不回退）。
     */
    private final GroovyShell sandboxShell;

    private final Map<String, Class<? extends Script>> scriptCache =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Class<? extends Script>> eldest) {
                    return size() > MAX_CACHED_SCRIPTS;
                }
            });

    private final Map<String, Class<? extends Script>> sandboxCache =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Class<? extends Script>> eldest) {
                    return size() > MAX_CACHED_SCRIPTS;
                }
            });

    private final ExecutorService scriptExecutor = Executors.newFixedThreadPool(
            Math.max(8, Runtime.getRuntime().availableProcessors()),
            runnable -> {
                Thread thread = new Thread(runnable, "dynamic-groovy-script");
                thread.setDaemon(true);
                return thread;
            });

    public GroovyScriptService() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setSourceEncoding("UTF-8");
        configuration.addCompilationCustomizers(
                new ASTTransformationCustomizer(ThreadInterrupt.class));
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader());
        this.shell = new GroovyShell(classLoader, new Binding(), configuration);
        this.sandboxShell = new GroovyShell(classLoader, new Binding(), GroovySandboxConfig.sandboxed());
    }

    /**
     * P1-1：沙箱执行（调度任务等平台外来源脚本）——编译期套白名单 AST，超时由调用方给定
     * （任务的 timeout_seconds 真正接到 Future，不再只吃全局默认）。
     *
     * @param timeoutSeconds 本轮脚本硬上限；&lt;=0 时取全局配置值
     */
    public void executeSandboxed(String script, Object requestData, long timeoutSeconds) throws Exception {
        doExecute(script, requestData, true, timeoutSeconds > 0 ? timeoutSeconds : this.timeoutSeconds);
    }

    public void executeScript(String script, Object requestData) throws Exception {
        doExecute(script, requestData, false, this.timeoutSeconds);
    }

    private void doExecute(String script, Object requestData, boolean sandboxed, long effectiveTimeout) throws Exception {
        if (StringUtils.isEmpty(script)) {
            return;
        }
        // 每组织并发配额（A1 净新增②）：防止单组织把共享执行池占满
        concurrencyGuard.runWithQuota(() -> {
            // 开启事务：加入外层事务（若存在），脚本与后续落库保持原子性
            TransactionStatus status = transactionManager.getTransaction(
                    new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED));
            try {
                log.debug("执行后台脚本，长度：{}，沙箱={}", script.length(), sandboxed);
                Binding binding = new Binding();
                binding.setVariable("daoService", daoService);
                binding.setVariable("requestData", requestData);
                run(compile(script, sandboxed), binding, effectiveTimeout);
                transactionManager.commit(status);
                return null;
            } catch (Exception e) {
                transactionManager.rollback(status);
                throw e;
            }
        });
    }

    /**
     * 脚本资产专用编译缓存：键从"脚本文本"改为调用方给定的稳定键
     * （(asset_id, version)）——草稿体可原地覆盖而版本不变，按文本缓存会让版本语义串味。
     * 仍走沙箱 shell + LRU，与常规 sandboxCache 共用容量上限。
     */
    public Class<? extends Script> compileSandboxedCached(String cacheKey, String script) {
        synchronized (sandboxCache) {
            Class<? extends Script> clazz = sandboxCache.get(cacheKey);
            if (clazz == null) {
                clazz = sandboxShell.parse(script).getClass();
                sandboxCache.put(cacheKey, clazz);
            }
            return clazz;
        }
    }

    /**
     * 在共享线程池上执行已编译脚本并返回结果（脚本资产执行器走此入口，复用同一池与
     * ThreadInterrupt 超时中断；输出由调用方按 result_type 收口）。
     *
     * @param variables 脚本绑定变量（daoService/params/scriptAssets/currentUser 等）
     */
    public Object runCompiled(Class<? extends Script> clazz, Map<String, Object> variables, long effectiveTimeout) throws Exception {
        Binding binding = new Binding();
        if (variables != null) {
            variables.forEach(binding::setVariable);
        }
        return run(clazz, binding, effectiveTimeout);
    }

    /**
     * 编译脚本并缓存结果，相同内容只编译一次（沙箱与常规各一份缓存，互不污染）
     */
    private Class<? extends Script> compile(String script, boolean sandboxed) {
        Map<String, Class<? extends Script>> cache = sandboxed ? sandboxCache : scriptCache;
        GroovyShell target = sandboxed ? sandboxShell : shell;
        synchronized (cache) {
            Class<? extends Script> clazz = cache.get(script);
            if (clazz == null) {
                clazz = target.parse(script).getClass();
                cache.put(script, clazz);
            }
            return clazz;
        }
    }

    /**
     * 在共享线程池中执行脚本，超时取消（中断检查依赖编译期插入的 AST 转换）
     */
    private Object run(Class<? extends Script> clazz, Binding binding, long effectiveTimeout) throws Exception {
        Script instance = clazz.getDeclaredConstructor(Binding.class).newInstance(binding);
        Future<Object> future = scriptExecutor.submit((Callable<Object>) instance::run);
        try {
            return future.get(effectiveTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ServiceException("脚本执行超时（" + effectiveTimeout + "秒），已中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        scriptExecutor.shutdownNow();
    }
}
