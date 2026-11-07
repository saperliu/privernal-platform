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
import com.privernal.system.domain.SystemRoleUser;
import com.privernal.system.domain.SystemRoleUserDept;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.filter.SystemRoleUserFilterDTO;
import com.privernal.system.mapper.SystemRoleUserDeptMapper;
import com.privernal.system.mapper.SystemRoleUserMapper;
import com.privernal.system.service.ISystemRoleUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色与用户关系的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色与用户关系的Service业务层处理
 */
@Slf4j
@Service
public class SystemRoleUserServiceImpl implements ISystemRoleUserService {
    @Autowired
    private SystemRoleUserMapper systemRoleUserMapper;

    @Autowired
    private SystemRoleUserDeptMapper systemRoleUserDeptMapper;

    /**
     * 查询角色与用户关系
     *
     * @param id 角色与用户关系主键
     * @return 角色与用户关系
     */
    @Transactional(readOnly = true)
    @Override
    public SystemRoleUser selectSystemRoleUserById(String id) {
        return systemRoleUserMapper.selectById(id);
    }

    /**
     * 查询角色与用户关系列表
     *
     * @param systemRoleUser 角色与用户关系
     * @return 角色与用户关系
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleUser> selectSystemRoleUserList(SystemRoleUserFilterDTO systemRoleUser) {
        return systemRoleUserMapper.selectSystemRoleUserList(systemRoleUser);
    }

    /**
     * 新增角色与用户关系
     *
     * @param systemRoleUser 角色与用户关系
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemRoleUser(SystemRoleUser systemRoleUser) {
        return systemRoleUserMapper.insert(systemRoleUser);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertBatchSystemRoleUser(SystemRoleUserDTO systemRoleUserDTO) {
        if (systemRoleUserDTO.getUserDTOList() != null && systemRoleUserDTO.getUserDTOList().size() > 0) {

            SystemRoleUserFilterDTO filterDTO = new SystemRoleUserFilterDTO();
            filterDTO.setRoleId(systemRoleUserDTO.getRoleId());
            List<SystemRoleUser> roleMenuList = systemRoleUserMapper.selectSystemRoleUserList(filterDTO);

            for (SystemUserDTO systemUserDTO : systemRoleUserDTO.getUserDTOList()) {
                SystemRoleUser systemRoleUser = new SystemRoleUser();
                systemRoleUser.setRoleId(systemRoleUserDTO.getRoleId());
                systemRoleUser.setUserId(systemUserDTO.getId());
                if (roleMenuList != null) {
                    boolean flag = false;
                    for (SystemRoleUser roleMenu : roleMenuList) {
                        if (systemUserDTO.getId().equals(roleMenu.getUserId())) {
                            // 不存在的功能添加进去
                            flag = true;
                            roleMenu.setExits(true);
                        }
                    }
                    if (!flag) {
                        // 不存在，添加
                        systemRoleUserMapper.insert(systemRoleUser);
                    }
                } else {
                    systemRoleUserMapper.insert(systemRoleUser);
                }
            }
            // 添加时不删除未选择的。
//            if (roleMenuList != null) {
//                for (SystemRoleUser roleMenu : roleMenuList) {
//                    if (!roleMenu.isExits()) {
//                        // 已经不存的功能，删除
//                        roleMenu.setDeletedBy(SecurityUtils.getUserId());
//                        roleMenu.setDeletedTime(new Date());
//                        systemRoleUserMapper.updateById(roleMenu);
//                        systemRoleUserMapper.deleteById(roleMenu);
//                    }
//                }
//            }
            return systemRoleUserDTO.getUserDTOList().size();
        } else {
            throw new ServiceException("请选择人员");
        }
    }

    /**
     * 修改角色与用户关系
     *
     * @param systemRoleUser 角色与用户关系
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemRoleUser(SystemRoleUser systemRoleUser) {
        return systemRoleUserMapper.updateById(systemRoleUser);
    }

    /**
     * 批量删除角色与用户关系
     *
     * @param ids 需要删除的角色与用户关系主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleUserByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemRoleUserById(id);
        }
        return ids.length;
    }

    /**
     * 删除角色与用户关系信息
     *
     * @param id 角色与用户关系主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleUserById(String id) {
        // 删除此人关联的服务部门
        List<SystemRoleUserDept> systemRoleUserDeptList = systemRoleUserDeptMapper.selectSystemRoleUserDeptByRoleUserId(id);
        if (systemRoleUserDeptList != null) {
            for (SystemRoleUserDept systemRoleUserDept : systemRoleUserDeptList) {
                systemRoleUserDeptMapper.deleteFillById(systemRoleUserDept.getId());
            }
        }
        return systemRoleUserMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemRoleUserDTO selectRoleUserByUserIdAndRoleId(String userId, String roleId) {
        return systemRoleUserMapper.selectRoleUserByUserIdAndRoleId(userId, roleId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleUserDTO> selectAuthRoleByUserId(String userId, String organizationId) {
        return systemRoleUserMapper.selectAuthRoleByUserId(userId, organizationId);
    }
}
