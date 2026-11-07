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

package com.privernal.common.datascope.aspect;


import lombok.Data;

import java.io.Serializable;

@Data
public class PrivernalDataScopeDTO implements Serializable {

    String mainAlias;

    /**
     * 当前主表的部门字段名。查询的sql语句的部门字段的别名。
     */
    String deptField;

    /**
     * 当前的权限标识。授权时选择的菜单数据的权限标识，如：system:user:list 表示用户的列表，
     * 程序会根据此权限标识查询授权给此标识的数据权限是什么。
     */
    String permission;

    /**
     * 操作，单个PrivernalMapper上的注解. 如 list
     * 它与 permissionMain 配合，生成: system:user:list
     */
    String permissionOperation;


    /**
     * 当前主表的权限字段的前缀，具体操作根据方法再进行拼接
     */
    String permissionMain;

    /**
     * 忽略掉部门条件，也就是查询当前组织的所有数据。
     */
     boolean ignoreDepartment;
}
