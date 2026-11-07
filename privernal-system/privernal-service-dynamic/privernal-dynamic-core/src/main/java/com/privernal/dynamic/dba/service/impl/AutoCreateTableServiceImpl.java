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

package com.privernal.dynamic.dba.service.impl;

import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dba.annotation.*;
import com.privernal.dynamic.dba.constants.Constants;
import com.privernal.dynamic.dba.dialect.DatabaseDialect;
import com.privernal.dynamic.dba.dialect.DynamicTypeContext;
import com.privernal.dynamic.dba.init.SpringContextUtil;
import com.privernal.dynamic.dba.mapper.DynamicTableMapper;
import com.privernal.dynamic.dba.service.AutoCreateTableService;
import com.privernal.dynamic.dba.utils.FormListConfigUtils;
import com.privernal.dynamic.dba.type.DatabaseTypeMapper;
import com.privernal.dynamic.dba.utils.ClassTools;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dba.utils.JsonValueCodec;
import com.privernal.dynamic.dba.vo.CreateTableVo;
import com.privernal.dynamic.dba.vo.MysqlTableInfo;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.service.IDynamicFormColumnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.Map.Entry;

/**
 * 自动建表服务实现类
 * <p>
 * 项目启动时自动扫描配置目录中的 model 类（带有 {@link Table} 注解），
 * 根据数据库方言（{@link DatabaseDialect}）生成对应的 DDL SQL，
 * 实现跨数据库的自动建表与表结构变更。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>读取配置项 {@code privernal.dynamic.table.auto} 获取自动建表模式</li>
 *   <li>扫描 model 包中所有带 {@link Table} 注解的类</li>
 *   <li>对比 model 定义与数据库现有表结构，计算差异</li>
 *   <li>使用 {@link DatabaseDialect} 生成方言相关的 SQL 并执行</li>
 * </ol>
 *
 * @author saperliu
 * @version 1.1.0
 * @see AutoCreateTableService
 * @see DatabaseDialect
 */
@Transactional
@Service
@Slf4j
public class AutoCreateTableServiceImpl implements AutoCreateTableService {

    @Autowired
    private DynamicTableMapper dynamicTableMapper;

    @Autowired
    private SpringContextUtil springContextUtil;

    @Autowired
    private IDynamicFormColumnService iDynamicFormColumnService;

    @Autowired
    private DatabaseDialect databaseDialect;

    /**
     * 获取当前数据库方言的类型映射器
     *
     * @return 类型映射器实例
     */
    private DatabaseTypeMapper getTypeMapper() {
        return databaseDialect.getTypeMapper();
    }

    /**
     * 要扫描的 model 所在的包路径
     */
    private static String pack = null;

    /**
     * 自动创建模式：update 表示增量更新，create 表示删除原表后重建
     */
    private static String tableAuto = null;

    /**
     * 自动创建/更新表结构（推荐入口）
     * <p>
     * 从旧方法 {@link #createMysqlTable()} 迁移而来，功能一致但方法名不再绑定特定数据库。
     * </p>
     */
    @Override
    public void autoCreateTable() {
        doAutoCreateTable();
    }

    /**
     * 读取配置文件的三种状态（创建表、更新表、不做任何事情）
     *
     * @deprecated 请使用 {@link #autoCreateTable()} 替代
     */
    @Override
    @Deprecated
    public void createMysqlTable() {
        doAutoCreateTable();
    }

    /**
     * 执行自动建表的核心逻辑
     */
    private void doAutoCreateTable() {
        log.info("开始执行自动建表流程，当前方言：{}", databaseDialect.getName());

        // 读取配置信息
        pack = springContextUtil.getConfig(Constants.MODEL_PACK_KEY);
        tableAuto = springContextUtil.getConfig(Constants.TABLE_AUTO_KEY);

        log.info("自动建表模式：{}，扫描包路径：{}", tableAuto, pack);

        // 配置校验
        if (!"none".equals(tableAuto) && !"update".equals(tableAuto) && !"create".equals(tableAuto)) {
            log.warn("配置 mybatis.table.auto 错误无法识别，当前配置只支持 [none/update/create] 三种类型!");
            return;
        }

        // 不做任何事情
        if ("none".equals(tableAuto)) {
            log.info("配置 mybatis.table.auto=none，跳过自动建表");
            return;
        }

        // 从包 package 中获取所有的 Class
        Set<Class<?>> classes = ClassTools.getClasses(pack);
        log.info("扫描到 {} 个 model 类", classes.size());

        // 初始化用于存储各种操作表结构的容器
        Map<String, Map<String, List<CreateTableVo>>> baseTableMap = FormTransferTable.initTableMap();

        int tableCount = 0;
        // 循环全部的 model
        for (Class<?> clas : classes) {
            // 没有打注解不需要创建表
            if (null == clas.getAnnotation(Table.class)) {
                continue;
            }
            // 构建出全部表的增删改的 map
            buildTableMapConstruct(clas, baseTableMap);
            tableCount++;
        }

        log.info("需要处理的表数量：{}", tableCount);

        // 根据传入的 map，分别去创建或修改表结构
        createOrModifyTableConstruct(baseTableMap);

        log.info("自动建表流程完成");
    }


