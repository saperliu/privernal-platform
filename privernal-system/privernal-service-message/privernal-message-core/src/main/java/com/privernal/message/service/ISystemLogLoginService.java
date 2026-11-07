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

import com.privernal.message.domain.SystemLogLogin;
import com.privernal.message.dto.SystemLogLoginDTO;
import com.privernal.message.dto.SystemLogLoginDetailDTO;
import com.privernal.message.filter.SystemLogLoginFilterDTO;

import java.util.List;

/**
 * 登录日志的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 登录日志的Service接口
 */
public interface ISystemLogLoginService {
    /**
     * 查询登录日志
     *
     * @param id 登录日志主键
     * @return 登录日志
     */
    public SystemLogLogin selectSystemLogLoginById(String id);

    /**
     * 查询系统登录日志
     *
     * @param id 系统登录日志主键
     * @return 系统登录日志
     */
    public SystemLogLoginDetailDTO selectSystemLogLoginDetailDTOById(String id);

    /**
     * 查询系统登录日志列表
     *
     * @param systemLogLogin 系统登录日志
     * @return 系统登录日志集合
     */
    public List<SystemLogLoginDTO> selectSystemLogLoginList(SystemLogLoginFilterDTO systemLogLogin);

    /**
     * 新增登录日志
     *
     * @param systemLogLogin 登录日志
     * @return 结果
     */
    public int insertSystemLogLogin(SystemLogLogin systemLogLogin);

    /**
     * 修改登录日志
     *
     * @param systemLogLogin 登录日志
     * @return 结果
     */
    public int updateSystemLogLogin(SystemLogLogin systemLogLogin);

    /**
     * 批量删除登录日志
     *
     * @param ids 需要删除的登录日志主键集合
     * @return 结果
     */
    public int deleteSystemLogLoginByIds(String[] ids);

    /**
     * 删除登录日志信息
     *
     * @param id 登录日志主键
     * @return 结果
     */
    public int deleteSystemLogLoginById(String id);

}
