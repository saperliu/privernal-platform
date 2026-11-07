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

package com.privernal.dynamic.report.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.service.IDynamicFormColumnService;
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.domain.DynamicReport;
import com.privernal.dynamic.datav.service.connector.DatavSourceSecrets;
import com.privernal.system.api.ISecretCipherService;
import com.privernal.dynamic.report.mapper.DynamicDatasetMapper;
import com.privernal.dynamic.report.mapper.DynamicReportMapper;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 报表数据集服务实现
 * <p>
 * 列快照在建数据集时从 dynamic_form_column 解析一次落库（全列，不按 show_list 过滤——
 * 新表默认不勾选显示列，按 showList 过滤会让新表一个维度都选不到）。
 * dataType 由物理列类型判定：number（数值，可作度量）/ date（时间，可带粒度分组）/ text。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DynamicDatasetServiceImpl implements IDynamicDatasetService {

    /** 临时数据集（B8 问数）的运行期 datasetCode 前缀；持久化保存链会拒绝该前缀编码 */
    public static final String TRANSIENT_PREFIX = "TRANSIENT:";

    @Autowired
    private DynamicDatasetMapper datasetMapper;

    @Autowired
    private DynamicReportMapper reportMapper;

    @Autowired
    private IDynamicFormColumnService dynamicFormColumnService;

    @Autowired
    private com.privernal.dynamic.report.service.DynamicReportTableResolver tableResolver;

    @Autowired
    private com.privernal.dynamic.datav.service.DatavDataSourceResolver datavDataSourceResolver;

    /** 上传形态的 CSV 字节存在 dynamic 模块自己的表里，生命周期跟着数据集走 */
    @Autowired
    private com.privernal.dynamic.datav.service.IDynamicDatavFileService datavFileService;

    /** 机读密钥的可逆存储复用 system-api 的接口，禁止在本模块再写一套 AES */
    @Autowired
    private ISecretCipherService secretCipher;

    /** 子表定义解析（M3-2 白名单关联）：sub-table 节点/物理子表名与建表链同一套口径 */
    @Autowired
    private com.privernal.dynamic.dbs.service.DynamicSubTableService dynamicSubTableService;

    /** 关联登记（M3-2）：relation 关联必须命中 dynamic_form_relation，模型不得自创 JOIN */
    @Autowired
    private com.privernal.dynamic.dbs.service.DynamicFormRelationService dynamicFormRelationService;

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDataset saveDynamicDataset(DynamicDataset dataset) {
        if (dataset == null || StringUtils.isBlank(dataset.getDatasetCode())) {
            throw new ServiceException("数据集编码不能为空");
        }
        // 多源数据集（M3）：无 tableCode，列快照由连接器抽样产出；动态表走原 SQL 路径
        boolean external = datavDataSourceResolver.isExternalSource(dataset.getSourceType());
        if (!external && StringUtils.isBlank(dataset.getTableCode())) {
            throw new ServiceException("数据集必须绑定动态表");
        }

        DynamicDataset exists = findByCode(dataset.getDatasetCode());
        // 提交值里的派生列要留到快照重算之后再合并回来
        JSONArray submittedDerived = com.privernal.dynamic.datav.service.DerivedColumnSpec
                .submittedDerived(dataset.getColumnSnapshot());
        if (external) {
            // 凭据在落库前换成密文；表单把掩码原样提交回来时沿用库里已有的密文（M3.3）
            dataset.setSourceConfig(DatavSourceSecrets.seal(dataset.getSourceConfig(),
                    exists == null ? null : exists.getSourceConfig(), secretCipher));
        }
        if (exists == null) {
            dataset.setId(null);
            dataset.setCreatedBy(SecurityUtils.getUserId());
            dataset.setCreatedTime(new Date());
            dataset.setOrganizationId(SecurityUtils.getOrganizationId());
            dataset.setDepartmentId(SecurityUtils.getDepartmentId());
        } else {
            dataset.setId(exists.getId());
            dataset.setOrganizationId(exists.getOrganizationId());
            dataset.setDepartmentId(exists.getDepartmentId());
            dataset.setUpdatedBy(SecurityUtils.getUserId());
            dataset.setUpdatedTime(new Date());
        }
        if (external) {
            // 先释放按库里旧连接参数建的池，再抽样。顺序反了会把本次刚建好的池当成旧的关掉；
            // 不驱逐则旧指纹一直挂在连接器上，攒到上限后新数据源直接报「池数量已达上限」。
            if (exists != null) {
                datavDataSourceResolver.invalidate(exists.getSourceType(), exists.getSourceConfig());
            }
            dataset.setColumnSnapshot(datavDataSourceResolver.sampleColumns(
                    dataset.getSourceType(), dataset.getSourceConfig()));
            JSONArray columns = dataset.getColumnSnapshot() == null ? null : dataset.getColumnSnapshot().getJSONArray("columns");
            if (columns == null || columns.isEmpty()) {
                throw new ServiceException("数据源抽样没有得到任何字段，请检查接口地址与数据路径");
            }
        } else {
            // 逻辑表编码 → 物理表单（三级容错解析：带前缀/原编码/组织内后缀），列快照按解析到的表单解析；
            // B8-M2-1：解析+快照构建统一走 buildTransientDataset，问数临时数据集与保存链同路径；
            // M3-2：提交快照里的 joins 只当作「想关联什么」的意图，列与登记关系服务端重解析（不信任提交值）
            DynamicDataset transientDs = buildTransientDataset(dataset.getTableCode(),
                    dataset.getColumnSnapshot() == null ? null : dataset.getColumnSnapshot().getJSONArray("joins"));
            dataset.setColumnSnapshot(transientDs.getColumnSnapshot());
        }
        // 重新产出的快照只含物理列，先并回用户定义的派生列再整体校验；
        // 校验放在落库前，是为了让表达式写错在保存时就报错，而不是等某个图表取数时才暴露
        if (dataset.getColumnSnapshot() != null) {
            JSONArray physical = dataset.getColumnSnapshot().getJSONArray("columns");
            com.privernal.dynamic.datav.service.DerivedColumnSpec.mergeInto(physical, submittedDerived);
            validateDerived(dataset.getColumnSnapshot());
        }

        if (exists == null) {
            datasetMapper.insert(dataset);
        } else {
            LambdaPrivernalWrapper<DynamicDataset> wrapper = new LambdaPrivernalWrapper<>();
            wrapper.eq(DynamicDataset::getId, dataset.getId());
            datasetMapper.update(dataset, wrapper);
        }
        if (external) {
            syncUploadedFile(dataset.getSourceConfig(), exists == null ? null : exists.getSourceConfig(),
                    dataset.getDatasetCode());
        }
        return dataset;
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicDataset buildTransientDataset(String tableCode) {
        if (StringUtils.isBlank(tableCode)) {
            throw new ServiceException("表编码不能为空");
        }
        com.privernal.dynamic.dbs.domain.DynamicForm form = tableResolver.resolveForm(tableCode);
        if (form == null) {
            throw new ServiceException("动态表不存在：" + tableCode);
        }
        JSONObject snapshot = resolveColumnSnapshot(form.getId());
        if (snapshot == null) {
            throw new ServiceException("动态表没有任何列，无法构建数据集：" + tableCode);
        }
        // 只在内存里构建：不 insert/update，datasetCode 仅作运行期标识与 chart 事件回显
        DynamicDataset dataset = new DynamicDataset();
        dataset.setDatasetCode(TRANSIENT_PREFIX + tableCode);
        dataset.setDatasetName(StringUtils.isNotBlank(form.getTableName()) ? form.getTableName() : tableCode);
        dataset.setTableCode(tableCode);
        dataset.setAppId(form.getAppId());
        dataset.setSourceType("dynamic");
        dataset.setColumnSnapshot(snapshot);
        return dataset;
    }

    /** 一次问数最多挂几个白名单关联：JOIN 面失控等同 Text2SQL（刻意不做），宁紧勿松 */
    private static final int MAX_JOINS = 3;

    /** 标识符白名单（M3-2）：关联字段/物理表名都拼进 SQL，必须先行校验 */
    private static final java.util.regex.Pattern JOIN_IDENTIFIER =
            java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    @Override
    @Transactional(readOnly = true)
    public DynamicDataset buildTransientDataset(String tableCode, JSONArray joins) {
        DynamicDataset dataset = buildTransientDataset(tableCode);
        if (joins == null || joins.isEmpty()) {
            return dataset;
        }
        com.privernal.dynamic.dbs.domain.DynamicForm form = tableResolver.resolveForm(tableCode);
        if (form == null) {
            throw new ServiceException("动态表不存在：" + tableCode);
        }
        JSONObject snapshot = dataset.getColumnSnapshot();
        snapshot.put("joins", resolveJoinMeta(form, snapshot, joins));
        dataset.setColumnSnapshot(snapshot);
        return dataset;
    }

    /**
     * 白名单关联解析：subTable=主表元数据里的 sub-table 节点（物理表=主表编码_字段编码，
     * ON=parent_id）；relation=dynamic_form_relation 已登记关系且主表该字段节点的
     * relation.formCode 指向目标表（ON=目标表.id）。关联列以「{field}_{列}」前缀并入快照，
     * 与主表列撞名直接拒绝——SQL 里没有歧义引用的生存空间。
     */
    private JSONArray resolveJoinMeta(com.privernal.dynamic.dbs.domain.DynamicForm mainForm,
                                      JSONObject mainSnapshot, JSONArray joins) {
        if (joins.size() > MAX_JOINS) {
            throw new ServiceException("一次问数最多挂 " + MAX_JOINS + " 个关联，请收窄口径");
        }
        JSONArray meta = new JSONArray();
        JSONArray mergedColumns = mainSnapshot.getJSONArray("columns");
        java.util.Set<String> used = new java.util.LinkedHashSet<>();
        for (int i = 0; i < mergedColumns.size(); i++) {
            used.add(mergedColumns.getJSONObject(i).getString("fieldCode"));
        }
        for (int i = 0; i < joins.size(); i++) {
            JSONObject join = joins.getJSONObject(i);
            if (join == null) {
                throw new ServiceException("关联配置必须是对象：{type, field, tableCode?}");
            }
            String type = StringUtils.trimToEmpty(join.getString("type"));
            if (!"subTable".equals(type) && !"relation".equals(type)) {
                throw new ServiceException("不支持的关联类型：" + type + "（仅支持 subTable|relation）");
            }
            String field = join.getString("field");
            if (StringUtils.isBlank(field) || !JOIN_IDENTIFIER.matcher(field).matches()) {
                throw new ServiceException("关联字段 field 非法：" + field + "（须是主表单的字段编码）");
            }
            if ("subTable".equals(type)) {
                meta.add(resolveSubTableJoin(mainForm, field, used));
            } else {
                meta.add(resolveRelationJoin(mainForm, field,
                        StringUtils.trimToEmpty(join.getString("tableCode")), used));
            }
        }
        return meta;
    }

    /** 子表关联：field 必须命中主表元数据的 sub-table 节点；行内列与建表链同一来源 */
    private JSONObject resolveSubTableJoin(com.privernal.dynamic.dbs.domain.DynamicForm mainForm,
                                           String field, java.util.Set<String> used) {
        com.privernal.dynamic.dbs.service.DynamicSubTableService.SubTableDef def = null;
        for (com.privernal.dynamic.dbs.service.DynamicSubTableService.SubTableDef candidate
                : dynamicSubTableService.resolveSubTables(mainForm.getTableCode(), mainForm.getMetaData())) {
            if (field.equals(candidate.getField())) {
                def = candidate;
                break;
            }
        }
        if (def == null) {
            throw new ServiceException("该关联未登记，请先在表单设计中配置子表：「" + field
                    + "」不是「" + mainForm.getTableName() + "」的 sub-table 字段");
        }
        JSONArray joinColumns = new JSONArray();
        collectSubTableColumns(def.getNode().getJSONArray("children"), field, joinColumns, used);
        if (joinColumns.isEmpty()) {
            throw new ServiceException("子表「" + field + "」没有任何行内字段，无法关联统计");
        }
        JSONObject joinMeta = new JSONObject();
        joinMeta.put("type", "subTable");
        joinMeta.put("field", field);
        joinMeta.put("label", StringUtils.isNotBlank(def.getLabel()) ? def.getLabel() : field);
        joinMeta.put("physicalTable", def.getChildTable());
        joinMeta.put("columns", joinColumns);
        return joinMeta;
    }

    /** 递归收集子表行内字段（跳过嵌套子表）；列名清洗与建表链同走 FormTransferTable.toDbColumnName */
    private void collectSubTableColumns(JSONArray children, String joinField,
                                        JSONArray out, java.util.Set<String> used) {
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.size(); i++) {
            JSONObject child = children.getJSONObject(i);
            if (child == null || "sub-table".equals(child.getString("type"))) {
                continue;
            }
            if (StringUtils.isNotBlank(child.getString("field"))) {
                String rawColumn = FormTransferTable.toDbColumnName(child.getString("field"));
                if (StringUtils.isBlank(rawColumn) || !JOIN_IDENTIFIER.matcher(rawColumn).matches()) {
                    continue;
                }
                String prefixed = joinField + "_" + rawColumn;
                if (used.contains(prefixed)) {
                    throw new ServiceException("关联列与已有列重名：" + prefixed + "，请调整表单字段编码");
                }
                used.add(prefixed);
                JSONObject item = new JSONObject();
                item.put("fieldCode", prefixed);
                item.put("column", rawColumn);
                item.put("fieldName", StringUtils.isNotBlank(child.getString("label"))
                        ? child.getString("label") : child.getString("field"));
                item.put("dataType", resolveDataType(FormTransferTable.formCompToColumnType(
                        StringUtils.defaultIfBlank(child.getString("type"), "text"))));
                out.add(item);
                continue;
            }
            collectSubTableColumns(child.getJSONArray("children"), joinField, out, used);
        }
    }

    /** 引用关联：必须命中 dynamic_form_relation 登记且主表该字段的 relation.formCode 指向目标表 */
    private JSONObject resolveRelationJoin(com.privernal.dynamic.dbs.domain.DynamicForm mainForm,
                                           String field, String targetTableCode, java.util.Set<String> used) {
        if (StringUtils.isBlank(targetTableCode)) {
            throw new ServiceException("relation 关联必须传 tableCode（被关联表的逻辑表码）");
        }
        com.privernal.dynamic.dbs.domain.DynamicForm targetForm = tableResolver.resolveForm(targetTableCode);
        if (targetForm == null) {
            throw new ServiceException("关联目标表不存在：" + targetTableCode);
        }
        String targetPhysical = targetForm.getTableCode();
        if (dynamicFormRelationService.selectByReferencer(mainForm.getTableCode(), targetPhysical).isEmpty()) {
            throw new ServiceException("该关联未登记，请先在表单设计中配置引用关系：「"
                    + mainForm.getTableName() + "」→「" + targetPhysical + "」");
        }
        if (!metaDataDeclaresRelation(mainForm.getMetaData(), field, targetPhysical)) {
            throw new ServiceException("主表字段「" + field + "」没有配置指向「" + targetPhysical
                    + "」的关联（relation.formCode 不匹配），不能按它关联");
        }
        JSONObject targetSnapshot = resolveColumnSnapshot(targetForm.getId());
        if (targetSnapshot == null || targetSnapshot.getJSONArray("columns") == null
                || targetSnapshot.getJSONArray("columns").isEmpty()) {
            throw new ServiceException("关联目标表没有任何列，无法关联：" + targetPhysical);
        }
        JSONArray joinColumns = new JSONArray();
        for (int i = 0; i < targetSnapshot.getJSONArray("columns").size(); i++) {
            JSONObject column = targetSnapshot.getJSONArray("columns").getJSONObject(i);
            String raw = column.getString("fieldCode");
            String prefixed = field + "_" + raw;
            if (used.contains(prefixed)) {
                throw new ServiceException("关联列与已有列重名：" + prefixed + "，请调整表单字段编码");
            }
            used.add(prefixed);
            JSONObject item = new JSONObject();
            item.put("fieldCode", prefixed);
            item.put("column", raw);
            item.put("fieldName", column.getString("fieldName"));
            item.put("dataType", column.getString("dataType"));
            joinColumns.add(item);
        }
        JSONObject joinMeta = new JSONObject();
        joinMeta.put("type", "relation");
        joinMeta.put("field", field);
        joinMeta.put("label", StringUtils.isNotBlank(targetForm.getTableName())
                ? targetForm.getTableName() : targetPhysical);
        joinMeta.put("physicalTable", targetPhysical);
        joinMeta.put("columns", joinColumns);
        return joinMeta;
    }

    /**
     * 主表元数据里 field 字段的节点是否配置了指向 targetPhysical 的 relation
     * （设计器挂在 props.relation，遗留写法 componentProps.relation，与登记解析同口径）。
     */
    private boolean metaDataDeclaresRelation(Object node, String field, String targetPhysical) {
        if (node instanceof JSONObject obj) {
            if (field.equals(obj.getString("field"))) {
                JSONObject relation = obj.getJSONObject("props") instanceof JSONObject props
                        ? props.getJSONObject("relation") : null;
                if (relation == null && obj.getJSONObject("componentProps") instanceof JSONObject cp) {
                    relation = cp.getJSONObject("relation");
                }
                if (relation != null && StringUtils.isNotBlank(relation.getString("formCode"))) {
                    com.privernal.dynamic.dbs.domain.DynamicForm referenced =
                            tableResolver.resolveForm(relation.getString("formCode"));
                    if (referenced != null && targetPhysical.equals(referenced.getTableCode())) {
                        return true;
                    }
                }
            }
            for (Object value : obj.values()) {
                if (metaDataDeclaresRelation(value, field, targetPhysical)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof JSONArray array) {
            for (Object item : array) {
                if (metaDataDeclaresRelation(item, field, targetPhysical)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 上传形态的 CSV 文件跟着数据集走：本次配置里的 fileId 绑到本数据集，
     * 上一次用而现在不再引用的那份就地清掉。
     * <p>
     * 「换成 URL 形态」「重新上传换一个文件」「删掉整个数据集」三条路都会让旧字节失去唯一引用，
     * 而 CSV 的字节是几百 KB 起步——留在库里就是一张除了体积什么都不长的表。
     */
    private void syncUploadedFile(JSONObject current, JSONObject previous, String datasetCode) {
        String currentFileId = uploadedFileId(current);
        String previousFileId = uploadedFileId(previous);
        if (StringUtils.isNotBlank(currentFileId)) {
            datavFileService.bindDataset(currentFileId, datasetCode);
        }
        if (StringUtils.isNotBlank(previousFileId) && !previousFileId.equals(currentFileId)) {
            datavFileService.deleteByFileCode(previousFileId);
        }
    }

    /** 取上传形态的 fileId；URL 形态与非 CSV 源都返回 null（没有需要随数据集清理的行） */
    private static String uploadedFileId(JSONObject sourceConfig) {
        if (!com.privernal.dynamic.datav.service.connector.CsvSourceConnector.isUploadMode(sourceConfig)) {
            return null;
        }
        return StringUtils.trimToNull(sourceConfig.getString("fileId"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDataset refreshDataset(String datasetCode) {
        DynamicDataset dataset = findByCode(datasetCode);
        if (dataset == null) {
            throw new ServiceException("数据集不存在：" + datasetCode);
        }
        JSONObject oldSnapshot = dataset.getColumnSnapshot();
        JSONObject newSnapshot;
        if (datavDataSourceResolver.isExternalSource(dataset.getSourceType())) {
            newSnapshot = datavDataSourceResolver.sampleColumns(dataset.getSourceType(), dataset.getSourceConfig());
        } else {
            com.privernal.dynamic.dbs.domain.DynamicForm form = tableResolver.resolveForm(dataset.getTableCode());
            if (form == null) {
                throw new ServiceException("数据集绑定的动态表已删除：" + dataset.getTableCode());
            }
            // M3-2：刷新保留已有 joins 意图并重解析——登记被删/子表改名时在这里给可读错误
            newSnapshot = buildTransientDataset(dataset.getTableCode(),
                    oldSnapshot == null ? null : oldSnapshot.getJSONArray("joins")).getColumnSnapshot();
        }
        // 重算出的快照只认识物理列：不把库里已有的计算字段并回来，一次刷新就静默抹掉它们，
        // 引用它们的图表只会报「列不存在」，看不出是刷新动作造成的
        if (newSnapshot != null && oldSnapshot != null) {
            com.privernal.dynamic.datav.service.DerivedColumnSpec.mergeInto(
                    newSnapshot.getJSONArray("columns"),
                    com.privernal.dynamic.datav.service.DerivedColumnSpec.submittedDerived(oldSnapshot));
        }
        // 已保存图表引用了被删除字段时报错，不做静默兼容（放在合并之后，否则计算字段会被当成「已删除」）
        if (oldSnapshot != null) {
            JSONArray oldColumns = oldSnapshot.getJSONArray("columns");
            JSONArray newColumns = newSnapshot == null ? null : newSnapshot.getJSONArray("columns");
            List<String> removed = new ArrayList<>();
            for (int i = 0; oldColumns != null && i < oldColumns.size(); i++) {
                String fieldCode = oldColumns.getJSONObject(i).getString("fieldCode");
                boolean stillThere = false;
                for (int j = 0; newColumns != null && j < newColumns.size(); j++) {
                    if (fieldCode.equals(newColumns.getJSONObject(j).getString("fieldCode"))) {
                        stillThere = true;
                        break;
                    }
                }
                if (!stillThere) {
                    removed.add(fieldCode);
                }
            }
            if (!removed.isEmpty()) {
                LambdaPrivernalWrapper<DynamicReport> reportWrapper = new LambdaPrivernalWrapper<>();
                reportWrapper.eq(DynamicReport::getDatasetCode, datasetCode);
                List<DynamicReport> reports = reportMapper.selectList(reportWrapper);
                if (!reports.isEmpty()) {
                    throw new ServiceException("字段 " + String.join(", ", removed)
                            + " 已被图表引用（数据集 " + datasetCode + " 下 " + reports.size()
                            + " 个图表），请先调整图表配置再刷新");
                }
            }
        }
        // 合并回来的计算字段可能引用了这次重算中消失的物理列，落库前校验掉，
        // 否则错误要等到某个图表取数时才暴露，而且报的是「列不存在」
        validateDerived(newSnapshot);
        dataset.setColumnSnapshot(newSnapshot);
        LambdaPrivernalWrapper<DynamicDataset> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDataset::getId, dataset.getId());
        dataset.setUpdatedBy(SecurityUtils.getUserId());
        dataset.setUpdatedTime(new Date());
        datasetMapper.update(dataset, wrapper);
        return dataset;
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicDataset findByCode(String datasetCode) {
        if (StringUtils.isBlank(datasetCode)) {
            return null;
        }
        LambdaPrivernalWrapper<DynamicDataset> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDataset::getDatasetCode, datasetCode);
        return datasetMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DynamicDataset> listByApp(String appId) {
        LambdaPrivernalWrapper<DynamicDataset> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDataset::getAppId, appId);
        wrapper.orderByDesc(DynamicDataset::getCreatedTime);
        return datasetMapper.selectList(wrapper);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteByCode(String datasetCode) {
        DynamicDataset dataset = findByCode(datasetCode);
        if (dataset == null) {
            return 0;
        }
        LambdaPrivernalWrapper<DynamicReport> reportWrapper = new LambdaPrivernalWrapper<>();
        reportWrapper.eq(DynamicReport::getDatasetCode, datasetCode);
        Long referenced = reportMapper.selectCount(reportWrapper);
        if (referenced != null && referenced > 0) {
            throw new ServiceException("数据集已被 " + referenced + " 个图表引用，请先删除相关图表");
        }
        // 先改名释放 dataset_code 唯一索引，便于同名数据集重建（与 dynamic_form.table_code 同模式）
        DynamicDataset rename = new DynamicDataset();
        rename.setDatasetCode(datasetCode + "_deleted_" + dataset.getId());
        LambdaPrivernalWrapper<DynamicDataset> renameWrapper = new LambdaPrivernalWrapper<>();
        renameWrapper.eq(DynamicDataset::getId, dataset.getId());
        datasetMapper.update(rename, renameWrapper);
        // 外部源可能要释放连接池；即便后续删除回滚，代价也只是下次重新建连
        datavDataSourceResolver.invalidate(dataset.getSourceType(), dataset.getSourceConfig());
        int rows = datasetMapper.deleteFillById(dataset.getId());
        // 上传形态的字节只有这一个数据集在用，数据集没了它就只是体积
        datavFileService.deleteByDatasetCode(datasetCode);
        return rows;
    }

    /** 派生列表达式校验：错误里带上是哪个计算字段，否则用户要在一堆字段里自己找 */
    private void validateDerived(JSONObject snapshot) {
        JSONArray columns = snapshot == null ? null : snapshot.getJSONArray("columns");
        if (columns == null) {
            return;
        }
        java.util.Map<String, com.alibaba.fastjson2.JSONObject> derived = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> baseTypes = new java.util.LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            com.alibaba.fastjson2.JSONObject column = columns.getJSONObject(i);
            String code = column == null ? null : column.getString("fieldCode");
            if (StringUtils.isBlank(code)) {
                continue;
            }
            if (StringUtils.isNotBlank(column.getString("expression"))) {
                derived.put(code, column);
                continue;
            }
            baseTypes.put(code, StringUtils.defaultIfBlank(column.getString("dataType"), "text"));
        }
        com.privernal.dynamic.datav.service.DerivedColumnSpec.validate(derived, baseTypes);
    }

    /**
     * 从 dynamic_form_column 解析列快照（全列）：{columns:[{fieldCode, fieldName, dataType}]}
     */
    private JSONObject resolveColumnSnapshot(String formId) {
        List<DynamicFormColumn> columns = dynamicFormColumnService.selectDynamicFormColumnByFormId(formId);
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        JSONArray arr = new JSONArray();
        for (DynamicFormColumn column : columns) {
            if (StringUtils.isBlank(column.getFiledCode())) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("fieldCode", column.getFiledCode());
            item.put("fieldName", StringUtils.isNotBlank(column.getFiledName())
                    ? column.getFiledName() : column.getFiledCode());
            item.put("dataType", resolveDataType(column.getFiledType()));
            arr.add(item);
        }
        JSONObject snapshot = new JSONObject();
        snapshot.put("columns", arr);
        return snapshot;
    }

    private String resolveDataType(String filedType) {
        if (FormTransferTable.isNumericColumnType(filedType)) {
            return "number";
        }
        if (FormTransferTable.isTemporalColumnType(filedType)) {
            return "date";
        }
        return "text";
    }
}
