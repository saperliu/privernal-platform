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

import com.privernal.common.core.exception.PreAuthorizeException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.aspect.DataScopeHelper;
import com.privernal.common.datascope.aspect.IgnoreStrategy;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dbs.domain.DynamicFormRelation;
import com.privernal.dynamic.dbs.util.DynamicTableAuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author saperliu
 * @version 1.0.0
 * @Description 关联表单显示数据的读取授权：设计器里的关联表单下拉、列表的关联字段翻译都要读
 * 「被关联表」的记录。产品口径是「能用引用方表单的人就应看到它的显示值」，
 * 所以这里不要求持有被关联表的 list 按钮权限，改为按引用关系放行；引用关系由
 * {@link DynamicFormRelationService} 在表单保存时登记成 dynamic_form_relation，读取时按索引查表，
 * 不再全表扫 meta_data。审批人等流程参与人虽无引用方表单权限，也按其经手的业务数据放行，
 * 并把返回列尽量收敛到配置用到的字段（字段级权限暂未实现）。
 */
@Service
public class DynamicRelationDataService {

    /** 引用方表单的运行时权限：能查看/新增/编辑该表单的人，才需要关联数据源 */
    private static final String[] REFERENCER_OPERATIONS = {"list", "add", "edit"};

    /** 设计态权限：设计器里配置关联组件时，被关联表单可能还没被任何已保存表单引用 */
    private static final String[] DESIGN_PERMISSIONS = {"dynamic:form:list", "dynamic:form:query", "dynamic:form:edit"};

    /** 任何关联场景都需要的主键与数据标题列（下拉取值 value=record.id，标题兜底显示） */
    private static final String[] BASE_COLUMNS = {"id", "title"};

    @Autowired
    DynamicFormRelationService dynamicFormRelationService;

    @Autowired
    DynamicFlowAccessService dynamicFlowAccessService;

    /**
     * 判定当前登录人能否读取 targetTableCode 的显示数据，并给出读取范围。
     *
     * @param targetTableCode 被关联的表单编码（relation.formCode）
     * @param fromTableCode   引用方表单编码，可为空（未保存的表单还没有引用方，此时反查引用方）
     * @param businessId      流程业务数据id，可为空；审批人没有引用方表单的按钮权限时按流程参与人放行
     * @return 读取授权；{@code viaReferencer=true} 表示这次读取是靠引用方表单放行拿到的
     * @throws PreAuthorizeException 既不持有被关联表权限，也不是任何可访问表单的关联数据源
     */
    @Transactional(readOnly = true)
    public RelationReadGrant resolveReadGrant(String targetTableCode, String fromTableCode, String businessId) {
        if (StringUtils.isEmpty(targetTableCode)) {
            throw new PreAuthorizeException();
        }
        if (DynamicTableAuthUtil.hasPermission(targetTableCode, "list")) {
            return new RelationReadGrant(false, null);
        }
        ReadGrant grant = grantFromReferencer(fromTableCode, targetTableCode);
        if (!grant.granted) {
            grant = grantFromFlowHandler(fromTableCode, targetTableCode, businessId);
        }
        if (!grant.granted) {
            grant = grantFromAnyReferencer(targetTableCode);
        }
        if (grant.granted) {
            return new RelationReadGrant(true, grant.unrestricted || grant.columns.isEmpty()
                    ? null : grant.columns);
        }
        if (DynamicTableAuthUtil.hasAnyPermission(DESIGN_PERMISSIONS)) {
            return new RelationReadGrant(false, null);
        }
        throw new PreAuthorizeException();
    }

    /**
     * 指定的引用方表单是否把 targetTableCode 配置为关联数据源；
     * 没有登记关系或当前用户无权访问该表单时 granted=false，交给反查兜底
     */
    private ReadGrant grantFromReferencer(String fromTableCode, String targetTableCode) {
        if (StringUtils.isEmpty(fromTableCode)
                || !DynamicTableAuthUtil.hasPermission(fromTableCode, REFERENCER_OPERATIONS)) {
            return new ReadGrant();
        }
        return grantFromRegistry(dynamicFormRelationService.selectByReferencer(fromTableCode, targetTableCode));
    }

    /**
     * 流程参与人放行：审批人看不到自己经手的申请单里的关联字段显示值就无法审批，
     * 而这类角色通常没有申请单表单的按钮权限。列收敛仍按申请单表单的关联登记，与引用方放行同口径。
     */
    private ReadGrant grantFromFlowHandler(String fromTableCode, String targetTableCode, String businessId) {
        if (!dynamicFlowAccessService.isFlowParticipant(fromTableCode, businessId)) {
            return new ReadGrant();
        }
        return grantFromRegistry(dynamicFormRelationService.selectByReferencer(fromTableCode, targetTableCode));
    }

