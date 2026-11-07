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

package com.privernal.job.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.filter.ScheduleTaskFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务管理查询 Mapper——管理查询才走行级数据范围。
 *
 * <p>类上 @PrivernalDataScope(permissionMain="schedule:task")：PrivernalDataScopeAspect 按方法名拼
 * ":list/:edit/:add/:remove" 并与 system_role_menu.permission 精确比对，因此本接口的
 * 查询方法统一以 List 结尾、permission 显式写全（与 SQL 权限码、Controller @PreAuthorize 三处一致）。
 * 写操作（按 id 的增删改）统一在 ScheduleTaskCoreMapper（@IgnoreDataScope），由服务层
 * @PreAuthorize 把关，避免行级改写把按 id 的 UPDATE 变成 0 行。</p>
 *
 * @author saperliu
 */
@PrivernalDataScope(permissionMain = "schedule:task")
public interface ScheduleTaskMapper extends PrivernalMapper<SystemScheduleTask> {

    @PrivernalDataScope(mainAlias = "t", permission = "schedule:task:list")
    List<SystemScheduleTask> selectScheduleTaskList(@Param("filter") ScheduleTaskFilterDTO filter);
}
