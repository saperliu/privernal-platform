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
import com.privernal.flow.enums.FlowPermissionTypeEnum;
import com.privernal.flow.enums.FlowScriptRuleEnum;
import com.privernal.system.dto.SystemRoleDTO;
import com.privernal.system.feign.RemoteUserService;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.SkipJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程定义纯校验服务（与 {@code DynamicFormValidateService} 同范式）：
 * 零副作用，只读检查，返回结构化错误清单。存在的原因是把两类现在<b>不会报错</b>的缺陷前置暴露：
 * <ol>
 * <li><b>办理人静默匹配不上</b>：{@code permission_flag} 的未知前缀与不存在的角色 id 一路存得进去，
 * 运行时 {@code FlowUserResolveServiceImpl} 取不到人只 warn 日志，表现为「流程提交了但没人能审」；</li>
 * <li><b>超长被截断或整次失败</b>：{@code permission_flag}(1000)/{@code ext}(255)/{@code node_ratio}(10)
 * 三列后端零长度校验，结果取决于目标库 sql_mode——严格模式直接报错，非严格模式静默截断（skill §4.8）。</li>
 * </ol>
 * 另外顺带校验结构红线（无开始节点 / 节点无出边 / nextNodeCode 悬空 / anyNodeSkip 悬空 /
 * nodeRatio 非数字串会在流转时抛 NumberFormatException）。
 * <p>
 * 注意：本服务<b>不</b>被保存链调用，只由 {@code POST /back/flow/definition/validate} 暴露。
 * 已上线的人工配置里存在失效角色，保存链强行拦会挡住日常改流程，所以闸门放在生成方（AI 一档工具面）。
 */
