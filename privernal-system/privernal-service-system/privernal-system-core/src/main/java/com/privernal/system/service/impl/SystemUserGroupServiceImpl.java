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
import com.privernal.system.domain.SystemUserGroup;
import com.privernal.system.domain.SystemUserGroupUser;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.dto.SystemUserGroupUserDTO;
import com.privernal.system.filter.SystemUserGroupFilterDTO;
import com.privernal.system.filter.SystemUserGroupUserFilterDTO;
import com.privernal.system.mapper.SystemUserGroupMapper;
import com.privernal.system.mapper.SystemUserGroupUserMapper;
import com.privernal.system.service.ISystemUserGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.privernal.common.core.constant.DataConstants.DATA_DELETE_FLAG_NOT;

/**
 * 用户组的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户组的Service业务层处理
 */
@Slf4j
@Service
public class SystemUserGroupServiceImpl implements ISystemUserGroupService {
    @Autowired
    private SystemUserGroupMapper systemUserGroupMapper;
    @Autowired
    private SystemUserGroupUserMapper systemUserGroupUserMapper;

    /**
     * 查询用户组
     *
     * @param id 用户组主键
     * @return 用户组
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUserGroup selectSystemUserGroupById(String id) {
        return systemUserGroupMapper.selectById(id);
    }

    /**
     * 查询用户组列表
     *
     * @param systemUserGroup 用户组
     * @return 用户组
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserGroup> selectSystemUserGroupList(SystemUserGroupFilterDTO systemUserGroup) {
        return systemUserGroupMapper.selectSystemUserGroupList(systemUserGroup);
    }

    /**
     * 新增用户组
     *
     * @param systemUserGroup 用户组
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemUserGroup(SystemUserGroup systemUserGroup) {
        return systemUserGroupMapper.insert(systemUserGroup);
    }

    /**
     * 修改用户组
     *
     * @param systemUserGroup 用户组
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemUserGroup(SystemUserGroup systemUserGroup) {
        return systemUserGroupMapper.updateById(systemUserGroup);
    }

    /**
     * 批量删除用户组
     *
     * @param ids 需要删除的用户组主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserGroupByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemUserGroupById(id);
        }
        return ids.length;
    }

    /**
     * 删除用户组信息
     *
     * @param id 用户组主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserGroupById(String id) {
        // 删除此用户组里的用户关系
        List<SystemUserGroupUser> systemUserGroupUserList = systemUserGroupUserMapper.selectList(SystemUserGroupUser::getGroupId, id, SystemUserGroupUser::getDeletedFlag, DATA_DELETE_FLAG_NOT);
        if (systemUserGroupUserList != null && !systemUserGroupUserList.isEmpty()) {
            for (SystemUserGroupUser groupUser : systemUserGroupUserList) {
                systemUserGroupUserMapper.deleteFillById(groupUser.getId());
            }
        }
        return systemUserGroupMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserGroupUserDTO> selectSystemUserByGroupList(String groupId, String nickName) {
        return systemUserGroupMapper.selectSystemUserByGroupList(groupId, nickName);
    }

    /**
     * @param departmentId
     * @param groupCode
     * @return
     */
    @Override
    public List<SystemUserGroupUserDTO> selectGroupUserByDepartmentIdAndGroupCode(String departmentId, String groupCode) {
        return systemUserGroupMapper.selectGroupUserByDepartmentIdAndGroupCode(departmentId, groupCode);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertBatchSystemUserGroup(SystemUserGroupUserDTO systemUserGroupUserDTO) {

        if (systemUserGroupUserDTO.getUserDTOList() != null && systemUserGroupUserDTO.getUserDTOList().size() > 0) {

            SystemUserGroupUserFilterDTO filterDTO = new SystemUserGroupUserFilterDTO();
            filterDTO.setGroupId(systemUserGroupUserDTO.getGroupId());
            List<SystemUserGroupUser> roleMenuList = systemUserGroupUserMapper.selectSystemUserGroupUserList(filterDTO);

            for (SystemUserDTO systemUserDTO : systemUserGroupUserDTO.getUserDTOList()) {
                SystemUserGroupUser systemUserGroupUser = new SystemUserGroupUser();
                systemUserGroupUser.setGroupId(systemUserGroupUserDTO.getGroupId());
                systemUserGroupUser.setUserId(systemUserDTO.getId());
                if (roleMenuList != null) {
                    boolean flag = false;
                    for (SystemUserGroupUser roleMenu : roleMenuList) {
                        if (systemUserDTO.getId().equals(roleMenu.getUserId())) {
                            // 不存在的功能添加进去
                            flag = true;
                            roleMenu.setExits(true);
                        }
                    }
                    if (!flag) {
                        // 不存在，添加
                        systemUserGroupUserMapper.insert(systemUserGroupUser);
                    }
                } else {
                    systemUserGroupUserMapper.insert(systemUserGroupUser);
                }
            }
            return systemUserGroupUserDTO.getUserDTOList().size();
        } else {
            throw new ServiceException("请选择人员");
        }
    }
}
