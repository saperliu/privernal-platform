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

import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.message.domain.SystemMessage;
import com.privernal.message.domain.SystemMessageUser;
import com.privernal.message.dto.*;
import com.privernal.message.filter.SystemMessageFilterDTO;
import com.privernal.message.filter.SystemMessageUserFilterDTO;
import com.privernal.message.mapper.SystemMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.privernal.message.mapper.SystemMessageUserMapper;
import com.privernal.message.service.ISystemMessageService;
import com.privernal.system.constant.SystemConstant;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.feign.RemoteUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统消息的Service业务层处理
 *
 * @author saperliu
 * @date 2023-05-30
 * @Description 系统消息的Service业务层处理
 */
@Slf4j
@Service
public class SystemMessageServiceImpl implements ISystemMessageService {
    @Autowired
    private SystemMessageMapper systemMessageMapper;

    @Autowired
    private SystemMessageUserMapper systemMessageUserMapper;

    @Autowired
    private RemoteUserService remoteUserService;

    /**
     * 查询系统消息
     *
     * @param id 系统消息主键
     * @return 系统消息
     */
    @Transactional(readOnly = true)
    @Override
    public SystemMessage selectSystemMessageById(String id) {
        return systemMessageMapper.selectById(id);
    }

    /**
     * 查询全员通知（messagePerson=ALL，按创建时间倒序取最近 20 条），首页公告栏使用
     */
    @Transactional(readOnly = true)
    @Override
    public List<SystemMessage> selectBulletinMessageList() {
        return systemMessageMapper.selectList(
                new LambdaQueryWrapper<SystemMessage>()
                        .eq(SystemMessage::getDeletedFlag, "N")
                        .eq(SystemMessage::getMessagePerson, "ALL")
                        .orderByDesc(SystemMessage::getCreatedTime)
                        .last("limit 20"));
    }

    /**
     * 查询系统消息通知
     *
     * @param id 系统消息通知主键
     * @return 系统消息通知
     */
    @Transactional(readOnly = true)
    @Override
    public SystemMessageDetailDTO selectSystemMessageDetailDTOById(String id) {
        SystemMessage systemMessage = systemMessageMapper.selectById(id);
        SystemMessageDetailDTO systemMessageDetailDTO = new SystemMessageDetailDTO();
        if (systemMessage != null) {
            BeanUtils.copyProperties(systemMessage, systemMessageDetailDTO);

            List<SystemMessageUser> userList = systemMessageUserMapper.selectList(SystemMessageUser::getMessageId, systemMessage.getId());
            if (userList != null && !userList.isEmpty()) {
                List<SystemMessageUserDTO> systemMessageUserDTOList = new ArrayList<>();
                List<String> ids = new ArrayList<>();
                for (SystemMessageUser systemMessageUser : userList) {
                    SystemMessageUserDTO systemMessageUserDTO = new SystemMessageUserDTO();
                    BeanUtils.copyProperties(systemMessageUser, systemMessageUserDTO);
                    systemMessageUserDTOList.add(systemMessageUserDTO);
                    ids.add(systemMessageUser.getUserId());
                }
                systemMessageDetailDTO.setUserList(systemMessageUserDTOList);
                systemMessageDetailDTO.setUserIds(ids);
            }
        }
        return systemMessageDetailDTO;
    }

