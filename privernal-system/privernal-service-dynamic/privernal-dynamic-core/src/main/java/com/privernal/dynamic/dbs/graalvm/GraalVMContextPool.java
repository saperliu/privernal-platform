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

package com.privernal.dynamic.dbs.graalvm;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 脚本执行的上下文池
 * 减少创建和销毁的开销
 * 使用context时，需要设置环境上的变量。
 */
public class GraalVMContextPool {

    private static final Logger log = LoggerFactory.getLogger(GraalVMContextPool.class);

    private static final GenericObjectPool<Context> pool;

    static {
        // 创建并配置 GenericObjectPool
        GenericObjectPoolConfig<Context> config = new GenericObjectPoolConfig<>();
        config.setMinIdle(10); // 最小空闲实例数
        config.setMaxIdle(15); // 最大空闲实例数
        config.setMaxTotal(100); // 池中最大实例数
        config.setTestOnBorrow(true); // 在借用对象时进行验证

        // 初始化池
        pool = new GenericObjectPool<>(new ContextFactory(), config);
    }

    public static Context borrowContext() throws Exception {
        return pool.borrowObject();
    }

    public static void returnContext(Context context) {
        try {
            // 清理 Context 中的变量
            cleanupContext(context);
            // 将 Context 返回到池中
            pool.returnObject(context);
        } catch (Exception e) {
            log.warn("脚本引擎 Context 归还失败：{}", e.getMessage());
        }
    }

    private static void cleanupContext(Context context) {
        Value bindings = context.getBindings("groovy");
        if (bindings != null) {
            // 清除所有绑定的变量
            // 获取 Groovy 语言的 Bindings
            Set<String> keys = bindings.getMemberKeys();
            // 遍历并移除所有绑定的变量
            for (String key : keys) {
                bindings.removeMember(key);
            }
        }
    }

    // Context 工厂类
    private static class ContextFactory extends BasePooledObjectFactory<Context> {

        @Override
        public Context create() throws Exception {
            // 创建带有自定义选项的 Engine
            Engine engine = Engine.newBuilder()
                    .option("engine.WarnInterpreterOnly", "true") // 设置引擎级别选项
                    .build();
            return Context.newBuilder()
                    .engine(engine) // 使用共享引擎以提高性能
                    // 新-7 预留上下文默认收口：宿主成员仅注解放行、宿主类查找全禁、
                    // IO/子进程/本地访问关闭。当前零调用方；接回调用方时必须显式放宽
                    // 并按最小权限给到所需能力，否则脚本侧即全权 Java 访问
                    .allowHostAccess(HostAccess.EXPLICIT)
                    .allowHostClassLookup(className -> false)
                    .allowCreateProcess(false)
                    .allowIO(IOAccess.NONE)
                    .allowNativeAccess(false)
                    .build();
        }

        @Override
        public PooledObject<Context> wrap(Context context) {
            return new DefaultPooledObject<>(context);
        }

        @Override
        public void destroyObject(PooledObject<Context> p) throws Exception {
            p.getObject().close();
            super.destroyObject(p);
        }

        @Override
        public boolean validateObject(PooledObject<Context> p) {
            // 可以在这里添加对 Context 实例的健康检查逻辑
            return true;
        }
    }

    public static void shutdown() throws InterruptedException {
        pool.close();
    }

    public static void setVariable(Context context, String key, Object value) {
        // 将参数转换为Polyglot Values, 防止参数类型不兼容
        Value polyglotValue = context.asValue(value);
        context.getBindings("groovy").putMember(key, polyglotValue);
    }

}
