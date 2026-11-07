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

package com.privernal.dynamic.report.service;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.mapper.DynamicFormMapper;
import com.privernal.dynamic.dbs.service.DynamicTableCodePrefixService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 报表底层动态表解析器：逻辑编码 → 物理表编码。
 * <p>
 * 动态表的存储编码 = 建表时的「组织前缀 + 用户编码」（无分隔符拼接），且存在两类历史形态：
 * 前缀功能上线前创建的表（无前缀）、组织编码为空时创建的表（无前缀）。因此解析按三级容错：
 * <ol>
 * <li>带组织前缀的拼接编码（现建表链路口径，decorate 幂等）</li>
 * <li>原编码精确匹配（无前缀历史表）</li>
 * <li>当前组织内以该编码结尾的表单取最新一条（前缀拼接无分隔符，无法精确反推）</li>
 * </ol>
 * 数据集保存与运行时聚合查询共用本解析，保证两边看到同一张表。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class DynamicReportTableResolver {

    @Autowired
    private DynamicFormMapper dynamicFormMapper;

    @Autowired
    private DynamicTableCodePrefixService dynamicTableCodePrefixService;

    /**
     * 解析逻辑编码对应的动态表单（其 tableCode 即物理表名）
     *
     * @param logicalTableCode 数据集存的逻辑编码
     * @return 表单；三级都未命中返回 null
     */
    public DynamicForm resolveForm(String logicalTableCode) {
        if (StringUtils.isBlank(logicalTableCode)) {
            return null;
        }
        // 1. 带组织前缀的拼接编码（decorate 已带前缀时原样返回；编码过长会抛错，降级继续尝试）
        try {
            DynamicForm form = dynamicFormMapper.selectOne(new LambdaPrivernalWrapper<DynamicForm>()
                    .eq(DynamicForm::getTableCode, dynamicTableCodePrefixService.decorate(logicalTableCode)));
            if (form != null) {
                return form;
            }
        } catch (Exception e) {
            log.info("按前缀编码解析动态表未命中（{}），降级尝试：{}", e.getMessage(), logicalTableCode);
        }
        // 2. 原编码精确匹配（无前缀历史表）
        DynamicForm form = dynamicFormMapper.selectOne(new LambdaPrivernalWrapper<DynamicForm>()
                .eq(DynamicForm::getTableCode, logicalTableCode));
        if (form != null) {
            return form;
        }
        // 3. 当前组织内后缀匹配取最新（organization 拦截器也会自动按当前组织收敛）
        if (!IDENTIFIER_PATTERN.matcher(logicalTableCode).matches()) {
            return null;
        }
        List<DynamicForm> candidates = dynamicFormMapper.selectList(new LambdaPrivernalWrapper<DynamicForm>()
                .eq(DynamicForm::getOrganizationId, SecurityUtils.getOrganizationId())
                .likeLeft(DynamicForm::getTableCode, logicalTableCode)
                .orderByDesc(DynamicForm::getCreatedTime));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** 标识符白名单（与查询服务同口径） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    /**
     * 解析逻辑编码对应的物理表名（供聚合 SQL FROM 子句使用），解析失败返回 null
     */
    public String resolvePhysicalTableCode(String logicalTableCode) {
        DynamicForm form = resolveForm(logicalTableCode);
        if (form == null || StringUtils.isBlank(form.getTableCode())
                || !IDENTIFIER_PATTERN.matcher(form.getTableCode()).matches()) {
            return null;
        }
        return form.getTableCode();
    }

    /**
     * 严格解析（应用包导入专用，task-upgrade P2-3，2026-10-06 落地）：与 {@link #resolveForm}
     * 共用前两级口径，但**不做第③级"当前组织内后缀匹配"**——导入语境下歧义匹配可能挂错表单，
     * 宁可不解析、由调用方走"缺表跳过 + 导入报告"。
     * <p>
     * 入参允许是源环境的物理表名（带源组织前缀）：原样精确未命中时，剥掉首段
     * {@code 组织编码_} 形态前缀取逻辑名，再按"逻辑名精确 → 目标前缀拼接（decorate）"两级重试。
     * "先原样精确"的顺序保证：源表在目标环境原样存在（同组织码克隆、无前缀历史表）时
     * 永远走第一级、不会触发剥前缀；剥前缀的残余误判（源无前缀表名首段恰似组织码、
     * 且目标环境恰有剥后同名表）概率极低，且结果写入导入报告可人工核对。
     *
     * @param physicalOrLogicalCode 源环境物理表名或逻辑编码
     * @return 目标环境表单；解析不出返回 null
     */
    public DynamicForm resolveFormStrict(String physicalOrLogicalCode) {
        if (StringUtils.isBlank(physicalOrLogicalCode)) {
            return null;
        }
        String code = physicalOrLogicalCode.trim();
        DynamicForm form = selectByTableCodeExact(code);
        if (form != null) {
            return form;
        }
        String logical = stripOrgPrefix(code);
        if (logical.equals(code)) {
            return null;
        }
        form = selectByTableCodeExact(logical);
        if (form != null) {
            return form;
        }
        try {
            return dynamicFormMapper.selectOne(new LambdaPrivernalWrapper<DynamicForm>()
                    .eq(DynamicForm::getTableCode, dynamicTableCodePrefixService.decorate(logical)));
        } catch (Exception e) {
            // 编码过长等 decorate 异常按"解析不出"处理，交给调用方走跳过 + 报告
            log.info("严格解析目标前缀拼接未命中（{}）：{}", logical, e.getMessage());
            return null;
        }
    }

    private DynamicForm selectByTableCodeExact(String tableCode) {
        return dynamicFormMapper.selectOne(new LambdaPrivernalWrapper<DynamicForm>()
                .eq(DynamicForm::getTableCode, tableCode));
    }

    /**
     * 剥首段 {@code 组织编码_} 形态前缀：首段须为小写字母开头、后接字母/数字
     * （与 {@link DynamicTableCodePrefixService} normalize 产物同形），形不符原样返回。
     */
    static String stripOrgPrefix(String code) {
        int idx = code.indexOf('_');
        if (idx <= 0 || idx == code.length() - 1) {
            return code;
        }
        String head = code.substring(0, idx);
        if (!head.matches("[a-z][a-z0-9]*")) {
            return code;
        }
        return code.substring(idx + 1);
    }
}
