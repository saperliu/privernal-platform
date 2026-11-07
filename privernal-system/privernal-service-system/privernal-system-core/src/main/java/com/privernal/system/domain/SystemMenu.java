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

package com.privernal.system.domain;

import com.privernal.common.core.utils.tree.TreeNode;
import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单对象 system_menu
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 菜单的实体
 */
@Data
public class SystemMenu extends BaseEntity implements TreeNode<SystemMenu, String> {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 菜单名称
     */
    private String menuName;
    /**
     * 菜单编码
     */
    private String menuCode;
    /**
     * 显示顺序;显示顺序
     */
    private Long orderNum;
    /**
     * 路由地址;路由地址
     */
    private String path;
    /**
     * 组件路径;组件路径
     */
    private String component;
    /**
     * 路由参数;路由参数
     */
    private String params;
    /**
     * 是否为外链;是否为外链（0是 1否）
     */
    private Integer isFrame;
    /**
     * 是否缓存（1 缓存 0 不缓存）
     */
    private Integer isCache;
    /**
     * 菜单类型（M目录 C菜单 F按钮）
     */
    private String menuType;
    /**
     * 菜单状态（0显示 1隐藏）
     */
    private Integer visible;
    /**
     * 菜单状态;菜单状态（3停用 1正常）
     */
    private Integer status;
    /**
     * 权限标识;权限标识
     */
    private String permission;
    /**
     * 父级菜单;父级菜单
     */
    private String parentId;
    /**
     * 菜单图标;菜单图标
     */
    private String icon;

    /**
     * 组织ID;所属组织的配置
     */
    private String organizationId;

    /**
     * 是否APP功能;（0是 1不是）
     */
    private Integer appMenu;

    /**
     * 子菜单
     */
    private transient List<SystemMenu> children = new ArrayList<SystemMenu>();

    /**
     * 是否使用
     */
    private transient boolean use = false;
    /**
     * 是否是应用的必选功能
     */
    private transient Integer mustMenu = 0;

    /**
     * 应用ID
     */
    private transient String appId;
    // 注意：不要手写 getOrderNum()（TreeNode 接口要求返回 Comparable）。
    // 手写会阻止 Lombok 生成 Long getOrderNum()，MyBatis 3.5.19+ 对 Comparable
    // 类型的属性找不到 TypeHandler，插入时抛 IllegalStateException。
    // 删除手写方法后 Lombok 生成 Long getter 协变覆盖接口方法，MyBatis 正常解析。

}