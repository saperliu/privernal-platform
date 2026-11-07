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

package com.privernal.flow.service.impl;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.flow.constant.FlowConstant;
import com.privernal.flow.context.FlowContextHolder;
import com.privernal.flow.enums.FlowPermissionTypeEnum;
import com.privernal.flow.enums.FlowScriptRuleEnum;
import com.privernal.flow.service.IFlowUserResolveService;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemOrganizationDTO;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.feign.RemoteUserService;
import com.privernal.system.filter.WorkflowCodeFilterDTO;
import com.privernal.system.filter.WorkflowUserFilterDTO;
import com.privernal.common.web.domain.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 工作流办理人解析服务实现
 * 将 warm-flow 节点权限标识（user:/role:/dept:/deptLeader:/orgLeader:/userGroup:/script:）
 * 结合平台组织、部门、人员、角色体系解析为具体用户id
 *
 * @author saperliu
 */
@Service
public class FlowUserResolveServiceImpl implements IFlowUserResolveService {

    private static final Logger log = LoggerFactory.getLogger(FlowUserResolveServiceImpl.class);

    /** 经理函数锚点：发起人 / 指定用户 / 指定部门 */
    private static final String ANCHOR_INITIATOR = "initiator";

    private static final String ANCHOR_USER = "user";

    private static final String ANCHOR_DEPT = "dept";

    /** 逐层经理最多向上几级，防止配置出病态层级把部门树走完 */
    private static final int MAX_LEVEL_UP = 10;

    @Autowired
    private RemoteUserService remoteUserService;

