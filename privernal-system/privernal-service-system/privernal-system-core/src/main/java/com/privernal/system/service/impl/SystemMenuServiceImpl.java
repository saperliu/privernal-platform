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

import com.privernal.common.core.constant.MenuConstants;
import com.privernal.common.core.constant.RoleConstants;
import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.tree.TreeUtils;
import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.*;
import com.privernal.system.dto.SystemMenuDTO;
import com.privernal.system.filter.SystemMenuFilterDTO;
import com.privernal.system.mapper.*;
import com.privernal.system.service.ISystemMenuService;
import com.privernal.system.vo.MetaVo;
import com.privernal.system.vo.RouterVo;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.*;

/**
 * 菜单的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 菜单的Service业务层处理
 */
@Slf4j
@Service
public class SystemMenuServiceImpl implements ISystemMenuService {
    @Autowired
    private SystemMenuMapper systemMenuMapper;
    @Autowired
    private SystemRoleMenuMapper systemRoleMenuMapper;
    @Autowired
    private SystemOrganizationMenuMapper systemOrganizationMenuMapper;
    @Autowired
    private SystemAppMenuMapper systemAppMenuMapper;
    @Autowired
    private SystemUserAppMenuMapper systemUserAppMenuMapper;

    @Autowired
    private SystemAppInfoMapper systemAppInfoMapper;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    /**
     * 查询菜单
     *
     * @param id 菜单主键
     * @return 菜单
     */
    @Transactional(readOnly = true)
    @Override
    public SystemMenu selectSystemMenuById(String id) {
        return systemMenuMapper.selectById(id);
    }

