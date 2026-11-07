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

package com.privernal.dynamic.dbs.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dba.dialect.DatabaseDialect;
import com.privernal.dynamic.dba.mapper.DynamicTableDataMapper;
import com.privernal.dynamic.dba.mapper.DynamicTableMapper;
import com.privernal.dynamic.dba.service.AutoCreateTableService;
import com.privernal.dynamic.dba.utils.ClassTools;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dba.utils.JsonValueCodec;
import com.privernal.dynamic.dba.utils.ShadowColumnSupport;
import com.privernal.dynamic.dba.vo.MysqlTableInfo;
import com.privernal.dynamic.dbs.service.DynamicSubTableService;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 动态表单子表服务实现
 * <p>
 * 子表表名 = 主表编码_子表字段编码；行数据通过 parent_id 关联主表数据。
 * 保存采用「整体重建」策略（先删该父数据全部旧行再插入当前行集合），
 * 与前端子表行组件的增删改交互天然对齐，保证子表 CRUD 完整。
 * 所有表名/列名先经白名单校验后以 ${} 拼接，值一律 #{} 参数绑定。
 * </p>
 */
@Slf4j
@Service
public class DynamicSubTableServiceImpl implements DynamicSubTableService {

    /**
     * 合法表名/列名白名单（与 DynamicTableServiceImpl 同规则）：动态拼接进 SQL 前必须校验
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    @Autowired
    private DynamicTableDataMapper dynamicTableDataMapper;

    @Autowired
    private DynamicTableMapper dynamicTableMapper;

    @Autowired
    private AutoCreateTableService autoCreateTableService;

    @Autowired
    private DatabaseDialect databaseDialect;

    @Override
    public List<SubTableDef> resolveSubTables(String mainTableCode, JSONObject metaData) {
        List<SubTableDef> defs = new ArrayList<>();
        if (metaData == null || mainTableCode == null || mainTableCode.isEmpty()) {
            return defs;
        }
        JSONArray schemas = metaData.getJSONArray("schemas");
        collectSubTables(schemas, mainTableCode, defs);
        return defs;
    }

    /**
     * 递归收集 schema 树中的子表节点
     */
    private void collectSubTables(JSONArray nodes, String mainTableCode, List<SubTableDef> defs) {
        if (nodes == null) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null) {
                continue;
            }
            if ("sub-table".equals(node.getString("type")) && node.getString("field") != null) {
                SubTableDef def = new SubTableDef();
                def.setField(node.getString("field"));
                def.setLabel(node.getString("label") == null ? node.getString("field") : node.getString("label"));
                def.setChildTable(mainTableCode + "_" + toDbColumn(def.getField()));
                def.setNode(node);
                defs.add(def);
                // 子表行内不支持嵌套子表，不再下钻
                continue;
            }
            collectSubTables(node.getJSONArray("children"), mainTableCode, defs);
        }
    }

    @Override
    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void ensureSubTables(String database, String mainTableCode, JSONObject metaData) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            for (SubTableDef def : resolveSubTables(mainTableCode, metaData)) {
                int exist = dynamicTableMapper.executeQueryForCount(databaseDialect.buildTableExistsSql(def.getChildTable()));
                log.info("同步子表表结构：table={} isCreate={}", def.getChildTable(), exist == 0);
                autoCreateTableService.createTableByFields(def.getChildTable(),
                        FormTransferTable.getSubTableFields(def.getNode()), exist == 0);
            }
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
        }
    }

    @Override
    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void saveRows(String database, String childTable, String parentId, List<Map<String, Object>> rows) {
        assertValidIdentifier(childTable);
        if (parentId == null || parentId.isEmpty()) {
            throw new ServiceException("子表行数据缺少父数据 ID");
        }
        // 整体重建：先清该父数据的旧行，再插入当前行集合（与前端行增删改交互对齐，天然幂等）
        dynamicTableDataMapper.deleteByParentIds(childTable, List.of(parentId));
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // 列存在性兜底：回显时补的驼峰别名键等未知键不得写入子表表
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        Set<String> existingColumns;
        try {
            OrganizationContextHolder.setIgnore(true);
            List<MysqlTableInfo> tableColumns = dynamicTableMapper.executeQueryForColumns(databaseDialect.buildGetTableColumnsSql(childTable));
            existingColumns = new HashSet<>(ClassTools.getPropertyValueList(tableColumns, MysqlTableInfo.COLUMN_NAME_KEY));
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
        }
        Date now = new Date();
        String userId = SecurityUtils.getUserId();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", IdWorker.get32UUID());
            record.put("parent_id", parentId);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String column = toDbColumn(entry.getKey());
                if ("id".equalsIgnoreCase(entry.getKey()) || "parent_id".equals(column)) {
                    // 行数据以新 ID 重建，忽略提交中携带的行 id/parent_id
                    continue;
                }
                if (!"id".equalsIgnoreCase(column) && !"parent_id".equals(column) && !existingColumns.contains(column)) {
                    // 未知列（回显别名等）剔除，仅记录
                    log.debug("剔除子表行未知列：table={}, column={}", childTable, column);
                    continue;
                }
                if (JsonValueCodec.isShadowColumn(column)) {
                    // 影子列由数据库生成，赋值会报错
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof Map || value instanceof Collection) {
                    // 嵌套结构（如行内子表）序列化为 JSON 文本，避免 Java 对象直接写文本列；
                    // 主表开关打开时子表同步走统一信封，保证读写协议一致
                    value = ShadowColumnSupport.isJsonProtocolEnabled()
                            ? JsonValueCodec.wrap(value)
                            : com.alibaba.fastjson2.JSON.toJSONString(value);
                }
                record.put(column, value);
            }
            record.put("created_time", now);
            if (userId != null && !userId.isEmpty()) {
                record.put("created_by", userId);
                record.put("department_id", SecurityUtils.getDepartmentId());
            }
            // organization_id 与主表插入同口径显式带上：组织回填拦截器可能被同请求先前的
            // 元数据查询置位 ignore 而跳过本语句（NOT NULL 列直接失败），不依赖拦截器状态
            String organizationId = OrganizationContextHolder.getOrganizationId();
            if (StringUtils.isEmpty(organizationId)) {
                organizationId = SecurityUtils.getOrganizationId();
            }
            if (!StringUtils.isEmpty(organizationId)) {
                record.put("organization_id", organizationId);
            }
            dynamicTableDataMapper.insert(childTable, record);
        }
        log.debug("保存子表行数据：table={}, parentId={}, rows={}", childTable, parentId, rows.size());
    }

    @Override
    @DS("#database")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findRows(String database, String childTable, String parentId) {
        assertValidIdentifier(childTable);
        if (parentId == null || parentId.isEmpty()) {
            return new ArrayList<>();
        }
        return dynamicTableDataMapper.selectByParentId(childTable, parentId);
    }

    @Override
    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteByParentIds(String database, String childTable, Collection<String> parentIds) {
        assertValidIdentifier(childTable);
        if (parentIds == null || parentIds.isEmpty()) {
            return;
        }
        dynamicTableDataMapper.deleteByParentIds(childTable, new ArrayList<>(parentIds));
        log.debug("级联删除子表行数据：table={}, parentIds={}", childTable, parentIds);
    }

    @Override
    @DS("#database")
    @Transactional(readOnly = true)
    public void assertUniqueFields(String database, String tableName, JSONObject metaData, Map<String, Object> data) {
        if (metaData == null || data == null) {
            return;
        }
        List<String[]> uniqueFields = new ArrayList<>();
        collectUniqueFields(metaData.getJSONArray("schemas"), uniqueFields);
        if (uniqueFields.isEmpty()) {
            return;
        }
        String excludeId = data.get("id") == null ? null : data.get("id").toString();
        for (String[] field : uniqueFields) {
            // field[0]=字段编码（formData 键），field[1]=标签，field[2]=数据库列名
            Object value = data.get(field[0]);
            if (value == null || "".equals(value.toString())) {
                continue;
            }
            int count = dynamicTableDataMapper.countByColumnValue(tableName, field[2], value.toString(), excludeId);
            if (count > 0) {
                throw new ServiceException("字段「" + field[1] + "」的值已存在，不允许重复：" + value);
            }
        }
    }

    @Override
    @DS("#database")
    @Transactional(readOnly = true)
    public Map<String, String> buildRowFieldConditions(String database, String mainTableCode, JSONObject metaData,
                                                       Map<String, Object> filters, List<DynamicFormColumnDTO> columns) {
        Map<String, String> conditions = new LinkedHashMap<>();
        if (filters == null || filters.isEmpty()) {
            return conditions;
        }
        if (!isValidIdentifier(mainTableCode)) {
            throw new ServiceException("表单编码不合法：" + mainTableCode);
        }
        List<SubTableDef> subTables = resolveSubTables(mainTableCode, metaData);
        // 子表列结构按表缓存：条件类型以子表表实际元数据为准，比列配置（dynamic_form_column）更可信
        Map<String, Map<String, String>> columnsCache = new HashMap<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object raw = entry.getValue();
            if (raw == null || "".equals(raw)) {
                continue;
            }
            int dot = key.indexOf('.');
            String column = dot > 0 ? FormTransferTable.toDbColumnName(key.substring(dot + 1)) : null;
            SubTableDef def = dot > 0 ? matchSubTable(subTables, key.substring(0, dot)) : null;
            if (def == null || column == null
                    || !isValidIdentifier(def.getChildTable()) || !isValidIdentifier(column)) {
                throw staleField(columns, key);
            }
            String columnType = columnsCache.computeIfAbsent(def.getChildTable(),
                    table -> queryTableColumnTypes(database, table)).get(column);
            if (columnType == null) {
                throw staleField(columns, key);
            }
            String condition = FormTransferTable.subTableExistsCondition(
                    def.getChildTable(), column, columnType, mainTableCode, raw);
            if (condition != null) {
                conditions.put(key, condition);
            }
        }
        return conditions;
    }

    /**
     * 列配置里的子表行字段在当前表单/子表表结构中已不存在（子表被删除或行内字段改版）
     */
    private ServiceException staleField(List<DynamicFormColumnDTO> columns, String key) {
        String name = key;
        if (columns != null) {
            for (DynamicFormColumnDTO column : columns) {
                if (key.equals(column.getFiledCode()) && column.getFiledName() != null) {
                    name = column.getFiledName();
                    break;
                }
            }
        }
        return new ServiceException("该子表字段已失效：" + name);
    }

    @Override
    @DS("#database")
    @Transactional(readOnly = true)
    public String buildRowFieldSortExpression(String database, String mainTableCode, String columnCode,
                                              boolean ascending) {
        if (!isValidIdentifier(mainTableCode)) {
            throw new ServiceException("表单编码不合法：" + mainTableCode);
        }
        int dot = columnCode == null ? -1 : columnCode.indexOf('.');
        String childTable = dot > 0 ? toChildTableName(mainTableCode, columnCode.substring(0, dot)) : null;
        String column = dot > 0 ? FormTransferTable.toDbColumnName(columnCode.substring(dot + 1)) : null;
        if (childTable == null || column == null || !isValidIdentifier(childTable) || !isValidIdentifier(column)
                || !queryTableColumnTypes(database, childTable).containsKey(column)) {
            // 列表排序没有列配置可查显示名，失效提示只能带列编码
            throw staleField(null, columnCode);
        }
        return "(SELECT " + (ascending ? "MIN" : "MAX") + "(st.`" + column + "`) FROM `" + childTable
                + "` st WHERE st.parent_id = `" + mainTableCode + "`.id)";
    }

    /**
     * 子表数据库表名（主表编码_子表字段编码，与建表同源）
     */
    private String toChildTableName(String mainTableCode, String subField) {
        String column = FormTransferTable.toDbColumnName(subField);
        return column == null ? null : mainTableCode + "_" + column;
    }

    /**
     * 子表实际列名 → 数据库类型，表不存在或查询失败返回空集（元数据查询需忽略组织数据范围）
     */
    private Map<String, String> queryTableColumnTypes(String database, String childTable) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<MysqlTableInfo> tableColumns =
                    dynamicTableMapper.executeQueryForColumns(databaseDialect.buildGetTableColumnsSql(childTable));
            Map<String, String> types = new HashMap<>();
            if (tableColumns != null) {
                for (MysqlTableInfo info : tableColumns) {
                    if (info != null && info.getColumn_name() != null) {
                        types.put(info.getColumn_name(), info.getData_type());
                    }
                }
            }
            return types;
        } catch (Exception e) {
            log.warn("查询子表列结构失败：table={}, {}", childTable, e.getMessage());
            return new HashMap<>();
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
        }
    }

    /**
     * 按列编码中的子表字段定位子表定义：列配置里的子表字段是设计器原名（可含连字符），
     * 与子表表名推导同源，比对前统一到数据库列名形态
     */
    private SubTableDef matchSubTable(List<SubTableDef> subTables, String subField) {
        String column = FormTransferTable.toDbColumnName(subField);
        if (column == null) {
            return null;
        }
        for (SubTableDef def : subTables) {
            if (column.equals(FormTransferTable.toDbColumnName(def.getField()))) {
                return def;
            }
        }
        return null;
    }

    /**
     * 递归收集「不允许重复」（props.unique=true）的输入字段：字段编码/标签/数据库列名
     */
    private void collectUniqueFields(JSONArray nodes, List<String[]> out) {
        if (nodes == null) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null) {
                continue;
            }
            JSONObject props = node.getJSONObject("props");
            if (props == null) {
                props = node.getJSONObject("componentProps");
            }
            if (node.getBoolean("input") == Boolean.TRUE
                    && node.getString("field") != null
                    && props != null && Boolean.TRUE.equals(props.getBoolean("unique"))) {
                String field = node.getString("field");
                String label = node.getString("label") == null ? field : node.getString("label");
                out.add(new String[]{field, label, toDbColumn(field)});
            }
            collectUniqueFields(node.getJSONArray("children"), out);
        }
    }

    /**
     * 字段编码转数据库列名（与建表列名推导同一转换），并校验合法标识符
     */
    private String toDbColumn(String field) {
        String column = FormTransferTable.toDbColumnName(field);
        assertValidIdentifier(column);
        return column;
    }

    private static void assertValidIdentifier(String name) {
        if (!isValidIdentifier(name)) {
            throw new IllegalArgumentException("非法的表名或列名：" + name);
        }
    }

    private static boolean isValidIdentifier(String name) {
        return name != null && IDENTIFIER_PATTERN.matcher(name).matches();
    }
}
