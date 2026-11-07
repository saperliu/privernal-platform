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

package com.privernal.dynamic.dbs.mapper;

import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.dynamic.dbs.domain.DynamicFormRelation;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;

/**
 * 动态表单关联引用注册 Mapper
 * <p>
 * 不加 {@code @PrivernalDataScope}：关联引用是表单配置元信息，读取授权要覆盖「有引用方表单权限但非创建人」
 * 的业务用户，按部门/本人数据范围过滤会让关联下拉恒为空；组织隔离由拦截器自动生效。
 * <p>
 * SQL 在 XML（resources/mapper/dynamic/DynamicFormRelationMapper.xml）：deleted_time 含
 * MySQL/SQLite 方言差异，走 privernal.dialect 片段；{@code @InterceptorIgnore} 标注在接口方法上
 * 依旧生效（MP 拦截器从 mapper 接口方法读取，与 SQL 所在位置无关）。
 *
 * @author saperliu
 */
public interface DynamicFormRelationMapper extends PrivernalMapper<DynamicFormRelation> {

    /**
     * 按引用方表单清理登记（逻辑删除）。
     * <p>
     * 显式参数化 SQL + 跳过组织拦截器：form_id 全局唯一、删除范围精确，无需组织过滤；
     * 且避免 MP wrapper-delete 与组织拦截器 beforePrepare 重写在运行环境组合下
     * 产生「Parameter index out of range」的问题。
     *
     * @param formId 引用方表单ID
     * @return 清理行数
     */
    @InterceptorIgnore(tenantLine = "true")
    int deleteByFormId(@Param("formId") String formId);

    /**
     * 清理「别的表单引用了某张被删表」的登记（逻辑删除），按被关联表编码
     *
     * @param targetTableCode 被关联表单编码
     * @return 清理行数
     */
    @InterceptorIgnore(tenantLine = "true")
    int deleteByTargetTableCode(@Param("targetTableCode") String targetTableCode);
}
