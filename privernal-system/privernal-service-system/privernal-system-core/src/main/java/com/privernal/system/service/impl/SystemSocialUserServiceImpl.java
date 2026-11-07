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

import com.privernal.common.core.constant.UserConstants;
import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.*;
import com.privernal.system.dto.SystemSocialUserDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.filter.SystemSocialUserFilterDTO;
import com.privernal.system.mapper.*;
import com.privernal.system.service.ISystemSocialUserService;
import com.privernal.system.service.ISystemUserService;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static com.privernal.common.core.constant.UserConstants.USER_TYPE_SYSTEM;

/**
 * 社交账号的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 社交账号的Service业务层处理
 */
@Slf4j
@Service
public class SystemSocialUserServiceImpl implements ISystemSocialUserService {
    @Autowired
    private SystemSocialUserMapper systemSocialUserMapper;

    @Autowired
    private SystemUserSocialUserMapper systemUserSocialUserMapper;

    @Autowired
    private SystemUserMapper systemUserMapper;

    @Autowired
    private SystemRoleUserMapper systemRoleUserMapper;

    @Autowired
    private ISystemUserService systemUserService;

    @Autowired
    private SystemUserServiceOrgMapper systemUserServiceOrgMapper;

    /**
     * 查询社交账号
     *
     * @param id 社交账号主键
     * @return 社交账号
     */
    @Transactional(readOnly = true)
    @Override
    public SystemSocialUser selectSystemSocialUserById(String id) {
        return systemSocialUserMapper.selectById(id);
    }