    /**
     * 反查引用方表单：按登记表查出引用了目标表的表单，取当前用户有权限的那些，
     * 合并它们的显示字段与回填源字段
     */
    private ReadGrant grantFromAnyReferencer(String targetTableCode) {
        Map<String, List<DynamicFormRelation>> byReferencer = new LinkedHashMap<>();
        for (DynamicFormRelation relation : dynamicFormRelationService.selectByTarget(targetTableCode)) {
            if (StringUtils.isNotEmpty(relation.getTableCode())) {
                byReferencer.computeIfAbsent(relation.getTableCode(), key -> new ArrayList<>()).add(relation);
            }
        }
        ReadGrant merged = new ReadGrant();
        byReferencer.forEach((referencer, relations) -> {
            if (!DynamicTableAuthUtil.hasPermission(referencer, REFERENCER_OPERATIONS)) {
                return;
            }
            merge(merged, grantFromRegistry(relations));
        });
        return merged;
    }

    /** 引用登记换算成可返回的列：return_columns（显示字段 + 回填源字段）+ id/title。 */
    private ReadGrant grantFromRegistry(List<DynamicFormRelation> relations) {
        ReadGrant grant = new ReadGrant();
        for (DynamicFormRelation relation : relations) {
            grant.granted = true;
            // 配置了数据范围时不收敛列：范围表达式在前端对返回行求值，参与筛选的字段未必出现在
            // displayField/mappings 里，裁列会让筛选条件恒不成立、下拉恒为空
            if (Integer.valueOf(1).equals(relation.getHasFilter())) {
                grant.unrestricted = true;
            }
            if (StringUtils.isNotEmpty(relation.getReturnColumns())) {
                for (String column : relation.getReturnColumns().split(",")) {
                    addColumnVariants(grant.columns, column);
                }
            }
        }
        if (!grant.granted || grant.unrestricted) {
            return grant;
        }
        // 未配置显示字段时仍放行 id/title，因为「有此表单的权限即能看到它的显示值」是本接口的授权口径
        for (String base : BASE_COLUMNS) {
            addColumnVariants(grant.columns, base);
        }
        return grant;
    }

    private void merge(ReadGrant target, ReadGrant source) {
        if (!source.granted) {
            return;
        }
        target.granted = true;
        target.unrestricted |= source.unrestricted;
        target.columns.addAll(source.columns);
    }

    /**
     * 读取表单定义与关联显示数据时忽略「部门/本人」数据范围：
     * dynamic_form 上的 @PrivernalDataScope 与数据表默认收敛为只看自己创建的行，而业务用户通常是被授予了
     * 引用方表单的按钮权限、并非表单或被关联数据的创建者，不忽略则关联下拉恒为空。
     * 只忽略部门数据范围，组织隔离与表级功能权限仍然生效；忽略策略是线程级的，必须在 finally 还原，
     * 否则会泄漏到同一请求后续的数据查询。
     */
    public static <T> T withoutDataScope(Supplier<T> reader) {
        boolean outerDepartment = Boolean.TRUE.equals(DataScopeHelper.getIgnoreDataPermission());
        boolean outerOrganization = Boolean.TRUE.equals(DataScopeHelper.getIgnoreOrganization());
        DataScopeHelper.setIgnoreDataPermission(true);
        try {
            return reader.get();
        } finally {
            if (outerDepartment || outerOrganization) {
                DataScopeHelper.handle(IgnoreStrategy.builder()
                        .dataPermission(outerDepartment).organization(outerOrganization).build());
            } else {
                DataScopeHelper.clearIgnoreStrategy();
            }
        }
    }

    /**
     * 列名补驼峰与下划线两种写法：登记里的字段编码可能保留组件原名（驼峰），
     * 而动态表数据行的键是数据库列名（下划线），两种都要能通过列收敛
     */
    private void addColumnVariants(Set<String> columns, String field) {
        if (StringUtils.isEmpty(field)) {
            return;
        }
        String column = field.trim();
        if (StringUtils.isEmpty(column)) {
            return;
        }
        columns.add(column);
        columns.add(FormTransferTable.toDbColumnName(column));
        columns.add(camelCase(column));
    }

    /** 下划线转驼峰（user_name → userName），与前端字段取值归一化保持一致 */
    private String camelCase(String field) {
        StringBuilder builder = new StringBuilder(field.length());
        boolean upperNext = false;
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /** 内部授权结论：unrestricted 表示该引用方需要完整列（配置了数据范围），合并时任一成立即整体不收敛 */
    private static final class ReadGrant {
        private boolean granted;
        private boolean unrestricted;
        private final Set<String> columns = new LinkedHashSet<>();
    }

    /**
     * 关联显示数据的读取范围：
     * - viaReferencer：本次读取由引用方表单放行（调用方需据此放开被关联表的行级数据范围）
     * - visibleColumns：允许返回的列；为 null 表示不收敛列
     */
    public static final class RelationReadGrant {

        private final boolean viaReferencer;
        private final Set<String> visibleColumns;

        private RelationReadGrant(boolean viaReferencer, Set<String> visibleColumns) {
            this.viaReferencer = viaReferencer;
            this.visibleColumns = visibleColumns;
        }

        public boolean isViaReferencer() {
            return viaReferencer;
        }

        public Set<String> getVisibleColumns() {
            return visibleColumns;
        }
    }
}