    /**
     * 查询菜单列表
     *
     * @param systemMenu 菜单
     * @return 菜单
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemMenu> selectSystemMenuList(SystemMenuFilterDTO systemMenu) {
        return systemMenuMapper.selectSystemMenuList(systemMenu);
    }

    /**
     * 新增菜单
     *
     * @param systemMenu 菜单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemMenu(SystemMenu systemMenu) {
        systemMenuMapper.insert(systemMenu);
        SystemOrganizationMenu systemOrganizationMenu = new SystemOrganizationMenu();
        systemOrganizationMenu.setOrganizationId(systemMenu.getOrganizationId());
        systemOrganizationMenu.setStatus(WorkflowDataStatus.FINISH.getCode());
        systemOrganizationMenu.setMenuId(systemMenu.getId());
        systemOrganizationMenuMapper.insert(systemOrganizationMenu);

        // 查询该组织唯一的系统级的角色
        LambdaPrivernalWrapper<SystemRole> wrapper = new LambdaPrivernalWrapper<SystemRole>();
        wrapper.eq(SystemRole::getSystemType, DataConstants.DATA_TYPE_SYSTEM).eq(SystemRole::getRoleKey, RoleConstants.SUPER_ROLE_ADMIN).eq(SystemRole::getOrganizationId, systemMenu.getOrganizationId());
        wrapper.eq(SystemRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        SystemRole systemRole = systemRoleMapper.selectOne(wrapper);

        //给默认的角色，增加这些菜单授权 todo 只给组织管理员角色增加了，并未给所有角色增加此新的菜单。需要手动增加。
        // 组织没有系统级管理员角色时必须跳过而不是 NPE：菜单与组织关联已经写成功，
        // 让它抛异常会把调用方（表单保存、仪表盘自动注册菜单）整个事务一起回滚。
        if (systemRole == null) {
            log.warn("组织 {} 没有系统级的组织管理员角色，菜单 {} 已创建但未自动授权",
                    systemMenu.getOrganizationId(), systemMenu.getMenuCode());
            return 1;
        }
        SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
        systemRoleMenu.setMenuId(systemMenu.getId());
        systemRoleMenu.setRoleId(systemRole.getId());
        systemRoleMenu.setAppId(systemMenu.getAppId());
        systemRoleMenu.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
        systemRoleMenu.setPermission(systemMenu.getPermission());
        systemRoleMenuMapper.insert(systemRoleMenu);
        return 1;
    }

    /**
     * 修改菜单
     *
     * @param systemMenu 菜单
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemMenu(SystemMenu systemMenu) {
        return systemMenuMapper.updateById(systemMenu);
    }

    /**
     * 批量删除菜单
     *
     * @param ids 需要删除的菜单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemMenuByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemMenuById(id);
        }
        return ids.length;
    }

    /**
     * 删除菜单信息
     *
     * @param id 菜单主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemMenuById(String id) {
        // 删除角色关联的菜单信息
        List<SystemRoleMenu> userAppMenuList = systemRoleMenuMapper.selectList(SystemRoleMenu::getMenuId, id);
        if (userAppMenuList != null && !userAppMenuList.isEmpty()) {
            for (SystemRoleMenu groupUser : userAppMenuList) {
                systemRoleMenuMapper.deleteFillById(groupUser.getId());
            }
        }

        // 删除组织关联的菜单

        List<SystemOrganizationMenu> systemOrganizationMenuList = systemOrganizationMenuMapper.selectList(SystemOrganizationMenu::getMenuId, id);
        if (systemOrganizationMenuList != null && !systemOrganizationMenuList.isEmpty()) {
            for (SystemOrganizationMenu groupUser : systemOrganizationMenuList) {
                systemOrganizationMenuMapper.deleteFillById(groupUser.getId());
            }
        }
        // 删除应用关联的菜单

        List<SystemAppMenu> systemAppMenuList = systemAppMenuMapper.selectList(SystemAppMenu::getMenuId, id);
        if (systemAppMenuList != null && !systemAppMenuList.isEmpty()) {
            for (SystemAppMenu groupUser : systemAppMenuList) {
                systemAppMenuMapper.deleteFillById(groupUser.getId());
            }
        }

        // 删除用户收藏的常用功能
        List<SystemUserAppMenu> systemUserAppMenuList = systemUserAppMenuMapper.selectList(SystemUserAppMenu::getMenuId, id);
        if (systemUserAppMenuList != null && !systemUserAppMenuList.isEmpty()) {
            for (SystemUserAppMenu groupUser : systemUserAppMenuList) {
                systemUserAppMenuMapper.deleteFillById(groupUser.getId());
            }
        }
        return systemMenuMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public Set<String> getAuthMenuPermissionByUserId(String userId, String organizationId) {
        Assert.hasLength(userId, "缺少参数userId");
        Assert.hasLength(organizationId, "缺少参数organizationId");
        List<String> perms = systemMenuMapper.selectMenuPermsByUserId(userId, organizationId);
        Set<String> permsSet = new HashSet<>();
        for (String permission : perms) {
            if (StringUtils.isNotEmpty(permission)) {
                permsSet.addAll(Arrays.asList(permission.trim().split(",")));
            }
        }
        return permsSet;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemMenu> getRoutersByUserId(String userId, String organizationId) {
        Assert.hasLength(userId, "缺少参数userId");
        Assert.hasLength(organizationId, "缺少参数organizationId");
        List<SystemMenu> menus = null;
        if (SecurityUtils.isSuperPlatformRoleUser() || SecurityUtils.isSuperRoleUser()) {
            // 如果是组织管理员或者平台管理员，则直接查询此组织拥有的所有菜单，去掉部门和角色权限的限制。
            menus = systemMenuMapper.selectRoutersByOrganizationId(organizationId);
        } else {
            menus = systemMenuMapper.selectRoutersTreeByUserId(userId, organizationId);
        }

        menus = TreeUtils.convertToTree(menus, MenuConstants.TOP_MENU_PARENT_ID);
        return menus;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemMenu> getMenuButtonTreeByUserId(String userId, String organizationId) {
        Assert.hasLength(userId, "缺少参数userId");
        Assert.hasLength(organizationId, "缺少参数organizationId");
        List<SystemMenu> menus = null;
        if (SecurityUtils.isSuperPlatformRoleUser() || SecurityUtils.isSuperRoleUser()) {
            // 如果是组织管理员或者平台管理员，则直接查询此组织拥有的所有菜单，去掉部门和角色权限的限制
            // 在初次创建完组织后，要给创建的默认角色授权功能，这时不特殊处理，是查询不到组织有的功能的。
            menus = systemMenuMapper.selectMenuAllByOrganizationId(organizationId);
        } else {
            menus = systemMenuMapper.selectMenuButtonTreeByUserId(userId, organizationId);
        }
        // parentID 是顶级菜单的ID
//        return getChildPerms(menus, MenuConstants.TOP_MENU_PARENT_ID);
        menus = TreeUtils.convertToTree(menus, MenuConstants.TOP_MENU_PARENT_ID);
        return menus;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemMenuDTO> getMenuButtonTreeByUserIdAndAppId(String userId, String organizationId, String appCode) {
        Assert.hasLength(userId, "缺少参数userId");
        Assert.hasLength(organizationId, "缺少参数organizationId");
        Assert.hasLength(appCode, "缺少参数appCode");
        List<SystemMenuDTO> menus = null;
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            // 因为新的组织，并没有appInfo的权限，全平台只有一个应用。
            OrganizationContextHolder.setIgnore(true);
            LambdaPrivernalWrapper<SystemAppInfo> wrapper = new LambdaPrivernalWrapper<>();
            wrapper.eq(SystemAppInfo::getCode, appCode);
            wrapper.eq(SystemAppInfo::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
            SystemAppInfo systemAppInfo = systemAppInfoMapper.selectOne(wrapper);
            if (SecurityUtils.isSuperPlatformRoleUser() || SecurityUtils.isSuperRoleUser()) {
                // 如果是组织管理员或者平台管理员，则直接查询此组织拥有的所有菜单，去掉部门和角色权限的限制
                // 在初次创建完组织后，要给创建的默认角色授权功能，这时不特殊处理，是查询不到组织有的功能的。
                menus = systemMenuMapper.selectMenuButtonTreeByOrganizationAndAppId(organizationId, systemAppInfo.getId());
            } else {
                menus = systemMenuMapper.selectMenuButtonTreeByUserIdAndAppId(userId, organizationId, systemAppInfo.getId());
            }
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
//        return getTreeChild(menus);
        menus = TreeUtils.convertToTree(menus, null);
        return menus;
    }

    /**
     * 构建前端路由所需要的菜单
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    @Override
    public List<RouterVo> buildMenus(List<SystemMenu> menus) {
        List<RouterVo> routers = new LinkedList<RouterVo>();
        for (SystemMenu menu : menus) {
            RouterVo router = new RouterVo();
            router.setHidden("0".equals(menu.getVisible() + ""));
            router.setName(menu.getMenuCode());
            router.setPath(getRouterPath(menu));
            router.setComponent(getComponent(menu));
            router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), StringUtils.equals("1", menu.getIsCache() + ""), StringUtils.equals("0", menu.getVisible() + "")));
            List<SystemMenu> cMenus = menu.getChildren();
            if (cMenus != null && !cMenus.isEmpty() && MenuConstants.TYPE_DIR.equals(menu.getMenuType())) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildMenus(cMenus));
            } else if (isMenuFrame(menu)) {
                router.setMeta(null);
                List<RouterVo> childrenList = new ArrayList<RouterVo>();
                RouterVo children = new RouterVo();
                children.setPath(menu.getPath());
                children.setComponent(menu.getComponent());
                children.setName(StringUtils.capitalize(menu.getPath()));
                children.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), StringUtils.equals("1", menu.getIsCache() + ""), StringUtils.equals("0", menu.getVisible() + "")));
                childrenList.add(children);
                router.setChildren(childrenList);
            }
            if (MenuConstants.TYPE_DIR.equals(menu.getMenuType()) || MenuConstants.TYPE_MENU.equals(menu.getMenuType())) {
                // 只有目录与菜单加入路由。前台菜单不需要按钮
                routers.add(router);
            }
        }
        return routers;
    }

    /**
     * 获取路由名称
     *
     * @param menu 菜单信息
     * @return 路由名称
     */
    public String getRouteName(SystemMenu menu) {
        String routerName = StringUtils.capitalize(menu.getPath());
        // 非外链并且是一级目录（类型为目录）
        if (isMenuFrame(menu)) {
            routerName = StringUtils.EMPTY;
        }
        return routerName;
    }

