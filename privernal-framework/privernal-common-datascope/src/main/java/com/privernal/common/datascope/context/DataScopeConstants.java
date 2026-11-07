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

package com.privernal.common.datascope.context;

import java.util.ArrayList;

public class DataScopeConstants {

    /**
     * 数据范围选项（功能授权抽屉下拉来源）。
     * 自定义(40) 口径：本部门 ∪ 该角色用户配置的服务部门（system_role_user_dept），
     * 装配层 getAuthRoleDeptScope 负责把 40 行规范化为 ORG 行，切面不感知 40。
     */
    public static ArrayList<DataScope> SCOPE_LIST = new ArrayList<DataScope>() {{
        add(new DataScope("本人",10));
        add(new DataScope("本部门",20));
        add(new DataScope("本部门及以下",30));
        add(new DataScope("自定义",40));
        add(new DataScope("全部",50)); }}; ;
}
