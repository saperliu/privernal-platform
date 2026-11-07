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

import com.alibaba.fastjson2.JSONObject;

/**
 * 联动动作 SPI（§7.4）。实现为 Spring bean，由 {@link ActionHandlerRegistry} 按 type 收集。
 * <p>
 * ctx 显式携带事件前后快照/rule/depth/event_id/组织与数据源上下文——不依赖
 * ThreadLocal/SecurityContextHolder（ASYNC 换线程即丢，§7.5.3）。
 */
public interface LinkageActionHandler {

    /** 动作类型标识，与 rules.actions[].type 对应 */
    String type();

    /**
     * 真实执行。实现约定：
     * <ul>
     *   <li>动作产生的数据写入必须走动态表服务层收敛点（带代理写入属性），
     *       且写入前用 {@link DataWriteAttributes#push} 压入 sourceTag=LINKAGE、depth=事件深度+1，
     *       finally 恢复——递归防护与事件标记靠它（§7.5.3）；</li>
     *   <li>动作内部异常直接抛出，由执行器统一计 FAILED/PARTIAL 并按 stopOnFail 决定是否继续。</li>
     * </ul>
     *
     * @return 执行结果（summary 记影响行数等摘要）
     */
    ActionResult perform(ActionConfig actionConfig, LinkageContext ctx);

    /**
     * 试跑（§7.5.5）：不真正执行，返回条件命中/影响范围预览。
     * 数据更新/删除类动作的 dry-run 必须返回实际命中主键列表与行数，
     * 「删除前预览」与试跑共用同一条查询。
     */
    JSONObject dryRun(ActionConfig actionConfig, LinkageContext ctx);
}
