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

import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import lombok.Getter;

import java.util.Map;

/**
 * 单次规则执行的上下文（§7.5.3）：随方法参数显式传递并序列化进异步任务载荷，
 * 不依赖 ThreadLocal/SecurityContextHolder。
 */
@Getter
public class LinkageContext {

    private final DynamicLinkageRule rule;

    private final LinkageEvent event;

    /** 本规则执行的有效深度（事件深度 + 1，用于动作写入时的级联标记） */
    private final int depth;

    /** 目标数据所在业务库（事件携带，缺失时由组织解析，解析结果缓存在此） */
    private String databaseName;

    public LinkageContext(DynamicLinkageRule rule, LinkageEvent event, int depth) {
        this.rule = rule;
        this.event = event;
        this.depth = depth;
        this.databaseName = event.getDatabaseName();
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    /** 条件判定用的数据：DELETE 用 before（动作要引用被删数据，§7.2），其余用 after */
    public Map<String, Object> conditionData() {
        return "DELETE".equals(event.getEventType()) || event.getAfter() == null
                ? event.getBefore() : event.getAfter();
    }

    public String displayRule() {
        return rule.getRuleCode() + "(" + rule.getName() + ")";
    }
}
