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

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.system.domain.SystemRoleMenu;
import com.privernal.system.dto.SystemAppMenuDTO;
import com.privernal.system.dto.SystemRoleMenuDTO;
import com.privernal.system.dto.SystemRoleMenuScopeDTO;
import com.privernal.system.filter.SystemAppMenuFilterDTO;
import com.privernal.system.filter.SystemRoleMenuFilterDTO;
import com.privernal.system.mapper.SystemAppMenuMapper;
import com.privernal.system.mapper.SystemRoleMenuMapper;
import com.privernal.system.service.ISystemRoleMenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色与菜单关系的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色与菜单关系的Service业务层处理
 */
@Slf4j
@Service
public class SystemRoleMenuServiceImpl implements ISystemRoleMenuService {
    @Autowired
    private SystemRoleMenuMapper systemRoleMenuMapper;

    @Autowired
    private SystemAppMenuMapper systemAppMenuMapper;

    /**
     * 查询角色与菜单关系
     *
     * @param id 角色与菜单关系主键
     * @return 角色与菜单关系
     */
    @Transactional(readOnly = true)
    @Override
    public SystemRoleMenu selectSystemRoleMenuById(String id) {
        return systemRoleMenuMapper.selectById(id);
    }

    /**
     * 查询角色与菜单关系列表
     *
     * @param systemRoleMenu 角色与菜单关系
     * @return 角色与菜单关系
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleMenu> selectSystemRoleMenuList(SystemRoleMenuFilterDTO systemRoleMenu) {
        return systemRoleMenuMapper.selectSystemRoleMenuList(systemRoleMenu);
    }

    /**
     * 新增角色与菜单关系
     *
     * @param systemRoleMenu 角色与菜单关系
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemRoleMenu(SystemRoleMenu systemRoleMenu) {
        return systemRoleMenuMapper.insert(systemRoleMenu);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertBatchSystemRoleMenu(SystemRoleMenuDTO systemRoleMenuDTO) {
        if (systemRoleMenuDTO.getMenuDTOList() != null && !systemRoleMenuDTO.getMenuDTOList().isEmpty()) {

            Map<String, List<SystemAppMenuDTO>> appMap = new HashMap<>();
            Map<String, List<SystemRoleMenu>> roleMenuMap = new HashMap<>();
            // 真正处理掉的条数。原先直接返回 menuDTOList.size()，于是「一条都没插」的畸形请求
            // （最常见是漏了 actionTag）照样回「操作成功」，调用方以为授权已落库、实际角色权限集合是空的。
            int processed = 0;
            for (SystemRoleMenuScopeDTO systemMenuDTO : systemRoleMenuDTO.getMenuDTOList()) {

                if (DataConstants.ACTION_TAG_ADD.equals(systemMenuDTO.getActionTag())) {
                    // 先确认是同一个应用下的同一份菜单：ADD 原先无条件 insert，
                    // 于是对同一菜单重复授权就会长出一条重复行（实测第二次 ADD 仍 +1）。
                    List<SystemRoleMenu> sameAppMenus = loadRoleMenusOfApp(roleMenuMap, systemRoleMenuDTO.getRoleId(), systemMenuDTO.getAppId());
                    boolean alreadyGranted = sameAppMenus.stream()
                            .anyMatch(rm -> StringUtils.equals(rm.getMenuId(), systemMenuDTO.getMenuId()));
                    if (alreadyGranted) {
                        // 已授过就算处理成功：不能计入 0 处理，否则「全部重复提交」会被
                        // 后面那句「没有任何一条被处理」误判成请求写错了。
                        processed++;
                        continue;
                    }
                    SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
                    systemRoleMenu.setMenuId(systemMenuDTO.getMenuId());
                    systemRoleMenu.setRoleId(systemRoleMenuDTO.getRoleId());
                    systemRoleMenu.setAppId(systemMenuDTO.getAppId());
                    systemRoleMenu.setPermission(systemMenuDTO.getPermission());
                    systemRoleMenu.setDataScope(systemMenuDTO.getDataScope());
                    log.info("--------getAppId-------{}", systemRoleMenuDTO.getAppId());
                    systemRoleMenuMapper.insert(systemRoleMenu);
                    // 同一次请求里勾了重复菜单时也不该插两条
                    sameAppMenus.add(systemRoleMenu);
                    processed++;
                }
                if (DataConstants.ACTION_TAG_DELETE.equals(systemMenuDTO.getActionTag())) {

                    systemRoleMenuMapper.deleteFillById(systemMenuDTO.getId());
                    processed++;
                }
                if (DataConstants.ACTION_TAG_EDIT.equals(systemMenuDTO.getActionTag())) {

                    SystemRoleMenu systemRoleMenu = systemRoleMenuMapper.selectById(systemMenuDTO.getId());
                    if (systemRoleMenu != null) {
                        systemRoleMenu.setDataScope(systemMenuDTO.getDataScope());
                        systemRoleMenuMapper.updateById(systemRoleMenu);
                        processed++;
                    }
                }
            }

            for (SystemRoleMenuScopeDTO systemMenuDTO : systemRoleMenuDTO.getMenuDTOList()) {
                if (DataConstants.ACTION_TAG_DELETE.equals(systemMenuDTO.getActionTag())) {
                    //判断是否只剩下应该的默认的功能，如果是，则也删除。
                    if (!appMap.containsKey(systemMenuDTO.getAppId())) {
                        processSystemAppDelete(appMap, roleMenuMap, systemRoleMenuDTO.getRoleId(), systemMenuDTO);
                    }
                }
                if (DataConstants.ACTION_TAG_ADD.equals(systemMenuDTO.getActionTag())) {
                    if (!appMap.containsKey(systemMenuDTO.getAppId())) {
                        //查询此应用的默认的功能项,如果是第一次添加，需要自动更新进去。
                        processSystemRoleMenuByApplicationId(appMap, roleMenuMap, systemRoleMenuDTO.getRoleId(), systemMenuDTO);
                    }
                }
            }

            if (processed == 0) {
                throw new ServiceException("角色菜单授权未生效：menuDTOList 每一项都必须带 actionTag（ADD / DELETE / EDIT），本次没有任何一条被处理");
            }
            return processed;
        } else {
            throw new ServiceException("请选择功能");
        }
    }

    /**
     * 处理应用默认的功能
     *
     * @param appMap
     * @param roleMenuMap
     * @param roleId
     * @param systemMenuDTO
     */
    private void processSystemRoleMenuByApplicationId(Map<String, List<SystemAppMenuDTO>> appMap, Map<String, List<SystemRoleMenu>> roleMenuMap, String roleId, SystemRoleMenuScopeDTO systemMenuDTO) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            // 去掉组织权限，因为查询应用的功能，不需要组织权限，平台只有一份
            OrganizationContextHolder.setIgnore(true);

