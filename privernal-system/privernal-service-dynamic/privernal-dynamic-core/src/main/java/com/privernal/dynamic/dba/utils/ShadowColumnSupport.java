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

import com.privernal.dynamic.dba.dialect.DatabaseDialect;
import com.privernal.dynamic.dba.dialect.DynamicTypeContext;
import com.privernal.dynamic.dba.type.DatabaseTypeMapper;
import com.privernal.dynamic.dba.vo.CreateTableVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 多值字段的影子（首值）生成列支持（S2）。
 * <p>
 * 问题背景：多选/附件/多人员这类字段的值长度不可预估，历史上落 {@code text} 列。
 * MySQL 对 TEXT 列建 B-tree 索引需要前缀长度，平台 DDL 未带 → 配了 {@code unique}/{@code dbIndex}
 * 会报 1170 <b>静默建不上索引</b>；把它们改成 {@code json} 列又会让等值/{@code IN}/{@code LIKE} 条件
 * 直接报 {@code Invalid JSON text}，报表还会按整个 JSON 文档分组。
 * <p>
 * 解法：原列类型不动，额外由数据库维护一个标量影子列 {@code {column}_v0}，
 * 内容为该字段的首值；<b>唯一约束与普通索引打在影子列上</b>，需要时可安全地被索引与分组。
 * 影子列是「首值口径」，多选字段整体作为报表维度仍然会失真，因此不自动暴露到报表。
 * </p>
 * 两个开关（默认关闭，零生产影响）：
 * <ul>
 *   <li>{@code dynamic.shadow-column.enabled} —— 是否为多值字段生成影子列（新字段建表/加列时生效）；</li>
 *   <li>{@code dynamic.json-value-protocol.enabled} —— 多值写入是否统一为 {@code {"v":[...]}} 信封。</li>
 * </ul>
 * 上线顺序要求：<b>先把读取端切到宽容解析</b>（{@link JsonValueCodec} 无条件兼容 4 种历史形态），
 * 再打开写入开关，最后再考虑影子列。三者反序都会造成 UI 显示或查询异常。
 *
 * @author saperliu
 * @version 1.0.0
 * @see JsonValueCodec
 */
@Component
public class ShadowColumnSupport {

    private static final Logger log = LoggerFactory.getLogger(ShadowColumnSupport.class);

    /**
     * 恒定多值的组件（与 {@code props.multiple} 无关）。
     * <p>
     * 刻意<b>不含 {@code sub-table}</b>：子表快照存的是行对象数组，"首值"是一个完整 JSON 对象，
     * 既不能索引也没有业务含义，白占一列。子表的可检索需求应由子表自身的列解决。
     */
    private static final Set<String> ALWAYS_MULTI_COMPONENTS = Set.of(
            "checkbox", "selectboxes", "upload-file", "upload-image",
            "user-multi-select", "dept-multi-select");

    /**
     * 可单值可多值的组件：仅当 {@code props.multiple == true} 才算多值
     */
    private static final Set<String> CONDITIONAL_MULTI_COMPONENTS = Set.of(
            "select", "radio", "cascader", "user-select", "dept-select");

    /**
     * 影子列首值的取值长度：与历史 varchar(250) 区分开，够装常见选项值/用户 id/URL 段
     */
    private static final int SHADOW_LENGTH = 191;

    private static boolean shadowEnabled;
    private static boolean jsonProtocolEnabled;

    @Value("${dynamic.shadow-column.enabled:false}")
    public void setShadowEnabled(boolean enabled) {
        shadowEnabled = enabled;
        log.info("动态表影子列（首值生成列）: {}", enabled ? "已启用" : "未启用");
    }

    @Value("${dynamic.json-value-protocol.enabled:false}")
    public void setJsonProtocolEnabled(boolean enabled) {
        jsonProtocolEnabled = enabled;
        log.info("动态表多值统一 JSON 信封协议: {}", enabled ? "已启用" : "未启用");
    }

    public static boolean isShadowEnabled() {
        return shadowEnabled;
    }

    public static boolean isJsonProtocolEnabled() {
        return jsonProtocolEnabled;
    }

    /**
     * 是否为多值/复合值组件。
     *
     * @param componentType schema 节点的 {@code type}
     * @param props         节点 props（可为 null）
     * @return 是否适合建影子列
     */
    public static boolean isMultiValueComponent(String componentType, Map props) {
        if (componentType == null) {
            return false;
        }
        if (ALWAYS_MULTI_COMPONENTS.contains(componentType)) {
            return true;
        }
        if (CONDITIONAL_MULTI_COMPONENTS.contains(componentType)) {
            return props != null && isTrue(props.get("multiple"));
        }
        return false;
    }

    /**
     * 构造某业务列的影子生成列定义。当前方言不支持生成列时返回 null（调用方据此跳过）。
     * <p>
     * 返回 null 的三种情况都必须静默跳过而不是报错：影子列是增量能力，
     * 不能因为它让存量环境的建表失败。
     * </p>
     *
     * @param source 业务列定义（已完成类型与长度推导）
     * @return 影子列定义；不支持/命名超长时返回 null
     */
    public static CreateTableVo buildShadowColumn(CreateTableVo source) {
        if (!shadowEnabled || source == null || source.getFieldName() == null) {
            return null;
        }
        DatabaseDialect dialect = DynamicTypeContext.getDialect();
        if (dialect == null || !dialect.supportsGeneratedShadowColumn()) {
            return null;
        }
        String shadowName = JsonValueCodec.shadowColumn(source.getFieldName());
        if (shadowName == null) {
            log.warn("业务列 {} 加影子列后缀后超过 64 字符，跳过（不截断，避免指向歧义列名）",
                    source.getFieldName());
            return null;
        }
        String expression = dialect.buildShadowColumnExpression(source.getFieldName());
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        DatabaseTypeMapper mapper = currentMapper();
        CreateTableVo shadow = new CreateTableVo();
        shadow.setFieldName(shadowName);
        shadow.setFieldType(mapper.getDatabaseTypeName(DatabaseTypeMapper.VARCHAR));
        shadow.setFieldTypeLength(mapper.getLengthCount(shadow.getFieldType()));
        shadow.setFieldLength(SHADOW_LENGTH);
        // 生成列一律可空：空值口径尚未定稿（{"v":[]} 还是 NULL），不能在这里强约束
        shadow.setFieldIsNull(true);
        shadow.setFieldDefaultValue(null);
        shadow.setComponentId(source.getComponentId());
        shadow.setShadowOfSourceColumn(source.getFieldName());
        shadow.setGeneratedExpression(expression);
        shadow.setGeneratedStorage(dialect.requiresStoredGeneration() ? "STORED" : "VIRTUAL");
        shadow.setFieldComment(shortenComment(source.getFieldComment()));
        return shadow;
    }

    /**
     * 与 {@code FormTransferTable#getTypeMapper()} 同源的取映射器方式：上下文未初始化时回退 MySQL，
     * 保证建表链路在单测/离线环境下不因缺少 Spring 上下文而中断。
     */
    private static DatabaseTypeMapper currentMapper() {
        DatabaseTypeMapper mapper = DynamicTypeContext.getTypeMapper();
        return mapper != null ? mapper : new com.privernal.dynamic.dba.type.MySQLTypeMapper();
    }

    private static String shortenComment(String sourceLabel) {
        String comment = (sourceLabel == null ? "" : sourceLabel) + "（首值影子列，勿直接写入）";
        return comment.length() > 200 ? comment.substring(0, 200) : comment;
    }

    private static boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && ("true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString()));
    }
}
