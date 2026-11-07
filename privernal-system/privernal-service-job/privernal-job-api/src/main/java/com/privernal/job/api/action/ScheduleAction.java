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

package com.privernal.job.api.action;

/**
 * BEAN/流程动作白名单注解。
 *
 * <p>启动时扫描本注解构建"动作名 → handler"白名单；@ScheduleAction 标注的
 * handler 其动作类型可以与别的 handler 共用（如 BEAN），白名单按 value 区分。</p>
 *
 * @author saperliu
 */
@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface ScheduleAction {

    /** 动作名（actionConfig.bean 的取值域），如 call-log-purge */
    String value();
}