    /**
     * 构建出全部表的增删改的map
     *
     * @param clas         package中的model的Class
     * @param baseTableMap 用于存储各种操作表结构的容器
     */
    private void buildTableMapConstruct(Class<?> clas, Map<String, Map<String, List<CreateTableVo>>> baseTableMap) {

        // 获取model的table注解
        Table table = clas.getAnnotation(Table.class);

        // 1. 用于存表的全部字段
        List<CreateTableVo> allFieldList = getAllFields(getTypeMapper(), clas);
        if (allFieldList.size() == 0) {
            log.warn("扫描model发现" + clas.getName() + "没有建表字段请检查！");
            return;
        }

        // 如果配置文件配置的是create，表示将所有的表删掉重新创建
        if ("create".equals(tableAuto)) {
            dynamicTableMapper.executeDDL(databaseDialect.buildDropTableSql(table.name()));
        }

        // 先查该表是否以存在
        int exist = dynamicTableMapper.executeQueryForCount(databaseDialect.buildTableExistsSql(table.name()));

        // 不存在时
        if (exist == 0) {
            baseTableMap.get(Constants.NEW_TABLE_MAP).put(table.name(), allFieldList);
            baseTableMap.get(Constants.ADDINDEX_TABLE_MAP).put(table.name(), getAddIndexList(null, allFieldList));
            baseTableMap.get(Constants.ADDUNIQUE_TABLE_MAP).put(table.name(), getAddUniqueList(null, allFieldList));
            return;
        }

        // 已存在时理论上做修改的操作，这里查出该表的结构
        List<MysqlTableInfo> tableColumnList = dynamicTableMapper.executeQueryForColumns(databaseDialect.buildGetTableColumnsSql(table.name()));

        // 从sysColumns中取出我们需要比较的列的List
        // 先取出name用来筛选出增加和删除的字段
        List<String> columnNames = ClassTools.getPropertyValueList(tableColumnList, MysqlTableInfo.COLUMN_NAME_KEY);

        // 验证对比从model中解析的allFieldList与从数据库查出来的columnList
        // 2. 找出增加的字段
        List<CreateTableVo> addFieldList = getAddFieldList(table, allFieldList, columnNames);

        // 3. 找出删除的字段
        List<CreateTableVo> removeFieldList = getRemoveFieldList(table, columnNames, allFieldList);

        // 4. 找出更新的字段
        List<CreateTableVo> modifyFieldList = getModifyFieldList(table, columnNames, tableColumnList, allFieldList);

        // 5. 找出需要删除主键的字段
        List<CreateTableVo> dropKeyFieldList = getDropKeyFieldList(table, columnNames, tableColumnList, allFieldList);

        // 查询当前表中全部的索引和唯一约束
        Set<String> allIndexAndUniqueNames = dynamicTableMapper.executeQueryForIndexes(databaseDialect.buildGetTableIndexesSql(table.name()));

        // 6. 找出需要删除的索引和唯一约束
        List<CreateTableVo> dropIndexAndUniqueFieldList = getDropIndexAndUniqueList(allIndexAndUniqueNames, allFieldList);

        // 7. 找出需要新增的索引
        List<CreateTableVo> addIndexFieldList = getAddIndexList(allIndexAndUniqueNames, allFieldList);

        // 8. 找出需要新增的唯一约束
        List<CreateTableVo> addUniqueFieldList = getAddUniqueList(allIndexAndUniqueNames, allFieldList);

        if (!addFieldList.isEmpty()) {
            baseTableMap.get(Constants.ADD_TABLE_MAP).put(table.name(), addFieldList);
        }
        if (!removeFieldList.isEmpty()) {
            baseTableMap.get(Constants.REMOVE_TABLE_MAP).put(table.name(), removeFieldList);
        }
        if (!modifyFieldList.isEmpty()) {
            baseTableMap.get(Constants.MODIFY_TABLE_MAP).put(table.name(), modifyFieldList);
        }
        if (!dropKeyFieldList.isEmpty()) {
            baseTableMap.get(Constants.DROPKEY_TABLE_MAP).put(table.name(), dropKeyFieldList);
        }
        if (!dropIndexAndUniqueFieldList.isEmpty()) {
            baseTableMap.get(Constants.DROPINDEXANDUNIQUE_TABLE_MAP).put(table.name(), dropIndexAndUniqueFieldList);
        }
        if (!addIndexFieldList.isEmpty()) {
            baseTableMap.get(Constants.ADDINDEX_TABLE_MAP).put(table.name(), addIndexFieldList);
        }
        if (!addUniqueFieldList.isEmpty()) {
            baseTableMap.get(Constants.ADDUNIQUE_TABLE_MAP).put(table.name(), addUniqueFieldList);
        }
    }

    /**
     * 找出需要新建的索引
     *
     * @param allIndexAndUniqueNames 当前数据库的索引很约束名
     * @param allFieldList           model中的所有字段
     * @return 需要新建的索引
     */
    private List<CreateTableVo> getAddIndexList(Set<String> allIndexAndUniqueNames, List<CreateTableVo> allFieldList) {
        List<CreateTableVo> addIndexFieldList = new ArrayList<CreateTableVo>();
        if (null == allIndexAndUniqueNames) {
            allIndexAndUniqueNames = new HashSet<String>();
        }
        for (CreateTableVo createTableVo : allFieldList) {
            if (null != createTableVo.getFieldIndexName()
                    && !allIndexAndUniqueNames.contains(createTableVo.getFieldIndexName())) {
                addIndexFieldList.add(createTableVo);
            }
        }
        return addIndexFieldList;
    }

    /**
     * 找出需要新建的唯一约束
     *
     * @param allIndexAndUniqueNames 当前数据库的索引很约束名
     * @param allFieldList           model中的所有字段
     * @return 需要新建的唯一约束
     */
    private List<CreateTableVo> getAddUniqueList(Set<String> allIndexAndUniqueNames, List<CreateTableVo> allFieldList) {
        List<CreateTableVo> addUniqueFieldList = new ArrayList<CreateTableVo>();
        if (null == allIndexAndUniqueNames) {
            allIndexAndUniqueNames = new HashSet<String>();
        }
        for (CreateTableVo createTableVo : allFieldList) {
            if (null != createTableVo.getFieldUniqueName()
                    && !allIndexAndUniqueNames.contains(createTableVo.getFieldUniqueName())) {
                addUniqueFieldList.add(createTableVo);
            }
        }
        return addUniqueFieldList;
    }

    /**
     * 找出需要删除的索引和唯一约束
     *
     * @param allIndexAndUniqueNames 当前数据库的索引很约束名
     * @param allFieldList           model中的所有字段
     * @return 需要删除的索引和唯一约束
     */
    private List<CreateTableVo> getDropIndexAndUniqueList(Set<String> allIndexAndUniqueNames, List<CreateTableVo> allFieldList) {
        List<CreateTableVo> dropIndexAndUniqueFieldList = new ArrayList<CreateTableVo>();
        if (null == allIndexAndUniqueNames || allIndexAndUniqueNames.isEmpty()) {
            return dropIndexAndUniqueFieldList;
        }
        List<String> currentModelIndexAndUnique = new ArrayList<String>();
        for (CreateTableVo createTableVo : allFieldList) {

            if (null != createTableVo.getFieldIndexName()) {
                currentModelIndexAndUnique.add(createTableVo.getFieldIndexName());
            }
            if (null != createTableVo.getFieldUniqueName()) {
                currentModelIndexAndUnique.add(createTableVo.getFieldUniqueName());
            }
        }
        for (String string : allIndexAndUniqueNames) {
            if (!currentModelIndexAndUnique.contains(string)) {
                CreateTableVo createTableVo = new CreateTableVo();
                createTableVo.setFieldIndexName(string);
                dropIndexAndUniqueFieldList.add(createTableVo);
            }
        }
        return dropIndexAndUniqueFieldList;
    }

    /**
     * 返回需要删除主键的字段
     *
     * @param table           表
     * @param columnNames     数据库中的结构
     * @param tableColumnList 表结构
     * @param allFieldList    model中的所有字段
     * @return 需要删除主键的字段
     */
    private List<CreateTableVo> getDropKeyFieldList(Table table, List<String> columnNames,
                                                    List<MysqlTableInfo> tableColumnList, List<CreateTableVo> allFieldList) {
        Map<String, CreateTableVo> fieldMap = getAllFieldMap(allFieldList);
        List<CreateTableVo> dropKeyFieldList = new ArrayList<CreateTableVo>();
        for (MysqlTableInfo sysColumn : tableColumnList) {
            CreateTableVo createTableVo = fieldMap.get(sysColumn.getColumn_name());
            if (createTableVo != null) {
                if (databaseDialect.isPrimaryKey(sysColumn) && !createTableVo.isFieldIsKey()) {
                    dropKeyFieldList.add(createTableVo);
                }

            }
        }
        return dropKeyFieldList;
    }

