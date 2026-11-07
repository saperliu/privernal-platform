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

package com.privernal.dynamic.dba.utils;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dba.annotation.LengthCount;
import com.privernal.dynamic.dba.constants.Constants;
import com.privernal.dynamic.dba.dialect.DynamicTypeContext;
import com.privernal.dynamic.dba.type.DatabaseTypeMapper;
import com.privernal.dynamic.dba.vo.CreateTableVo;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 动态表单与表结构转换工具
 * <p>
 * 负责将前端动态表单的 JSON 定义转换为数据库表结构（{@link CreateTableVo} 列表），
 * 并通过 {@link DatabaseTypeMapper} 实现跨数据库的类型映射。
 * </p>
 *
 * @author saperliu
 * @version 1.1.0
 * @see CreateTableVo
 * @see DatabaseTypeMapper
 */
public class FormTransferTable {

    private static final Logger log = LoggerFactory.getLogger(FormTransferTable.class);

    /**
     * 数值类数据库类型（information_schema 的 data_type 形态，含 MySQL / PostgreSQL 常见写法）
     */
    private static final Set<String> NUMERIC_TYPES = new HashSet<>(Arrays.asList(
            "tinyint", "smallint", "mediumint", "int", "integer", "bigint", "serial", "bigserial", "smallserial",
            "decimal", "numeric", "float", "double", "double precision", "real", "bit", "money", "smallmoney"));

    /**
     * 时间类数据库类型
     */
    private static final Set<String> TEMPORAL_TYPES = new HashSet<>(Arrays.asList(
            "date", "datetime", "timestamp", "timestamp without time zone", "timestamp with time zone",
            "time", "time without time zone", "time with time zone", "year"));

    private FormTransferTable() {
    }

