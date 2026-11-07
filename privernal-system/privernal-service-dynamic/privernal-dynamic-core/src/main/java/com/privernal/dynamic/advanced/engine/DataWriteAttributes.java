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

package com.privernal.dynamic.advanced.engine;

/**
 * 数据写入属性：随一次业务写入进入服务层收敛点的元信息（来源标记/级联深度/代理身份）。
 * <p>
 * 传递手段是「调用方在同线程 set → 调服务层 → finally restore」的显式小栈，不跨线程；
 * §7.5.3 禁止 ThreadLocal 的对象是<b>跨线程传播</b>联动上下文（depth/event_id 装进
 * {@link LinkageEvent} 显式传参），而 sourceTag 标记的是"发起这次写入的调用方"，
 * 生命周期就是一次方法调用，作用域不会丢。
 * <p>
 * 使用范式：
 * <pre>
 *     DataWriteAttributes.Snapshot prev = DataWriteAttributes.push(LINKAGE, depth + 1, null, null);
 *     try { dynamicTableService.saveOrUpdate(...); } finally { DataWriteAttributes.restore(prev); }
 * </pre>
 */
public final class DataWriteAttributes {

    public static final String SOURCE_NORMAL = "NORMAL";
    public static final String SOURCE_OPENAPI = "OPENAPI";
    public static final String SOURCE_PUBLIC_LINK = "PUBLIC_LINK";
    public static final String SOURCE_IMPORT = "IMPORT";
    public static final String SOURCE_LINKAGE = "LINKAGE";

    /** 递归防护第二重保险：sourceTag=LINKAGE 的写入即便 depth 丢失也不会形成 A↔B 死循环（§7.5.3） */
    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private DataWriteAttributes() {
    }

    public static Snapshot push(String sourceTag, int depth, String proxyCreatedBy,
                                String proxyDepartmentId, String organizationId) {
        Snapshot previous = HOLDER.get();
        Snapshot current = new Snapshot(sourceTag == null ? SOURCE_NORMAL : sourceTag, depth,
                proxyCreatedBy, proxyDepartmentId, organizationId);
        HOLDER.set(current);
        return previous;
    }

    public static void restore(Snapshot previous) {
        if (previous == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(previous);
        }
    }

    /** 只读当前属性，不修改栈 */
    public static Snapshot peek() {
        Snapshot current = HOLDER.get();
        return current != null ? current : Snapshot.EMPTY;
    }

    /**
     * 写入属性快照。proxyCreatedBy/proxyDepartmentId：免登/联动链路的代理身份
     * （动态表 created_by/department_id NOT NULL）；organizationId：写入归属组织——
     * 联动/免登线程忽略了组织拦截器且上下文可能被子调用清掉，组织必须随属性显式携带。
     */
    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(SOURCE_NORMAL, 0, null, null, null);

        public final String sourceTag;
        public final int depth;
        public final String proxyCreatedBy;
        public final String proxyDepartmentId;
        public final String organizationId;

        Snapshot(String sourceTag, int depth, String proxyCreatedBy, String proxyDepartmentId, String organizationId) {
            this.sourceTag = sourceTag;
            this.depth = depth;
            this.proxyCreatedBy = proxyCreatedBy;
            this.proxyDepartmentId = proxyDepartmentId;
            this.organizationId = organizationId;
        }

        public boolean isLinkage() {
            return SOURCE_LINKAGE.equals(sourceTag);
        }
    }
}