    /**
     * 根据数据库中表的结构和model中表的结构对比找出修改类型默认值等属性的字段
     *
     * @param table           表
     * @param columnNames     数据库中的结构
     * @param tableColumnList 表结构
     * @param allFieldList    model中的所有字段
     * @return 需要修改的字段
     */
    private List<CreateTableVo> getModifyFieldList(Table table, List<String> columnNames,
                                                   List<MysqlTableInfo> tableColumnList, List<CreateTableVo> allFieldList) {
        Map<String, CreateTableVo> fieldMap = getAllFieldMap(allFieldList);
        List<CreateTableVo> modifyFieldList = new ArrayList<CreateTableVo>();
        DatabaseTypeMapper typeMapper = getTypeMapper();

        for (MysqlTableInfo sysColumn : tableColumnList) {
            if (JsonValueCodec.isShadowColumn(sysColumn.getColumn_name())) {
                // 影子列不参与类型比对与 MODIFY：对生成列执行 MODIFY 会丢表达式、甚至报语法错误
                continue;
            }
            CreateTableVo createTableVo = fieldMap.get(sysColumn.getColumn_name());
            if (createTableVo != null) {
                CreateTableVo modifyTableParam = createTableVo.clone();

                boolean isDbPrimaryKey = databaseDialect.isPrimaryKey(sysColumn);

                if (!isDbPrimaryKey && createTableVo.isFieldIsKey()) {
                    modifyFieldList.add(modifyTableParam);
                    continue;
                }
                if (isDbPrimaryKey && createTableVo.isFieldIsKey()) {
                    modifyTableParam.setFieldIsKey(false);
                }

                if (!sysColumn.getData_type().toLowerCase().equals(createTableVo.getFieldType().toLowerCase())) {
                    modifyFieldList.add(modifyTableParam);
                    continue;
                }

                int lengthCount = typeMapper.getLengthCount(createTableVo.getFieldType());
                String typeAndLength = createTableVo.getFieldType().toLowerCase();
                if (lengthCount == 1) {
                    typeAndLength = typeAndLength + "(" + createTableVo.getFieldLength() + ")";
                } else if (lengthCount == 2) {
                    typeAndLength = typeAndLength + "(" + createTableVo.getFieldLength() + ","
                            + createTableVo.getFieldDecimalLength() + ")";
                }

                if (!sysColumn.getColumn_type().toLowerCase().equals(typeAndLength)) {
                    modifyFieldList.add(modifyTableParam);
                    continue;
                }

                if (databaseDialect.isAutoIncrement(sysColumn) && !createTableVo.isFieldIsAutoIncrement()) {
                    modifyFieldList.add(modifyTableParam);
                    continue;
                }

                String dbDefaultValue = databaseDialect.parseColumnDefaultValue(sysColumn);
                if (dbDefaultValue == null || dbDefaultValue.equals("")) {
                    if (!"NULL".equals(createTableVo.getFieldDefaultValue())) {
                        modifyFieldList.add(modifyTableParam);
                        continue;
                    }
                } else if (!dbDefaultValue.equals(createTableVo.getFieldDefaultValue())) {
                    modifyFieldList.add(modifyTableParam);
                    continue;
                }

                if (sysColumn.getIs_nullable().equals("NO") && !createTableVo.isFieldIsKey()) {
                    if (createTableVo.isFieldIsNull()) {
                        modifyFieldList.add(modifyTableParam);
                        continue;
                    }
                } else if (sysColumn.getIs_nullable().equals("YES") && !createTableVo.isFieldIsKey()) {
                    if (!createTableVo.isFieldIsNull()) {
                        modifyFieldList.add(modifyTableParam);
                        continue;
                    }
                }

                if (!sysColumn.getColumn_comment().equals(createTableVo.getFieldComment())) {
                    modifyFieldList.add(modifyTableParam);
                }
            }
        }
        return modifyFieldList;
    }

    /**
     * 将allFieldList转换为Map结构
     *
     * @param allFieldList
     * @return
     */
    private Map<String, CreateTableVo> getAllFieldMap(List<CreateTableVo> allFieldList) {
        // 将fieldList转成Map类型，字段名作为主键
        Map<String, CreateTableVo> fieldMap = new HashMap<String, CreateTableVo>();
        for (CreateTableVo createTableVo : allFieldList) {
            fieldMap.put(createTableVo.getFieldName(), createTableVo);
        }
        return fieldMap;
    }

    /**
     * 根据数据库中表的结构和model中表的结构对比找出删除的字段
     *
     * @param table        表
     * @param columnNames  数据库中的结构
     * @param allFieldList model中的所有字段
     */
    private List<CreateTableVo> getRemoveFieldList(Table table, List<String> columnNames, List<CreateTableVo> allFieldList) {
        Map<String, CreateTableVo> fieldMap = getAllFieldMap(allFieldList);
        // 用于存删除的字段
        List<CreateTableVo> removeFieldList = new ArrayList<CreateTableVo>();
        for (String fieldNm : columnNames) {
            // 判断该字段在新的model结构中是否存在
            if (fieldMap.get(fieldNm) == null) {
                // 不存在，做删除处理
                CreateTableVo createTableVo = new CreateTableVo();
                createTableVo.setFieldName(fieldNm);
                removeFieldList.add(createTableVo);
            }
        }
        return removeFieldList;
    }

    /**
     * 根据数据库中表的结构和model中表的结构对比找出新增的字段
     *
     * @param table        表
     * @param allFieldList model中的所有字段
     * @param columnNames  数据库中的结构
     * @return 新增的字段
     */
    private List<CreateTableVo> getAddFieldList(Table table, List<CreateTableVo> allFieldList, List<String> columnNames) {
        List<CreateTableVo> addFieldList = new ArrayList<CreateTableVo>();
        for (CreateTableVo createTableVo : allFieldList) {
            // 循环新的model中的字段，判断是否在数据库中已经存在
            if (!columnNames.contains(createTableVo.getFieldName())) {
                // 不存在，表示要在数据库中增加该字段
                addFieldList.add(createTableVo);
            }
        }
        return addFieldList;
    }

