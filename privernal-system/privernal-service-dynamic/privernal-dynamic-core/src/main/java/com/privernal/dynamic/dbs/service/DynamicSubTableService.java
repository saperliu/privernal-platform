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

package com.privernal.dynamic.dbs.service;

import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.alibaba.fastjson2.JSONObject;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 动态表单子表服务
 * <p>
 * 子表（表单设计器 sub-table 组件）数据独立存储：每张子表对应数据库表
 * {@code 主表编码_子表字段编码}，行数据含 parent_id 列保存主表数据 ID。
 * 主表数据的增删查按本服务的拆行/回查/级联删除协作，保证子表 CRUD 完整。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
public interface DynamicSubTableService {

    /**
     * 解析表单元数据中的全部子表定义
     *
     * @param mainTableCode 主表编码（表单编码，用于推导子表表名）
     * @param metaData      表单元数据（含 schemas）
     * @return 子表定义列表（field/label/childTable）
     */
    List<SubTableDef> resolveSubTables(String mainTableCode, JSONObject metaData);

    /**
     * 表单保存后同步子表表结构：不存在的子表表执行创建，已存在的追加缺失字段。
     * 子表表名 = 主表编码_子表字段编码。
     *
     * @param database      数据源名称
     * @param mainTableCode 主表编码
     * @param metaData      表单元数据
     */
    void ensureSubTables(String database, String mainTableCode, JSONObject metaData);

    /**
     * 保存子表行数据（整体重建：先删该父数据旧行，再插入当前行集合）
     *
     * @param database   数据源名称
     * @param childTable 子表表名
     * @param parentId   主表数据 ID
     * @param rows       行数据列表（键为设计器字段编码，驼峰/原名）
     */
    void saveRows(String database, String childTable, String parentId, List<Map<String, Object>> rows);

    /**
     * 按父数据 ID 查询子表行数据（编辑回显用）
     *
     * @param database   数据源名称
     * @param childTable 子表表名
     * @param parentId   主表数据 ID
     * @return 行数据列表
     */
    List<Map<String, Object>> findRows(String database, String childTable, String parentId);

    /**
     * 按父数据 ID 集合级联删除子表行（主表数据删除时调用）
     *
     * @param database   数据源名称
     * @param childTable 子表表名
     * @param parentIds  主表数据 ID 集合
     */
    void deleteByParentIds(String database, String childTable, Collection<String> parentIds);

    /**
     * 保存前校验「不允许重复」（props.unique）字段的值在库内唯一
     * <p>
     * 仅校验本轮提交中带值的字段；更新时排除自身 id。
     * </p>
     *
     * @param database 数据源名称
     * @param tableName 主表表名
     * @param metaData  表单元数据
     * @param data      本轮提交数据（键为设计器字段编码）
     */
    void assertUniqueFields(String database, String tableName, JSONObject metaData, Map<String, Object> data);

    /**
     * 把「子表字段.行内字段」形态的列表查询条件转成关联子表的 WHERE 片段。
     * <p>
     * 行内字段列配置（dynamic_form_column）记的是主表外的列，直接按主表列拼 SQL 会失败，
     * 故按当前表单的子表定义解析出子表表名与行内列名，条件按子表列的实际数据库类型拼接。
     * 表单改版后行内字段可被删除，而历史列配置会残留，此时对应查询条件已无意义，
     * 直接报错提示用户而不是静默忽略（静默忽略会让用户以为查询生效）。
     * </p>
     *
     * @param database      数据源名称
     * @param mainTableCode 主表编码
     * @param metaData      表单元数据
     * @param filters       过滤键（「子表字段.行内字段」）→ 查询值
     * @param columns       表单列配置（用于取失效字段的显示名）
     * @return 过滤键 → WHERE 片段；查询值为空时该键被跳过
     * @throws com.privernal.common.core.exception.ServiceException 子表或行内列已不存在
     */
    Map<String, String> buildRowFieldConditions(String database, String mainTableCode, JSONObject metaData,
                                                Map<String, Object> filters, List<DynamicFormColumnDTO> columns);

    /**
     * 把「子表字段.行内字段」形态的排序键转成主表查询可用的排序表达式。
     * <p>
     * 行内字段不是主表列，排序取该条主表数据全部子表行的极值：升序取最小值、降序取最大值。
     * 子表表名按命名规则（主表编码_子表字段编码）推导，并列不存在时同样视为字段已失效。
     * </p>
     *
     * @param database      数据源名称
     * @param mainTableCode 主表编码
     * @param columnCode    排序键（「子表字段.行内字段」）
     * @param ascending     是否升序
     * @return 排序表达式（含外层表名限定，不含方向）
     * @throws com.privernal.common.core.exception.ServiceException 子表或行内列已不存在
     */
    String buildRowFieldSortExpression(String database, String mainTableCode, String columnCode, boolean ascending);

    /**
     * 子表定义
     */
    class SubTableDef {

        /** 子表字段编码（formData 键） */
        private String field;

        /** 子表标签（显示名） */
        private String label;

        /** 子表数据库表名（主表编码_字段编码） */
        private String childTable;

        /** 子表 schema 节点（含行内字段 children） */
        private JSONObject node;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getChildTable() {
            return childTable;
        }

        public void setChildTable(String childTable) {
            this.childTable = childTable;
        }

        public JSONObject getNode() {
            return node;
        }

        public void setNode(JSONObject node) {
            this.node = node;
        }
    }
}
