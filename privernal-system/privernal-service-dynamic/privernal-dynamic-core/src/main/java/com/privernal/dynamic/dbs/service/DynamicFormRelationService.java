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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormRelation;
import com.privernal.dynamic.dbs.mapper.DynamicFormRelationMapper;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态表单关联引用注册表：把散落在 meta_data 里的关联配置（relation.formCode）登记成
 * 「引用方表单 → 被关联表单」的关系，表单保存/删除时同步。
 * <p>
 * 有了登记，关联显示数据的读取授权只需按索引查这张表；原先的写法要 cast(meta_data as char) like
 * 全表扫动态表单再逐个解析大 JSON，表单一多每次关联下拉都要付这笔代价（授权判定见
 * {@link DynamicRelationDataService}）。登记只存读取授权用得上的信息：引用到的字段与是否配了数据范围。
 *
 * @author saperliu
 */
@Service
public class DynamicFormRelationService {

    @Autowired
    private DynamicFormRelationMapper dynamicFormRelationMapper;

    /**
     * 按表单元数据重建该表单的关联引用登记（先清后写，幂等）
     *
     * @param form 保存后的动态表单（需含 id、table_code、meta_data）
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncFormRelations(DynamicForm form) {
        if (form == null || StringUtils.isEmpty(form.getId()) || StringUtils.isEmpty(form.getTableCode())) {
            return;
        }
        clearFormRelations(form.getId());
        for (Map.Entry<String, RelationUsage> entry : parseRelationUsages(form.getMetaData()).entrySet()) {
            RelationUsage usage = entry.getValue();
            DynamicFormRelation relation = new DynamicFormRelation();
            relation.setFormId(form.getId());
            relation.setTableCode(form.getTableCode());
            relation.setTargetTableCode(entry.getKey());
            relation.setReturnColumns(usage.columns.isEmpty()
                    ? null : String.join(",", usage.columns));
            relation.setHasFilter(usage.filtered ? 1 : 0);
            fillOrgInfo(form, relation);
            dynamicFormRelationMapper.insert(relation);
        }
    }

    /**
     * 清除某个表单的关联引用登记（表单删除，或表单改成不再引用任何表时）
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearFormRelations(String formId) {
        if (StringUtils.isEmpty(formId)) {
            return;
        }
        dynamicFormRelationMapper.deleteByFormId(formId);
    }

    /**
     * 清除「别的表单引用了某张被删表」的登记（按被关联表编码，表单删除时调用）。
     * 引用方表单的 meta_data 里仍留着失效的 relation 配置，运行期会因表不存在而报错，
     * 这里清登记是为了让读取授权立即失效（fail closed），不再按残留登记放行关联显示数据。
     *
     * @param targetTableCode 被删除表单的表编码
     * @return 清除的登记数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int clearRelationsByTargetTableCode(String targetTableCode) {
        if (StringUtils.isEmpty(targetTableCode)) {
            return 0;
        }
        return dynamicFormRelationMapper.deleteByTargetTableCode(targetTableCode);
    }

    /**
     * 正查：引用方表单对某个被关联表单的登记
     *
     * @param tableCode       引用方表单编码
     * @param targetTableCode 被关联表单编码
     */
    @Transactional(readOnly = true)
    public List<DynamicFormRelation> selectByReferencer(String tableCode, String targetTableCode) {
        if (StringUtils.isEmpty(tableCode) || StringUtils.isEmpty(targetTableCode)) {
            return List.of();
        }
        LambdaQueryWrapper<DynamicFormRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DynamicFormRelation::getTableCode, tableCode);
        wrapper.eq(DynamicFormRelation::getTargetTableCode, targetTableCode);
        return dynamicFormRelationMapper.selectList(wrapper);
    }

    /**
     * 反查：引用了某个被关联表单的全部登记（由调用方按引用方表单权限筛掉无权访问的）
     *
     * @param targetTableCode 被关联表单编码
     */
    @Transactional(readOnly = true)
    public List<DynamicFormRelation> selectByTarget(String targetTableCode) {
        if (StringUtils.isEmpty(targetTableCode)) {
            return List.of();
        }
        LambdaQueryWrapper<DynamicFormRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DynamicFormRelation::getTargetTableCode, targetTableCode);
        return dynamicFormRelationMapper.selectList(wrapper);
    }

    /**
     * 解析元数据里的关联配置，按被关联表单编码归并（同一表单里多个字段引用同一张表时合并字段与数据范围）
     *
     * @return key=被关联表单编码
     */
    private static Map<String, RelationUsage> parseRelationUsages(JSONObject metaData) {
        Map<String, RelationUsage> usages = new LinkedHashMap<>();
        collect(metaData, usages);
        return usages;
    }

    /** 递归查找关联配置：设计器把配置挂在节点的 props.relation 上（遗留写法为 componentProps.relation） */
    private static void collect(Object node, Map<String, RelationUsage> usages) {
        if (node instanceof JSONObject obj) {
            if (obj.get("relation") instanceof JSONObject relationConfig
                    && StringUtils.isNotEmpty(relationConfig.getString("formCode"))) {
                usages.computeIfAbsent(relationConfig.getString("formCode"), key -> new RelationUsage())
                        .add(relationConfig);
            }
            obj.values().forEach(value -> collect(value, usages));
        } else if (node instanceof JSONArray array) {
            array.forEach(value -> collect(value, usages));
        }
    }

    private void fillOrgInfo(DynamicForm form, DynamicFormRelation relation) {
        if (StringUtils.isEmpty(relation.getOrganizationId())) {
            relation.setOrganizationId(StringUtils.isNotEmpty(form.getOrganizationId())
                    ? form.getOrganizationId() : SecurityUtils.getOrganizationId());
        }
        if (StringUtils.isEmpty(relation.getDepartmentId())) {
            relation.setDepartmentId(StringUtils.isNotEmpty(form.getDepartmentId())
                    ? form.getDepartmentId() : SecurityUtils.getDepartmentId());
        }
    }

    /** 某个被关联表单在引用方元数据里的使用情况：引用到的字段 + 是否配置了数据范围 */
    private static final class RelationUsage {

        private final Set<String> columns = new LinkedHashSet<>();
        private boolean filtered;

        private void add(JSONObject relationConfig) {
            addIfPresent(relationConfig.getString("displayField"));
            if (relationConfig.get("mappings") instanceof JSONArray mappingArray) {
                mappingArray.stream().filter(JSONObject.class::isInstance)
                        .map(item -> (JSONObject) item)
                        .forEach(mapping -> addIfPresent(mapping.getString("field")));
            }
            filtered |= relationConfig.get("filter") instanceof JSONObject filterConfig && !filterConfig.isEmpty();
        }

        private void addIfPresent(String column) {
            if (StringUtils.isNotEmpty(column)) {
                columns.add(column);
            }
        }
    }
}
