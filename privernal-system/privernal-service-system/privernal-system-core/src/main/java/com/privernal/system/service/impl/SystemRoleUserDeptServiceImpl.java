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
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.system.domain.SystemRoleUserDept;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemRoleUserDeptDTO;
import com.privernal.system.filter.SystemRoleUserDeptFilterDTO;
import com.privernal.system.mapper.SystemRoleUserDeptMapper;
import com.privernal.system.service.ISystemRoleUserDeptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色用户与部门关系的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色用户与部门关系的Service业务层处理
 */
@Slf4j
@Service
public class SystemRoleUserDeptServiceImpl implements ISystemRoleUserDeptService {
    @Autowired
    private SystemRoleUserDeptMapper systemRoleUserDeptMapper;

    /**
     * 查询角色用户与部门关系
     *
     * @param id 角色用户与部门关系主键
     * @return 角色用户与部门关系
     */
    @Transactional(readOnly = true)
    @Override
    public SystemRoleUserDept selectSystemRoleUserDeptById(String id) {
        return systemRoleUserDeptMapper.selectById(id);
    }

    /**
     * 查询角色用户与部门关系列表
     *
     * @param systemRoleUserDept 角色用户与部门关系
     * @return 角色用户与部门关系
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleUserDept> selectSystemRoleUserDeptList(SystemRoleUserDeptFilterDTO systemRoleUserDept) {
        return systemRoleUserDeptMapper.selectSystemRoleUserDeptList(systemRoleUserDept);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleUserDeptDTO> selectSystemRoleUserDeptDTOList(SystemRoleUserDeptFilterDTO systemRoleUserDept) {
        return systemRoleUserDeptMapper.selectSystemRoleUserDeptDTOList(systemRoleUserDept);
    }

    /**
     * 新增角色用户与部门关系
     *
     * @param systemRoleUserDept 角色用户与部门关系
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemRoleUserDept(SystemRoleUserDept systemRoleUserDept) {
        return systemRoleUserDeptMapper.insert(systemRoleUserDept);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertBatchSystemRoleUserDept(SystemRoleUserDeptDTO systemRoleUserDeptDTO) {
        if (systemRoleUserDeptDTO.getAddDepartmentDTOList() != null && systemRoleUserDeptDTO.getAddDepartmentDTOList().size() > 0) {
            // 要新加的数据
            for (SystemDepartmentDTO systemDepartmentDTO : systemRoleUserDeptDTO.getAddDepartmentDTOList()) {
                SystemRoleUserDept systemRoleUserDept = new SystemRoleUserDept();
                systemRoleUserDept.setRoleUserId(systemRoleUserDeptDTO.getRoleUserId());
                systemRoleUserDept.setDepartmentId(systemDepartmentDTO.getId());
                systemRoleUserDept.setOrganizationId(systemDepartmentDTO.getOrganizationId());
                systemRoleUserDeptMapper.insert(systemRoleUserDept);
            }
        }

        if (systemRoleUserDeptDTO.getDelDepartmentDTOList() != null && systemRoleUserDeptDTO.getDelDepartmentDTOList().size() > 0) {
            // 要删除的数据
            LambdaPrivernalWrapper<SystemRoleUserDept> lambdaPrivernalWrapper = new LambdaPrivernalWrapper<SystemRoleUserDept>();
            for (SystemDepartmentDTO systemDepartmentDTO : systemRoleUserDeptDTO.getDelDepartmentDTOList()) {
                lambdaPrivernalWrapper.eq(SystemRoleUserDept::getRoleUserId, systemRoleUserDeptDTO.getRoleUserId());
                lambdaPrivernalWrapper.eq(SystemRoleUserDept::getDepartmentId, systemDepartmentDTO.getId());
                systemRoleUserDeptMapper.delete(lambdaPrivernalWrapper);
            }
        }
        return 1;
    }

    /**
     * 修改角色用户与部门关系
     *
     * @param systemRoleUserDept 角色用户与部门关系
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemRoleUserDept(SystemRoleUserDept systemRoleUserDept) {
        return systemRoleUserDeptMapper.updateById(systemRoleUserDept);
    }

    /**
     * 批量删除角色用户与部门关系
     *
     * @param ids 需要删除的角色用户与部门关系主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleUserDeptByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemRoleUserDeptById(id);
        }
        return ids.length;
    }

    /**
     * 删除角色用户与部门关系信息
     *
     * @param id 角色用户与部门关系主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleUserDeptById(String id) {

        return systemRoleUserDeptMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartmentDTO> selectSystemDeptByUserIdAndRoleId(String userId, String roleId) {
        return systemRoleUserDeptMapper.selectSystemDeptByUserIdAndRoleId(userId, roleId);
    }
}
