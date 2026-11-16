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

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.SystemRoleUser;
import com.privernal.system.domain.SystemRoleUserDept;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.filter.SystemRoleUserFilterDTO;
import com.privernal.system.mapper.SystemRoleUserDeptMapper;
import com.privernal.system.mapper.SystemRoleUserMapper;
import com.privernal.system.service.ISystemRoleUserService;
import com.privernal.system.support.UserPermissionSessionRefresher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
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

    @Autowired
    private UserPermissionSessionRefresher sessionRefresher;

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
        // R1-8 回收后重授：同 (roleId,userId) 已有软删行则复活，避免撞唯一键
        List<SystemRoleUser> allRowsIncludingDeleted =
                systemRoleUserMapper.selectListByRoleIdIncludingDeleted(systemRoleUser.getRoleId());
        insertOrRevive(systemRoleUser, allRowsIncludingDeleted);
        return 1;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertBatchSystemRoleUser(SystemRoleUserDTO systemRoleUserDTO) {
        if (systemRoleUserDTO.getUserDTOList() != null && systemRoleUserDTO.getUserDTOList().size() > 0) {

            SystemRoleUserFilterDTO filterDTO = new SystemRoleUserFilterDTO();
            filterDTO.setRoleId(systemRoleUserDTO.getRoleId());
            List<SystemRoleUser> roleMenuList = systemRoleUserMapper.selectSystemRoleUserList(filterDTO);
            // R1-8 回收后重授：连同已软删行一起看（到期回收/成员删除后 (role_id,user_id) 唯一键仍被占用，
            // 直接 insert 会撞 role_user_idx——有已软删行时复活而不是新插）
            List<SystemRoleUser> allRowsIncludingDeleted =
                    systemRoleUserMapper.selectListByRoleIdIncludingDeleted(systemRoleUserDTO.getRoleId());

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
                        insertOrRevive(systemRoleUser, allRowsIncludingDeleted);
                    }
                } else {
                    insertOrRevive(systemRoleUser, allRowsIncludingDeleted);
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
     * R1-8 回收后重授：同 (roleId,userId) 已有软删行则复活，否则新插。
     * 复活同时清掉旧 expire_time——重授即新授权，除非显式再设有效期。
     */
    private void insertOrRevive(SystemRoleUser systemRoleUser, List<SystemRoleUser> allRowsIncludingDeleted) {
        if (allRowsIncludingDeleted != null) {
            for (SystemRoleUser row : allRowsIncludingDeleted) {
                if ("Y".equals(row.getDeletedFlag())
                        && systemRoleUser.getRoleId().equals(row.getRoleId())
                        && systemRoleUser.getUserId().equals(row.getUserId())) {
                    systemRoleUserMapper.reviveById(row.getId(), SecurityUtils.getUserId());
                    return;
                }
            }
        }
        systemRoleUserMapper.insert(systemRoleUser);
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
     * R1-8 授权有效期：设置/清除授权有效期（expireTime=null 即恢复永久授权）。
     * 显式 wrapper 写入以支持置空（updateById 忽略 null 字段）；生效时效由会话快照刷新兜底。
     */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateExpireTime(String id, Date expireTime) {
        if (StringUtils.isEmpty(id)) {
            throw new ServiceException("缺少授权关系ID");
        }
        SystemRoleUser roleUser = systemRoleUserMapper.selectById(id);
        if (roleUser == null) {
            throw new ServiceException("授权关系不存在");
        }
        LambdaUpdateWrapper<SystemRoleUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(SystemRoleUser::getExpireTime, expireTime);
        wrapper.set(SystemRoleUser::getUpdatedBy, SecurityUtils.getUserId());
        wrapper.set(SystemRoleUser::getUpdatedTime, new Date());
        wrapper.eq(SystemRoleUser::getId, id);
        int rows = systemRoleUserMapper.update(null, wrapper);
        // 在线用户即时生效：重装配其会话权限快照（离线则下次登录自然生效）
        sessionRefresher.refresh(roleUser.getUserId());
        return rows;
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
