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
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.domain.DynamicDashboardItem;
import com.privernal.dynamic.report.domain.DynamicReport;
import com.privernal.dynamic.report.mapper.DynamicDashboardItemMapper;
import com.privernal.dynamic.report.mapper.DynamicReportMapper;
import com.privernal.dynamic.report.service.DynamicReportQueryService;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.privernal.dynamic.report.service.IDynamicReportService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表图表服务实现
 *
 * @author saperliu
 */
@Service
public class DynamicReportServiceImpl implements IDynamicReportService {

    /** 支持的图表类型：与执行查询用同一份白名单，避免两处各自漂移 */
    private static final Set<String> REPORT_TYPES = DynamicReportQueryService.ALLOWED_TYPES;

    @Autowired
    private DynamicReportMapper reportMapper;

    @Autowired
    private DynamicDashboardItemMapper dashboardItemMapper;

    @Autowired
    private IDynamicDatasetService datasetService;

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicReport saveDynamicReport(DynamicReport report) {
        if (report == null || StringUtils.isBlank(report.getReportCode())) {
            throw new ServiceException("图表编码不能为空");
        }
        if (StringUtils.isBlank(report.getReportType()) || !REPORT_TYPES.contains(report.getReportType())) {
            throw new ServiceException("不支持的图表类型：" + report.getReportType());
        }
        DynamicDataset dataset = datasetService.findByCode(report.getDatasetCode());
        if (dataset == null) {
            throw new ServiceException("图表绑定的数据集不存在：" + report.getDatasetCode());
        }
        validateConfig(dataset, report.getConfig());

        DynamicReport exists = findByCode(report.getReportCode());
        if (exists == null) {
            report.setId(null);
            report.setCreatedBy(SecurityUtils.getUserId());
            report.setCreatedTime(new Date());
            report.setOrganizationId(SecurityUtils.getOrganizationId());
            report.setDepartmentId(SecurityUtils.getDepartmentId());
            reportMapper.insert(report);
        } else {
            report.setId(exists.getId());
            report.setOrganizationId(exists.getOrganizationId());
            report.setDepartmentId(exists.getDepartmentId());
            report.setUpdatedBy(SecurityUtils.getUserId());
            report.setUpdatedTime(new Date());
            LambdaPrivernalWrapper<DynamicReport> wrapper = new LambdaPrivernalWrapper<>();
            wrapper.eq(DynamicReport::getId, report.getId());
            reportMapper.update(report, wrapper);
        }
        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicReport findByCode(String reportCode) {
        if (StringUtils.isBlank(reportCode)) {
            return null;
        }
        LambdaPrivernalWrapper<DynamicReport> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicReport::getReportCode, reportCode);
        return reportMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DynamicReport> listByApp(String appId) {
        LambdaPrivernalWrapper<DynamicReport> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicReport::getAppId, appId);
        wrapper.orderByDesc(DynamicReport::getCreatedTime);
        return reportMapper.selectList(wrapper);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteByCode(String reportCode) {
        DynamicReport report = findByCode(reportCode);
        if (report == null) {
            return 0;
        }
        LambdaPrivernalWrapper<DynamicDashboardItem> itemWrapper = new LambdaPrivernalWrapper<>();
        itemWrapper.eq(DynamicDashboardItem::getReportCode, reportCode);
        Long referenced = dashboardItemMapper.selectCount(itemWrapper);
        if (referenced != null && referenced > 0) {
            throw new ServiceException("图表已被 " + referenced + " 个仪表盘卡片引用，请先从仪表盘移除");
        }
        // 先改名释放 report_code 唯一索引，便于同名图表重建
        DynamicReport rename = new DynamicReport();
        rename.setReportCode(reportCode + "_deleted_" + report.getId());
        LambdaPrivernalWrapper<DynamicReport> renameWrapper = new LambdaPrivernalWrapper<>();
        renameWrapper.eq(DynamicReport::getId, report.getId());
        reportMapper.update(rename, renameWrapper);
        return reportMapper.deleteFillById(report.getId());
    }

    /**
     * 设计态就把配置里的字段挑干净。
     * 保存时不校验的话，字段拼错要等到看板打开才暴露，使用者看到的是「图是空的」而不是「字段选错了」。
     */
    private void validateConfig(DynamicDataset dataset, JSONObject config) {
        if (config == null) {
            return;
        }
        Map<String, String> columnTypes = columnTypes(dataset);

        JSONArray dimensions = config.getJSONArray("dimensions");
        if (dimensions != null) {
            for (int i = 0; i < dimensions.size(); i++) {
                JSONObject dimension = dimensions.getJSONObject(i);
                String field = requireInColumns(dimension.getString("field"), columnTypes, "维度");
                String granularity = dimension.getString("granularity");
                if (StringUtils.isNotBlank(granularity) && !"date".equals(columnTypes.get(field))) {
                    throw new ServiceException("时间粒度只能配在时间列上：" + field);
                }
            }
        }

        JSONArray measures = config.getJSONArray("measures");
        if (measures != null) {
            if (measures.size() > DynamicReportQueryService.MAX_MEASURES) {
                throw new ServiceException("单个图表最多 " + DynamicReportQueryService.MAX_MEASURES + " 个度量");
            }
            for (int i = 0; i < measures.size(); i++) {
                JSONObject measure = measures.getJSONObject(i);
                String agg = StringUtils.isBlank(measure.getString("agg"))
                        ? "SUM" : measure.getString("agg").toUpperCase();
                if (!DynamicReportQueryService.AGG_FUNCS.contains(agg)) {
                    throw new ServiceException("不支持的聚合函数：" + measure.getString("agg"));
                }
                String field = measure.getString("field");
                // COUNT 允许不填字段（对行计数），其余聚合必须落到数值列
                if ("COUNT".equals(agg) && StringUtils.isBlank(field)) {
                    continue;
                }
                String column = requireInColumns(field, columnTypes, "度量");
                if (!"COUNT".equals(agg) && !"number".equals(columnTypes.get(column))) {
                    throw new ServiceException("度量字段 " + column + " 不是数值列，无法执行 " + agg);
                }
            }
        }

        JSONArray filters = config.getJSONArray("filters");
        if (filters != null) {
            for (int i = 0; i < filters.size(); i++) {
                requireInColumns(filters.getJSONObject(i).getString("field"), columnTypes, "过滤");
            }
        }

        JSONObject sort = config.getJSONObject("sort");
        if (sort != null && StringUtils.isNotBlank(sort.getString("field"))) {
            String field = sort.getString("field");
            // 排序允许引用结果别名（value / m1…），执行期还有一次严格校验，这里只挡明显拼错的列
            if (!columnTypes.containsKey(field) && !"value".equals(field) && !field.matches("m\\d+")) {
                throw new ServiceException("排序字段既不是数据集列也不是结果别名：" + field);
            }
        }
    }

    private Map<String, String> columnTypes(DynamicDataset dataset) {
        Map<String, String> columnTypes = new LinkedHashMap<>();
        JSONObject snapshot = dataset.getColumnSnapshot();
        JSONArray columns = snapshot == null ? null : snapshot.getJSONArray("columns");
        if (columns == null || columns.isEmpty()) {
            throw new ServiceException("数据集列快照为空，请刷新数据集：" + dataset.getDatasetCode());
        }
        for (int i = 0; i < columns.size(); i++) {
            JSONObject column = columns.getJSONObject(i);
            columnTypes.put(column.getString("fieldCode"), column.getString("dataType"));
        }
        return columnTypes;
    }

    private String requireInColumns(String field, Map<String, String> columnTypes, String source) {
        if (StringUtils.isBlank(field)) {
            throw new ServiceException(source + "字段不能为空");
        }
        if (!columnTypes.containsKey(field)) {
            throw new ServiceException(source + "字段 " + field + " 不在数据集列快照中，请刷新数据集");
        }
        return field;
    }
}