@Service
public class FlowDefinitionValidateService {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionValidateService.class);

    /** flow_permission.permission_flag / flow_node.permission_flag 列长 */
    private static final int PERMISSION_FLAG_MAX = 1000;

    /** flow_node.ext 列长（varchar(255)，见 sql/warm-flow-all.sql） */
    private static final int EXT_MAX = 255;

    /** flow_node.node_ratio 列长 */
    private static final int NODE_RATIO_MAX = 10;

    /** 节点类型：开始 */
    private static final int NODE_TYPE_START = 0;

    /** 节点类型：结束 */
    private static final int NODE_TYPE_END = 2;

    @Autowired(required = false)
    private RemoteUserService remoteUserService;

    /**
     * 结构化校验：返回错误清单（空数组 = 通过）。每条 {code, message, field}。
     */
    public List<Map<String, Object>> validateDefinition(DefJson defJson) {
        List<Map<String, Object>> errors = new ArrayList<>();
        if (defJson == null) {
            errors.add(error("DEF_JSON_MISSING", "流程定义载荷缺失", null));
            return errors;
        }
        if (StringUtils.isBlank(defJson.getFlowCode())) {
            errors.add(error("FLOW_CODE_REQUIRED", "flowCode 不能为空", null));
        }
        if (StringUtils.isBlank(defJson.getFlowName())) {
            errors.add(error("FLOW_NAME_REQUIRED", "flowName 不能为空", null));
        }
        List<NodeJson> nodeList = defJson.getNodeList();
        if (nodeList == null || nodeList.isEmpty()) {
            errors.add(error("NODE_LIST_EMPTY", "nodeList 不能为空（至少要有可能串起开始与结束）", null));
            return errors;
        }

        Set<String> nodeCodes = new HashSet<>();
        boolean hasStart = false;
        for (NodeJson node : nodeList) {
            String code = node.getNodeCode();
            if (StringUtils.isBlank(code)) {
                errors.add(error("NODE_CODE_REQUIRED", "存在缺少 nodeCode 的节点：" + node.getNodeName(), null));
                continue;
            }
            if (!nodeCodes.add(code)) {
                errors.add(error("NODE_CODE_DUPLICATED", "节点编码重复：" + code, code));
            }
            if (node.getNodeType() != null && node.getNodeType() == NODE_TYPE_START) {
                hasStart = true;
            }
        }
        if (!hasStart) {
            errors.add(error("NO_START_NODE", "缺少 nodeType=0 的开始节点", null));
        }

        Set<String> roleIds = null;
        String roleSkipReason = null;
        if (hasRoleLookup(nodeList)) {
            try {
                roleIds = currentOrganizationRoleIds();
            } catch (Exception e) {
                // 远程不可达不阻塞其余校验，但要如实标注「角色存在性未验证」
                roleSkipReason = e.getMessage();
                errors.add(error("ROLE_CHECK_SKIPPED",
                        "办理人角色存在性校验未完成（服务不可达）：" + e.getMessage(), null));
            }
        }

        for (NodeJson node : nodeList) {
            String nodeCode = StringUtils.isBlank(node.getNodeCode()) ? node.getNodeName() : node.getNodeCode();
            checkNodeRatio(node, nodeCode, errors);
            checkExt(node, nodeCode, errors);
            checkPermissionFlag(node, nodeCode, roleIds, errors);
            checkAnyNodeSkip(node, nodeCode, nodeCodes, errors);
            checkEdges(node, nodeCode, nodeCodes, errors);
        }
        if (roleSkipReason != null) {
            log.warn("流程定义校验跳过角色存在性检查：{}", roleSkipReason);
        }
        return errors;
    }

    /** node_ratio 是 String 列：非数字串会在票签分支 new BigDecimal 时抛 NumberFormatException */
    private void checkNodeRatio(NodeJson node, String nodeCode, List<Map<String, Object>> errors) {
        String ratio = node.getNodeRatio();
        if (StringUtils.isBlank(ratio)) {
            return;
        }
        if (ratio.length() > NODE_RATIO_MAX) {
            errors.add(error("NODE_RATIO_TOO_LONG", "节点 " + nodeCode + " 的 nodeRatio 超长（"
                    + ratio.length() + " > " + NODE_RATIO_MAX + "），严格模式报错、非严格模式静默截断", nodeCode));
        }
        String text = ratio.trim();
        boolean numeric = text.matches("\\d+(\\.\\d+)?");
        boolean engineForm = text.startsWith("passCount=") || text.startsWith("rejectCount=")
                || text.contains("@@");
        if (!numeric && !engineForm) {
            errors.add(error("NODE_RATIO_NOT_NUMERIC", "节点 " + nodeCode + " 的 nodeRatio 必须是数字串"
                    + "（0=或签，1~99=票签百分比，100=会签），当前值「" + ratio + "」会在流转时抛异常", nodeCode));
        }
    }

    /** ext 列 varchar(255)：必须是 JSON 数组串 [{"code":..,"value":..}] */
    private void checkExt(NodeJson node, String nodeCode, List<Map<String, Object>> errors) {
        String ext = node.getExt();
        if (StringUtils.isBlank(ext)) {
            return;
        }
        if (ext.length() > EXT_MAX) {
            errors.add(error("EXT_TOO_LONG", "节点 " + nodeCode + " 的 ext 超长（" + ext.length()
                    + " > " + EXT_MAX + "）：保存失败或静默截断，取决于目标库 sql_mode", nodeCode));
        }
        String text = ext.trim();
        if (!text.startsWith("[") && !text.startsWith("{")) {
            errors.add(error("EXT_NOT_JSON", "节点 " + nodeCode + " 的 ext 必须是 JSON 数组串"
                    + "（形如 [{\"code\":\"wfTimeoutHours\",\"value\":\"24\"}]），当前值：" + ext, nodeCode));
        }
    }

    /**
     * permission_flag 逐段校验：格式 {@code 类型:值}（多来源用 @@ 连接），无前缀视为裸用户 id。
     * 未知类型与不存在的角色 id 现在都能存进去、运行时静默匹配不上，这里拦成明确错误。
     */
    private void checkPermissionFlag(NodeJson node, String nodeCode, Set<String> roleIds,
                                     List<Map<String, Object>> errors) {
        String flag = node.getPermissionFlag();
        if (StringUtils.isBlank(flag)) {
            if (node.getNodeType() != null && node.getNodeType() == NODE_TYPE_START) {
                return;
            }
            if (node.getNodeType() != null && node.getNodeType() == NODE_TYPE_END) {
                return;
            }
            errors.add(error("PERMISSION_FLAG_EMPTY", "节点 " + nodeCode
                    + " 没配办理人（permissionFlag 为空）：任务会无人可办", nodeCode));
            return;
        }
        if (flag.length() > PERMISSION_FLAG_MAX) {
            errors.add(error("PERMISSION_FLAG_TOO_LONG", "节点 " + nodeCode + " 的 permissionFlag 超长（"
                    + flag.length() + " > " + PERMISSION_FLAG_MAX + "）：多来源办理人最容易触顶，"
                    + "非严格模式的库会静默截断导致审批人少一个", nodeCode));
        }
        for (String segment : flag.split(FlowConstant.PERMISSION_FLAG_SEPARATOR)) {
            String part = segment.trim();
            if (part.isEmpty()) {
                continue;
            }
            int split = part.indexOf(FlowConstant.PERMISSION_TYPE_SEPARATOR);
            if (split < 0) {
                // 裸用户 id：只校非空，存在性不在本期范围（需按 userId 逐个回查）
                continue;
            }
            String type = part.substring(0, split);
            String value = part.substring(split + 1);
            FlowPermissionTypeEnum typeEnum = FlowPermissionTypeEnum.ofCode(type);
            if (typeEnum == null) {
                errors.add(error("PERMISSION_TYPE_UNKNOWN", "节点 " + nodeCode + " 的办理人类型「" + type
                        + "」不存在，可用类型：user/role/dept/deptLeader/orgLeader/userGroup/script"
                        + "（写错的类型不报错但谁都匹配不上）", nodeCode));
                continue;
            }
            if (value.isEmpty()) {
                errors.add(error("PERMISSION_VALUE_EMPTY", "节点 " + nodeCode + " 的办理人「" + part
                        + "」缺少值", nodeCode));
                continue;
            }
            if (typeEnum == FlowPermissionTypeEnum.ROLE && roleIds != null && !roleIds.contains(value)) {
                errors.add(error("ROLE_NOT_EXISTS", "节点 " + nodeCode + " 的办理人角色不存在或不属于当前组织："
                        + value + "（先用 list_roles 拿准 roleId，别猜）", nodeCode));
            }
            if (typeEnum == FlowPermissionTypeEnum.SCRIPT) {
                checkScriptValue(nodeCode, value, roleIds, errors);
            }
        }
    }

    /** script: 规则的 code 必须在枚举内；带角色参数的规则顺带校验角色存在性 */
    private void checkScriptValue(String nodeCode, String value, Set<String> roleIds,
                                  List<Map<String, Object>> errors) {
        String code = value.contains(":") ? value.substring(0, value.indexOf(':')) : value;
        boolean known = false;
        for (FlowScriptRuleEnum rule : FlowScriptRuleEnum.values()) {
            if (rule.getCode().equalsIgnoreCase(code)) {
                known = true;
                break;
            }
        }
        if (!known) {
            errors.add(error("SCRIPT_RULE_UNKNOWN", "节点 " + nodeCode + " 的脚本规则「" + code
                    + "」不存在（skill §4.4 的清单是唯一集合，不要自创）", nodeCode));
            return;
        }
        // 带角色参数的规则（INITIATOR_DEPT_ROLE:{roleId}、DEPT_ROLE:{anchor}:{roleId}）顺带校验角色
        if (roleIds != null && code.endsWith("DEPT_ROLE")) {
            String roleId = value.substring(value.lastIndexOf(':') + 1);
            if (!roleId.isEmpty() && !roleIds.contains(roleId)) {
                errors.add(error("ROLE_NOT_EXISTS", "节点 " + nodeCode + " 的脚本规则引用了不存在的角色："
                        + roleId + "（先用 list_roles 拿准 roleId，别猜）", nodeCode));
            }
        }
    }

    /** 驳回落点 anyNodeSkip 必须指向存在的节点 */
    private void checkAnyNodeSkip(NodeJson node, String nodeCode, Set<String> nodeCodes,
                                  List<Map<String, Object>> errors) {
        String anyNodeSkip = node.getAnyNodeSkip();
        if (StringUtils.isNotBlank(anyNodeSkip) && !nodeCodes.contains(anyNodeSkip)) {
            errors.add(error("ANY_NODE_SKIP_NOT_FOUND", "节点 " + nodeCode + " 的 anyNodeSkip 指向不存在的节点："
                    + anyNodeSkip, nodeCode));
        }
    }

    /** 结构红线：每个非结束节点至少一条出边，且 nextNodeCode 必须存在 */
    private void checkEdges(NodeJson node, String nodeCode, Set<String> nodeCodes,
                           List<Map<String, Object>> errors) {
        if (node.getNodeType() != null && node.getNodeType() == NODE_TYPE_END) {
            return;
        }
        List<SkipJson> skipList = node.getSkipList();
        if (skipList == null || skipList.isEmpty()) {
            errors.add(error("NODE_NO_OUTGOING", "节点 " + nodeCode
                    + " 没有任何出边（抄送节点也要有）：流程会卡死在这里", nodeCode));
            return;
        }
        for (SkipJson skip : skipList) {
            String next = skip.getNextNodeCode();
            if (StringUtils.isBlank(next)) {
                errors.add(error("NEXT_NODE_REQUIRED", "节点 " + nodeCode + " 的某条跳转缺少 nextNodeCode", nodeCode));
            } else if (!nodeCodes.contains(next)) {
                errors.add(error("NEXT_NODE_NOT_FOUND", "节点 " + nodeCode + " 的跳转指向不存在的节点：" + next, nodeCode));
            }
        }
    }

    private boolean hasRoleLookup(List<NodeJson> nodeList) {
        for (NodeJson node : nodeList) {
            String flag = node.getPermissionFlag();
            if (StringUtils.isNotBlank(flag)
                    && (flag.contains(FlowPermissionTypeEnum.ROLE.getCode() + FlowConstant.PERMISSION_TYPE_SEPARATOR)
                    || flag.contains("DEPT_ROLE") || flag.contains("INITIATOR_DEPT_ROLE"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 本组织角色 id 全集。按组织取全集再比对（selectRoleByRoleId 不带组织过滤，
     * 且角色不存在时会在 copyProperties 处 NPE，不能拿来逐个查）。
     */
    private Set<String> currentOrganizationRoleIds() {
        Set<String> ids = new HashSet<>();
        if (remoteUserService == null) {
            throw new IllegalStateException("RemoteUserService 不可用");
        }
        String organizationId = SecurityUtils.getOrganizationId();
        List<SystemRoleDTO> roles = remoteUserService.selectSystemRoleByOrganizationId(organizationId);
        if (roles != null) {
            for (SystemRoleDTO role : roles) {
                if (role != null && StringUtils.isNotBlank(role.getId())) {
                    ids.add(role.getId());
                }
            }
        }
        return ids;
    }

    private static Map<String, Object> error(String code, String message, String field) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("message", message);
        row.put("field", field);
        return row;
    }
}
