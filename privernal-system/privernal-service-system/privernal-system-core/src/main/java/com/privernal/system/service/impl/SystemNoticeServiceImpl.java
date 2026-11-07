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

package com.privernal.system.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.domain.SystemNotice;
import com.privernal.system.filter.SystemNoticeFilterDTO;
import com.privernal.system.mapper.SystemNoticeMapper;
import com.privernal.system.service.ISystemNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 通知的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 通知的Service业务层处理
 */
@Slf4j
@Service
public class SystemNoticeServiceImpl implements ISystemNoticeService {
    @Autowired
    private SystemNoticeMapper systemNoticeMapper;

    /**
     * 查询通知
     *
     * @param id 通知主键
     * @return 通知
     */
    @Transactional(readOnly = true)
    @Override
    public SystemNotice selectSystemNoticeById(String id) {
        return systemNoticeMapper.selectById(id);
    }

    /**
     * 查询通知列表
     *
     * @param systemNotice 通知
     * @return 通知
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemNotice> selectSystemNoticeList(SystemNoticeFilterDTO systemNotice) {
        return systemNoticeMapper.selectSystemNoticeList(systemNotice);
    }

    /**
     * 新增通知
     *
     * @param systemNotice 通知
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemNotice(SystemNotice systemNotice) {
        return systemNoticeMapper.insert(systemNotice);
    }

    /**
     * 修改通知
     *
     * @param systemNotice 通知
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemNotice(SystemNotice systemNotice) {
        return systemNoticeMapper.updateById(systemNotice);
    }

    /**
     * 批量删除通知
     *
     * @param ids 需要删除的通知主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemNoticeByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemNoticeById(id);
        }
        return ids.length;
    }

    /**
     * 删除通知信息
     *
     * @param id 通知主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemNoticeById(String id) {
        return systemNoticeMapper.deleteFillById(id);
    }

}
