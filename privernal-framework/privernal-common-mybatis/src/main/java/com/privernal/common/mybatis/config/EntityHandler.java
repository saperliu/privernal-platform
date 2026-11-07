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

package com.privernal.common.mybatis.config;

import cn.dev33.satoken.exception.NotWebContextException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2021-06-11 17:08
 * @Description 自动流入组织和部门字段
 */
@Slf4j
@Component
public class EntityHandler implements MetaObjectHandler {

    /**
     * 新增数据执行
     *
     * @param metaObject
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            String username = SecurityUtils.getUserId();
            Date nowDate = new Date();
            this.strictInsertFill(metaObject, "createdTime", () -> nowDate, Date.class);
            this.strictInsertFill(metaObject, "createdBy", () -> username, String.class);
            Object org = this.getFieldValByName("organizationId", metaObject);
            String orgId = SecurityUtils.getOrganizationId();
            if (StringUtils.isNotNull(org) && StringUtils.isEmpty(org.toString()) && StringUtils.isNotEmpty(orgId)) {
                this.strictInsertFill(metaObject, "organizationId", String.class, orgId);
                metaObject.setValue("organizationId", orgId);
            }
            Object dept = this.getFieldValByName("departmentId", metaObject);
            String deptId = SecurityUtils.getDepartmentId();
            if (StringUtils.isNotNull(dept) && StringUtils.isEmpty(dept.toString()) && StringUtils.isNotEmpty(deptId)) {
                this.strictInsertFill(metaObject, "departmentId", String.class, deptId);
                metaObject.setValue("departmentId", deptId);
            }
        } catch (NotWebContextException notWebContextException) {
            log.error(" inner fill not web env", notWebContextException);
        }
    }

    /**
     * 更新数据执行
     *
     * @param metaObject
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            String username = SecurityUtils.getUserId();
            Date nowDate = new Date();
            this.strictUpdateFill(metaObject, "updatedTime", () -> nowDate, Date.class);
            this.strictUpdateFill(metaObject, "updatedBy", () -> username, String.class);
        } catch (NotWebContextException notWebContextException) {
            log.error(" update fill not web env", notWebContextException);
        }
    }

}