    /**
     * 查询系统消息列表
     *
     * @param systemMessage 系统消息
     * @return 系统消息
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemMessageDTO> selectSystemMessageList(SystemMessageFilterDTO systemMessage) {
        return systemMessageMapper.selectSystemMessageList(systemMessage);
    }

    /**
     * 新增系统消息
     *
     * @param systemMessage 系统消息
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemMessage(SystemMessage systemMessage) {
        return systemMessageMapper.insert(systemMessage);
    }

    /**
     * 修改系统消息
     *
     * @param systemMessage 系统消息
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemMessage(SystemMessage systemMessage) {
        return systemMessageMapper.updateById(systemMessage);
    }

    /**
     * 批量删除系统消息
     *
     * @param ids 需要删除的系统消息主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemMessageByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemMessageById(id);
        }
        return ids.length;
    }

    /**
     * 删除系统消息信息
     *
     * @param id 系统消息主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemMessageById(String id) {
        return systemMessageMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MessageUserDTO> selectUserMessageList(SystemMessageUserFilterDTO userFilterDTO) {
        return systemMessageMapper.selectSystemMessageListByUser(userFilterDTO);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int saveUserMessageRead(String messageId, String userId, String nickName) {
        SystemMessage systemMessage = systemMessageMapper.selectById(messageId);
        if (systemMessage.getMessagePerson().equals(SystemConstant.MESSAGE_PERSON_ALL)) {
            // ALL 消息在发送时已为全员生成 system_message_user 关联行，
            // 标记已读必须先查再更新，直接 insert 会撞 unique_user_message 唯一键
            SystemMessageUser existing = systemMessageUserMapper.selectSystemMessageUserByMessageId(messageId, userId);
            if (existing != null) {
                existing.setStatus(WorkflowDataStatus.FINISH.getCode());
                return systemMessageUserMapper.updateById(existing);
            }
            SystemMessageUser systemMessageUser = new SystemMessageUser();
            systemMessageUser.setMessageId(messageId);
            systemMessageUser.setUserId(userId);
            systemMessageUser.setStatus(WorkflowDataStatus.FINISH.getCode());
            systemMessageUser.setNickName(nickName);
            return systemMessageUserMapper.insert(systemMessageUser);
        }
        if (systemMessage.getMessagePerson().equals(SystemConstant.MESSAGE_PERSON_SEL)) {
            // 直接选择的人员，直接更新
            SystemMessageUser systemMessageUser = systemMessageUserMapper.selectSystemMessageUserByMessageId(messageId, userId);
            if (systemMessageUser != null) {
                systemMessageUser.setStatus(WorkflowDataStatus.FINISH.getCode());
                return systemMessageUserMapper.updateById(systemMessageUser);
            }
        }
        return 0;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int saveUserMessageAllRead(String userId, String nickName) {
        // 更新所有有关联关系的状态为已读
        int x = systemMessageUserMapper.updateReadAll(userId);
        // 查询未读，没有关联关系的。

        List<SystemMessage> systemMessageList = systemMessageMapper.selectSystemMessageListNoRead();
        for (SystemMessage systemMessage : systemMessageList) {
            SystemMessageUser systemMessageUser = new SystemMessageUser();
            systemMessageUser.setMessageId(systemMessage.getId());
            systemMessageUser.setUserId(userId);
            systemMessageUser.setNickName(nickName);
            systemMessageUser.setStatus(WorkflowDataStatus.FINISH.getCode());
            systemMessageUserMapper.insert(systemMessageUser);
        }
        x = systemMessageList.size() + x;
        return x;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemMessageDTO saveSystemMessage(SystemMessageDTO systemMessageDTO) {

        SystemMessage systemMessage = new SystemMessage();
        BeanUtils.copyProperties(systemMessageDTO, systemMessage);
        // 填充组织/部门：前端表单不含组织字段，历史上 organization_id 为空，
        // 消息会被组织数据范围过滤成"所有组织都查不到"的孤儿数据
        if (StringUtils.isEmpty(systemMessage.getOrganizationId())) {
            systemMessage.setOrganizationId(resolveCurrentOrganizationId());
        }
        if (StringUtils.isEmpty(systemMessage.getDepartmentId())) {
            try {
                systemMessage.setDepartmentId(SecurityUtils.getDepartmentId());
            } catch (Exception ignored) {
                // 无会话场景（MQ/定时）取不到部门，保持为空
            }
        }
        Boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            // 系统超时发送的日志，没有session，所以取不到组织id。
            // MQ里的消息接收后会存储，同样没有操作的session.
            OrganizationContextHolder.setIgnore(true);
            systemMessageMapper.insert(systemMessage);

            // 接收人员：messagePerson=ALL 展开当前组织全部用户；SEL 用表单提交的指定人员。
            // 前端未选人时 userList 可能为 null（历史缺陷：直接 isEmpty 会 NPE 导致保存失败）
            List<SystemMessageUserDTO> userList = systemMessageDTO.getUserList() == null
                    ? new ArrayList<>() : systemMessageDTO.getUserList();
            if ("ALL".equals(systemMessageDTO.getMessagePerson())) {
                // 全员通知：展开组织内全部用户，逐一落接收记录（首页公告栏同步展示全员通知）
                userList = new ArrayList<>();
                try {
                    CommonResult<List<SystemUserDTO>> orgUsers =
                            remoteUserService.findUserByOrg(systemMessage.getOrganizationId(), "");
                    if (orgUsers != null && orgUsers.getCode() == CommonResult.SUCCESS && orgUsers.getData() != null) {
                        for (SystemUserDTO user : orgUsers.getData()) {
                            if (user == null || StringUtils.isEmpty(user.getId())) {
                                continue;
                            }
                            SystemMessageUserDTO item = new SystemMessageUserDTO();
                            item.setUserId(user.getId());
                            item.setNickName(user.getNickName());
                            userList.add(item);
                        }
                    } else {
                        log.warn("全员通知展开组织用户失败：{}",
                                orgUsers == null ? "无返回" : orgUsers.getMessage());
                    }
                } catch (Exception e) {
                    log.error("全员通知展开组织用户异常", e);
                }
            }
            if (!userList.isEmpty()) {
                // 保存用户
                userList.forEach(user -> {
                    SystemMessageUser systemMessageUser = new SystemMessageUser();
                    systemMessageUser.setMessageId(systemMessage.getId());
                    systemMessageUser.setUserId(user.getUserId());
                    systemMessageUser.setNickName(user.getNickName());
                    systemMessageUser.setStatus(WorkflowDataStatus.STAGING.getCode());
                    systemMessageUserMapper.insert(systemMessageUser);
                });
                // 发送web端的消息
                SendMessageDTO sendMessageDTO = new SendMessageDTO();
                BeanUtils.copyProperties(systemMessageDTO, sendMessageDTO);
                List<String> userIds = new ArrayList<>();
                userList.forEach(user -> {
                    userIds.add(user.getUserId());
                });
                sendMessageDTO.setUserList(userIds);
            }
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
        BeanUtils.copyProperties(systemMessage, systemMessageDTO);
        return systemMessageDTO;
    }

    /**
     * 解析当前组织：会话优先，其次组织上下文（MQ/定时场景）
     */
    private String resolveCurrentOrganizationId() {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            if (StringUtils.isNotEmpty(orgId)) {
                return orgId;
            }
        } catch (Exception ignored) {
            // 无会话场景
        }
        return OrganizationContextHolder.getOrganizationId();
    }
}
