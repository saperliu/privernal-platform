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

package com.privernal.system.service;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.domain.SystemOpenapiCallLog;
import com.privernal.system.domain.SystemOpenapiCredential;
import com.privernal.system.dto.OpenApiCallLogDTO;
import com.privernal.system.dto.OpenApiCallStatDTO;
import com.privernal.system.filter.OpenApiCallLogFilterDTO;
import com.privernal.system.mapper.SystemOpenapiCallLogMapper;
import com.privernal.system.mapper.SystemOpenapiCredentialMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 调用流水与统计。
 * <p>
 * 流水是旁路观测：落库失败不能影响业务响应，所以用 REQUIRES_NEW 且异常只记日志。
 */
@Slf4j
@Service
public class OpenApiCallLogService {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_DENIED = "DENIED";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_RATE_LIMITED = "RATE_LIMITED";

    @Autowired
    private SystemOpenapiCallLogMapper callLogMapper;

    @Autowired
    private SystemOpenapiCredentialMapper credentialMapper;

    /**
     * 记录一次调用并回写凭证的最近调用时间与累计次数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SystemOpenapiCallLog entry) {
        Date now = new Date();
        entry.setCreatedTime(now);
        entry.setCreatedBy(entry.getCreatedBy() == null ? "system" : entry.getCreatedBy());
        callLogMapper.insert(entry);
        if (StringUtils.isNotEmpty(entry.getCredentialId())) {
            LambdaUpdateWrapper<SystemOpenapiCredential> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(SystemOpenapiCredential::getId, entry.getCredentialId());
            wrapper.set(SystemOpenapiCredential::getLastCallTime, now);
            wrapper.setSql("call_count = call_count + 1");
            credentialMapper.update(null, wrapper);
        }
    }

    public void recordQuietly(SystemOpenapiCallLog entry) {
        try {
            record(entry);
        } catch (Exception e) {
            log.warn("OpenAPI 调用流水写入失败 requestId={}", entry == null ? null : entry.getRequestId(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<OpenApiCallLogDTO> selectList(OpenApiCallLogFilterDTO filter) {
        List<SystemOpenapiCallLog> rows = callLogMapper.selectList(buildWrapper(filter));
        List<OpenApiCallLogDTO> result = new ArrayList<>();
        for (SystemOpenapiCallLog row : rows) {
            result.add(toDTO(row));
        }
        return result;
    }

    /**
     * 聚合统计。groupByDay=true 按天聚合作趋势图，否则按动作聚合
     */
    @Transactional(readOnly = true)
    public List<OpenApiCallStatDTO> stat(OpenApiCallLogFilterDTO filter, boolean groupByDay) {
        QueryWrapper<SystemOpenapiCallLog> wrapper = new QueryWrapper<>();
        applyCommonConditions(wrapper, filter);
        String groupExpr = groupByDay ? "date(created_time)" : "action";
        wrapper.select(groupExpr + " AS group_key",
                "count(*) AS total_count",
                "sum(case when result = 'SUCCESS' then 1 else 0 end) AS success_count",
                "sum(case when result = 'SUCCESS' then 0 else 1 end) AS fail_count",
                "avg(cost_ms) AS avg_cost_ms");
        wrapper.groupBy(groupExpr);
        wrapper.orderByDesc(groupExpr);
        List<OpenApiCallStatDTO> result = new ArrayList<>();
        for (Map<String, Object> row : callLogMapper.selectMaps(wrapper)) {
            OpenApiCallStatDTO dto = new OpenApiCallStatDTO();
            dto.setGroupKey(asString(row.get("group_key")));
            dto.setTotalCount(asLong(row.get("total_count")));
            dto.setSuccessCount(asLong(row.get("success_count")));
            dto.setFailCount(asLong(row.get("fail_count")));
            dto.setAvgCostMs(asDouble(row.get("avg_cost_ms")));
            result.add(dto);
        }
        return result;
    }

    private LambdaQueryWrapper<SystemOpenapiCallLog> buildWrapper(OpenApiCallLogFilterDTO filter) {
        LambdaQueryWrapper<SystemOpenapiCallLog> wrapper = new LambdaQueryWrapper<>();
        applyCommonConditions(wrapper, filter);
        wrapper.orderByDesc(SystemOpenapiCallLog::getCreatedTime);
        return wrapper;
    }

