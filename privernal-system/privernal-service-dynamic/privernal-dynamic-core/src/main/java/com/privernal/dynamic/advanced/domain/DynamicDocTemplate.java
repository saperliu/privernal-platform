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

package com.privernal.dynamic.advanced.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 单据打印模板（plan-sheet-print §3.2）。一表单多模板。
 * <p>
 * 架构约束（第 8 条）：只存 content_html + binding_json——HTML 是唯一版式真相，
 * 禁存编辑器私有结构（ProseMirror/slate JSON），编辑器更换成本才能锁在 5–8 人日。
 * binding_json 描述"哪个表格（data-detail-key）的哪一行（data-template-row）是明细模板行、
 * 占位符对应哪些字段"；模板对字段是软引用：渲染期缺列跳过 + warnings（§2 缺列不炸）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dynamic_doc_template", autoResultMap = true)
public class DynamicDocTemplate extends BaseEntity {
    private static final long serialVersionUID = 1L;

    public static final String TYPE_DOC = "DOC";

    /** SHEET=Luckysheet 表格模板（第二种单据打印方式，plan-sheet-print §11） */
    public static final String TYPE_SHEET = "SHEET";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String departmentId;

    private String organizationId;

    /** 稳定编码；应用包判重键（不用 id/form_id） */
    private String templateCode;

    /** 模板名称 */
    private String name;

    /** 归属应用，随应用包走（应用包丢 appId 是已知复发缺陷 B3，导入必须保留） */
    private String appId;

    /** 关联表单（列表/详情打印入口） */
    private String formId;

    /** FORM | DATASET */
    private String sourceType;

    /** 逻辑 tableCode 或 datasetCode（不带组织前缀；执行时现场 decorate） */
    private String sourceCode;

    /** DOC=单据（当前唯一）；预留 GRID（宽表台账）等 */
    private String templateType;

    /** 启用 1 / 停用 0：运行态模板下拉只列启用行；停用不删，历史单据仍可按模板重打 */
    private String status;

    /** 版式真相：编辑器产出的 HTML（DOC 模板） */
    private String contentHtml;

    /** 版式真相（SHEET 模板）：Luckysheet 单 sheet 导出 JSON（celldata + config）；
     *  占位符绑定自描述在单元格文本里（{{field}}/{{子表.字段}}/{{SUM(子表.字段)}}），binding_json 仅作观测快照 */
    private String contentSheet;

    /** 占位符与明细区绑定（plan-sheet-print §3.4） */
    private String bindingJson;

    /** 参数定义 [{field,op,label,default}] */
    private String paramsJson;

    /** paper/orientation/margins/scale/headerFooter */
    private String printConfig;

    /** 默认模板 Y/N（唯一性由服务层单条条件 update 保证，§3.2 v2.1） */
    private String isDefault;

    /** 排序 */
    private Integer sort;
}