    /**
     * 迭代出所有model的所有fields
     *
     * @param mySqlTypeAndLengthMap mysql数据类型和对应几个长度的map
     * @param clas                  准备做为创建表依据的class
     * @return 表的全部字段
     */
    private List<CreateTableVo> getAllFields(DatabaseTypeMapper typeMapper, Class<?> clas) {
        List<CreateTableVo> fieldList = new ArrayList<CreateTableVo>();
        Field[] fields = clas.getDeclaredFields();

        fields = recursionParents(clas, fields);

        for (Field field : fields) {
            boolean hasAnnotation = field.isAnnotationPresent(Column.class);
            if (hasAnnotation) {
                Column column = field.getAnnotation(Column.class);
                CreateTableVo param = new CreateTableVo();
                param.setFieldName(column.name());
                param.setFieldType(column.type().toLowerCase());
                if ("bit".equals(column.type())) {
                    if (column.length() >= 255) {
                        param.setFieldLength(1);
                    }
                    if (column.length() <= 64) {
                        param.setFieldLength(column.length());
                    }
                } else {
                    param.setFieldLength(column.length());
                }
                param.setFieldDecimalLength(column.decimalLength());
                if (column.isKey()) {
                    param.setFieldIsNull(false);
                } else {
                    param.setFieldIsNull(column.isNull());
                }
                param.setFieldIsKey(column.isKey());
                param.setFieldIsAutoIncrement(column.isAutoIncrement());
                param.setFieldDefaultValue(column.defaultValue());
                param.setFieldComment(column.comment());
                int length = typeMapper.getLengthCount(column.type().toLowerCase());
                param.setFieldTypeLength(length);
                // 获取当前字段的@Index注解
                Index index = field.getAnnotation(Index.class);
                if (null != index) {
                    String[] indexValue = index.value();
                    param.setFieldIndexName((index.name() == null || index.name().equals(""))
                            ? (Constants.IDX + ((indexValue.length == 0) ? column.name() : stringArrFormat(indexValue)))
                            : index.name());
                    param.setFieldIndexValue(
                            indexValue.length == 0 ? Arrays.asList(column.name()) : Arrays.asList(indexValue));
                }
                // 获取当前字段的@Unique注解
                Unique unique = field.getAnnotation(Unique.class);
                if (null != unique) {
                    String[] uniqueValue = unique.value();
                    param.setFieldUniqueName((unique.name() == null || unique.name().equals(""))
                            ? (Constants.UNI
                            + ((uniqueValue.length == 0) ? column.name() : stringArrFormat(uniqueValue)))
                            : unique.name());
                    param.setFieldUniqueValue(
                            uniqueValue.length == 0 ? Arrays.asList(column.name()) : Arrays.asList(uniqueValue));
                }
                fieldList.add(param);
            }
        }
        return fieldList;
    }

    /**
     * String[] to format xxx_yyy_sss
     *
     * @param arr
     * @return
     */
    private String stringArrFormat(String[] arr) {
        return String.valueOf(Arrays.toString(arr)).replaceAll(",", "_").replaceAll(" ", "").replace("[", "")
                .replace("]", "");
    }

    /**
     * 递归扫描父类的fields
     *
     * @param clas   类
     * @param fields 属性
     */
    @SuppressWarnings("rawtypes")
    private Field[] recursionParents(Class<?> clas, Field[] fields) {
        if (clas.getSuperclass() != null) {
            Class clsSup = clas.getSuperclass();
            List<Field> fieldList = new ArrayList<Field>();
            fieldList.addAll(Arrays.asList(fields));
            fieldList.addAll(Arrays.asList(clsSup.getDeclaredFields()));
            fields = new Field[fieldList.size()];
            int i = 0;
            for (Object field : fieldList.toArray()) {
                fields[i] = (Field) field;
                i++;
            }
            fields = recursionParents(clsSup, fields);
        }
        return fields;
    }

    /**
     * 根据传入的map创建或修改表结构
     *
     * @param baseTableMap 操作sql的数据结构
     */
    private void createOrModifyTableConstruct(Map<String, Map<String, List<CreateTableVo>>> baseTableMap) {
        // 1. 创建表
        createTableByMap(baseTableMap.get(Constants.NEW_TABLE_MAP));
        // 2. 删除要变更主键的表的原来的字段的主键
        dropFieldsKeyByMap(baseTableMap.get(Constants.DROPKEY_TABLE_MAP));
        // 3. 添加新的字段
        addFieldsByMap(baseTableMap.get(Constants.ADD_TABLE_MAP));
        // 4. 删除字段
        removeFieldsByMap(baseTableMap.get(Constants.REMOVE_TABLE_MAP));
        // 5. 修改字段类型等
        modifyFieldsByMap(baseTableMap.get(Constants.MODIFY_TABLE_MAP));
        // 6. 删除索引和约束
        dropIndexAndUniqueByMap(baseTableMap.get(Constants.DROPINDEXANDUNIQUE_TABLE_MAP));
        // 7. 创建索引
        addIndexByMap(baseTableMap.get(Constants.ADDINDEX_TABLE_MAP));
        // 8. 创建约束
        addUniqueByMap(baseTableMap.get(Constants.ADDUNIQUE_TABLE_MAP));
        // 9.修改字段名称
        modifyFieldNameByMap(baseTableMap.get(Constants.MODIFY_FIELD_NAME));
    }

