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

package com.privernal.apppackage.dto;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用包：单个低代码应用的可移植元数据快照。
 *
 * 只包含元数据（表单定义、列表列配置、流程定义、节点字段/按钮权限、报表数据集/图表/仪表盘），
 * 不含任何业务数据行。主键、组织、租户、密钥与审计列不入包，由导入端按目标环境重新生成。
 *
 * @author saperliu
 */
@Data
public class AppPackageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前包结构版本（1.4 起：大屏与素材入包，datav-plan M7.5） */
    public static final String SPEC_VERSION = "1.4";

    /** 可导入的历史版本：1.4 向后兼容 1.3/1.2（旧包无 screens/assets 段，导入为空列表） */
    public static final List<String> SUPPORTED_VERSIONS = List.of(SPEC_VERSION, "1.3", "1.2");

    /** 包结构版本，导入时校验兼容性 */
    private String specVersion = SPEC_VERSION;

    /** 导出时间，仅用于人工比对 */
    private String exportedAt;

    private App app = new App();

    private List<Form> forms = new ArrayList<>();

    private List<Flow> flows = new ArrayList<>();

    /** 报表数据集（1.1 起；只存逻辑 tableCode 与列快照，导入端重新校验） */
    private List<Dataset> datasets = new ArrayList<>();

    /** 报表图表（1.1 起） */
    private List<ReportAsset> reports = new ArrayList<>();

    /** 仪表盘（1.1 起，含卡片明细） */
    private List<Dashboard> dashboards = new ArrayList<>();

    /** 设计器定时任务（1.2 起，：DESIGNER/DESIGNER_DATA 来源入包，调度状态列不入包） */
    private List<ScheduleTaskPackage> scheduleTasks = new ArrayList<>();

    /** 单据打印模板（1.3 起，plan-sheet-print §3.7）：判重键 template_code（稳定编码，不用 id/form_id）；
     * content_html 与组织无关原样落库（架构约束第 8 条的红利），formTableCode 供导入端重挂表单 */
    private List<DocTemplate> docTemplates = new ArrayList<>();

    /** 数据大屏（1.4 起）：判重键 screen_code（稳定业务编码）；schema 原样入包，
     *  导入端走 saveDatavScreen 复用菜单自动化与版本记录 */
    private List<DatavScreen> screens = new ArrayList<>();

    /** 大屏素材元数据（1.4 起）：只带「被导出大屏引用的」素材行。文件字节在文件服务
     *  的存储里，不入包——本地后端可随包拷贝 ${file.path} 目录，OSS/MinIO 需人工迁移后
     *  核对 file_url 可达性；素材行的登记本身是幂等的 */
    private List<DatavAsset> datavAssets = new ArrayList<>();

    /**
     * 定时任务资产：只带配置面。剥离不可迁移列（id/status/next_fire_time/pending/owner/
     * lease/running 系列/trigger_count/fail_count/version，§9.1 第3条）——next_fire_time 在
     * 目标环境按注册时刻重算。action_config/biz_context 里的物理表名是环境特定的，
     * 导入端不做自动改写，解析不到目标表单时跳过并在导入报告列明（§9.1 第4条）。
     */
    @Data
    public static class DocTemplate implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 稳定编码；应用包判重键（UNI(template_code, deleted_time)） */
        private String templateCode;
        private String name;
        /** 关联表单编码（导入端按它重挂新环境 form_id） */
        private String formTableCode;
        private String sourceType;
        /** 逻辑 tableCode（不带组织前缀，导入原样落库） */
        private String sourceCode;
        private String templateType;
        private String status;
        /** 版式真相：原样落库（HTML 与组织无关） */
        private String contentHtml;
        /** SHEET 模板版式真相（Luckysheet JSON，同样与组织无关原样落库） */
        private String contentSheet;
        private String bindingJson;
        private String paramsJson;
        private String printConfig;
        private String isDefault;
        private Integer sort;
    }

    @Data
    public static class ScheduleTaskPackage implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 应用内稳定编码，uk_code 判重主键 */
        private String taskCode;
        private String title;
        private String source;
        private String bizKey;
        private String triggerType;
        private String cronExpr;
        private Integer intervalSeconds;
        private Integer jitterSeconds;
        private String misfirePolicy;
        private Integer misfireWindowSeconds;
        private String concurrentPolicy;
        private Integer timeoutSeconds;
        private Integer maxRetry;
        private String actionType;
        private String actionConfig;
        private String bizContext;
        /** 一次性任务的原定触发时刻（导入按"缺失/已过期即跳过"处理，绝不导入即执行） */
        private java.util.Date fireTime;
        private String remark;
    }

    @Data
    public static class App implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 应用编码，同时用作菜单编码与路由 path，是跨环境的业务主键 */
        private String code;
        private String appName;
        private String icon;
        private String type;
        private String pcPath;
        private String appPath;
        private Long status;
        private Long sort;
    }

    @Data
    public static class Form implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 表单编码 = 物理表名，dynamic_form 上有全局唯一键 */
        private String tableCode;
        private String tableName;
        private Long isWorkflow;
        private String workflowCode;
        /** 设计器产出的完整 schema */
        private JSONObject metaData;
        /** 列表设计配置（showList/showQuery 不在 metaData 内，必须单独入包） */
        private List<Column> columns = new ArrayList<>();
    }

    @Data
    public static class Column implements Serializable {
        private static final long serialVersionUID = 1L;
        private String filedCode;
        private String filedName;
        private String filedType;
        private Long length;
        private Long scale;
        private Long sort;
        private String defaultValue;
        private String queryDefaultValue;
        private String orderDir;
        private Long showList;
        private Long showQuery;
        /** 敏感字段标记 Y/N（应用包必须携带，否则跨环境导入后标记丢失） */
        private String sensitive;
        /** 业务唯一键标记 Y/N（props.unique 物化镜像） */
        private String uniqueFlag;
    }

    @Data
    public static class Flow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String flowCode;
        private String flowName;
        /** warm-flow exportJson 产物，导入前会剥离 id/version（见 AppPackageService） */
        private JSONObject definition;
        private List<FormVersion> formVersions = new ArrayList<>();
    }

    @Data
    public static class FormVersion implements Serializable {
        private static final long serialVersionUID = 1L;
        private String tableCode;
        private String formName;
        private List<FieldPerm> fields = new ArrayList<>();
        private List<BtnPerm> buttons = new ArrayList<>();
    }

    @Data
    public static class FieldPerm implements Serializable {
        private static final long serialVersionUID = 1L;
        /** global 为全局默认节点，其余为流程节点编码 */
        private String nodeCode;
        private String fieldCode;
        private String fieldName;
        private Integer isShow;
        private Integer isEdit;
        private Integer isRequired;
        private String defaultType;
        private String defaultValue;
        private String dictType;
        private Integer sort;
    }

    @Data
    public static class BtnPerm implements Serializable {
        private static final long serialVersionUID = 1L;
        private String nodeCode;
        private String btnCode;
        private String btnName;
        private String aliasName;
        private Integer isShow;
        private Integer showDialog;
        private String color;
        private Integer sort;
    }

    @Data
    public static class Dataset implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 数据集编码，跨环境业务主键 */
        private String datasetCode;
        private String datasetName;
        /** 逻辑表编码（不带组织前缀），导入端按目标环境表单重新校验列快照 */
        private String tableCode;
        private JSONObject columnSnapshot;
        private JSONObject defaultFilter;
        /** 多源四列（1.4 起）：source_type=dynamic_table 的旧包缺省即动态表，行为不变。
         *  sourceConfig 导出前已剥离凭据密文（*Cipher 键），目标环境需重配凭据 */
        private String sourceType;
        private JSONObject sourceConfig;
        private JSONObject refreshConfig;
        private JSONObject computedColumns;
    }

    @Data
    public static class DatavScreen implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 业务主键，跨环境可移植；已存在时按 overwrite 决定覆盖或跳过 */
        private String screenCode;
        private String screenName;
        /** absolute | grid */
        private String layoutMode;
        /** 画布 JSON 原样入包（schemaVersion 由前端 migrate 兜底）；导入后为草稿态，需重新发布 */
        private String schemaJson;
    }

    @Data
    public static class DatavAsset implements Serializable {
        private static final long serialVersionUID = 1L;
        private String assetCode;
        private String assetName;
        private String category;
        /** image | video */
        private String assetType;
        /** 文件服务 URL：跨环境不保证可达（见顶层注释），导入端原样登记 */
        private String fileUrl;
        private String originalName;
        private Long fileSize;
    }

    @Data
    public static class ReportAsset implements Serializable {
        private static final long serialVersionUID = 1L;
        private String reportCode;
        private String reportName;
        private String reportType;
        private String datasetCode;
        private JSONObject config;
    }

    @Data
    public static class Dashboard implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dashboardCode;
        private String dashboardName;
        private JSONObject config;
        private List<DashboardItem> items = new ArrayList<>();
    }

    @Data
    public static class DashboardItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private String reportCode;
        private String title;
        private Integer span;
        private Integer orderNum;
        private JSONObject config;
    }
}