    @Override
    public List<String> expandCurrentUserFlags() {
        String userId = SecurityUtils.getUserId();
        Set<String> flags = new LinkedHashSet<>();
        if (StringUtils.isEmpty(userId)) {
            return new ArrayList<>(flags);
        }
        // 裸用户id用于匹配转换后的 flow_user 记录，user: 前缀用于匹配未转换的原始标识
        flags.add(userId);
        flags.add(FlowPermissionTypeEnum.USER.getCode() + FlowConstant.PERMISSION_TYPE_SEPARATOR + userId);
        try {
            List<SystemRoleUserDTO> roleUsers = remoteUserService.selectAuthRoleByUserId(userId, SecurityUtils.getOrganizationId());
            if (roleUsers != null) {
                for (SystemRoleUserDTO roleUser : roleUsers) {
                    if (roleUser != null && StringUtils.isNotEmpty(roleUser.getRoleId())) {
                        flags.add(FlowPermissionTypeEnum.ROLE.getCode() + FlowConstant.PERMISSION_TYPE_SEPARATOR + roleUser.getRoleId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("展开用户角色权限标识失败 userId={}", userId, e);
        }
        String departmentId = SecurityUtils.getDepartmentId();
        if (StringUtils.isNotEmpty(departmentId)) {
            flags.add(FlowPermissionTypeEnum.DEPT.getCode() + FlowConstant.PERMISSION_TYPE_SEPARATOR + departmentId);
        }
        return new ArrayList<>(flags);
    }

    /**
     * 拆解标识集合为具体用户id：user:/role:/dept:/deptLeader:/orgLeader:/userGroup:/script:。
     * 单个标识解析失败只记日志并跳过，绝不抛出——引擎侧 ExpressionUtil 是
     * {@code catch (Exception ignored)}，抛出去等于静默保留原始标识，谁都匹配不上。
     */
    @Override
    public List<String> convertPermissions(List<String> permissions) {
        Set<String> userIds = new LinkedHashSet<>();
        if (permissions == null || permissions.isEmpty()) {
            return new ArrayList<>(userIds);
        }
        for (String permission : permissions) {
            if (StringUtils.isEmpty(permission)) {
                continue;
            }
            int index = permission.indexOf(FlowConstant.PERMISSION_TYPE_SEPARATOR);
            if (index < 0) {
                // 无前缀视为用户id
                userIds.add(permission);
                continue;
            }
            String type = permission.substring(0, index);
            String value = permission.substring(index + 1);
            FlowPermissionTypeEnum typeEnum = FlowPermissionTypeEnum.ofCode(type);
            if (typeEnum == null) {
                // 未知前缀（可能是表达式产物），原样保留
                userIds.add(permission);
                continue;
            }
            try {
                userIds.addAll(resolveByType(typeEnum, value));
            } catch (Exception e) {
                log.warn("解析办理人权限标识失败 flag={}", permission, e);
            }
        }
        return new ArrayList<>(userIds);
    }

    /**
     * 按权限类型解析具体用户id
     */
    private List<String> resolveByType(FlowPermissionTypeEnum type, String value) {
        switch (type) {
            case USER:
                return StringUtils.isNotEmpty(value) ? Collections.singletonList(value) : new ArrayList<>();
            case ROLE:
                return findUserIdsByRole(value, null);
            case DEPT:
                WorkflowUserFilterDTO deptFilter = new WorkflowUserFilterDTO();
                deptFilter.setDeptList(Collections.singletonList(value));
                return extractUserIds(remoteUserService.findUserByDept(deptFilter, ""));
            case DEPT_LEADER:
                return resolveDeptLeader(value);
            case ORG_LEADER:
                return resolveOrgLeader(value);
            case USER_GROUP:
                WorkflowUserFilterDTO groupFilter = new WorkflowUserFilterDTO();
                groupFilter.setUserGroupList(Collections.singletonList(value));
                return extractUserIds(remoteUserService.findUserByGroup(groupFilter, ""));
            case SCRIPT:
                return resolveScript(value);
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 角色成员（departmentId 不为空时限定部门内角色）
     */
    private List<String> findUserIdsByRole(String roleId, String departmentId) {
        WorkflowUserFilterDTO roleFilter = new WorkflowUserFilterDTO();
        roleFilter.setRoleList(Collections.singletonList(roleId));
        if (StringUtils.isNotEmpty(departmentId)) {
            roleFilter.setDepartmentId(departmentId);
        }
        return extractUserIds(remoteUserService.findUserByRole(roleFilter, ""));
    }

    private List<String> resolveDeptLeader(String deptId) {
        if (StringUtils.isEmpty(deptId)) {
            return new ArrayList<>();
        }
        CommonResult<SystemDepartmentDTO> result = remoteUserService.findDeptById(deptId, "");
        if (result != null && result.getData() != null && StringUtils.isNotEmpty(result.getData().getLeaderId())) {
            return Collections.singletonList(result.getData().getLeaderId());
        }
        return new ArrayList<>();
    }

    private List<String> resolveOrgLeader(String orgId) {
        if (StringUtils.isEmpty(orgId)) {
            return new ArrayList<>();
        }
        CommonResult<SystemOrganizationDTO> result = remoteUserService.findOrganizationById(orgId, "");
        if (result != null && result.getData() != null && StringUtils.isNotEmpty(result.getData().getLeaderId())) {
            return Collections.singletonList(result.getData().getLeaderId());
        }
        return new ArrayList<>();
    }

    /**
     * 脚本规则解析（值格式：RULE_CODE 或 RULE_CODE:参数，参数如角色id/表单字段名）
     */
    private List<String> resolveScript(String value) {
        if (StringUtils.isEmpty(value)) {
            return new ArrayList<>();
        }
        int index = value.indexOf(FlowConstant.PERMISSION_TYPE_SEPARATOR);
        String code = index < 0 ? value : value.substring(0, index);
        String arg = index < 0 ? null : value.substring(index + 1);
        FlowScriptRuleEnum rule = FlowScriptRuleEnum.ofCode(code);
        if (rule == null) {
            log.warn("未知的脚本规则编码 {}", code);
            return new ArrayList<>();
        }
        FlowContextHolder.FlowContext context = FlowContextHolder.get();
        String initiatorId = context != null ? context.getInitiatorId() : SecurityUtils.getUserId();
        Map<String, Object> variables = context != null ? context.getVariables() : null;
        String organizationId = context != null ? context.getOrganizationId() : SecurityUtils.getOrganizationId();
        switch (rule) {
            case INITIATOR:
                return StringUtils.isNotEmpty(initiatorId) ? Collections.singletonList(initiatorId) : new ArrayList<>();
            case INITIATOR_DEPT_LEADER:
                return resolveDeptLeader(findUserDepartmentId(initiatorId));
            case INITIATOR_DEPT: {
                String deptId = findUserDepartmentId(initiatorId);
                if (StringUtils.isEmpty(deptId)) {
                    return new ArrayList<>();
                }
                WorkflowUserFilterDTO deptFilter = new WorkflowUserFilterDTO();
                deptFilter.setDeptList(Collections.singletonList(deptId));
                return extractUserIds(remoteUserService.findUserByDept(deptFilter, ""));
            }
            case INITIATOR_ORG_LEADER:
                return resolveOrgLeader(organizationId);
            case INITIATOR_DEPT_ROLE:
                return findUserIdsByRole(arg, findUserDepartmentId(initiatorId));
            case DATA_DEPT_LEADER:
                return resolveDeptLeader(variables == null ? null : Objects.toString(variables.get("departmentId"), null));
            case FORM_FIELD_USER:
                return splitFieldValues(variables, arg);
            case FORM_FIELD_ROLE:
                List<String> roleKeys = splitFieldValues(variables, arg);
                List<String> userIds = new ArrayList<>();
                for (String roleKey : roleKeys) {
                    WorkflowCodeFilterDTO codeFilter = new WorkflowCodeFilterDTO();
                    codeFilter.setCode(roleKey);
                    userIds.addAll(extractUserIds(remoteUserService.findUserByRoleCode(codeFilter, "")));
                }
                return userIds;
            case DATA_OWNER:
                return dataOwnerIds(variables);
            case FORM_FIELD_DEPT:
                return deptMemberIds(splitFieldValues(variables, arg));
            case DEPT_ROLE: {
                // 值格式 DEPT_ROLE:{锚点}:{roleId}，锚点 = initiator | user:{userId} | dept:{deptId}
                int last = arg == null ? -1 : arg.lastIndexOf(FlowConstant.PERMISSION_TYPE_SEPARATOR);
                if (last <= 0 || last == arg.length() - 1) {
                    log.warn("部门角色查找参数不完整：{}", arg);
                    return new ArrayList<>();
                }
                String roleId = arg.substring(last + 1);
                String deptId = anchorDepartmentId(arg.substring(0, last), initiatorId);
                return StringUtils.isEmpty(deptId) ? new ArrayList<>() : findUserIdsByRole(roleId, deptId);
            }
            case LEVEL_MANAGER:
                return levelManagerIds(arg, initiatorId);
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 解析经理函数的锚点部门：
     * initiator=发起人所属部门；user:{id}=该用户所属部门；dept:{id}=指定部门本身
     */
    private String anchorDepartmentId(String arg, String initiatorId) {
        if (StringUtils.isEmpty(arg) || ANCHOR_INITIATOR.equalsIgnoreCase(arg)) {
            return findUserDepartmentId(initiatorId);
        }
        int index = arg.indexOf(FlowConstant.PERMISSION_TYPE_SEPARATOR);
        String type = index < 0 ? arg : arg.substring(0, index);
        String id = index < 0 ? null : arg.substring(index + 1);
        if (ANCHOR_DEPT.equalsIgnoreCase(type)) {
            return id;
        }
        if (ANCHOR_USER.equalsIgnoreCase(type)) {
            return findUserDepartmentId(id);
        }
        log.warn("未知的办理人锚点 {}", arg);
        return null;
    }

    /**
     * 逐层经理查找：以锚点部门为起点向上数 N 级，取那**一个**部门的经理。
     * A 的上级是 B、上上级是 C，则 1 级＝B 的经理、2 级＝C 的经理，不是逐级累加。
     * 祖先链由 system_dept_relation 取，mapper 已按 level 升序返回（直接上级 level=1）。
     */
    private List<String> levelManagerIds(String arg, String initiatorId) {
        List<String> userIds = new ArrayList<>();
        int last = arg == null ? -1 : arg.lastIndexOf(FlowConstant.PERMISSION_TYPE_SEPARATOR);
        if (last <= 0) {
            log.warn("逐层经理查找缺少层级参数：{}", arg);
            return userIds;
        }
        int levels;
        try {
            levels = Integer.parseInt(arg.substring(last + 1).trim());
        } catch (NumberFormatException e) {
            log.warn("逐层经理查找层级不是数字：{}", arg);
            return userIds;
        }
        if (levels < 1 || levels > MAX_LEVEL_UP) {
            log.warn("逐层经理查找层级超出 1..{} 范围：{}", MAX_LEVEL_UP, arg);
            return userIds;
        }
        String deptId = anchorDepartmentId(arg.substring(0, last), initiatorId);
        if (StringUtils.isEmpty(deptId)) {
            return userIds;
        }
        try {
            List<SystemDepartmentDTO> ancestors = remoteUserService.selectSystemParentDeptById(deptId);
            if (ancestors == null || ancestors.size() < levels) {
                log.warn("锚点部门向上第 {} 级不存在，该节点无人可派：deptId={}", levels, deptId);
                return userIds;
            }
            SystemDepartmentDTO target = ancestors.get(levels - 1);
            if (target != null && StringUtils.isNotEmpty(target.getLeaderId())) {
                userIds.add(target.getLeaderId());
            } else {
                log.warn("锚点部门向上第 {} 级没有配置经理：deptId={}", levels, deptId);
            }
        } catch (Exception e) {
            log.warn("查询部门祖先链失败 deptId={}", deptId, e);
        }
        return userIds;
    }

    /**
     * 数据拥有者：流程变量里的创建人。动态表单落库时列名是 created_by，
     * 流程变量构造处会给下划线键再补一份驼峰别名，两个键都取一次。
     */
    private List<String> dataOwnerIds(Map<String, Object> variables) {
        List<String> userIds = new ArrayList<>();
        if (variables == null) {
            return userIds;
        }
        for (String key : new String[]{"created_by", "createdBy"}) {
            String value = Objects.toString(variables.get(key), "").trim();
            if (StringUtils.isNotEmpty(value)) {
                userIds.add(value);
                return userIds;
            }
        }
        return userIds;
    }

    /**
     * 表单部门组件选中的部门下的全员（一次按部门集合查，不逐部门扇出）
     */
    private List<String> deptMemberIds(List<String> deptIds) {
        if (deptIds.isEmpty()) {
            return new ArrayList<>();
        }
        WorkflowUserFilterDTO filter = new WorkflowUserFilterDTO();
        filter.setDeptList(deptIds);
        return extractUserIds(remoteUserService.findUserByDept(filter, ""));
    }

    /**
     * 从流程变量取字段值并拆成 id 列表（表单字段指定用户/角色/部门）。
     * 多选组件落库形态不统一：逗号分隔串与 JSON 数组串都要能吃，
     * 否则 ["a","b"] 会被当成一个脏 id 解析不出人。
     */
    private List<String> splitFieldValues(Map<String, Object> variables, String fieldName) {
        List<String> values = new ArrayList<>();
        if (variables == null || StringUtils.isEmpty(fieldName) || variables.get(fieldName) == null) {
            return values;
        }
        String raw = Objects.toString(variables.get(fieldName), "").trim();
        if (raw.isEmpty()) {
            return values;
        }
        if (raw.startsWith("[")) {
            try {
                values.addAll(com.alibaba.fastjson2.JSON.parseArray(raw, String.class));
                values.removeIf(StringUtils::isEmpty);
                return values;
            } catch (Exception e) {
                log.warn("流程变量 {} 不是合法 JSON 数组，退回逗号拆分", fieldName);
            }
        }
        for (String v : raw.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
            if (StringUtils.isNotEmpty(v.trim())) {
                values.add(v.trim());
            }
        }
        return values;
    }

    private String findUserDepartmentId(String userId) {
        if (StringUtils.isEmpty(userId)) {
            return null;
        }
        CommonResult<SystemUserDTO> result = remoteUserService.findUserById(userId, "");
        return result != null && result.getData() != null ? result.getData().getDepartmentId() : null;
    }

    private List<String> extractUserIds(CommonResult<List<SystemUserDTO>> result) {
        List<String> userIds = new ArrayList<>();
        if (result != null && result.getCode() == CommonResult.SUCCESS && result.getData() != null) {
            for (SystemUserDTO user : result.getData()) {
                if (user != null && StringUtils.isNotEmpty(user.getId())) {
                    userIds.add(user.getId());
                }
            }
        }
        return userIds;
    }

    /**
     * 找不到办理人时的兜底人：当前流程上下文的组织负责人
     */
    @Override
    public List<String> resolveFallbackOrgLeaders() {
        FlowContextHolder.FlowContext context = FlowContextHolder.get();
        String orgId = context != null ? context.getOrganizationId() : SecurityUtils.getOrganizationId();
        return resolveOrgLeader(orgId);
    }
}
