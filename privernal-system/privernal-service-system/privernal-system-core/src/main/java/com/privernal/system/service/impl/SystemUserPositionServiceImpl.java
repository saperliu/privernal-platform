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
import com.privernal.system.domain.SystemUserPosition;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.dto.SystemUserPositionDTO;
import com.privernal.system.filter.SystemUserPositionFilterDTO;
import com.privernal.system.mapper.SystemUserPositionMapper;
import com.privernal.system.service.ISystemUserPositionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户的岗位的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的岗位的Service业务层处理
 */
@Slf4j
@Service
public class SystemUserPositionServiceImpl implements ISystemUserPositionService {
    @Autowired
    private SystemUserPositionMapper systemUserPositionMapper;

    /**
     * 查询用户的岗位
     *
     * @param id 用户的岗位主键
     * @return 用户的岗位
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUserPosition selectSystemUserPositionById(String id) {
        return systemUserPositionMapper.selectById(id);
    }

    /**
     * 查询用户的岗位列表
     *
     * @param systemUserPosition 用户的岗位
     * @return 用户的岗位
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserPosition> selectSystemUserPositionList(SystemUserPositionFilterDTO systemUserPosition) {
        return systemUserPositionMapper.selectSystemUserPositionList(systemUserPosition);
    }

    /**
     * 新增用户的岗位
     *
     * @param systemUserPosition 用户的岗位
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemUserPosition(SystemUserPosition systemUserPosition) {
        return systemUserPositionMapper.insert(systemUserPosition);
    }

    /**
     * 修改用户的岗位
     *
     * @param systemUserPosition 用户的岗位
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemUserPosition(SystemUserPosition systemUserPosition) {
        return systemUserPositionMapper.updateById(systemUserPosition);
    }

    /**
     * 批量删除用户的岗位
     *
     * @param ids 需要删除的用户的岗位主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserPositionByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemUserPositionById(id);
        }
        return ids.length;
    }

    /**
     * 删除用户的岗位信息
     *
     * @param id 用户的岗位主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserPositionById(String id) {
        return systemUserPositionMapper.deleteFillById(id);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertBatchSystemUserPosition(SystemUserPositionDTO systemUserPositionDTO) {
        if (systemUserPositionDTO.getUserDTOList() != null && systemUserPositionDTO.getUserDTOList().size() > 0) {

            SystemUserPositionFilterDTO filterDTO = new SystemUserPositionFilterDTO();
            filterDTO.setPositionId(systemUserPositionDTO.getPositionId());
            List<SystemUserPosition> roleMenuList = systemUserPositionMapper.selectSystemUserPositionList(filterDTO);

            for (SystemUserDTO systemUserDTO : systemUserPositionDTO.getUserDTOList()) {
                SystemUserPosition systemUserPosition = new SystemUserPosition();
                systemUserPosition.setPositionId(systemUserPositionDTO.getPositionId());
                systemUserPosition.setUserId(systemUserDTO.getId());
                systemUserPosition.setIsMain(0L);
                if (roleMenuList != null) {
                    boolean flag = false;
                    for (SystemUserPosition roleMenu : roleMenuList) {
                        if (systemUserDTO.getId().equals(roleMenu.getUserId())) {
                            // 不存在的功能添加进去
                            flag = true;
                            roleMenu.setExits(true);
                        }
                    }
                    if (!flag) {
                        // 不存在，添加
                        systemUserPositionMapper.insert(systemUserPosition);
                    }
                } else {
                    systemUserPositionMapper.insert(systemUserPosition);
                }
            }
            return systemUserPositionDTO.getUserDTOList().size();
        } else {
            throw new ServiceException("请选择人员");
        }
    }
}