    private void dropIndexAndUniqueByMap(Map<String, List<CreateTableVo>> dropIndexAndUniqueMap) {
        if (!dropIndexAndUniqueMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : dropIndexAndUniqueMap.entrySet()) {
                for (CreateTableVo obj : entry.getValue()) {
                    String sql = databaseDialect.buildDropIndexSql(entry.getKey(), obj.getFieldIndexName());
                    log.info("开始删除表{}中的索引: {}", entry.getKey(), obj.getFieldIndexName());
                    dynamicTableMapper.executeDDL(sql);
                    log.info("完成删除表{}中的索引: {}", entry.getKey(), obj.getFieldIndexName());
                }
            }
        }
    }

    private void addIndexByMap(Map<String, List<CreateTableVo>> addIndexMap) {
        if (!addIndexMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : addIndexMap.entrySet()) {
                for (Object obj : entry.getValue()) {
                    CreateTableVo fieldProperties = (CreateTableVo) obj;
                    if (null != fieldProperties.getFieldIndexName()) {
                        String sql = databaseDialect.buildCreateIndexSql(entry.getKey(),
                                fieldProperties.getFieldIndexName(), fieldProperties.getFieldIndexValue());
                        log.info("开始创建表{}中的索引: {}", entry.getKey(), fieldProperties.getFieldIndexName());
                        dynamicTableMapper.executeDDL(sql);
                        log.info("完成创建表{}中的索引: {}", entry.getKey(), fieldProperties.getFieldIndexName());
                    }
                }
            }
        }
    }

    private void addUniqueByMap(Map<String, List<CreateTableVo>> addUniqueMap) {
        if (!addUniqueMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : addUniqueMap.entrySet()) {
                for (Object obj : entry.getValue()) {
                    CreateTableVo fieldProperties = (CreateTableVo) obj;
                    if (null != fieldProperties.getFieldUniqueName()) {
                        String sql = databaseDialect.buildCreateUniqueSql(entry.getKey(),
                                fieldProperties.getFieldUniqueName(), fieldProperties.getFieldUniqueValue());
                        log.info("开始创建表{}中的唯一约束: {}", entry.getKey(), fieldProperties.getFieldUniqueName());
                        dynamicTableMapper.executeDDL(sql);
                        log.info("完成创建表{}中的唯一约束: {}", entry.getKey(), fieldProperties.getFieldUniqueName());
                    }
                }
            }
        }
    }

    private void modifyFieldsByMap(Map<String, List<CreateTableVo>> modifyTableMap) {
        if (!databaseDialect.supportsModifyColumn()) {
            // SQLite 等类型无约束力的库：类型/长度差异不需要 DDL，整体跳过（重命名走独立通道不受影响）
            log.info("方言[{}]不支持 MODIFY COLUMN，跳过 {} 个字段的修改", databaseDialect.getName(),
                    modifyTableMap == null ? 0 : modifyTableMap.values().stream().mapToInt(List::size).sum());
            return;
        }
        if (modifyTableMap != null && !modifyTableMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : modifyTableMap.entrySet()) {
                for (Object obj : entry.getValue()) {
                    CreateTableVo fieldProperties = (CreateTableVo) obj;
                    String sql = databaseDialect.buildAlterTableModifyColumn(entry.getKey(), fieldProperties, null);
                    log.info("开始修改表{}中的字段: {}", entry.getKey(), fieldProperties.getFieldName());
                    dynamicTableMapper.executeDDL(sql);
                    log.info("完成修改表{}中的字段: {}", entry.getKey(), fieldProperties.getFieldName());
                }
            }
        }
    }

    private void modifyFieldNameByMap(Map<String, List<CreateTableVo>> modifyTableMap) {
        if (modifyTableMap != null && !modifyTableMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : modifyTableMap.entrySet()) {
                for (Object obj : entry.getValue()) {
                    CreateTableVo fieldProperties = (CreateTableVo) obj;
                    String sql = databaseDialect.buildAlterTableRenameColumn(
                            entry.getKey(), fieldProperties.getOldFileName(), fieldProperties.getFieldName());
                    log.info("开始修改表{}.{}中的字段名为: {}", entry.getKey(), fieldProperties.getOldFileName(), fieldProperties.getFieldName());
                    dynamicTableMapper.executeDDL(sql);
                    log.info("完成修改表{}.{}中的字段名为: {}", entry.getKey(), fieldProperties.getOldFileName(), fieldProperties.getFieldName());
                }
            }
        }
    }

    private void removeFieldsByMap(Map<String, List<CreateTableVo>> removeTableMap) {
        if (!removeTableMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : removeTableMap.entrySet()) {
                for (CreateTableVo obj : entry.getValue()) {
                    String fieldName = obj.getFieldName();
                    String sql = databaseDialect.buildAlterTableDropColumn(entry.getKey(), fieldName);
                    log.info("开始删除表{}中的字段: {}", entry.getKey(), fieldName);
                    dynamicTableMapper.executeDDL(sql);
                    log.info("完成删除表{}中的字段: {}", entry.getKey(), fieldName);
                }
            }
        }
    }

    private void addFieldsByMap(Map<String, List<CreateTableVo>> addTableMap) {
        if (!addTableMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : addTableMap.entrySet()) {
                for (Object obj : entry.getValue()) {
                    CreateTableVo fieldProperties = (CreateTableVo) obj;
                    String sql = databaseDialect.buildAlterTableAddColumn(entry.getKey(), fieldProperties);
                    log.info("开始为表{}增加字段: {}", entry.getKey(), fieldProperties.getFieldName());
                    dynamicTableMapper.executeDDL(sql);
                    log.info("完成为表{}增加字段: {}", entry.getKey(), fieldProperties.getFieldName());
                }
            }
        }
    }

    private void dropFieldsKeyByMap(Map<String, List<CreateTableVo>> dropKeyTableMap) {
        if (!databaseDialect.supportsModifyColumn()) {
            log.info("方言[{}]不支持 DROP PRIMARY KEY，跳过", databaseDialect.getName());
            return;
        }
        if (!dropKeyTableMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : dropKeyTableMap.entrySet()) {
                for (Object obj : entry.getValue()) {
                    CreateTableVo fieldProperties = (CreateTableVo) obj;
                    String sql = databaseDialect.buildDropPrimaryKeySql(entry.getKey(), fieldProperties);
                    log.info("开始为表{}删除主键: {}", entry.getKey(), fieldProperties.getFieldName());
                    dynamicTableMapper.executeDDL(sql);
                    log.info("完成为表{}删除主键: {}", entry.getKey(), fieldProperties.getFieldName());
                }
            }
        }
    }

    private void createTableByMap(Map<String, List<CreateTableVo>> newTableMap) {
        if (!newTableMap.isEmpty()) {
            for (Entry<String, List<CreateTableVo>> entry : newTableMap.entrySet()) {
                String sql = databaseDialect.buildCreateTableSql(entry.getKey(), entry.getValue());
                log.info("开始创建表：{}", entry.getKey());
                dynamicTableMapper.executeDDL(sql);
                log.info("完成创建表：{}", entry.getKey());
            }
        }
    }

    /**
     * 获取当前数据库的类型长度映射
     * 构建Map(字段名(小写),需要设置几个长度(0表示不需要设置，1表示需要设置一个，2表示需要设置两个))
     */
    public static Map<String, Integer> mySqlTypeAndLengthMap() {
        DatabaseTypeMapper mapper = DynamicTypeContext.getTypeMapper();
        if (mapper != null) {
            return mapper.getTypeLengthMap();
        }
        return com.privernal.dynamic.dba.type.DatabaseTypeMapper.buildLengthMap(
                com.privernal.dynamic.dba.constants.MySqlTypeConstant.class);
    }

    private void createOrModifyTableConstruct(String tableName, String formId, Map<String, Object> data, boolean isCreate) {
        // 初始化用于存储各种操作表结构的容器
        Map<String, Map<String, List<CreateTableVo>>> baseTableMap = FormTransferTable.initTableMap();

        // 1. 用于存表的全部字段
        List<CreateTableVo> allFieldList = FormTransferTable.getAllFields(data, isCreate);

        // 列表列配置（metaData.listConfig）：建列/加列时按物理列名命中即用，未命中走默认口径
        Map<String, FormListConfigUtils.Config> listConfig = FormListConfigUtils.resolve(data);

        // 子表 JSON 快照列（主表 TEXT 列，存子表行数组）：存储兼容列不是业务展示列，
        // 默认不进列表/筛选（列表显示走「子表字段.行内列名」的点号列），否则列表会把整段 JSON 吐给用户
        Set<String> subTableSnapshotColumns = new HashSet<>();
        List<Map<String, Object>> subTableNodes = new ArrayList<>();
        collectSubTableNodes(data.get("schemas"), subTableNodes);
        for (Map<String, Object> subTable : subTableNodes) {
            Object subField = subTable.get("field");
            if (subField != null) {
                subTableSnapshotColumns.add(FormTransferTable.toDbColumnName(subField.toString()));
            }
        }

        // 不存在时
        if (isCreate) {
            baseTableMap.get(Constants.NEW_TABLE_MAP).put(tableName, allFieldList);
            baseTableMap.get(Constants.ADDINDEX_TABLE_MAP).put(tableName, getAddIndexList(null, allFieldList));
            baseTableMap.get(Constants.ADDUNIQUE_TABLE_MAP).put(tableName, getAddUniqueList(null, allFieldList));

            if (formId != null) {
                // 增加到列表中
                int sortIndex = 0;
                for (CreateTableVo field : allFieldList) {
                    // 影子列是数据库维护的辅助列，不能进 dynamic_form_column：
                    // 否则列表设计页会出现 xxx_v0 幽灵字段、查询按业务字段匹配到它、列统计被污染
                    if (JsonValueCodec.isShadowColumn(field.getFieldName())) {
                        continue;
                    }

                    DynamicFormColumn dynamicFormColumn = new DynamicFormColumn();
                    dynamicFormColumn.setFormId(formId);
                    dynamicFormColumn.setFiledCode(field.getFieldName());
                    dynamicFormColumn.setFiledName(field.getFieldComment());
                    dynamicFormColumn.setFiledType(field.getFieldType());
                    dynamicFormColumn.setLength(Long.parseLong("" + field.getFieldLength()));
                    dynamicFormColumn.setScale(Long.parseLong("" + field.getFieldDecimalLength()));
                    dynamicFormColumn.setDefaultValue(field.getFieldDefaultValue());
                    dynamicFormColumn.setDepartmentId(SecurityUtils.getDepartmentId());
                    dynamicFormColumn.setOrganizationId(SecurityUtils.getOrganizationId());
                    dynamicFormColumn.setComponentId(field.getComponentId());
                    // 初始序号按表单字段顺序 1..N，列表设计打开即有序号（sort 为空前端只能兜底显示 0）
                    dynamicFormColumn.setSort(Long.valueOf(++sortIndex));
 // unique_flag 是组件 props.unique 的物化镜像；sensitive 列级配置，新列默认 N
                    dynamicFormColumn.setUniqueFlag(field.isFieldIsUnique() ? "Y" : "N");
                    dynamicFormColumn.setSensitive("N");
                    // 列表显示/高级筛选开关：metaData.listConfig 显式配置优先（AI 生成链用它精确点名），
                    // 未配置时保持既有默认口径——业务字段（有 componentId）默认进列表并进高级筛选，与列表设计
                    // 「从表单生成字段」的 showList=1/showQuery=1 一致；系统公共列不显示，title 默认显示。
                    FormListConfigUtils.Config columnConfig = listConfig.get(field.getFieldName());
                    if (columnConfig != null) {
                        dynamicFormColumn.setShowList(columnConfig.showList ? 1L : 0L);
                        dynamicFormColumn.setShowQuery(columnConfig.showQuery ? 1L : 0L);
                    } else if (subTableSnapshotColumns.contains(field.getFieldName())) {
                        // 子表快照列保持默认不显示（上方注释），显式写 0 防止列实体默认值/前端兜底再点亮
                        dynamicFormColumn.setShowList(0L);
                        dynamicFormColumn.setShowQuery(0L);
                    } else if (field.getComponentId() != null && !field.getComponentId().isEmpty()) {
                        dynamicFormColumn.setShowList(1L);
                        dynamicFormColumn.setShowQuery(1L);
                    } else if ("title".equals(field.getFieldName())) {
                        dynamicFormColumn.setShowList(1L);
                    }

                    iDynamicFormColumnService.insertDynamicFormColumn(dynamicFormColumn);
                }
                // 子表行内字段记入列配置（filedCode=子表字段.行内字段），列表设计可选择显示子表字段
                recordSubTableColumns(formId, data);
            }
        } else {

            // 已存在时理论上做修改的操作，这里查出该表的结构
            List<MysqlTableInfo> tableColumnList = dynamicTableMapper.executeQueryForColumns(databaseDialect.buildGetTableColumnsSql(tableName));
            // 从sysColumns中取出我们需要比较的列的List
            // 先取出name用来筛选出增加和删除的字段
            List<String> columnNames = ClassTools.getPropertyValueList(tableColumnList, MysqlTableInfo.COLUMN_NAME_KEY);

            // 验证对比从model中解析的allFieldList与从数据库查出来的columnList
            // 2. 找出增加的字段
            List<CreateTableVo> addFieldList = getAddFieldList(null, allFieldList, columnNames);

            // 3. 找出删除的字段
            List<CreateTableVo> removeFieldList = getRemoveFieldList(null, columnNames, allFieldList);

            // 处理要删除的字段，有可能不是删除，是修改
            if (formId != null && !removeFieldList.isEmpty()) {

                List<DynamicFormColumn> tableColumnList2 = iDynamicFormColumnService.selectDynamicFormColumnByFormId(formId);

                for (CreateTableVo removeField : removeFieldList) {
                    for (DynamicFormColumn field : tableColumnList2) {
                        if (field.getFiledCode().equals(removeField.getFieldName())) {
                            removeField.setComponentId(field.getComponentId());
                        }
                    }

                }
            }

            // 4. 找出更新的字段
            List<CreateTableVo> modifyFieldList = getModifyFieldList(null, columnNames, tableColumnList, allFieldList);

            // 处理新增的字段，有可能是修改字段名，而不是增加字段
            if (!addFieldList.isEmpty() && !removeFieldList.isEmpty()) {
                List<CreateTableVo> modifyL = new ArrayList<CreateTableVo>();
                for (CreateTableVo field : addFieldList) {
                    for (CreateTableVo removeField : removeFieldList) {
                        // 新增的在删除的里面，则是说明是修改字段名，而不是新增。
                        if (field.getComponentId().equals(removeField.getComponentId())) {
                            // 找到相同的字段，则修改字段名
                            field.setOldFileName(removeField.getFieldName());
                            modifyL.add(field);
                        }
                    }
                }
                if (!modifyL.isEmpty()) {
                    for (CreateTableVo field : modifyL) {
                        // 删除新增的字段
                        addFieldList.removeIf(removeField -> field.getFieldName().equals(removeField.getFieldName()));
                        // 删除删除的字段
                        removeFieldList.removeIf(removeField -> field.getComponentId().equals(removeField.getComponentId()));

                    }

                    baseTableMap.get(Constants.MODIFY_FIELD_NAME).put(tableName, modifyL);
                }
            }

            // 5. 找出需要删除主键的字段
            List<CreateTableVo> dropKeyFieldList = getDropKeyFieldList(null, columnNames, tableColumnList, allFieldList);

            // 查询当前表中全部的索引和唯一约束
            Set<String> allIndexAndUniqueNames = dynamicTableMapper.executeQueryForIndexes(databaseDialect.buildGetTableIndexesSql(tableName));

            // 6. 找出需要删除的索引和唯一约束
            List<CreateTableVo> dropIndexAndUniqueFieldList = getDropIndexAndUniqueList(allIndexAndUniqueNames, allFieldList);

            // 7. 找出需要新增的索引
            List<CreateTableVo> addIndexFieldList = getAddIndexList(allIndexAndUniqueNames, allFieldList);

            // 8. 找出需要新增的唯一约束
            List<CreateTableVo> addUniqueFieldList = getAddUniqueList(allIndexAndUniqueNames, allFieldList);

            if (!addFieldList.isEmpty()) {
                baseTableMap.get(Constants.ADD_TABLE_MAP).put(tableName, addFieldList);
                if (formId != null) {
                    // 迭代新增列的初始序号接在已有列最大序号之后，列表设计打开即有序号
                    long maxSort = 0;
                    for (DynamicFormColumn existing : iDynamicFormColumnService.selectDynamicFormColumnByFormId(formId)) {
                        if (existing.getSort() != null && existing.getSort() > maxSort) {
                            maxSort = existing.getSort();
                        }
                    }
                    // 增加到列表中
                    for (CreateTableVo field : addFieldList) {
                        // 影子列不进列配置（同 create 分支），只建物理列
                        if (JsonValueCodec.isShadowColumn(field.getFieldName())) {
                            continue;
                        }

                        DynamicFormColumn dynamicFormColumn = new DynamicFormColumn();
                        dynamicFormColumn.setFormId(formId);
                        dynamicFormColumn.setFiledCode(field.getFieldName());
                        dynamicFormColumn.setFiledName(field.getFieldComment());
                        dynamicFormColumn.setFiledType(field.getFieldType());
                        dynamicFormColumn.setLength(Long.parseLong("" + field.getFieldLength()));
                        dynamicFormColumn.setScale(Long.parseLong("" + field.getFieldDecimalLength()));
                        dynamicFormColumn.setDefaultValue(field.getFieldDefaultValue());
                        dynamicFormColumn.setDepartmentId(SecurityUtils.getDepartmentId());
                        dynamicFormColumn.setOrganizationId(SecurityUtils.getOrganizationId());
                        dynamicFormColumn.setComponentId(field.getComponentId());
                        dynamicFormColumn.setSort(++maxSort);
 // unique_flag 物化镜像；新列 sensitive 默认 N
                        dynamicFormColumn.setUniqueFlag(field.isFieldIsUnique() ? "Y" : "N");
                        dynamicFormColumn.setSensitive("N");
                        // 迭代新增列同样要吃 listConfig，否则「表单加字段后列表看不见」会在迭代场景复发。
                        // 未显式配置的新列只进列表不进高级筛选：已有表的筛选面板是人调过的，不被新增字段挤占。
                        FormListConfigUtils.Config addedConfig = listConfig.get(field.getFieldName());
                        if (addedConfig != null) {
                            dynamicFormColumn.setShowList(addedConfig.showList ? 1L : 0L);
                            dynamicFormColumn.setShowQuery(addedConfig.showQuery ? 1L : 0L);
                        } else if (subTableSnapshotColumns.contains(field.getFieldName())) {
                            dynamicFormColumn.setShowList(0L);
                            dynamicFormColumn.setShowQuery(0L);
                        } else if (field.getComponentId() != null && !field.getComponentId().isEmpty()) {
                            dynamicFormColumn.setShowList(1L);
                        }
                        iDynamicFormColumnService.insertDynamicFormColumn(dynamicFormColumn);
                    }
                }
            }
            if (!removeFieldList.isEmpty()) {
                baseTableMap.get(Constants.REMOVE_TABLE_MAP).put(tableName, removeFieldList);
                if (formId != null) {
                    // 增加到列表中
                    for (CreateTableVo field : removeFieldList) {
                        iDynamicFormColumnService.deleteDynamicFormColumnByFormId(formId, field.getFieldName());
                    }
                    // 引用完整性守卫（§8.2/T3）：扫四配置对被删列的引用，结果经静态暂存由表单保存响应带回
                    try {
                        java.util.Set<String> removedCols = new java.util.HashSet<>();
                        for (CreateTableVo field : removeFieldList) {
                            removedCols.add(field.getFieldName());
                        }
                        com.privernal.dynamic.advanced.service.ColumnReferenceGuard.recordRemoved(formId, removedCols);
                    } catch (Exception guardError) {
                        log.warn("引用完整性守卫扫描失败（不阻断保存）：{}", guardError.getMessage());
                    }
                }
            }
            if (!modifyFieldList.isEmpty()) {
                baseTableMap.get(Constants.MODIFY_TABLE_MAP).put(tableName, modifyFieldList);
                if (formId != null) {
                    // 修改表中的数据
                    for (CreateTableVo field : modifyFieldList) {
                        // 影子列没有列配置记录，也不该被 MODIFY（MySQL 不能 MODIFY 生成列不带表达式）
                        if (JsonValueCodec.isShadowColumn(field.getFieldName())) {
                            continue;
                        }

                        DynamicFormColumn dynamicFormColumn = iDynamicFormColumnService.selectDynamicFormColumnByFormId(formId, field.getFieldName());
                        if(dynamicFormColumn==null){
                            log.error("--------DynamicFormColumn not ------------{}", field.getFieldName());
                            continue;
                        }
                        dynamicFormColumn.setFiledCode(field.getFieldName());
                        // 子表快照列（filedCode=子表字段，组头名载体）不覆盖 filedName：
                        // 列表设计里用户改的组头名要保留（清空后由前端回落设计器 label）
                        if (!subTableSnapshotColumns.contains(field.getFieldName())) {
                            dynamicFormColumn.setFiledName(field.getFieldComment());
                        }
                        dynamicFormColumn.setFiledType(field.getFieldType());
                        dynamicFormColumn.setLength(Long.parseLong("" + field.getFieldLength()));
                        dynamicFormColumn.setScale(Long.parseLong("" + field.getFieldDecimalLength()));
                        dynamicFormColumn.setDefaultValue(field.getFieldDefaultValue());
                        dynamicFormColumn.setDepartmentId(SecurityUtils.getDepartmentId());
                        dynamicFormColumn.setOrganizationId(SecurityUtils.getOrganizationId());
                        dynamicFormColumn.setComponentId(field.getComponentId());
                        // unique_flag 随 props 刷新（真相在 meta_data，列记录是镜像）；
                        // sensitive 是列级配置的真相，实体是从库里的读改写，不动它即天然保留（§4.4 最大坑的回归点）
                        dynamicFormColumn.setUniqueFlag(field.isFieldIsUnique() ? "Y" : "N");
                        iDynamicFormColumnService.updateDynamicFormColumn(dynamicFormColumn);
                    }
                }
            }
            if (!dropKeyFieldList.isEmpty()) {
                baseTableMap.get(Constants.DROPKEY_TABLE_MAP).put(tableName, dropKeyFieldList);
            }
            if (!dropIndexAndUniqueFieldList.isEmpty()) {
                baseTableMap.get(Constants.DROPINDEXANDUNIQUE_TABLE_MAP).put(tableName, dropIndexAndUniqueFieldList);
            }
            if (!addIndexFieldList.isEmpty()) {
                baseTableMap.get(Constants.ADDINDEX_TABLE_MAP).put(tableName, addIndexFieldList);
            }
            if (!addUniqueFieldList.isEmpty()) {
                baseTableMap.get(Constants.ADDUNIQUE_TABLE_MAP).put(tableName, addUniqueFieldList);
            }

            // 改表分支同样补记子表行内字段的列配置（filedCode=子表字段.行内列名）：
            // 旧的平铺列配置已随 REMOVE 同步删除，这里补带前缀的新记录，保证列表设计可选择子表字段
            if (formId != null) {
                recordSubTableColumns(formId, data);
            }

            log.info("------------------------------removeFieldList:{}. addFieldList:{} modifyFieldList:{} dropKeyFieldList:{}. dropIndexAndUniqueFieldList: {} dropIndexAndUniqueFieldList:{} addUniqueFieldList:{}",removeFieldList.size(), addFieldList.size(), modifyFieldList.size(), dropKeyFieldList.size(), dropIndexAndUniqueFieldList.size(), addIndexFieldList.size(), addUniqueFieldList.size());
        }


        // 根据传入的map，分别去创建或修改表结构
        createOrModifyTableConstruct(baseTableMap);

    }

    @Override
    public void createTransferTable(String tableName, Map<String, Object> data, boolean isCreate) {
        createOrModifyTableConstruct(tableName, null, data, isCreate);

    }

    @Override
    public void createTableByFields(String tableName, List<CreateTableVo> fields, boolean isCreate) {        Map<String, Map<String, List<CreateTableVo>>> baseTableMap = FormTransferTable.initTableMap();
        if (isCreate) {
            baseTableMap.get(Constants.NEW_TABLE_MAP).put(tableName, fields);
            baseTableMap.get(Constants.ADDINDEX_TABLE_MAP).put(tableName, getAddIndexList(null, fields));
            baseTableMap.get(Constants.ADDUNIQUE_TABLE_MAP).put(tableName, getAddUniqueList(null, fields));
        } else {
            // 已存在的子表做增量改表：只追加缺失字段（子表行字段只增不删，避免误删历史数据列）
            List<MysqlTableInfo> tableColumnList = dynamicTableMapper.executeQueryForColumns(databaseDialect.buildGetTableColumnsSql(tableName));
            List<String> columnNames = ClassTools.getPropertyValueList(tableColumnList, MysqlTableInfo.COLUMN_NAME_KEY);
            List<CreateTableVo> addFieldList = getAddFieldList(null, fields, columnNames);
            if (!addFieldList.isEmpty()) {
                baseTableMap.get(Constants.ADD_TABLE_MAP).put(tableName, addFieldList);
            }
        }
        createOrModifyTableConstruct(baseTableMap);
    }

    /**
     * 子表行内字段记入列配置（filedCode=子表字段.行内列名）：
     * 行内字段不再平铺建进主表，但列表设计仍可选择显示/查询/排序；
     * 列类型取子表建表字段（{@link FormTransferTable#getSubTableFields}）的真实类型，
     * 列表查询与排序按该类型决定匹配方式（数值范围、时间区间、文本模糊），不能统一记成 TEXT。
     * 已存在的列配置做同步更新，表单反复保存不再新增重复记录。
     */
    @SuppressWarnings("unchecked")
    private void recordSubTableColumns(String formId, Map<String, Object> data) {
        Map<String, DynamicFormColumn> existingColumns = new HashMap<>();
        for (DynamicFormColumn exist : iDynamicFormColumnService.selectDynamicFormColumnByFormId(formId)) {
            if (exist.getFiledCode() != null) {
                existingColumns.putIfAbsent(exist.getFiledCode(), exist);
            }
        }
        List<Map<String, Object>> subTables = new ArrayList<>();
        collectSubTableNodes(data.get("schemas"), subTables);
        for (Map<String, Object> subTable : subTables) {
            Object subField = subTable.get("field");
            Object subLabel = subTable.get("label");
            if (subField == null) {
                continue;
            }
            String prefix = subField + ".";
            Map<String, CreateTableVo> rowFieldColumns = new HashMap<>();
            for (CreateTableVo vo : FormTransferTable.getSubTableFields(subTable)) {
                rowFieldColumns.putIfAbsent(vo.getFieldName(), vo);
            }
            List<Map<String, Object>> rowFields = new ArrayList<>();
            collectSubTableRowFields(subTable.get("children"), rowFields);
            for (Map<String, Object> rowField : rowFields) {
                Object rowFieldCode = rowField.get("field");
                if (rowFieldCode == null) {
                    continue;
                }
                String column = FormTransferTable.toDbColumnName(rowFieldCode.toString());
                String filedCode = prefix + column;
                CreateTableVo columnVo = rowFieldColumns.get(column);
                String filedName = (subLabel == null ? subField : subLabel) + "-"
                        + (rowField.get("label") == null ? rowFieldCode : rowField.get("label"));
                DynamicFormColumn dynamicFormColumn = existingColumns.get(filedCode);
                boolean isCreate = dynamicFormColumn == null;
                if (isCreate) {
                    dynamicFormColumn = new DynamicFormColumn();
                    dynamicFormColumn.setFormId(formId);
                    dynamicFormColumn.setDepartmentId(SecurityUtils.getDepartmentId());
                    dynamicFormColumn.setOrganizationId(SecurityUtils.getOrganizationId());
                    // 默认列名=「子表label-行内label」只在新记录时落一次；已存在的记录不再覆盖
                    // filedName，列表设计中用户改的名字才能保留（清空后由前端回落设计器 label）
                    dynamicFormColumn.setFiledName(filedName);
                }
                dynamicFormColumn.setFiledCode(filedCode);
                dynamicFormColumn.setFiledType(columnVo != null ? columnVo.getFieldType() : "TEXT");
                dynamicFormColumn.setLength(Long.parseLong(columnVo != null ? "" + columnVo.getFieldLength() : "255"));
                dynamicFormColumn.setScale(Long.parseLong(columnVo != null ? "" + columnVo.getFieldDecimalLength() : "0"));
                // 子表行内字段同样物化 unique_flag；新行 sensitive 默认 N（已有行走读改写，保留旧值）
                if (columnVo != null) {
                    dynamicFormColumn.setUniqueFlag(columnVo.isFieldIsUnique() ? "Y" : "N");
                }
                if (isCreate) {
                    dynamicFormColumn.setSensitive("N");
                    // 与主表业务字段同口径：新建子表列默认进列表并进高级筛选（列表显示为
                    // 「子表名跨列 + 行内字段名」的合并表头形态），列表设计中可再关闭
                    dynamicFormColumn.setShowList(1L);
                    dynamicFormColumn.setShowQuery(1L);
                    iDynamicFormColumnService.insertDynamicFormColumn(dynamicFormColumn);
                    existingColumns.put(filedCode, dynamicFormColumn);
                } else {
                    // 更新只写非空属性，列表设计中配置的显示/查询开关原样保留
                    iDynamicFormColumnService.updateDynamicFormColumn(dynamicFormColumn);
                }
            }
        }
    }

    /**
     * 递归收集 schema 树中的子表节点
     */
    @SuppressWarnings("unchecked")
    private void collectSubTableNodes(Object node, List<Map<String, Object>> out) {
        if (node instanceof List) {
            for (Object item : (List<Object>) node) {
                collectSubTableNodes(item, out);
            }
        } else if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if ("sub-table".equals(map.get("type"))) {
                out.add(map);
                return;
            }
            collectSubTableNodes(map.get("children"), out);
        }
    }

    /**
     * 递归收集子表行内的输入字段节点
     */
    @SuppressWarnings("unchecked")
    private void collectSubTableRowFields(Object node, List<Map<String, Object>> out) {
        if (node instanceof List) {
            for (Object item : (List<Object>) node) {
                collectSubTableRowFields(item, out);
            }
        } else if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if (Boolean.TRUE.equals(map.get("input")) && map.get("field") != null) {
                out.add(map);
            }
            collectSubTableRowFields(map.get("children"), out);
        }
    }

    @Override
    public void createDynamicTable(DynamicForm dynamicForm, boolean isCreate) {
        createOrModifyTableConstruct(dynamicForm.getTableCode(), dynamicForm.getId(), dynamicForm.getMetaData(), isCreate);
    }
}