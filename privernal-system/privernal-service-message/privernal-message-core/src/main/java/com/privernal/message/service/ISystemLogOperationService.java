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

package com.privernal.message.service;

import com.privernal.message.domain.SystemLogOperation;
import com.privernal.message.dto.SystemLogOperationDTO;
import com.privernal.message.dto.SystemLogOperationDetailDTO;
import com.privernal.message.filter.SystemLogOperationFilterDTO;

import java.util.List;

/**
 * 操作日志的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 操作日志的Service接口
 */
public interface ISystemLogOperationService {
    /**
     * 查询操作日志
     *
     * @param id 操作日志主键
     * @return 操作日志
     */
    public SystemLogOperation selectSystemLogOperationById(String id);

    /**
     * 查询系统操作日志
     *
     * @param id 系统操作日志主键
     * @return 系统操作日志
     */
    public SystemLogOperationDetailDTO selectSystemLogOperationDetailDTOById(String id);

    /**
     * 查询系统操作日志列表
     *
     * @param systemLogOperation 系统操作日志
     * @return 系统操作日志集合
     */
    public List<SystemLogOperationDTO> selectSystemLogOperationList(SystemLogOperationFilterDTO systemLogOperation);

    /**
     * 新增操作日志
     *
     * @param systemLogOperation 操作日志
     * @return 结果
     */
    public int insertSystemLogOperation(SystemLogOperation systemLogOperation);

    /**
     * 修改操作日志
     *
     * @param systemLogOperation 操作日志
     * @return 结果
     */
    public int updateSystemLogOperation(SystemLogOperation systemLogOperation);

    /**
     * 批量删除操作日志
     *
     * @param ids 需要删除的操作日志主键集合
     * @return 结果
     */
    public int deleteSystemLogOperationByIds(String[] ids);

    /**
     * 删除操作日志信息
     *
     * @param id 操作日志主键
     * @return 结果
     */
    public int deleteSystemLogOperationById(String id);

}