    private void applyCommonConditions(QueryWrapper<SystemOpenapiCallLog> wrapper, OpenApiCallLogFilterDTO filter) {
        if (filter == null) {
            return;
        }
        wrapper.eq(StringUtils.isNotEmpty(filter.getCredentialId()), "credential_id", filter.getCredentialId());
        wrapper.eq(StringUtils.isNotEmpty(filter.getAppId()), "app_id", filter.getAppId());
        wrapper.eq(StringUtils.isNotEmpty(filter.getAccessKey()), "access_key", filter.getAccessKey());
        wrapper.eq(StringUtils.isNotEmpty(filter.getAction()), "action", filter.getAction());
        wrapper.eq(StringUtils.isNotEmpty(filter.getTableCode()), "table_code", filter.getTableCode());
        wrapper.eq(StringUtils.isNotEmpty(filter.getResult()), "result", filter.getResult());
        wrapper.eq(StringUtils.isNotEmpty(filter.getOrganizationId()), "organization_id", filter.getOrganizationId());
        wrapper.ge(StringUtils.isNotEmpty(filter.getBeginTime()), "created_time", filter.getBeginTime());
        wrapper.le(StringUtils.isNotEmpty(filter.getEndTime()), "created_time", filter.getEndTime());
    }

    private void applyCommonConditions(LambdaQueryWrapper<SystemOpenapiCallLog> wrapper, OpenApiCallLogFilterDTO filter) {
        if (filter == null) {
            return;
        }
        wrapper.eq(StringUtils.isNotEmpty(filter.getCredentialId()), SystemOpenapiCallLog::getCredentialId, filter.getCredentialId());
        wrapper.eq(StringUtils.isNotEmpty(filter.getAppId()), SystemOpenapiCallLog::getAppId, filter.getAppId());
        wrapper.eq(StringUtils.isNotEmpty(filter.getAccessKey()), SystemOpenapiCallLog::getAccessKey, filter.getAccessKey());
        wrapper.eq(StringUtils.isNotEmpty(filter.getAction()), SystemOpenapiCallLog::getAction, filter.getAction());
        wrapper.eq(StringUtils.isNotEmpty(filter.getTableCode()), SystemOpenapiCallLog::getTableCode, filter.getTableCode());
        wrapper.eq(StringUtils.isNotEmpty(filter.getResult()), SystemOpenapiCallLog::getResult, filter.getResult());
        wrapper.eq(StringUtils.isNotEmpty(filter.getOrganizationId()), SystemOpenapiCallLog::getOrganizationId, filter.getOrganizationId());
        wrapper.ge(StringUtils.isNotEmpty(filter.getBeginTime()), SystemOpenapiCallLog::getCreatedTime, filter.getBeginTime());
        wrapper.le(StringUtils.isNotEmpty(filter.getEndTime()), SystemOpenapiCallLog::getCreatedTime, filter.getEndTime());
    }

    private OpenApiCallLogDTO toDTO(SystemOpenapiCallLog row) {
        OpenApiCallLogDTO dto = new OpenApiCallLogDTO();
        dto.setId(row.getId());
        dto.setRequestId(row.getRequestId());
        dto.setCredentialId(row.getCredentialId());
        dto.setAppId(row.getAppId());
        dto.setAccessKey(row.getAccessKey());
        dto.setEndpoint(row.getEndpoint());
        dto.setHttpMethod(row.getHttpMethod());
        dto.setAction(row.getAction());
        dto.setTableCode(row.getTableCode());
        dto.setBizId(row.getBizId());
        dto.setResult(row.getResult());
        dto.setStatusCode(row.getStatusCode());
        dto.setErrorCode(row.getErrorCode());
        dto.setErrorMsg(row.getErrorMsg());
        dto.setCostMs(row.getCostMs());
        dto.setClientIp(row.getClientIp());
        dto.setOrganizationId(row.getOrganizationId());
        dto.setCreatedTime(row.getCreatedTime());
        return dto;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
