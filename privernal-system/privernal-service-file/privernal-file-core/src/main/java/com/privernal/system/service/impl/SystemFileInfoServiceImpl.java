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
import com.privernal.system.domain.SystemFileInfo;
import com.privernal.system.dto.SystemFileInfoDTO;
import com.privernal.system.filter.SystemFileInfoFilterDTO;
import com.privernal.system.mapper.SystemFileInfoMapper;
import com.privernal.system.service.ISystemFileInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文件信息的Service业务层处理
 *
 * @author saperliu
 * @date 2023-10-16
 * @Description 文件信息的Service业务层处理
 */
@Slf4j
@Service
public class SystemFileInfoServiceImpl implements ISystemFileInfoService {
    @Autowired
    private SystemFileInfoMapper systemFileInfoMapper;

    /**
     * 查询文件信息
     *
     * @param id 文件信息主键
     * @return 文件信息
     */
    @Transactional(readOnly = true)
    @Override
    public SystemFileInfo selectSystemFileInfoById(String id) {
        return systemFileInfoMapper.selectById(id);
    }

    /**
     * 查询文件信息列表
     *
     * @param systemFileInfo 文件信息
     * @return 文件信息
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemFileInfoDTO> selectSystemFileInfoList(SystemFileInfoFilterDTO systemFileInfo) {
        return systemFileInfoMapper.selectSystemFileInfoList(systemFileInfo);
    }

    /**
     * 新增文件信息
     *
     * @param systemFileInfo 文件信息
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemFileInfo(SystemFileInfo systemFileInfo) {
        return systemFileInfoMapper.insert(systemFileInfo);
    }

    /**
     * 修改文件信息
     *
     * @param systemFileInfo 文件信息
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemFileInfo(SystemFileInfo systemFileInfo) {
        return systemFileInfoMapper.updateById(systemFileInfo);
    }

    /**
     * 批量删除文件信息
     *
     * @param ids 需要删除的文件信息主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemFileInfoByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemFileInfoById(id);
        }
        return ids.length;
    }

    /**
     * 删除文件信息信息
     *
     * @param id 文件信息主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemFileInfoById(String id) {
        return systemFileInfoMapper.deleteFillById(id);
    }

}
