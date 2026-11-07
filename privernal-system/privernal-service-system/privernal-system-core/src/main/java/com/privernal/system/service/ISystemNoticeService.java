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

package com.privernal.system.service;

import com.privernal.system.domain.SystemNotice;
import com.privernal.system.filter.SystemNoticeFilterDTO;

import java.util.List;

/**
 * 通知的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 通知的Service接口
 */
public interface ISystemNoticeService {
    /**
     * 查询通知
     *
     * @param id 通知主键
     * @return 通知
     */
    public SystemNotice selectSystemNoticeById(String id);

    /**
     * 查询通知列表
     *
     * @param systemNotice 通知
     * @return 通知集合
     */
    public List<SystemNotice> selectSystemNoticeList(SystemNoticeFilterDTO systemNotice);

    /**
     * 新增通知
     *
     * @param systemNotice 通知
     * @return 结果
     */
    public int insertSystemNotice(SystemNotice systemNotice);

    /**
     * 修改通知
     *
     * @param systemNotice 通知
     * @return 结果
     */
    public int updateSystemNotice(SystemNotice systemNotice);

    /**
     * 批量删除通知
     *
     * @param ids 需要删除的通知主键集合
     * @return 结果
     */
    public int deleteSystemNoticeByIds(String[] ids);

    /**
     * 删除通知信息
     *
     * @param id 通知主键
     * @return 结果
     */
    public int deleteSystemNoticeById(String id);

}
