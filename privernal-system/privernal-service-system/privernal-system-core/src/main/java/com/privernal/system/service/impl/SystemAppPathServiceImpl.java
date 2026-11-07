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
import org.springframework.beans.BeanUtils;
import com.privernal.system.domain.SystemAppPath;
import com.privernal.system.dto.SystemAppPathDTO;
import com.privernal.system.dto.SystemAppPathDetailDTO;
import com.privernal.system.filter.SystemAppPathFilterDTO;
import com.privernal.system.mapper.SystemAppPathMapper;
import com.privernal.system.service.ISystemAppPathService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 应用可以访问的路径的Service业务层处理
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用可以访问的路径的Service业务层处理
 */
@Slf4j
@Service
public class SystemAppPathServiceImpl implements ISystemAppPathService {
    @Autowired
    private SystemAppPathMapper systemAppPathMapper;

    /**
     * 查询应用可以访问的路径
     *
     * @param id 应用可以访问的路径主键
     * @return 应用可以访问的路径
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppPath selectSystemAppPathById(String id) {
        return systemAppPathMapper.selectById(id);
    }

    /**
     * 查询应用可以访问的路径
     *
     * @param id 应用可以访问的路径主键
     * @return 应用可以访问的路径
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppPathDetailDTO selectSystemAppPathDetailDTOById(String id) {
        SystemAppPath systemAppPath = systemAppPathMapper.selectById(id);
        SystemAppPathDetailDTO systemAppPathDetailDTO = new SystemAppPathDetailDTO();
        if (systemAppPath != null) {
            BeanUtils.copyProperties(systemAppPath, systemAppPathDetailDTO);
        }
        return systemAppPathDetailDTO;
    }

    /**
     * 查询应用可以访问的路径列表
     *
     * @param systemAppPath 应用可以访问的路径
     * @return 应用可以访问的路径
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemAppPathDTO> selectSystemAppPathList(SystemAppPathFilterDTO systemAppPath) {
        return systemAppPathMapper.selectSystemAppPathList(systemAppPath);
    }

    /**
     * 新增应用可以访问的路径
     *
     * @param systemAppPath 应用可以访问的路径
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppPath insertSystemAppPath(SystemAppPath systemAppPath) {
        systemAppPathMapper.insert(systemAppPath);
        return systemAppPath;
    }

    /**
     * 修改应用可以访问的路径
     *
     * @param systemAppPath 应用可以访问的路径
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppPath updateSystemAppPath(SystemAppPath systemAppPath) {
        systemAppPathMapper.updateById(systemAppPath);
        return systemAppPath;
    }

    /**
     * 批量删除应用可以访问的路径
     *
     * @param ids 需要删除的应用可以访问的路径主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppPathByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemAppPathById(id);
        }
        return ids.length;
    }

    /**
     * 删除应用可以访问的路径信息
     *
     * @param id 应用可以访问的路径主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppPathById(String id) {
        return systemAppPathMapper.deleteFillById(id);
    }

}
