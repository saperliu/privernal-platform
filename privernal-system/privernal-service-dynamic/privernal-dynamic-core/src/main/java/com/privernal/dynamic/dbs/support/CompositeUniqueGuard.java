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

package com.privernal.dynamic.dbs.support;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.dynamic.dba.mapper.DynamicTableDataMapper;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.mapper.DynamicFormMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 复合唯一组应用层判重兜底（D14/C2 的 5.9 收尾）。
 * <p>
 * DDL 侧的 (uni_key_{name}, deleted_time) 唯一索引依赖 md5 生成列，方言不支持生成列时
 * （SQLite 无 md5）{@link FormTransferTable#appendCompositeUniqueColumns} 按闸门静默跳过，
 * 表上没有 uni_key 列、数据库层无从判重——本类按同一组定义在写入路径做查-改兜底，
 * 让 dev/local 与 pro 的判重行为对齐。
 * <p>
 * key 计算与生成列表达式 {@code concat_ws('|', md5(m1), md5(m2), …)} 同口径：
 * 成员 NULL 的 md5 为 NULL、concat_ws 跳过 NULL（= Java 侧跳过 null 成员），空串不是 NULL 照常参与；
 * 由此「[NULL,'x'] 与 ['x',NULL] 同 key」的库端语义也如实复刻。
 * <p>
 * 局限（有意接受）：查-改非锁定，并发双写存在穿透窗口——有生成列的方言由数据库索引兜住，
 * 兜底方言（dev/local SQLite）为单用户开发环境，不为此加锁。
 */
@Slf4j
@Component
public class CompositeUniqueGuard {

    @Autowired
    private DynamicTableDataMapper dynamicTableDataMapper;

    @Autowired
    private DynamicFormMapper dynamicFormMapper;

    /**
     * 解析表单 metaData 的复合唯一组（成员列限物理表已存在列）。
     * 元数据读取/解析失败不阻断保存：有生成列的方言判重不受影响，兜底方言降级为不判重并留 warn。
     */
    public List<FormTransferTable.CompositeUniqueGroup> resolveGroups(String tableName, Set<String> existingColumns) {
        try {
            DynamicForm form = dynamicFormMapper.selectOne(
                    new LambdaPrivernalWrapper<DynamicForm>().eq(DynamicForm::getTableCode, tableName));
            if (form == null || form.getMetaData() == null) {
                return List.of();
            }
            return FormTransferTable.resolveCompositeUniqueGroups(form.getMetaData(), existingColumns);
        } catch (Exception e) {
            log.warn("复合唯一组解析失败，跳过应用层判重：table={} error={}", tableName, e.getMessage());
            return List.of();
        }
    }

    /**
     * 对一次写入（新增/更新合并行/还原行）按组判重，冲突抛 {@link ServiceException}。
     *
     * @param tableName   物理表名
     * @param groups      复合唯一组（{@link #resolveGroups} 产出）
     * @param effectiveRow 生效行（列名→值）：新增传提交数据、更新传「库里全行 ∪ 本次提交键」、还原传被还原行
     * @param columnTypes 列名→data_type（数值/时间列按存储口径归一，其余按原文比较）
     * @param excludeId   排除自身（更新传主键；新增/还原传 null，还原行仍是软删态不会进活跃候选集）
     */
    public void assertNoConflict(String tableName, List<FormTransferTable.CompositeUniqueGroup> groups,
                                 Map<String, Object> effectiveRow, Map<String, String> columnTypes,
                                 String excludeId) {
        if (groups == null || groups.isEmpty() || effectiveRow == null) {
            return;
        }
        for (FormTransferTable.CompositeUniqueGroup group : groups) {
            String key = computeUniKey(group.members(), effectiveRow, columnTypes);
            List<Object> eqValues = new ArrayList<>();
            int nonNullCount = 0;
            for (String member : group.members()) {
                Object value = effectiveRow.get(member);
                if (value != null) {
                    eqValues.add(value);
                    nonNullCount++;
                }
            }
            List<Map<String, Object>> candidates = dynamicTableDataMapper.selectUniKeyRows(
                    tableName, group.members(), eqValues, nonNullCount, excludeId);
            for (Map<String, Object> candidate : candidates) {
                if (computeUniKey(group.members(), candidate, columnTypes).equals(key)) {
                    throw new ServiceException("复合唯一约束「" + group.name() + "」冲突：相同组合的数据已存在（"
                            + describeMembers(group.members(), effectiveRow) + "）");
                }
            }
        }
    }

    /**
     * 回收站还原前的冲突预检：还原把 deleted_time 拨回哨兵，同组同值冲突在 MySQL 由唯一索引拦截
     * （restoreById 注释口径），SQLite 无 uni_key 列只能在这里补同一拦截。
     */
    public void assertRestoreNoConflict(String tableName, List<FormTransferTable.CompositeUniqueGroup> groups,
                                        Map<String, String> columnTypes, String id) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        List<String> memberUnion = groups.stream()
                .flatMap(g -> g.members().stream())
                .distinct()
                .toList();
        Map<String, Object> restoredRow = dynamicTableDataMapper.selectMemberRowById(tableName, memberUnion, id);
        if (restoredRow == null || restoredRow.isEmpty()) {
            return;
        }
        assertNoConflict(tableName, groups, restoredRow, columnTypes, id);
    }

    /**
     * 与生成列表达式同口径拼 key：concat_ws('|', md5(m1), md5(m2), …)。
     * 数值/时间列值先归一到存储读回口径，保证「新提交值」与「库里读回值」两侧同一字符串形态。
     */
    private String computeUniKey(List<String> members, Map<String, Object> row, Map<String, String> columnTypes) {
        StringBuilder joined = null;
        for (String member : members) {
            String normalized = normalizeValue(row.get(member), columnTypes == null ? null : columnTypes.get(member));
            if (normalized == null) {
                continue;
            }
            String md5 = md5Hex(normalized);
            joined = joined == null ? new StringBuilder(md5) : joined.append('|').append(md5);
        }
        return joined == null ? "" : joined.toString();
    }

    /**
     * 值归一：Date 按 SQLite 时间 TEXT 口径（%Y-%m-%d %H:%M:%f，见 DialectSnippets.now3）；
     * 数值列统一走 BigDecimal 去尾零（提交侧 "27.10" 与库里读回 27.1 归一），非数值脏数据按原文；
     * 其余（TEXT 成员）保持原文——MySQL md5() 对文本列即原文摘要，不做数值化以免 "0086"/"86" 误判。
     */
    private String normalizeValue(Object value, String columnType) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(date);
        }
        if (FormTransferTable.isNumericColumnType(columnType)) {
            String text = String.valueOf(value).trim();
            try {
                return new BigDecimal(text).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return text;
            }
        }
        return String.valueOf(value);
    }

    /**
     * MD5 在这里是 DDL 生成列 md5() 的 Java 镜像（判重指纹，非加密/完整性用途）：
     * 算法必须与 MySQL 端 concat_ws('|', md5(...)) 逐字节一致，换 SHA-256 会与库端 key 失配。
     */
    private String md5Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return java.util.HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    private String describeMembers(List<String> members, Map<String, Object> row) {
        StringBuilder builder = new StringBuilder();
        for (String member : members) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(member).append('=').append(row.get(member) == null ? "null" : row.get(member));
        }
        return builder.toString();
    }
}
