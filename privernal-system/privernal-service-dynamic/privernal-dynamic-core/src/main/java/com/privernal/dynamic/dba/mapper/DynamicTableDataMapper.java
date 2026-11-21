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

package com.privernal.dynamic.dba.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.datascope.mapper.DataScopeMapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态表数据操作 Mapper
 * <p>
 * 提供动态表数据的 CRUD 操作能力，表名和列名均为动态参数，
 * 支持通过 SQL 注入的方式操作任意动态表的数据。
 * </p>
 *
 * @author saperliu
 * @version 1.1.0
 * @see DynamicTableMapper
 */
@PrivernalDataScope(permissionOne = "dynamic:")
public interface DynamicTableDataMapper extends DataScopeMapper {

    /**
     * 向动态表中插入一条数据
     *
     * @param tableName 目标表名
     * @param tableMap  数据 Map（key=列名，value=值）
     */
    void insert(@Param("tableName") String tableName, @Param("tableMap") Map<String, Object> tableMap);

    /**
     * 根据主键更新动态表中的数据
     *
     * @param tableName 目标表名
     * @param tableMap  数据 Map（需包含主键 ID）
     */
    void update(@Param("tableName") String tableName, @Param("tableMap") Map<String, Object> tableMap);

    /**
     * 根据多个 AND 条件删除数据
     *
     * @param tableName 目标表名
     * @param tableMap  条件 Map（key=列名，value=期望值）
     */
    void delete(@Param("tableName") String tableName, @Param("tableMap") Map<Object, Map<Object, Object>> tableMap);

    /**
     * 根据 ID 删除一条数据
     *
     * @param tableName 目标表名
     * @param id        数据 ID
     */
    void deleteById(@Param("tableName") String tableName, @Param("id") String id,
                    @Param("deletedBy") String deletedBy);

    /**
     * 根据 ID 列表批量删除数据
     *
     * @param tableName 目标表名
     * @param list      数据 ID 列表
     */
    void deleteByIds(@Param("tableName") String tableName, @Param("list") List<String> list,
                     @Param("deletedBy") String deletedBy);

    /** 回收站还原：软删行回活跃态（deleted_time 回哨兵默认） */
    void restoreById(@Param("tableName") String tableName, @Param("id") String id);

    /** 回收站彻底删除：物理清除已软删行 */
    void purgeById(@Param("tableName") String tableName, @Param("id") String id);

    /** 回收站分页：软删行按删除时间倒序 */
    List<Map<String, Object>> selectRecycleBin(@Param("tableName") String tableName,
                                               @Param("startRow") long startRow,
                                               @Param("pageSize") long pageSize);

    /** 回收站计数 */
    int countRecycleBin(@Param("tableName") String tableName);

    /** 数值列合计（sumColumns 由服务端按列元数据筛出，条件与分页查询一致） */
    Map<String, Object> selectSums(@Param("tableName") String tableName,
                                   @Param("tableMap") Map<String, Object> tableMap,
                                   @Param("condParams") Map<String, Object> condParams,
                                   @Param("sumColumns") List<String> sumColumns);

    /**
     * 根据 ID 查询单条数据
     *
     * @param tableName 目标表名
     * @param id        数据 ID
     * @return 数据 Map，若不存在返回 null
     */
    HashMap<String, Object> selectById(@Param("tableName") String tableName, @Param("id") String id);

    /**
     * 条件查询数据列表
     *
     * @param tableName  目标表名
     * @param tableMap   查询条件 Map
     * @param condParams 条件片段 {@code #{condParams.pN}} 占位的绑定参数（SafeConditionFragment 重建后）
     * @return 数据列表
     */
    @MapKey("id")
    List<Map<String, Object>> selectList(@Param("tableName") String tableName, @Param("tableMap") Map<Object, Object> tableMap,
                                         @Param("condParams") Map<String, Object> condParams);

    /**
     * 条件查询数据总数
     *
     * @param tableName  目标表名
     * @param tableMap   查询条件 Map
     * @param condParams 条件片段 {@code #{condParams.pN}} 占位的绑定参数（SafeConditionFragment 重建后）
     * @return 符合条件的记录数
     */
    int countData(@Param("tableName") String tableName, @Param("tableMap") Map<Object, Object> tableMap,
                  @Param("condParams") Map<String, Object> condParams);

