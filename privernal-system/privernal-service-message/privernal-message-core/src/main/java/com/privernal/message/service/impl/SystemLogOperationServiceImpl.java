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

package com.privernal.message.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.web.domain.CommonResult;
import org.springframework.beans.BeanUtils;
import com.privernal.message.domain.SystemLogOperation;
import com.privernal.message.dto.SystemLogOperationDTO;
import com.privernal.message.dto.SystemLogOperationDetailDTO;
import com.privernal.message.dto.SystemPermissionAuditDTO;
import com.privernal.message.filter.SystemLogOperationFilterDTO;
import com.privernal.message.mapper.SystemLogOperationMapper;
import com.privernal.message.service.ISystemLogOperationService;
import com.privernal.system.dto.RemoteNameResolveDTO;
import com.privernal.system.dto.RemoteNameResolveResultDTO;
import com.privernal.system.feign.RemoteUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 操作日志的Service业务层处理
 */
@Slf4j
@Service
public class SystemLogOperationServiceImpl implements ISystemLogOperationService {
    @Autowired
    private SystemLogOperationMapper systemLogOperationMapper;

    @Autowired
    private RemoteUserService remoteUserService;

    /**
     * 查询操作日志
     *
     * @param id 操作日志主键
     * @return 操作日志
     */
    @Transactional(readOnly = true)
    @Override
    public SystemLogOperation selectSystemLogOperationById(String id) {
        return systemLogOperationMapper.selectById(id);
    }

    /**
     * 查询系统操作日志
     *
     * @param id 系统操作日志主键
     * @return 系统操作日志
     */
    @Transactional(readOnly = true)
    @Override
    public SystemLogOperationDetailDTO selectSystemLogOperationDetailDTOById(String id) {
        SystemLogOperation systemLogOperation = systemLogOperationMapper.selectById(id);
        SystemLogOperationDetailDTO systemLogOperationDetailDTO = new SystemLogOperationDetailDTO();
        if (systemLogOperation != null) {
            BeanUtils.copyProperties(systemLogOperation, systemLogOperationDetailDTO);
        }
        return systemLogOperationDetailDTO;
    }

    /**
     * 查询系统操作日志列表
     *
     * @param systemLogOperation 系统操作日志
     * @return 系统操作日志
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemLogOperationDTO> selectSystemLogOperationList(SystemLogOperationFilterDTO systemLogOperation) {
        return systemLogOperationMapper.selectSystemLogOperationList(systemLogOperation);
    }

    /**
     * R1-8 权限审计报表：权限相关变更日志 + 动作归类/对象名称解析富化。
     * 名称解析走 inner 批量端点（每页一次）；解析失败降级为原始 id 展示，不影响列表。
     */
    @Transactional(readOnly = true)
    @Override
    public List<SystemPermissionAuditDTO> selectPermissionAuditList(SystemLogOperationFilterDTO systemLogOperation) {
        List<SystemPermissionAuditDTO> rows = systemLogOperationMapper.selectPermissionAuditList(systemLogOperation);
        enrichAuditRows(rows);
        return rows;
    }

    /** 每行解析出的待解析 id 引用 */
    private static class AuditRefs {
        final Set<String> userIds = new LinkedHashSet<>();
        final Set<String> roleIds = new LinkedHashSet<>();
        /** 对象摘要里出现的用户/角色 id 占位（${u:xxx} / ${r:xxx}），富化时替换为名称 */
        final List<String[]> summarySlots = new ArrayList<>();
        String summaryPrefix = "";
    }

