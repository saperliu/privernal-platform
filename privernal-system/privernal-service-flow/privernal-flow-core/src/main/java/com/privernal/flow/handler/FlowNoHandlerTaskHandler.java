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

package com.privernal.flow.handler;

import com.privernal.flow.service.IFlowUserResolveService;
import com.privernal.flow.utils.FlowNodeExtUtil;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「找不到办理人」兜底处理器
 * <p>
 * 节点 ext.wfsNoFoundUser 两种策略，互斥：
 * <ul>
 *   <li>MANAGE 与<b>未配置（默认）</b>：把组织负责人写成<b>本节点的办理人</b>，任务留在这里正常待办、正常审批；</li>
 *   <li>NEXT：直接流转到下一节点，<b>被跳过的节点不留办理人</b>。</li>
 * </ul>
 * 之所以放在任务生成之后处理：引擎的 convertPermissions 回调只拿到标识列表、拿不到是哪个节点，
 * 节点级策略在这里才看得到；又因为 {@code TaskServiceImpl} 对无办理人的任务会抛权限异常，
 * NEXT 只能靠 {@code ignore(true)} 由系统自己跳过。
 * </p>
 *
 * @author saperliu
 */
@Component
public class FlowNoHandlerTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(FlowNoHandlerTaskHandler.class);

    /** 连续跳过的最大次数，防止一串无人节点导致死循环 */
    private static final int MAX_SKIP_ROUNDS = 20;

    private final FlowCcTaskHandler ccTaskHandler;

    private final IFlowUserResolveService flowUserResolveService;

    public FlowNoHandlerTaskHandler(FlowCcTaskHandler ccTaskHandler, IFlowUserResolveService flowUserResolveService) {
        this.ccTaskHandler = ccTaskHandler;
        this.flowUserResolveService = flowUserResolveService;
    }

    /**
     * 处理实例当前停留的、没有任何办理人的待办任务
     */
    public void handleUnresolvedNodes(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        for (int round = 0; round < MAX_SKIP_ROUNDS; round++) {
            List<Task> tasks = FlowEngine.taskService().getByInsId(instanceId);
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            boolean advanced = false;
            for (Task task : tasks) {
                if (hasHandler(task)) {
                    continue;
                }
                Node node = ccTaskHandler.findNode(task);
                if (isAssignToOrgAdmin(node)) {
                    assignOrgAdmin(task);
                    // 交给管理员后任务必须停在本节点，不能继续往下走
                    continue;
                }
                if (skipTask(task)) {
                    advanced = true;
                }
            }
            // 只有真的跳过了节点才可能冒出新任务，需要再扫一轮
            if (!advanced) {
                return;
            }
        }
    }

    /**
     * 任务是否已有办理人：直接看 flow_user，而不是重新解析节点配置
     * （重新解析会受组织变动影响，和任务创建当时落库的结果不一致）
     */
    private boolean hasHandler(Task task) {
        List<User> users = FlowEngine.userService()
                .listByAssociatedAndTypes(task.getId(), UserType.APPROVAL.getKey());
        return users != null && !users.isEmpty();
    }

    /**
     * 节点是否按「交给组织管理员」处理：只有显式配了 NEXT 才跳过，未配置默认交给管理员
     */
    private boolean isAssignToOrgAdmin(Node node) {
        if (node == null) {
            return true;
        }
        String strategy = FlowNodeExtUtil.parseExt(node.getExt()).get(FlowNodeExtUtil.KEY_NO_FOUND_USER);
        return !FlowNodeExtUtil.NO_FOUND_NEXT.equals(strategy);
    }

    /**
     * 把组织负责人补成本节点的办理人
     */
    private void assignOrgAdmin(Task task) {
        List<String> orgAdmins = flowUserResolveService.resolveFallbackOrgLeaders();
        if (orgAdmins.isEmpty()) {
            log.error("节点要求交给组织负责人，但当前组织未配置负责人，任务将停留在该节点 taskId={} nodeCode={}",
                    task.getId(), task.getNodeCode());
            return;
        }
        try {
            FlowEngine.userService()
                    .updatePermission(task.getId(), orgAdmins, UserType.APPROVAL.getKey(), false, null);
            log.warn("节点未解析到办理人，已按节点配置交给组织负责人 {} taskId={} nodeCode={}",
                    orgAdmins, task.getId(), task.getNodeCode());
        } catch (Exception e) {
            log.error("给节点补组织负责人失败，任务将停留在该节点 taskId={} nodeCode={}",
                    task.getId(), task.getNodeCode(), e);
        }
    }

    /**
     * 静默流转到下一节点：被跳过的节点不留办理人
     */
    private boolean skipTask(Task task) {
        try {
            FlowParams params = FlowParams.build()
                    .ignore(true)
                    .skipType(SkipType.PASS.getKey())
                    .message("未解析到办理人，按节点配置自动进入下一节点");
            FlowEngine.taskService().skip(task.getId(), params);
            log.warn("节点未解析到办理人，已自动进入下一节点 taskId={} nodeCode={}",
                    task.getId(), task.getNodeCode());
            return true;
        } catch (Exception e) {
            // 单个节点失败不阻断主流程：宁可留下一个没人能办的任务让人工介入，也不要静默改流程走向
            log.error("无人节点自动流转失败，任务将停留在该节点 taskId={} nodeCode={}",
                    task.getId(), task.getNodeCode(), e);
            return false;
        }
    }
}