    /**
     * 获取路由地址
     *
     * @param menu 菜单信息
     * @return 路由地址
     */
    public String getRouterPath(SystemMenu menu) {
        String routerPath = menu.getPath();
        // 非外链并且是一级目录（类型为目录）
        if (MenuConstants.TOP_MENU_PARENT_ID.equals(menu.getParentId()) && MenuConstants.TYPE_DIR.equals(menu.getMenuType())
                && MenuConstants.NO_FRAME.equals(menu.getIsFrame() + "")) {
            routerPath = "/" + menu.getPath();
        }
        // 非外链并且是一级目录（类型为菜单）
        else if (isMenuFrame(menu)) {
            routerPath = "/";
        }
        return routerPath;
    }

    /**
     * 获取组件信息
     *
     * @param menu 菜单信息
     * @return 组件信息
     */
    public String getComponent(SystemMenu menu) {
        String component = MenuConstants.LAYOUT;
        if (StringUtils.isNotEmpty(menu.getComponent()) && !isMenuFrame(menu)) {
            component = menu.getComponent();
        } else if (StringUtils.isEmpty(menu.getComponent()) && isParentView(menu)) {
            component = MenuConstants.PARENT_VIEW;
        }
        return component;
    }

    /**
     * 是否为菜单内部跳转
     *
     * @param menu 菜单信息
     * @return 结果
     */
    public boolean isMenuFrame(SystemMenu menu) {
        return MenuConstants.TOP_MENU_PARENT_ID.equals(menu.getParentId()) && MenuConstants.TYPE_MENU.equals(menu.getMenuType())
                && MenuConstants.NO_FRAME.equals(menu.getIsFrame() + "");
    }