    /**
     * 组件类型转换为数据库列类型（用于组件库组件）
     *
     * @param compType 组件类型（如 number, textfield, datetime 等）
     * @return 对应数据库类型名
     */
    public static String formCompTransferColumnType(String compType) {
        DatabaseTypeMapper mapper = getTypeMapper();
        switch (compType) {
            case "number":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.INT);
            case "textfield":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "datetime":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.DATETIME);
            case "address":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.TEXT);
            case "checkbox":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "time":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.TIME);
            case "selectboxes":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "select":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "radio":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            default:
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.TEXT);
        }
    }

    /**
     * 组件类型转换为数据库列类型（用于表单设计器组件）
     *
     * @param compType 组件类型（如 number, decimal, text, textarea 等）
     * @return 对应数据库类型名
     */
    public static String formCompToColumnType(String compType) {
        DatabaseTypeMapper mapper = getTypeMapper();
        switch (compType) {
            case "number":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.INT);
            case "decimal":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.DECIMAL);
            case "text":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "textarea":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "password":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "date":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.DATETIME);
            case "address":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.TEXT);
            case "checkbox":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "time":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.TIME);
            case "selectboxes":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "select":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "radio":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            case "":
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR);
            default:
                return mapper.getDatabaseTypeName(DatabaseTypeMapper.TEXT);
        }
    }

    /**
     * 初始化建表 Map 结构
     * <p>
     * 创建包含所有建表操作分类的 Map，用于在解析表单字段时
     * 自动归类到新建、修改、删除等不同操作中。
     * </p>
     *
     * @return 建表操作 Map
     */
    public static Map<String, Map<String, List<CreateTableVo>>> initTableMap() {
        Map<String, Map<String, List<CreateTableVo>>> baseTableMap = new HashMap<String, Map<String, List<CreateTableVo>>>();
        baseTableMap.put(Constants.NEW_TABLE_MAP, new HashMap<String, List<CreateTableVo>>());
        baseTableMap.put(Constants.MODIFY_TABLE_MAP, new HashMap<String, List<CreateTableVo>>());
        baseTableMap.put(Constants.ADD_TABLE_MAP, new HashMap<String, List<CreateTableVo>>());
        baseTableMap.put(Constants.REMOVE_TABLE_MAP, new HashMap<String, List<CreateTableVo>>());
        baseTableMap.put(Constants.DROPKEY_TABLE_MAP, new HashMap<String, List<CreateTableVo>>());
        baseTableMap.put(Constants.DROPINDEXANDUNIQUE_TABLE_MAP, new HashMap<String, List<CreateTableVo>>());
        baseTableMap.put(Constants.ADDINDEX_TABLE_MAP, new HashMap<String, List<CreateTableVo>>());
        baseTableMap.put(Constants.ADDUNIQUE_TABLE_MAP, new HashMap<String, List<CreateTableVo>>());
        baseTableMap.put(Constants.MODIFY_FIELD_NAME, new HashMap<String, List<CreateTableVo>>());
        return baseTableMap;
    }

    /**
     * 从表单 JSON 数据中提取所有字段定义，生成建表 VO 列表
     *
     * @param data    表单数据（含 schemas 节点）
     * @param isCreate 是否为新建表
     * @return 建表字段列表
     */
    public static List<CreateTableVo> getAllFields(Map<String, Object> data, boolean isCreate) {
        List<CreateTableVo> fieldList = new ArrayList<CreateTableVo>();
        DatabaseTypeMapper mapper = getTypeMapper();

        CreateTableVo paramId = new CreateTableVo();
        paramId.setFieldIsKey(true);
        paramId.setFieldLength(36);
        paramId.setFieldComment("ID");
        paramId.setFieldName(Constants.KEY_FIELD);
        paramId.setFieldIsAutoIncrement(false);
        paramId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        paramId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        fieldList.add(paramId);

        List schemas = (List) data.get("schemas");
        for (Object obj : schemas) {
            Map topMap = (Map) obj;
            List childList = (List) topMap.get("children");
            if (childList != null) {
                for (Object childObj : childList) {
                    Map childObjpMap = (Map) childObj;
                    // 根级子表与嵌套子表同口径：只保留主表 JSON 快照列，行内字段由
                    // ensureSubTables 落独立子表表；这里若照常递归会把行内字段平铺建进主表
                    if ("sub-table".equals(childObjpMap.get("type"))) {
                        appendFieldVo(childObjpMap, fieldList);
                        continue;
                    }
                    List childObjList = (List) childObjpMap.get("children");
                    getChildList(childObj, childObjList, fieldList);
                }
            }
        }

        formCommonColumn(fieldList);
        return fieldList;
    }

    /**
     * 递归解析子节点中的表单字段
     *
     * @param child    当前节点
     * @param childList 子节点列表
     * @param fieldList 输出字段列表
     */
    /**
     * 表单字段编码转数据库列名：驼峰转下划线，并将连字符等非法标识符字符统一替换为下划线。
     * 设计器字段编码沿用组件类型（如 color-picker_xxx），若不清洗，建表 DDL 与数据读写 SQL 都会因非法标识符失败。
     * DDL 推导与数据写入（transferName）必须使用同一转换，保证表单字段与数据库列全链路一致。
     */
    public static String toDbColumnName(String field) {
        if (field == null) {
            return null;
        }
        return StringUtils.toUnderScoreCase(field).replaceAll("[^0-9a-zA-Z_]", "_");
    }

    public static void getChildList(Object child, List childList, List<CreateTableVo> fieldList) {
        if (childList != null) {
            for (Object obj : childList) {
                Map childTwoMap = (Map) obj;
                // 子表（sub-table）的行内字段落在独立的子表数据库表（主表编码_子表编码），
                // 不再平铺建进主表；子表字段本身仍保留 TEXT 列存 JSON 快照（列表/流程变量兼容）
                if ("sub-table".equals(childTwoMap.get("type"))) {
                    appendFieldVo(childTwoMap, fieldList);
                    continue;
                }
                List chilTowList = (List) childTwoMap.get("children");
                getChildList(childTwoMap, chilTowList, fieldList);
            }
        }

        appendFieldVo(child, fieldList);
    }

    /**
     * 为单个 schema 节点生成建表字段 VO（input=true 的组件；sub-table 组件生成 JSON 快照 TEXT 列）
     */
    private static void appendFieldVo(Object child, List<CreateTableVo> fieldList) {
        Map jsonObject = (Map) child;
        if (jsonObject.get("input") != null && jsonObject.get("input").equals(true)) {
            CreateTableVo param = new CreateTableVo();
            param.setFieldDefaultValue(null);
            param.setFieldComment(null);

            String fieldName = jsonObject.get("field").toString();
            String dbColumnName = toDbColumnName(fieldName);
            param.setFieldName(dbColumnName);
            param.setComponentId(jsonObject.get("id").toString());

            DatabaseTypeMapper mapper = getTypeMapper();

            // 兼容新旧两代表单 schema：新版 phoenix-designer 属性在 props，旧版 epic-designer 在 componentProps；
            // 组件未配置属性时按空配置处理，避免空指针
            Object propsObj = jsonObject.get("props") != null ? jsonObject.get("props") : jsonObject.get("componentProps");
            Map propsMap = (propsObj instanceof Map) ? (Map) propsObj : null;

            if ("input".equals(jsonObject.get("type"))) {
                Object inputType = propsMap != null ? propsMap.get("type") : null;
                param.setFieldType(formCompToColumnType(inputType != null ? inputType.toString() : "text"));
            } else {
                if ("number".equals(jsonObject.get("type"))) {
                    Object precision = propsMap != null ? propsMap.get("precision") : null;
                    if (precision != null && Integer.parseInt(precision.toString()) > 0) {
                        param.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.DECIMAL));
                        param.setFieldDecimalLength(Integer.parseInt(precision.toString()));
                    } else {
                        param.setFieldType(formCompToColumnType(jsonObject.get("type").toString()));
                    }
                } else {
                    param.setFieldType(formCompToColumnType(jsonObject.get("type").toString()));
                }
            }

            param.setFieldComment(jsonObject.get("label").toString());

            if (propsMap != null) {
                if (propsMap.get("defaultValue") != null && !"".equals(propsMap.get("defaultValue"))) {
                    param.setFieldDefaultValue(propsMap.get("defaultValue").toString());
                } else {
                    param.setFieldDefaultValue(null);
                }

                // 索引/唯一约束的目标列：多值字段在影子列开启时落到"首值影子列"，否则落业务列。
                // 原实现直接用业务列名，对落 text 的多值列 MySQL 会报 1170（TEXT 需前缀长度）而静默失效。
                String indexedColumn = dbColumnName;
                boolean multiValue = ShadowColumnSupport.isMultiValueComponent(
                        String.valueOf(jsonObject.get("type")), propsMap);
                if (multiValue) {
                    String candidateShadow = JsonValueCodec.shadowColumn(dbColumnName);
                    if (candidateShadow != null && ShadowColumnSupport.isShadowEnabled()
                            && supportsShadowColumnNow()) {
                        indexedColumn = candidateShadow;
                    }
                }

                if (propsMap.get("unique") != null && !"".equals(propsMap.get("unique"))) {
                    boolean isUnique = (Boolean) propsMap.get("unique");
                    param.setFieldIsUnique(isUnique);
                    if (isUnique) {
                        param.setFieldUniqueName(Constants.UNI + dbColumnName);
                        // 业务唯一索引带 deleted_time：数据删除为软删（T15），单列唯一会被已删行占位
                        // 导致同值无法重录；(col, deleted_time) 允许多行历史软删共存
                        param.setFieldUniqueValue(Arrays.asList(indexedColumn, "deleted_time"));
                    }
                }

                if (propsMap.get("dbIndex") != null && !"".equals(propsMap.get("dbIndex"))) {
                    if ((Boolean) propsMap.get("dbIndex")) {
                        param.setFieldIndexName(Constants.IDX + dbColumnName);
                        param.setFieldIndexValue(Arrays.asList(indexedColumn));
                    }
                }

                int length = mapper.getLengthCount(param.getFieldType());
                param.setFieldTypeLength(length);

                if (propsMap.get("maxlength") != null && !"".equals(propsMap.get("maxlength"))) {
                    param.setFieldLength(Integer.parseInt(propsMap.get("maxlength").toString()));
                } else {
                    if (length == 1) {
                        param.setFieldLength(250);
                    }
                    if (length == 2) {
                        param.setFieldLength(15);
                        if (param.getFieldDecimalLength() <= 0) {
                            param.setFieldDecimalLength(6);
                        }
                    }
                }
            } else {
                int length = mapper.getLengthCount(param.getFieldType());
                param.setFieldTypeLength(length);
                if (length == 1) {
                    if (!param.getFieldType().toLowerCase().contains("bit")
                            && !param.getFieldType().toLowerCase().contains("boolean")) {
                        param.setFieldLength(250);
                    } else {
                        param.setFieldLength(60);
                    }
                }
                if (length == 2) {
                    param.setFieldLength(10);
                    param.setFieldDecimalLength(2);
                }
                param.setFieldIsNull(true);
            }

            if (jsonObject.get("rules") != null) {
                List validateList = (List) jsonObject.get("rules");
                if (validateList != null) {
                    boolean flag = false;
                    for (Object obj : validateList) {
                        Map validate = (Map) obj;
                        if ("true".equals(validate.get("required"))) {
                            flag = true;
                        }
                    }
                    param.setFieldIsNull(!flag);
                } else {
                    param.setFieldIsNull(true);
                }
            } else {
                param.setFieldIsNull(true);
            }

            fieldList.add(param);

            // 多值/复合值字段追加"首值影子列"：原列类型不动（避免存量 DDL 变更与 JSON 列的查询类型冲突），
            // 由数据库维护一个可索引、可分组的标量首值列，供 unique/dbIndex 与后续查询路由使用。
            // 方言不支持或开关未开时 buildShadowColumn 返回 null，整体退化为现状行为。
            if (multiValueForShadow(jsonObject, propsMap)) {
                CreateTableVo shadow = ShadowColumnSupport.buildShadowColumn(param);
                if (shadow != null) {
                    fieldList.add(shadow);
                }
            }
        }
    }

    /**
     * 判断 schema 节点是否为需要影子列的多值组件。
     */
    private static boolean multiValueForShadow(Map jsonObject, Map propsMap) {
        if (jsonObject == null || jsonObject.get("type") == null) {
            return false;
        }
        return ShadowColumnSupport.isMultiValueComponent(String.valueOf(jsonObject.get("type")), propsMap);
    }

    /**
     * 解析子表节点的行字段列表（用于创建子表数据库表）
     * <p>
     * 子表表结构 = 主键 id + 父表关联 parent_id + 行内输入字段（复用 {@link #getChildList} 解析，
     * 保留行内字段的 unique/index/required 配置）+ 精简公共列（审计/租户/组织，不含 title/status 等主表业务列）。
     * </p>
     *
     * @param subTableNode 子表组件 schema 节点（type=sub-table，children 为行内组件）
     * @return 子表建表字段列表
     */
    public static List<CreateTableVo> getSubTableFields(Map subTableNode) {
        List<CreateTableVo> fieldList = new ArrayList<>();
        DatabaseTypeMapper mapper = getTypeMapper();

        CreateTableVo paramId = new CreateTableVo();
        paramId.setFieldIsKey(true);
        paramId.setFieldLength(36);
        paramId.setFieldComment("ID");
        paramId.setFieldName(Constants.KEY_FIELD);
        paramId.setFieldIsAutoIncrement(false);
        paramId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        paramId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        fieldList.add(paramId);

        CreateTableVo parentId = new CreateTableVo();
        parentId.setFieldIsKey(false);
        parentId.setFieldLength(36);
        parentId.setFieldComment("父表数据ID");
        parentId.setFieldName("parent_id");
        parentId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        parentId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        parentId.setFieldIsNull(false);
        fieldList.add(parentId);

        Object children = subTableNode.get("children");
        if (children instanceof List) {
            // 逐个行内字段解析（getChildList 会把 child 自身建列），
            // 不能把子表节点整体传入——否则子表同名 TEXT 列会被误建进子表表
            for (Object rowChild : (List) children) {
                if (!(rowChild instanceof Map)) {
                    continue;
                }
                Map rowNode = (Map) rowChild;
                if ("sub-table".equals(rowNode.get("type"))) {
                    // 行内不支持嵌套子表
                    continue;
                }
                getChildList(rowNode, (List) rowNode.get("children"), fieldList);
            }
        }

        formSubTableCommonColumn(fieldList);
        return fieldList;
    }

    /**
     * 添加子表公共字段（审计/租户/组织，不含主表的 title/status/remark 等业务列）
     *
     * @param fieldList 字段列表
     */
    private static void formSubTableCommonColumn(List<CreateTableVo> fieldList) {
        DatabaseTypeMapper mapper = getTypeMapper();

        CreateTableVo tenantId = new CreateTableVo();
        tenantId.setFieldIsKey(false);
        tenantId.setFieldLength(255);
        tenantId.setFieldComment("租户号");
        tenantId.setFieldName("tenant_id");
        tenantId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        tenantId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        tenantId.setFieldIsNull(false);
        tenantId.setFieldDefaultValue("system-default");
        fieldList.add(tenantId);

        CreateTableVo revision = new CreateTableVo();
        revision.setFieldIsKey(false);
        revision.setFieldComment("乐观锁");
        revision.setFieldName("revision");
        revision.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.INT));
        revision.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.INT));
        revision.setFieldIsNull(false);
        revision.setFieldDefaultValue("1");
        fieldList.add(revision);

        CreateTableVo createdBy = new CreateTableVo();
        createdBy.setFieldIsKey(false);
        createdBy.setFieldLength(36);
        createdBy.setFieldComment("创建人");
        createdBy.setFieldName("created_by");
        createdBy.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        createdBy.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        createdBy.setFieldIsNull(false);
        fieldList.add(createdBy);

        CreateTableVo createdTime = new CreateTableVo();
        createdTime.setFieldIsKey(false);
        createdTime.setFieldComment("创建时间");
        createdTime.setFieldName("created_time");
        createdTime.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.DATETIME));
        createdTime.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.DATETIME));
        createdTime.setFieldIsNull(false);
        fieldList.add(createdTime);

        CreateTableVo updatedBy = new CreateTableVo();
        updatedBy.setFieldIsKey(false);
        updatedBy.setFieldLength(36);
        updatedBy.setFieldComment("更新人");
        updatedBy.setFieldName("updated_by");
        updatedBy.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        updatedBy.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        updatedBy.setFieldDefaultValue(null);
        updatedBy.setFieldIsNull(true);
        fieldList.add(updatedBy);

        CreateTableVo updatedTime = new CreateTableVo();
        updatedTime.setFieldIsKey(false);
        updatedTime.setFieldComment("更新时间");
        updatedTime.setFieldName("updated_time");
        updatedTime.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.DATETIME));
        updatedTime.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.DATETIME));
        updatedTime.setFieldDefaultValue(null);
        updatedTime.setFieldIsNull(true);
        fieldList.add(updatedTime);

        CreateTableVo deletedFlag = new CreateTableVo();
        deletedFlag.setFieldIsKey(false);
        deletedFlag.setFieldLength(1);
        deletedFlag.setFieldComment("是否删除");
        deletedFlag.setFieldName("deleted_flag");
        deletedFlag.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        deletedFlag.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        deletedFlag.setFieldIsNull(false);
        deletedFlag.setFieldDefaultValue("N");
        fieldList.add(deletedFlag);

        CreateTableVo departmentId = new CreateTableVo();
        departmentId.setFieldIsKey(false);
        departmentId.setFieldLength(36);
        departmentId.setFieldComment("所属部门");
        departmentId.setFieldName("department_id");
        departmentId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        departmentId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        departmentId.setFieldIsNull(false);
        fieldList.add(departmentId);

        CreateTableVo organizationId = new CreateTableVo();
        organizationId.setFieldIsKey(false);
        organizationId.setFieldLength(36);
        organizationId.setFieldComment("所属组织");
        organizationId.setFieldName("organization_id");
        organizationId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        organizationId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        organizationId.setFieldIsNull(false);
        fieldList.add(organizationId);
    }

    /**
     * 添加公共字段（租户、乐观锁、审计字段等）
     *
     * @param fieldList 字段列表
     */
    public static void formCommonColumn(List<CreateTableVo> fieldList) {
        DatabaseTypeMapper mapper = getTypeMapper();

        CreateTableVo tenantId = new CreateTableVo();
        tenantId.setFieldIsKey(false);
        tenantId.setFieldLength(255);
        tenantId.setFieldComment("租户号");
        tenantId.setFieldName("tenant_id");
        tenantId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        tenantId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        tenantId.setFieldIsNull(false);
        tenantId.setFieldDefaultValue("system-default");
        fieldList.add(tenantId);

        CreateTableVo revision = new CreateTableVo();
        revision.setFieldIsKey(false);
        revision.setFieldComment("乐观锁");
        revision.setFieldName("revision");
        revision.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.INT));
        revision.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.INT));
        revision.setFieldIsNull(false);
        revision.setFieldDefaultValue("1");
        fieldList.add(revision);

        CreateTableVo createdBy = new CreateTableVo();
        createdBy.setFieldIsKey(false);
        createdBy.setFieldLength(36);
        createdBy.setFieldComment("创建人");
        createdBy.setFieldName("created_by");
        createdBy.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        createdBy.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        createdBy.setFieldIsNull(false);
        createdBy.setFieldDefaultValue(null);
        fieldList.add(createdBy);

        CreateTableVo createdTime = new CreateTableVo();
        createdTime.setFieldIsKey(false);
        createdTime.setFieldComment("创建时间");
        createdTime.setFieldName("created_time");
        createdTime.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.DATETIME));
        createdTime.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.DATETIME));
        createdTime.setFieldIsNull(false);
        createdTime.setFieldDefaultValue(null);
        fieldList.add(createdTime);

        CreateTableVo updatedBy = new CreateTableVo();
        updatedBy.setFieldIsKey(false);
        updatedBy.setFieldLength(36);
        updatedBy.setFieldComment("更新人");
        updatedBy.setFieldName("updated_by");
        updatedBy.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        updatedBy.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        updatedBy.setFieldDefaultValue(null);
        updatedBy.setFieldIsNull(true);
        fieldList.add(updatedBy);

        CreateTableVo updatedTime = new CreateTableVo();
        updatedTime.setFieldIsKey(false);
        updatedTime.setFieldComment("更新时间");
        updatedTime.setFieldName("updated_time");
        updatedTime.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.DATETIME));
        updatedTime.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.DATETIME));
        updatedTime.setFieldDefaultValue(null);
        updatedTime.setFieldIsNull(true);
        fieldList.add(updatedTime);

        CreateTableVo deletedFlag = new CreateTableVo();
        deletedFlag.setFieldIsKey(false);
        deletedFlag.setFieldLength(1);
        deletedFlag.setFieldComment("是否删除");
        deletedFlag.setFieldName("deleted_flag");
        deletedFlag.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        deletedFlag.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        deletedFlag.setFieldIsNull(false);
        deletedFlag.setFieldDefaultValue("N");
        fieldList.add(deletedFlag);

        CreateTableVo deletedBy = new CreateTableVo();
        deletedBy.setFieldIsKey(false);
        deletedBy.setFieldLength(36);
        deletedBy.setFieldComment("删除人");
        deletedBy.setFieldName("deleted_by");
        deletedBy.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        deletedBy.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        deletedBy.setFieldDefaultValue(null);
        deletedBy.setFieldIsNull(true);
        fieldList.add(deletedBy);

        CreateTableVo deletedTime = new CreateTableVo();
        deletedTime.setFieldIsKey(false);
        deletedTime.setFieldComment("删除时间");
        deletedTime.setFieldName("deleted_time");
        deletedTime.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.DATETIME));
        deletedTime.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.DATETIME));
        deletedTime.setFieldDefaultValue(null);
        deletedTime.setFieldIsNull(true);
        fieldList.add(deletedTime);

        CreateTableVo departmentId = new CreateTableVo();
        departmentId.setFieldIsKey(false);
        departmentId.setFieldLength(36);
        departmentId.setFieldComment("所属部门");
        departmentId.setFieldName("department_id");
        departmentId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        departmentId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        departmentId.setFieldIsNull(false);
        fieldList.add(departmentId);

        CreateTableVo organizationId = new CreateTableVo();
        organizationId.setFieldIsKey(false);
        organizationId.setFieldLength(36);
        organizationId.setFieldComment("所属组织");
        organizationId.setFieldName("organization_id");
        organizationId.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        organizationId.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        organizationId.setFieldIsNull(false);
        fieldList.add(organizationId);

        CreateTableVo remark = new CreateTableVo();
        remark.setFieldIsKey(false);
        remark.setFieldLength(900);
        remark.setFieldComment("备注");
        remark.setFieldName("remark");
        remark.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        remark.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        remark.setFieldIsNull(true);
        fieldList.add(remark);

        CreateTableVo title = new CreateTableVo();
        title.setFieldIsKey(false);
        title.setFieldLength(255);
        title.setFieldComment("数据标题");
        title.setFieldName("title");
        title.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        title.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.VARCHAR));
        title.setFieldIsNull(true);
        fieldList.add(title);

        CreateTableVo status = new CreateTableVo();
        status.setFieldIsKey(false);
        status.setFieldComment("状态");
        status.setFieldName("status");
        status.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.INT));
        status.setFieldTypeLength(mapper.getLengthCount(DatabaseTypeMapper.INT));
        status.setFieldIsNull(false);
        status.setFieldDefaultValue("1");
        fieldList.add(status);
    }

    /**
     * 获取 MySQL 类型与长度的映射表（兼容旧逻辑）
     */
    public static Map<String, Object> mySqlTypeAndLengthMap() {
        Field[] fields = com.privernal.dynamic.dba.constants.MySqlTypeConstant.class.getDeclaredFields();
        Map<String, Object> map = new HashMap<String, Object>();
        for (Field field : fields) {
            LengthCount lengthCount = field.getAnnotation(LengthCount.class);
            map.put(field.getName().toLowerCase(), lengthCount.LengthCount());
        }
        return map;
    }

    /**
     * 根据字段类型构建动态 SQL 查询条件
     * <p>
     * 支持 datetime、time、int、decimal 等范围查询，以及关键词模糊查询。
     * </p>
     *
     * @param data   数据条件
     * @param fields 字段定义列表
     */
    public static void dataGetSQL(Map<String, Object> data, List<DynamicFormColumnDTO> fields) {
        DatabaseTypeMapper mapper = getTypeMapper();
        Set<String> unmatchedKeys = new HashSet<>();
        Set<String> emptyValueKeys = new HashSet<>();
        for (String key : data.keySet()) {
            if (Constants.PAGE_NUM.equals(key) || Constants.PAGE_SIZE.equals(key) || Constants.ORDER_BY.equals(key)) {
                // 分页/排序协议键由上层解析，不参与条件构建也不剔除
                continue;
            }
            DynamicFormColumnDTO field = findField(fields, key);
            if (field == null) {
                // 未在表单列定义中配置的字段不允许作为查询条件：
                // 其值会以 SQL 片段形式拼进 WHERE 子句，直接透传存在注入风险
                unmatchedKeys.add(key);
                continue;
            }
            assertValidColumnCode(key);
            if (data.get(key) != null && field.getFiledType().equals(mapper.getDatabaseTypeName(DatabaseTypeMapper.DATETIME))) {
                List list = (List) data.get(key);
                data.put(key, "  " + key + " BETWEEN '" + escapeLiteral(list.get(0)) + "' and  '" + escapeLiteral(list.get(1)) + "'");
            } else if (data.get(key) != null && field.getFiledType().equals(mapper.getDatabaseTypeName(DatabaseTypeMapper.TIME))) {
                List list = (List) data.get(key);
                data.put(key, "  " + key + " BETWEEN '" + escapeLiteral(list.get(0)) + "' and  '" + escapeLiteral(list.get(1)) + "'");
            } else if (data.get(key) != null && field.getFiledType().equals(mapper.getDatabaseTypeName(DatabaseTypeMapper.INT))) {
                List list = (List) data.get(key);
                data.put(key, "  " + key + " BETWEEN " + requireNumber(list.get(0)) + " and  " + requireNumber(list.get(1)));
            } else if (data.get(key) != null && field.getFiledType().equals(mapper.getDatabaseTypeName(DatabaseTypeMapper.DECIMAL))) {
                List list = (List) data.get(key);
                data.put(key, "  " + key + " BETWEEN " + requireNumber(list.get(0)) + " and  " + requireNumber(list.get(1)));
            } else {
                Object raw = data.get(key);
                if (raw != null && !"".equals(raw)) {
                    String fieldType = field.getFiledType() == null ? "" : field.getFiledType().toLowerCase();
                    boolean charType = fieldType.equals(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR).toLowerCase())
                            || fieldType.equals(mapper.getDatabaseTypeName(DatabaseTypeMapper.TEXT).toLowerCase())
                            || fieldType.equals(mapper.getDatabaseTypeName(DatabaseTypeMapper.MEDIUMTEXT).toLowerCase())
                            || fieldType.equals(mapper.getDatabaseTypeName(DatabaseTypeMapper.LONGTEXT).toLowerCase());
                    if (raw instanceof List) {
                        // 多选（复选框/多选下拉）：按 IN 查询解析，空值项剔除
                        List<?> values = (List<?>) raw;
                        StringBuilder inClause = new StringBuilder();
                        for (Object item : values) {
                            if (item == null || "".equals(item)) {
                                continue;
                            }
                            if (inClause.length() > 0) {
                                inClause.append(", ");
                            }
                            inClause.append('\'').append(escapeLiteral(item)).append('\'');
                        }
                        if (inClause.length() > 0) {
                            data.put(key, "  " + key + " IN (" + inClause + ")");
                        } else {
                            emptyValueKeys.add(key);
                        }
                    } else if (charType) {
                        // 字符组件（输入框/文本域）：like 查询
                        data.put(key, "  " + key + " LIKE '%" + escapeLiteral(raw) + "%'");
                    } else {
                        data.put(key, "  " + key + " = '" + escapeLiteral(raw) + "'");
                    }
                }
            }
        }
        data.keySet().removeAll(unmatchedKeys);
        data.keySet().removeAll(emptyValueKeys);
    }

    private static DynamicFormColumnDTO findField(List<DynamicFormColumnDTO> fields, String code) {
        if (fields == null) {
            return null;
        }
        for (DynamicFormColumnDTO field : fields) {
            if (field.getFiledCode() != null && field.getFiledCode().equals(code)) {
                return field;
            }
        }
        return null;
    }

    /**
     * 列编码拼入 SQL 片段前校验为合法标识符
     */
    private static void assertValidColumnCode(String code) {
        if (code == null || !code.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("非法的查询字段：" + code);
        }
    }

    /**
     * 是否为「子表字段.行内字段」形态的列编码：子表行内字段落在独立子表数据库表，不是主表列，
     * 不能作为主表 WHERE 列直接拼接（列配置见 AutoCreateTableServiceImpl#recordSubTableColumns）
     */
    public static boolean isSubTableColumnCode(String code) {
        return code != null && code.indexOf('.') > 0;
    }

    /**
     * 子表行字段的查询条件：关联子表表 EXISTS，主表数据只要有任一子表行命中即返回。
     * <p>
     * 行内字段值不在主表列上，条件必须下推到子表表（主表编码_子表字段编码）按 parent_id 关联；
     * 表名/列名由调用方先行白名单校验，列类型取子表表实际元数据（与主表查询同一条件规则），
     * 值一律校验或转义后拼接。
     * </p>
     *
     * @param childTable  子表数据库表名
     * @param childColumn 子表行内列名（下划线形态）
     * @param columnType  子表行内列的数据库类型
     * @param mainTable   主表表名（关联外层数据 ID）
     * @param raw         查询值（标量或集合）
     * @return WHERE 片段，无有效查询值时返回 null
     */
    public static String subTableExistsCondition(String childTable, String childColumn, String columnType,
                                                 String mainTable, Object raw) {
        String rowCondition = rowFieldCondition(childColumn, columnType, raw);
        if (rowCondition == null) {
            return null;
        }
        return " EXISTS (SELECT 1 FROM `" + childTable + "` st WHERE st.parent_id = `" + mainTable
                + "`.id AND " + rowCondition + ")";
    }

    /**
     * 子表行内列的取值条件，规则与 {@link #dataGetSQL} 中主表列一致：
     * 数值/时间列的两端值按 BETWEEN，数值列单值按数值等值，其余集合按 IN，文本单值按模糊匹配
     */
    private static String rowFieldCondition(String column, String columnType, Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        String quoted = "`" + column + "`";
        boolean numeric = isNumericType(columnType);
        boolean temporal = isTemporalType(columnType);
        Collection<?> values = raw instanceof Collection ? (Collection<?>) raw
                : (raw instanceof Object[] ? Arrays.asList((Object[]) raw) : null);
        if (values == null) {
            if (numeric) {
                return quoted + " = " + requireNumber(raw);
            }
            if (temporal) {
                return quoted + " = '" + escapeLiteral(raw) + "'";
            }
            return quoted + " LIKE '%" + escapeLiteral(raw) + "%'";
        }
        List<Object> present = new ArrayList<>();
        for (Object item : values) {
            if (item != null && !"".equals(item)) {
                present.add(item);
            }
        }
        if (present.isEmpty()) {
            return null;
        }
        if ((numeric || temporal) && present.size() == 2) {
            // 范围查询（数值列单值时前端也会包装成 [v, v]）
            return numeric
                    ? quoted + " BETWEEN " + requireNumber(present.get(0)) + " and " + requireNumber(present.get(1))
                    : quoted + " BETWEEN '" + escapeLiteral(present.get(0)) + "' and '" + escapeLiteral(present.get(1)) + "'";
        }
        StringBuilder inClause = new StringBuilder();
        for (Object item : present) {
            if (inClause.length() > 0) {
                inClause.append(", ");
            }
            inClause.append(numeric ? requireNumber(item) : "'" + escapeLiteral(item) + "'");
        }
        return quoted + " IN (" + inClause + ")";
    }

    /**
     * 数值类列：查询值不带引号拼接，必须按数值校验
     */
    private static boolean isNumericType(String columnType) {
        return columnType != null && NUMERIC_TYPES.contains(columnType.trim().toLowerCase());
    }

    /**
     * 时间类列：范围查询按字符串比较
     */
    private static boolean isTemporalType(String columnType) {
        return columnType != null && TEMPORAL_TYPES.contains(columnType.trim().toLowerCase());
    }

    /**
     * 供报表数据集解析列快照使用：物理列类型是否数值型（number 维度/度量判定）
     */
    public static boolean isNumericColumnType(String columnType) {
        return isNumericType(columnType);
    }

    /**
     * 供报表数据集解析列快照使用：物理列类型是否时间型（date 维度判定，可带粒度分组）
     */
    public static boolean isTemporalColumnType(String columnType) {
        return isTemporalType(columnType);
    }

    /**
     * 转义 SQL 字符串字面量中的单引号，防止值拼接破坏引号闭合
     */
    private static String escapeLiteral(Object value) {
        // 先转反斜杠再翻倍单引号：MySQL 默认把 \\ 当转义符，只翻倍引号可被 \\' 逃逸闭合
        return value.toString().replace("\\", "\\\\").replace("'", "''");
    }

    /**
     * 数值条件必须匹配数字格式（无引号拼接，必须严格校验）
     */
    private static String requireNumber(Object value) {
        String s = value.toString();
        if (!s.matches("-?\\d+(\\.\\d+)?")) {
            throw new IllegalArgumentException("非法的数值查询条件：" + s);
        }
        return s;
    }

    /**
     * 当前方言是否支持"生成列 + 索引"（影子列前提）。
     * 上下文未初始化（离线/单测）时按不支持处理：影子列是增量能力，不能因为它阻断建表。
     */
    private static boolean supportsShadowColumnNow() {
        com.privernal.dynamic.dba.dialect.DatabaseDialect dialect = DynamicTypeContext.getDialect();
        return dialect != null && dialect.supportsGeneratedShadowColumn();
    }

    /**
     * 获取当前类型映射器，若未初始化则回退到 MySQL 类型映射器
     *
     * @return 类型映射器实例
     */
    private static DatabaseTypeMapper getTypeMapper() {
        DatabaseTypeMapper mapper = DynamicTypeContext.getTypeMapper();
        if (mapper == null) {
            log.warn("DynamicTypeContext 未初始化，回退到 MySQLTypeMapper");
            mapper = new com.privernal.dynamic.dba.type.MySQLTypeMapper();
        }
        return mapper;
    }
}