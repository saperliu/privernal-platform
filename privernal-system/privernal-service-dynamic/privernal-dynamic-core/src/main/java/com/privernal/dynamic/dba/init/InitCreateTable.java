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

package com.privernal.dynamic.dba.init;

import com.privernal.dynamic.dba.constants.Constants;
import com.privernal.dynamic.dba.constants.DialectConstant;
import com.privernal.dynamic.dba.service.AutoCreateTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 动态表初始化器
 * <p>
 * 应用启动时根据数据库类型配置，自动扫描指定包下的实体类并创建/更新数据库表结构。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
@Component
public class InitCreateTable {

    private static final Logger log = LoggerFactory.getLogger(InitCreateTable.class);

    @Autowired
    private SpringContextUtil springContextUtil;

    @Autowired
    private AutoCreateTableService autoCreateTableService;

    /**
     * 应用启动时初始化表结构
     */
    @PostConstruct
    public void initCreate() {
        // 获取配置信息
        String databaseType = springContextUtil.getConfig(Constants.DATABASE_TYPE_KEY);
        if (databaseType == null || Constants.NULL.equals(databaseType)) {
            databaseType = DialectConstant.DEFAULT_DIALECT;
        }

        log.info("开始初始化动态表结构，当前数据库类型：{}", databaseType);

        // 统一的建表入口，内部根据方言执行对应 SQL
        autoCreateTableService.autoCreateTable();

        log.info("动态表结构初始化完成");
    }
}