    private void enrichAuditRows(List<SystemPermissionAuditDTO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<AuditRefs> refsList = new ArrayList<>(rows.size());
        Set<String> allUserIds = new LinkedHashSet<>();
        Set<String> allRoleIds = new LinkedHashSet<>();
        for (SystemPermissionAuditDTO row : rows) {
            AuditRefs refs = parseAuditRow(row);
            refsList.add(refs);
            allUserIds.addAll(refs.userIds);
            allRoleIds.addAll(refs.roleIds);
        }
        Map<String, String> userMap = Collections.emptyMap();
        Map<String, String> roleMap = Collections.emptyMap();
        if (!allUserIds.isEmpty() || !allRoleIds.isEmpty()) {
            try {
                RemoteNameResolveDTO resolveDTO = new RemoteNameResolveDTO();
                resolveDTO.setUserIds(new ArrayList<>(allUserIds));
                resolveDTO.setRoleIds(new ArrayList<>(allRoleIds));
                CommonResult<RemoteNameResolveResultDTO> result =
                        remoteUserService.resolveNames(resolveDTO, Constants.REQUEST_INNER);
                if (result != null && result.getData() != null) {
                    userMap = result.getData().getUserNames() == null ? Collections.emptyMap() : result.getData().getUserNames();
                    roleMap = result.getData().getRoleNames() == null ? Collections.emptyMap() : result.getData().getRoleNames();
                }
            } catch (Exception e) {
                log.warn("[R1-8] 权限审计名称解析失败，降级展示原始id：{}", e.getMessage());
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            SystemPermissionAuditDTO row = rows.get(i);
            AuditRefs refs = refsList.get(i);
            row.setUserNames(joinNames(refs.userIds, userMap));
            row.setRoleNames(joinNames(refs.roleIds, roleMap));
            String summary = refs.summaryPrefix;
            for (String[] slot : refs.summarySlots) {
                if ("t".equals(slot[0])) {
                    // 字面文本槽位
                    summary = summary + (slot[1] == null ? "" : slot[1]);
                } else if (slot[1] != null && !slot[1].isEmpty()) {
                    Map<String, String> map = "u".equals(slot[0]) ? userMap : roleMap;
                    String name = map.getOrDefault(slot[1], slot[1]);
                    summary = summary.isEmpty() ? name : summary + name;
                }
            }
            row.setTargetSummary(summary.isEmpty() ? null : summary);
        }
    }

    private String joinNames(Set<String> ids, Map<String, String> nameMap) {
        if (ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(nameMap.getOrDefault(id, id));
        }
        return sb.toString();
    }

    /** 按端点归类动作并抽取对象 id（解析失败按原样展示参数摘要） */
    private AuditRefs parseAuditRow(SystemPermissionAuditDTO row) {
        AuditRefs refs = new AuditRefs();
        String url = StringUtils.defaultString(row.getOperationUrl());
        String method = StringUtils.defaultString(row.getRequestMethod());
        boolean isDelete = "DELETE".equalsIgnoreCase(method);
        JSONObject json = tryParseJson(row.getOperationParam());
        row.setAction(classifyAction(url, method, json, row));

        if (url.contains("/flow/task/handover")) {
            addUserId(json, "fromUserId", refs);
            addUserId(json, "toUserId", refs);
            refs.summaryPrefix = "待办移交：";
            refs.summarySlots.add(userSlot(json, "fromUserId"));
            refs.summarySlots.add(new String[]{"t", " → "});
            refs.summarySlots.add(userSlot(json, "toUserId"));
        } else if (url.contains("/flow/delegate")) {
            addUserId(json, "userId", refs);
            addUserId(json, "agentId", refs);
            refs.summaryPrefix = "代理规则：";
            refs.summarySlots.add(userSlot(json, "userId"));
            refs.summarySlots.add(new String[]{"t", " 代理→ "});
            refs.summarySlots.add(userSlot(json, "agentId"));
        } else if (url.contains("/roleUser/expire")) {
            String expireTime = json == null ? null : json.getString("expireTime");
            // expire 端点的对象 id 在请求参数里（URL 尾段是动作名）
            refs.summaryPrefix = "授权关系 " + (json == null || StringUtils.isEmpty(json.getString("id")) ? tailId(url) : json.getString("id"))
                    + " 有效期 → " + (StringUtils.isEmpty(expireTime) ? "永久" : expireTime);
        } else if (url.contains("/roleUser/batch/save")) {
            addRoleId(json, "roleId", refs);
            refs.summaryPrefix = "授予角色 ";
            refs.summarySlots.add(roleSlot(json, "roleId"));
            refs.summarySlots.add(new String[]{"t", "："});
            JSONArray users = json == null ? null : json.getJSONArray("userDTOList");
            if (users != null) {
                for (int i = 0; i < users.size(); i++) {
                    JSONObject user = users.getJSONObject(i);
                    if (user != null && user.getString("id") != null) {
                        refs.userIds.add(user.getString("id"));
                        refs.summarySlots.add(new String[]{"u", user.getString("id")});
                    }
                }
            }
        } else if (url.contains("/roleUser/save") || url.contains("/roleUser/edit")) {
            addRoleId(json, "roleId", refs);
            addUserId(json, "userId", refs);
            refs.summaryPrefix = isDelete ? "回收授权关系 " : "授权关系变更：";
            refs.summarySlots.add(userSlot(json, "userId"));
            refs.summarySlots.add(new String[]{"t", " ← 角色 "});
            refs.summarySlots.add(roleSlot(json, "roleId"));
        } else if (url.contains("/roleUser") || url.contains("/roleUserDept")) {
            refs.summaryPrefix = (isDelete ? "回收授权关系 " : "服务部门授权变更 ") + tailId(url);
        } else if (url.contains("/roleMenu")) {
            addRoleId(json, "roleId", refs);
            refs.summaryPrefix = isDelete ? "回收角色菜单 " : "角色菜单授权变更：";
            refs.summarySlots.add(roleSlot(json, "roleId"));
            if (!isDelete && json != null && json.getString("menuId") != null) {
                refs.summarySlots.add(new String[]{"t", " ← 菜单 " + json.getString("menuId")});
            }
        } else if (url.contains("/role/save")) {
            refs.summaryPrefix = "新增角色 " + (json == null ? "" : StringUtils.defaultString(json.getString("roleName")));
        } else if (url.contains("/role/edit")) {
            refs.summaryPrefix = "角色变更 " + (json == null ? "" : StringUtils.defaultString(json.getString("roleName")));
        } else if (url.contains("/role/")) {
            refs.summaryPrefix = "删除角色 " + tailId(url);
        } else if (url.contains("/user/edit")) {
            String status = json == null ? null : json.getString("status");
            if ("3".equals(status)) {
                refs.summaryPrefix = "停用账号（离职调岗联动回收授权）";
            } else {
                refs.summaryPrefix = "用户信息变更 " + StringUtils.defaultString(json == null ? null : json.getString("loginName"));
            }
        } else if (url.contains("/user/")) {
            refs.summaryPrefix = "删除用户 " + tailId(url);
        }
        return refs;
    }

    private String classifyAction(String url, String method, JSONObject json, SystemPermissionAuditDTO row) {
        boolean isDelete = "DELETE".equalsIgnoreCase(method);
        if (url.contains("/flow/task/handover")) {
            return "离职移交";
        }
        if (url.contains("/flow/delegate")) {
            return isDelete ? "代理规则删除" : ("PUT".equalsIgnoreCase(method) ? "代理规则修改" : "代理规则新增");
        }
        if (url.contains("/roleUser/expire")) {
            return "设置授权有效期";
        }
        if (url.contains("/roleUser")) {
            if (isDelete) {
                return "回收授权";
            }
            return url.contains("/roleUser/edit") ? "授权变更" : "新增授权";
        }
        if (url.contains("/roleMenu")) {
            return isDelete ? "回收角色菜单" : "角色菜单授权";
        }
        if (url.contains("/roleUserDept")) {
            return isDelete ? "回收服务部门" : "服务部门授权";
        }
        if (url.contains("/role/save")) {
            return "新增角色";
        }
        if (url.contains("/role/edit")) {
            return "角色变更";
        }
        if (url.contains("/role/")) {
            return "删除角色";
        }
        if (url.contains("/user/edit")) {
            return "3".equals(json == null ? null : json.getString("status")) ? "停用账号" : "用户变更";
        }
        if (url.contains("/user/")) {
            return "删除用户";
        }
        return StringUtils.defaultString(row.getName(), "权限相关变更");
    }

    private JSONObject tryParseJson(String param) {
        if (StringUtils.isEmpty(param)) {
            return null;
        }
        try {
            return JSON.parseObject(param);
        } catch (Exception e) {
            return null;
        }
    }

    private void addUserId(JSONObject json, String key, AuditRefs refs) {
        String v = json == null ? null : json.getString(key);
        if (StringUtils.isNotEmpty(v)) {
            refs.userIds.add(v);
        }
    }

    private void addRoleId(JSONObject json, String key, AuditRefs refs) {
        String v = json == null ? null : json.getString(key);
        if (StringUtils.isNotEmpty(v)) {
            refs.roleIds.add(v);
        }
    }

    private String[] userSlot(JSONObject json, String key) {
        return new String[]{"u", json == null ? null : StringUtils.defaultString(json.getString(key))};
    }

    private String[] roleSlot(JSONObject json, String key) {
        return new String[]{"r", json == null ? null : StringUtils.defaultString(json.getString(key))};
    }

    /** 删除类端点的对象 id 在 URL 尾段（如 /back/system/roleUser/{ids}） */
    private String tailId(String url) {
        if (StringUtils.isEmpty(url)) {
            return "";
        }
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    /**
     * 新增操作日志
     *
     * @param systemLogOperation 操作日志
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemLogOperation(SystemLogOperation systemLogOperation) {
        return systemLogOperationMapper.insert(systemLogOperation);
    }

    /**
     * 修改操作日志
     *
     * @param systemLogOperation 操作日志
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemLogOperation(SystemLogOperation systemLogOperation) {
        return systemLogOperationMapper.updateById(systemLogOperation);
    }

    /**
     * 批量删除操作日志
     *
     * @param ids 需要删除的操作日志主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemLogOperationByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemLogOperationById(id);
        }
        return ids.length;
    }

    /**
     * 删除操作日志信息
     *
     * @param id 操作日志主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemLogOperationById(String id) {
        return systemLogOperationMapper.deleteFillById(id);
    }

}