    /**
     * 执行自定义 SQL 查询
     *
     * @param tableName 目标表名
     * @param value     动态 SQL 语句
     * @return 查询结果列表
     */
    List<HashMap<String, Object>> querySql(@Param("tableName") String tableName, String value);

    /**
     * 根据条件更新数据（支持将字段更新为 null）
     *
     * @param tableName 目标表名
     * @param tableMap  数据 Map（需包含主键 ID）
     */
    void updateWithNull(@Param("tableName") String tableName, @Param("tableMap") Map<Object, Object> tableMap);

    /**
     * 按父数据 ID 删除子表行数据（子表整体重建 / 主表数据级联删除）
     * <p>与 {@link #selectByParentId} 同样不加行级数据范围，否则删不掉别部门写的子表行，重插后变成重复行。
     *
     * @param tableName 子表表名
     * @param list      父数据 ID 列表
     */
    @IgnoreDataScope
    void deleteByParentIds(@Param("tableName") String tableName, @Param("list") List<String> list);

    /** 子表列合计（按主行聚合，服务端按列元数据筛数值列） */
    java.math.BigDecimal selectSumByParent(@Param("tableName") String tableName,
                                           @Param("column") String column,
                                           @Param("parentId") String parentId);

    /** 单列按主键更新（子表合计回写主表字段用，列名服务端白名单给出） */
    void updateColumnById(@Param("tableName") String tableName,
                          @Param("column") String column,
                          @Param("value") Object value,
                          @Param("id") String id);

    /**
     * 子表行随主表行授权，不加行级数据范围：
     * 子表行的 department_id 取的是「保存时编辑人所在部门」，与主表行未必同部门。
     * 若按行过滤，会出现主表能打开、子表回显缺行，而「整体重建」的 deleteByParentIds 又过滤掉别部门写的行，
     * 与随后重插的副本叠加成重复行。
     *
     * @param tableName 子表表名
     * @param parentId  父数据 ID
     * @return 行数据列表（键为数据库列名）
     */
    @IgnoreDataScope
    List<Map<String, Object>> selectByParentId(@Param("tableName") String tableName, @Param("parentId") String parentId);

    /**
     * 统计某列等于指定值（且 id 不同于排除值）用于「不允许重复」保存校验。
     * 校验必须比行级范围宽：同名单元格上建的是数据库唯一索引（{@code UNI_列名}），
     * 行级过滤会让校验放行、插入时改由 MySQL 报 1062，用户看到的是一句看不懂的数据库错误。
     *
     * @param tableName 表名
     * @param column    列名（调用前必须校验为合法标识符）
     * @param value     期望值
     * @param excludeId 排除的数据 ID（更新时排除自身），可为 null
     * @return 命中行数
     */
    @IgnoreDataScope
    int countByColumnValue(@Param("tableName") String tableName, @Param("column") String column,
                           @Param("value") String value, @Param("excludeId") String excludeId);

    /**
     * 复合唯一应用层判重兜底（5.9）候选行捞取（活跃行）。
     * 筛条件是「非空成员有序序列相等」的超集：每列 is null 或 ∈ eqValues，且非空列数 = nonNullCount
     * （eqValues 为空时退化为全 NULL 行，即 key 空串口径）。筛余由应用层按生成列口径归一 key 精确比对。
     * 列名来自服务端解析（metaData→列名 ∩ 物理列，过标识符校验）后 ${} 拼接；值全部 #{} 参数化。
     */
    List<Map<String, Object>> selectUniKeyRows(@Param("tableName") String tableName,
                                               @Param("memberColumns") List<String> memberColumns,
                                               @Param("eqValues") List<Object> eqValues,
                                               @Param("nonNullCount") int nonNullCount,
                                               @Param("excludeId") String excludeId);

    /** 复合唯一兜底·还原路径：按主键取行成员值（不过软删过滤，还原前读被还原行） */
    Map<String, Object> selectMemberRowById(@Param("tableName") String tableName,
                                            @Param("memberColumns") List<String> memberColumns,
                                            @Param("id") String id);
}