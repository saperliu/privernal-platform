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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.privernal.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 动态表数据导入任务持久化（R2-19 导入导出增强）。
 * <ul>
 *   <li>替代 IoDataService 内存 Map：任务全生命周期落库，重启可续、多实例可见（进度读库）；</li>
 *   <li>多实例续跑：{@code worker_id + heartbeat_time}——心跳超过 stale 阈值视为执行实例死亡，
 *       由 IoImportTaskMaintainer CAS 认领（WHERE 带 status/心跳条件，防双实例重复执行）；</li>
 *   <li>rows_json 存「以表头为键的原始字符串行」，类型转换在执行期做——续跑经 JSON 往返后与首跑行为一致；</li>
 *   <li>created_by=发起人，天然留痕；errors_json 封顶 5000 条失败明细。</li>
 * </ul>
 * JSON 列一律 String 字段承载（同 {@link DynamicFormIoConfig#getExportColumns()} 先例：
 * JSONObjectTypeHandler 只支持对象，顶层数组会炸）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dynamic_io_import_task")
public class DynamicIoImportTask extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 待映射确认（预检完成，等 start 带映射启动） */
    public static final String STATUS_PENDING_MAPPING = "PENDING_MAPPING";
    /** 执行中（含被认领续跑） */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 完成（行级失败不算；失败明细看 errors_json） */
    public static final String STATUS_FINISHED = "FINISHED";
    /** 任务级失败（整批 REJECT / 任务异常终止） */
    public static final String STATUS_FAILED = "FAILED";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    private String departmentId;

    private String organizationId;

    /** 关联 dynamic_form.id */
    private String formId;

    /** 动态物理表名 */
    private String tableCode;

    /** 业务数据源名 */
    private String databaseName;

    /** PENDING_MAPPING/RUNNING/FINISHED/FAILED */
    private String status;

    /** 总行数 */
    private Integer total;

    /** 已处理行数（含失败/跳过，续跑断点） */
    private Integer processed;

    /** 成功行数 */
    private Integer success;

    /** 跳过行数 */
    private Integer skipped;

    /** 失败行数 */
    private Integer failed;

    /** 执行实例标识 ip+pid+启动时间（CAS 认领防多实例重复执行） */
    private String workerId;

    /** 执行心跳；超过 stale 阈值可被重新认领续跑 */
    private java.util.Date heartbeatTime;

    /** Excel 表头行原始字符串数组 JSON */
    private String headersJson;

    /** 自定义列映射 {表头:字段编码}，空串=忽略该列 */
    private String mappingJson;

    /** 跨环境列结构预检报告（matched/unmatched/missingRequired/warnings） */
    private String precheckJson;

    /** 解析后的原始行（以表头为键的字符串值；类型转换在执行期做，保证续跑与首跑一致） */
    private String rowsJson;

    /** 失败行明细数组（封顶 5000 条） */
    private String errorsJson;

    /** 发起时 io 配置快照（dup/strict/runBackscript/importFlowMode 等） */
    private String configJson;

    /** 结束时间 */
    private java.util.Date finishedTime;
}
