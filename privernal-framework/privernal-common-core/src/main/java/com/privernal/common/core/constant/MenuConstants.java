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

package com.privernal.common.core.constant;

/**
 * 菜单与路由常量：菜单类型、顶级父节点、外链标记与前端组件标识。
 *
 * @author saperliu
 */
public class MenuConstants
{
    private MenuConstants()
    {
    }

    /**
     * 顶级菜单的parentId（菜单）
     */
    public static final String TOP_MENU_PARENT_ID = "0";

    /**
     * 是否菜单外链（否）
     */
    public static final String NO_FRAME = "1";

    /**
     * 菜单类型（目录）
     */
    public static final String TYPE_DIR = "M";

    /**
     * 菜单类型（菜单）
     */
    public static final String TYPE_MENU = "C";

    /**
     * 菜单类型（按钮）
     */
    public static final String TYPE_BUTTON = "F";

    /**
     * Layout组件标识
     */
    public static final String LAYOUT = "Layout";

    /**
     * ParentView组件标识
     */
    public static final String PARENT_VIEW = "ParentView";
}