    /**
     * 是否为parent_view组件
     *
     * @param menu 菜单信息
     * @return 结果
     */
    public boolean isParentView(SystemMenu menu) {
        return !MenuConstants.TOP_MENU_PARENT_ID.equals(menu.getParentId()) && MenuConstants.TYPE_DIR.equals(menu.getMenuType());
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemMenu> getAuthAppMenuByUserId(String userId, String organizationId) {
        return systemMenuMapper.getAuthAppMenuByUserId(userId, organizationId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemMenuDTO> getRoutersByUserIdAndAppId(String userId, String organizationId, String appId, String code) {
        Assert.hasLength(userId, "缺少参数userId");
        Assert.hasLength(organizationId, "缺少参数organizationId");
        Assert.isTrue(StringUtils.isNotEmpty(appId) || StringUtils.isNotEmpty(code), "缺少参数appId或code");
        List<SystemMenuDTO> menus = null;
//        if (SecurityUtils.isSuperPlatformRoleUser() || SecurityUtils.isSuperRoleUser()) {
//            // 如果是组织管理员或者平台管理员，则直接查询此组织拥有的所有菜单，去掉部门和角色权限的限制。
//            menus = systemMenuMapper.selectRoutersByOrganizationId(organizationId);
//        } else {
//            menus = systemMenuMapper.selectRoutersTreeByUserId(userId, organizationId);
//        }
//        log.info(" menus is   {}", JSON.toJSONString(menus));
//        //只查询某应用的ID
//        List<SystemAppMenuDTO> systemAppMenuList = new ArrayList<>();
//        if (StringUtils.isNotEmpty(appId)) {
//            systemAppMenuList = systemAppMenuMapper.selectSystemAppMenuListByAppId(appId);
//        }
//        if (StringUtils.isNotEmpty(code)) {
//            systemAppMenuList = systemAppMenuMapper.selectSystemAppMenuListByAppCode(code);
//        }
//        log.info(" systemAppMenuList is   {}", JSON.toJSONString(systemAppMenuList));
//        // 去掉menus里不在systemAppMenuList集合里的数据
//        List<SystemAppMenuDTO> finalSystemAppMenuList = systemAppMenuList;
//        menus.removeIf(menu -> finalSystemAppMenuList.stream().noneMatch(systemAppMenu -> menu.getId().equals(systemAppMenu.getMenuId())));

        log.info(" result menus is   {}", JSON.toJSONString(menus));

        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            // 因为新的组织，并没有appInfo的权限，全平台只有一个应用。
            OrganizationContextHolder.setIgnore(true);
            if (StringUtils.isNotEmpty(code)) {
                LambdaPrivernalWrapper<SystemAppInfo> wrapper = new LambdaPrivernalWrapper<>();
                wrapper.eq(SystemAppInfo::getCode, code);
                wrapper.eq(SystemAppInfo::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
                SystemAppInfo systemAppInfo = systemAppInfoMapper.selectOne(SystemAppInfo::getCode, code);
                appId = systemAppInfo.getId();
            }

            if (SecurityUtils.isSuperPlatformRoleUser() || SecurityUtils.isSuperRoleUser()) {
                // 如果是组织管理员或者平台管理员，则直接查询此组织拥有的所有菜单，去掉部门和角色权限的限制
                menus = systemMenuMapper.selectMenuButtonTreeByOrganizationAndAppId(organizationId, appId);
            } else {
                menus = systemMenuMapper.selectMenuButtonTreeByUserIdAndAppId(userId, organizationId, appId);
            }
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
        menus = TreeUtils.convertToTree(menus, null);
        return menus;
    }

    @Override
    public List<RouterVo> buildMenuDTOs(List<SystemMenuDTO> menus) {
        List<RouterVo> routers = new LinkedList<RouterVo>();
        for (SystemMenuDTO menu : menus) {
            RouterVo router = new RouterVo();
            router.setHidden("0".equals(menu.getVisible() + ""));
            router.setName(menu.getMenuCode());
            router.setPath(getRouterPath(menu));
            router.setComponent(getComponent(menu));
            router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), StringUtils.equals("1", menu.getIsCache() + ""), StringUtils.equals("0", menu.getVisible() + "") || StringUtils.equals("0", menu.getVisibleMenu() + "")));
            List<SystemMenuDTO> cMenus = menu.getChildren();
            if (cMenus != null && !cMenus.isEmpty() && MenuConstants.TYPE_DIR.equals(menu.getMenuType())) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildMenuDTOs(cMenus));
            } else if (isMenuFrame(menu)) {
                router.setMeta(null);
                List<RouterVo> childrenList = new ArrayList<RouterVo>();
                RouterVo children = new RouterVo();
                children.setPath(menu.getPath());
                children.setComponent(menu.getComponent());
                children.setName(StringUtils.capitalize(menu.getPath()));
                children.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), StringUtils.equals("1", menu.getIsCache() + ""), StringUtils.equals("0", menu.getVisible() + "") || StringUtils.equals("0", menu.getVisibleMenu() + "")));
                childrenList.add(children);
                router.setChildren(childrenList);
            }
            if (MenuConstants.TYPE_DIR.equals(menu.getMenuType()) || MenuConstants.TYPE_MENU.equals(menu.getMenuType())) {
                // 只有目录与菜单加入路由。前台菜单不需要按钮
                routers.add(router);
            }
        }
        return routers;
    }

    /**
     * 获取路由地址
     *
     * @param menu 菜单信息
     * @return 路由地址
     */
    private String getRouterPath(SystemMenuDTO menu) {
        String routerPath = menu.getPath();
        // 非外链并且是一级目录（类型为目录）
        if (MenuConstants.TOP_MENU_PARENT_ID.equals(menu.getParentId()) && MenuConstants.TYPE_DIR.equals(menu.getMenuType())
                && MenuConstants.NO_FRAME.equals(menu.getIsFrame() + "")) {
            routerPath = "/" + menu.getPath();
        }
        // 非外链并且是一级目录（类型为菜单）
        else if (isMenuFrame(menu)) {
            routerPath = "/";
        }
        return routerPath;
    }

    /**
     * 获取组件信息
     *
     * @param menu 菜单信息
     * @return 组件信息
     */
    private String getComponent(SystemMenuDTO menu) {
        String component = MenuConstants.LAYOUT;
        if (StringUtils.isNotEmpty(menu.getComponent()) && !isMenuFrame(menu)) {
            component = menu.getComponent();
        } else if (StringUtils.isEmpty(menu.getComponent()) && isParentView(menu)) {
            component = MenuConstants.PARENT_VIEW;
        }
        return component;
    }

    /**
     * 是否为菜单内部跳转
     *
     * @param menu 菜单信息
     * @return 结果
     */
    private boolean isMenuFrame(SystemMenuDTO menu) {
        return MenuConstants.TOP_MENU_PARENT_ID.equals(menu.getParentId()) && MenuConstants.TYPE_MENU.equals(menu.getMenuType())
                && MenuConstants.NO_FRAME.equals(menu.getIsFrame() + "");
    }

    /**
     * 是否为parent_view组件
     *
     * @param menu 菜单信息
     * @return 结果
     */
    private boolean isParentView(SystemMenuDTO menu) {
        return !MenuConstants.TOP_MENU_PARENT_ID.equals(menu.getParentId()) && MenuConstants.TYPE_DIR.equals(menu.getMenuType());
    }

    @Override
    public SystemMenu selectSystemMenuByCode(String code) {
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        LambdaPrivernalWrapper<SystemMenu> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(SystemMenu::getMenuCode, code);
        wrapper.eq(SystemMenu::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        return  systemMenuMapper.selectOne(wrapper);
    }
}