            String appId = systemMenuDTO.getAppId();
            List<SystemRoleMenu> existList = loadRoleMenusOfApp(roleMenuMap, roleId, appId);

            List<SystemAppMenuDTO> appMenuList = appMap.get(appId);
            if (appMenuList == null) {
                SystemAppMenuFilterDTO filter = new SystemAppMenuFilterDTO();
                filter.setAppId(appId);
                filter.setMustMenu(DataConstants.DATA_STATUS_YES.intValue());
                filter.setVisibleMenu(DataConstants.DATA_STATUS_NOT.intValue());
                appMenuList = systemAppMenuMapper.selectAppMenuListByAppId(filter);
                appMap.put(appId, appMenuList);
            }

            for (SystemAppMenuDTO systemAppMenu : appMenuList) {
                // 判重只在同一个应用内做（existList 已按 appId 取过），命中就跳过
                boolean exist = existList.stream()
                        .anyMatch(rm -> StringUtils.equals(rm.getMenuId(), systemAppMenu.getMenuId()));
                if (exist) {
                    continue;
                }
                SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
                systemRoleMenu.setMenuId(systemAppMenu.getMenuId());
                systemRoleMenu.setRoleId(roleId);
                systemRoleMenu.setAppId(systemAppMenu.getAppId());
                // 必选项的 permission 取它自己那条：原先走「已缓存」分支时会把本次点击那条
                // 菜单的 permission 复制给所有必选项，结果是角色权限集合里拿不到必选项真正的码，
                // 菜单在侧栏可见、接口却 403。
                systemRoleMenu.setPermission(systemAppMenu.getPermission());
                systemRoleMenu.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                systemRoleMenuMapper.insert(systemRoleMenu);
                // 同一次请求里勾了重复菜单时也不该插两条
                existList.add(systemRoleMenu);
            }

        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }

    /**
     * 取「某角色在某应用下」已有的授权行，按 roleId + appId 缓存。
     * <p>
     * 两处判重/清理都必须先确认是同一个应用：
     * <ul>
     *   <li>缓存键原先只有 roleId，一次请求勾了多个应用的菜单时，第二个应用会拿第一个应用的授权行去判重；</li>
     *   <li>查询原先无条件 {@code eq(app_id, ?)}，而 app_id 为空时这会生成 {@code app_id = NULL}——
     *       SQL 里恒不成立，于是该应用已有的授权行一条也查不到，判重形同虚设，
     *       每做一次 ADD 就把同一批必选功能重复插一遍（实测同一角色 8 条涨到 18 条）。</li>
     * </ul>
     */
    private List<SystemRoleMenu> loadRoleMenusOfApp(Map<String, List<SystemRoleMenu>> roleMenuMap, String roleId, String appId) {
        String cacheKey = roleId + '@' + StringUtils.trimToEmpty(appId);
        List<SystemRoleMenu> cached = roleMenuMap.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        LambdaPrivernalWrapper<SystemRoleMenu> queryWrapper = new LambdaPrivernalWrapper<>();
        queryWrapper.eq(SystemRoleMenu::getRoleId, roleId);
        if (StringUtils.isBlank(appId)) {
            queryWrapper.isNull(SystemRoleMenu::getAppId);
        } else {
            queryWrapper.eq(SystemRoleMenu::getAppId, appId);
        }
        queryWrapper.eq(SystemRoleMenu::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        List<SystemRoleMenu> list = new ArrayList<>(systemRoleMenuMapper.selectList(queryWrapper));
        roleMenuMap.put(cacheKey, list);
        return list;
    }

    /**
     * 处理应用所有功能取消后，剩下的默认功能，也做删除处理
     *
     * @param appMap
     * @param roleMenuMap
     * @param roleId
     * @param systemMenuDTO
     */
    private void processSystemAppDelete(Map<String, List<SystemAppMenuDTO>> appMap, Map<String, List<SystemRoleMenu>> roleMenuMap, String roleId, SystemRoleMenuScopeDTO systemMenuDTO) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            // 去掉组织权限，因为查询应用的功能，不需要组织权限，平台只有一份
            OrganizationContextHolder.setIgnore(true);

            String appId = systemMenuDTO.getAppId();
            // 与 ADD 侧同一把尺子：先确认是同一个应用下的授权行，再谈「是不是只剩默认功能」
            List<SystemRoleMenu> existList = loadRoleMenusOfApp(roleMenuMap, roleId, appId);
            if (existList.isEmpty()) {
                return;
            }

            List<SystemAppMenuDTO> appMenuList = appMap.get(appId);
            if (appMenuList == null) {
                SystemAppMenuFilterDTO systemAppMenuFilterDTO = new SystemAppMenuFilterDTO();
                systemAppMenuFilterDTO.setAppId(appId);
                systemAppMenuFilterDTO.setMustMenu(DataConstants.DATA_STATUS_YES.intValue());
                systemAppMenuFilterDTO.setVisibleMenu(DataConstants.DATA_STATUS_NOT.intValue());
                appMenuList = systemAppMenuMapper.selectAppMenuListByAppId(systemAppMenuFilterDTO);
                appMap.put(appId, appMenuList);
            }
            if (appMenuList.isEmpty()) {
                return;
            }

            Set<String> mustMenuIds = new HashSet<>();
            for (SystemAppMenuDTO systemAppMenu : appMenuList) {
                mustMenuIds.add(systemAppMenu.getMenuId());
            }
            Set<String> existMenuIds = new HashSet<>();
            for (SystemRoleMenu systemRoleMenu : existList) {
                existMenuIds.add(systemRoleMenu.getMenuId());
            }
            // 原先只比条数：同应用下不同菜单凑出相同数量并不罕见，而这里删的是授权行，
            // 误删等于静默掉权限。逐条比菜单 id 才算「确实只剩这批默认功能」。
            if (existMenuIds.equals(mustMenuIds)) {
                for (SystemRoleMenu systemRoleMenu : existList) {
                    systemRoleMenuMapper.deleteFillById(systemRoleMenu.getId());
                }
            }

        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 修改角色与菜单关系
     *
     * @param systemRoleMenu 角色与菜单关系
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemRoleMenu(SystemRoleMenu systemRoleMenu) {
        return systemRoleMenuMapper.updateById(systemRoleMenu);
    }

    /**
     * 批量删除角色与菜单关系
     *
     * @param ids 需要删除的角色与菜单关系主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleMenuByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemRoleMenuById(id);
        }
        return ids.length;
    }

    /**
     * 删除角色与菜单关系信息
     *
     * @param id 角色与菜单关系主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleMenuById(String id) {
        return systemRoleMenuMapper.deleteFillById(id);
    }

}
