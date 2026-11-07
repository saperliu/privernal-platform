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

package com.privernal.dynamic.dbs.graalvm;

/**
 * 权限的检查，因为是动态的表单，所以不能使用注解。
 *
 * 接口权限可以拦截。但是数据权限如何处理？
 */
public class PermissionUtil {


    public static boolean checkPermission(String tableCode,String permission)
    {
        // 获取当前用户
        // 判断当前用户是否有权限
        return true;
    }

}