    /**
     * 查询社交账号列表
     *
     * @param systemSocialUser 社交账号
     * @return 社交账号
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemSocialUser> selectSystemSocialUserList(SystemSocialUserFilterDTO systemSocialUser) {
        return systemSocialUserMapper.selectSystemSocialUserList(systemSocialUser);
    }

    /**
     * 新增社交账号
     *
     * @param systemSocialUser 社交账号
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemSocialUser(SystemSocialUser systemSocialUser) {
        return systemSocialUserMapper.insert(systemSocialUser);
    }

    /**
     * 修改社交账号
     *
     * @param systemSocialUser 社交账号
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemSocialUser(SystemSocialUser systemSocialUser) {
        return systemSocialUserMapper.updateById(systemSocialUser);
    }

    /**
     * 批量删除社交账号
     *
     * @param ids 需要删除的社交账号主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemSocialUserByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemSocialUserById(id);
        }
        return ids.length;
    }

    /**
     * 删除社交账号信息
     *
     * @param id 社交账号主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemSocialUserById(String id) {
        return systemSocialUserMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemUser findSocialUser(SystemSocialUserDTO systemSocialUserDTO) {
        List<SystemUser> list = systemSocialUserMapper.selectSysUserBySocial(systemSocialUserDTO);
        if (list != null && list.size() == 1) {
            return list.get(0);
        } else {
            return null;
        }
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemUser saveSysSocialUser(SystemSocialUserDTO sysSocialUser) {
        //先判断是否已经存在此用户
        SystemUserSocialUser userSocialUser = systemUserSocialUserMapper.selectSysUserSocialUserByOpenId(sysSocialUser.getOpenId());
        log.info("---------saveSysSocialUser-------sysSocialUser :{}", JSON.toJSONString(sysSocialUser));
        log.info("---------saveSysSocialUser-------userSocialUser :{}", JSON.toJSONString(userSocialUser));
        if (userSocialUser != null) {
            // 已经注册过
            SystemUser sysUser = systemUserMapper.selectSystemUserById(userSocialUser.getUserId());
            if (StringUtils.isNotEmpty(sysSocialUser.getAvatarUrl())) {
                // 更新一下头像和昵称
                sysUser.setNickName(sysSocialUser.getNickName());
                sysUser.setAvatar(sysSocialUser.getAvatarUrl());
                sysUser.setGender(sysSocialUser.getGender());
                systemUserMapper.updateById(sysUser);
            } else {
                if (StringUtils.isNotEmpty(sysSocialUser.getPurePhoneNumber())) {
                    sysUser.setPhone(sysSocialUser.getPurePhoneNumber());
                    systemUserMapper.updateById(sysUser);
                }
            }
            return sysUser;
        } else {
            // UnionId有可能为空
            if (StringUtils.isNotEmpty(sysSocialUser.getUnionId())) {
                userSocialUser = systemUserSocialUserMapper.selectSysUserSocialUserByUnionId(sysSocialUser.getUnionId(), sysSocialUser.getOrganizationId());
            }
            log.info("---------saveSysSocialUser---2----userSocialUser :{}", JSON.toJSONString(userSocialUser));
            if (userSocialUser != null) {
                // 同步的用户，第一次缺少openId。
                if (StringUtils.isNotEmpty(sysSocialUser.getOpenId())) {
                    SystemSocialUser systemSocialUser = systemSocialUserMapper.selectById(userSocialUser.getSocialUserId());
                    systemSocialUser.setOpenId(sysSocialUser.getOpenId());
                    systemSocialUser.setAppCode(sysSocialUser.getAppCode());
                    systemSocialUser.setSource(sysSocialUser.getSource());
                    systemSocialUserMapper.updateById(systemSocialUser);
                }
                SystemUser sysUser = systemUserMapper.selectSystemUserById(userSocialUser.getUserId());
                if (StringUtils.isNotEmpty(sysSocialUser.getAvatarUrl())) {
                    // 更新一下头像和昵称
                    sysUser.setNickName(sysSocialUser.getNickName());
                    sysUser.setAvatar(sysSocialUser.getAvatarUrl());
                    sysUser.setGender(sysSocialUser.getGender());
                    systemUserMapper.updateById(sysUser);
                } else {
                    if (StringUtils.isNotEmpty(sysSocialUser.getPurePhoneNumber())) {
                        sysUser.setPhone(sysSocialUser.getPurePhoneNumber());
                        systemUserMapper.updateById(sysUser);
                    }
                }
                return sysUser;
            } else {
                //既不是同步用户，也未注册过
                //解决，手机号要唯一的问题
                if (StringUtils.isNotEmpty(sysSocialUser.getPurePhoneNumber())) {
                    // 说明已经有此手机号的人员，则查询出，再绑定一个新的社交帐号
                    SystemUserDTO systemUserDTO = systemUserMapper.selectUserByPhone(sysSocialUser.getPurePhoneNumber());
                    log.info("---------saveSysSocialUser-------systemUserDTO :{}", JSON.toJSONString(systemUserDTO));
                    if (systemUserDTO != null) {
                        //查询有此手机号的人员
                        if (StringUtils.isNotEmpty(sysSocialUser.getOrganizationId()) && sysSocialUser.getOrganizationId().equals(systemUserDTO.getOrganizationId())) {
                            //组织相同
                        } else {
                            // 增加服务的组织和部门
                            SystemUserServiceOrg systemUserServiceOrg = new SystemUserServiceOrg();
                            systemUserServiceOrg.setUserId(systemUserDTO.getId());
                            systemUserServiceOrg.setOrganizationId(sysSocialUser.getOrganizationId());
                            systemUserServiceOrg.setDepartmentId(sysSocialUser.getDepartmentId());
                            systemUserServiceOrg.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                            systemUserServiceOrg.setCreatedBy(USER_TYPE_SYSTEM);
                            systemUserServiceOrgMapper.insert(systemUserServiceOrg);
                        }

                        Date date = new Date();
                        // 创建社交账号
                        SystemSocialUser socialUser = new SystemSocialUser();
                        BeanUtils.copyProperties(sysSocialUser, socialUser);
                        socialUser.setCreatedBy(USER_TYPE_SYSTEM);
                        socialUser.setCreatedTime(date);
                        systemSocialUserMapper.insert(socialUser);

                        SystemUserSocialUser sysUserSocialUser = new SystemUserSocialUser();
                        sysUserSocialUser.setSocialUserId(socialUser.getId());
                        sysUserSocialUser.setUserId(systemUserDTO.getId());
                        sysUserSocialUser.setCreatedBy(USER_TYPE_SYSTEM);
                        sysUserSocialUser.setCreatedTime(date);
                        systemUserSocialUserMapper.insert(sysUserSocialUser);

                        if (StringUtils.isNotEmpty(sysSocialUser.getRoleId())) {
                            SystemRoleUser sysUserRole = new SystemRoleUser();
                            sysUserRole.setRoleId(sysSocialUser.getRoleId());
                            sysUserRole.setUserId(systemUserDTO.getId());
                            sysUserRole.setCreatedBy(USER_TYPE_SYSTEM);
                            systemRoleUserMapper.insert(sysUserRole);
                        }
                        SystemUser systemUser = new SystemUser();
                        BeanUtils.copyProperties(systemUserDTO, systemUser);
                        systemUser.setOrganizationId(sysSocialUser.getOrganizationId());
                        systemUser.setDepartmentId(sysSocialUser.getDepartmentId());
                        return systemUser;
                    } else {
                        log.info("---------saveSysSocialUser----have PhoneNumber :{}", sysSocialUser.getPurePhoneNumber());
                        // 查询无此手机号的人员
                        return saveNewSystemUser(sysSocialUser);
                    }
                } else {
                    log.info("---------saveSysSocialUser---no---getPurePhoneNumber :{}", sysSocialUser.getPurePhoneNumber());
                    // 否则
                    return saveNewSystemUser(sysSocialUser);
                }
            }
        }
    }

    private SystemUser saveNewSystemUser(SystemSocialUserDTO sysSocialUser) {
        // 创建系统用户
        Date date = new Date();
        SystemUser sysUser = new SystemUser();
        if (StringUtils.isNotEmpty(sysSocialUser.getNickName())) {
            sysUser.setNickName(sysSocialUser.getNickName());
            sysUser.setAvatar(sysSocialUser.getAvatarUrl());
            sysUser.setGender(sysSocialUser.getGender());
        } else {
            sysUser.setNickName(RandomUtil.randomString(10).toUpperCase());
            sysUser.setGender("2");
        }

        sysUser.setPhone(sysSocialUser.getPurePhoneNumber());
        if (StringUtils.isEmpty(sysSocialUser.getUid())) {
            String wxName = sysSocialUser.getSource() + "_" + RandomUtil.randomString(10);
            sysUser.setLoginName(wxName);
        } else {
            sysUser.setLoginName(sysSocialUser.getUid());
        }

        // 是否需要审批
        if (UserConstants.USER_REQUIRE_APPROVE_Y.equals(sysSocialUser.getExamineUser())) {
            sysUser.setStatus(UserConstants.USER_STATUS_APPROVE);
        } else {
            sysUser.setStatus(WorkflowDataStatus.FINISH.getCode());
        }
        sysUser.setCreatedBy(USER_TYPE_SYSTEM);
        sysUser.setCreatedTime(date);
        sysUser.setDeletedFlag(DataConstants.DATA_DELETE_FLAG_NOT);
        sysUser.setPassword(SecurityUtils.encryptPassword(RandomUtil.randomString(10)));
        sysUser.setDepartmentId(sysSocialUser.getDepartmentId());
        sysUser.setOrganizationId(sysSocialUser.getOrganizationId());
        sysUser.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
        systemUserService.insertSystemUser(sysUser);
        // 创建社交账号
        SystemSocialUser socialUser = new SystemSocialUser();
        BeanUtils.copyProperties(sysSocialUser, socialUser);
        socialUser.setCreatedBy(USER_TYPE_SYSTEM);
        socialUser.setCreatedTime(date);
        systemSocialUserMapper.insert(socialUser);

        SystemUserSocialUser sysUserSocialUser = new SystemUserSocialUser();
        sysUserSocialUser.setSocialUserId(socialUser.getId());
        sysUserSocialUser.setUserId(sysUser.getId());
        sysUserSocialUser.setCreatedBy(USER_TYPE_SYSTEM);
        sysUserSocialUser.setCreatedTime(date);
        systemUserSocialUserMapper.insert(sysUserSocialUser);

        if (StringUtils.isNotEmpty(sysSocialUser.getRoleId())) {
            SystemRoleUser sysUserRole = new SystemRoleUser();
            sysUserRole.setRoleId(sysSocialUser.getRoleId());
            sysUserRole.setUserId(sysUser.getId());
            sysUserRole.setCreatedBy(USER_TYPE_SYSTEM);
            systemRoleUserMapper.insert(sysUserRole);
        }
        return sysUser;
    }

    @Transactional(readOnly = true)
    @Override
    public SystemSocialUser findSysSocialUserByUser(SystemUserSocialUser sysUserSocialUser) {
        return systemSocialUserMapper.selectSysSocialUserByUser(sysUserSocialUser);
    }
}
