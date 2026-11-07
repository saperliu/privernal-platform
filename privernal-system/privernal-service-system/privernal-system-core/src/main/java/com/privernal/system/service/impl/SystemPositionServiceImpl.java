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
import com.privernal.system.domain.SystemPosition;
import com.privernal.system.dto.SystemUserPositionDTO;
import com.privernal.system.filter.SystemPositionFilterDTO;
import com.privernal.system.mapper.SystemPositionMapper;
import com.privernal.system.service.ISystemPositionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 岗位的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 岗位的Service业务层处理
 */
@Slf4j
@Service
public class SystemPositionServiceImpl implements ISystemPositionService {
    @Autowired
    private SystemPositionMapper systemPositionMapper;

    /**
     * 查询岗位
     *
     * @param id 岗位主键
     * @return 岗位
     */
    @Transactional(readOnly = true)
    @Override
    public SystemPosition selectSystemPositionById(String id) {
        return systemPositionMapper.selectById(id);
    }

    /**
     * 查询岗位列表
     *
     * @param systemPosition 岗位
     * @return 岗位
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemPosition> selectSystemPositionList(SystemPositionFilterDTO systemPosition) {
        return systemPositionMapper.selectSystemPositionList(systemPosition);
    }

    /**
     * 新增岗位
     *
     * @param systemPosition 岗位
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemPosition(SystemPosition systemPosition) {
        return systemPositionMapper.insert(systemPosition);
    }

    /**
     * 修改岗位
     *
     * @param systemPosition 岗位
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemPosition(SystemPosition systemPosition) {
        return systemPositionMapper.updateById(systemPosition);
    }

    /**
     * 批量删除岗位
     *
     * @param ids 需要删除的岗位主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemPositionByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemPositionById(id);
        }
        return ids.length;
    }

    /**
     * 删除岗位信息
     *
     * @param id 岗位主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemPositionById(String id) {
        return systemPositionMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserPositionDTO> selectSystemUserByPositionList(String positionId, String nickName) {
        return systemPositionMapper.selectSystemUserPositionList(positionId, nickName);
    }
}
