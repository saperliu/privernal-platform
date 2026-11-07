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

package com.privernal.dynamic.dbs.groovy.env;

import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dba.mapper.DynamicTableDataMapper;
import com.privernal.dynamic.dba.vo.PageResultInfo;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DaoServiceImpl implements DaoService{

    @Autowired
    private DynamicTableService dynamicTableService;
    @Override
    public String saveOrUpdate(String tableName, Map<String, Object> data) {
        return dynamicTableService.saveOrUpdate(SecurityUtils.getDatabaseName(),tableName,data);
    }

    @Override
    public void deleteById(String tableName, String id) {
        dynamicTableService.deleteById(SecurityUtils.getDatabaseName(),tableName,id);
    }

    @Override
    public void deleteByIds(String tableName, List<String> data) {
        dynamicTableService.deleteByIds(SecurityUtils.getDatabaseName(),tableName,data);
    }

    @Override
    public PageResultInfo findByPage(String tableName, Map<String, Object> data) {
        return dynamicTableService.findByPage(SecurityUtils.getDatabaseName(),tableName,data);
    }

    @Override
    public HashMap<String, Object> findById(String tableName, String id) {
        return dynamicTableService.findById(SecurityUtils.getDatabaseName(),tableName,id);
    }

    @Override
    public List<HashMap<String, Object>> querySql(String tableName, String sql) {
        return dynamicTableService.querySql(SecurityUtils.getDatabaseName(),tableName,sql);
    }
}
