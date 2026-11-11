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

package com.privernal.flow.controller;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.flow.domain.FlowTaskComment;
import com.privernal.flow.mapper.FlowTaskCommentMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程实例评论（R2-17 评论与附件）：按实例维度沉淀过程沟通记录。
 * 附件复用 /back/file/upload（module=flow-comment），文件元信息 JSON 存 attachments。
 *
 * @author saperliu
 */
@RestController
@RequestMapping("/back/flow/comment")
@DS("master")
public class FlowTaskCommentController {

    /** 评论内容长度上限（与审批意见同量级） */
    private static final int CONTENT_MAX_LENGTH = 2000;

    @Autowired
    private FlowTaskCommentMapper commentMapper;

    /**
     * 评论分页（按实例，最新在前）
     */
    @Operation(summary = "评论分页")
    @PreAuthorize(hasPermi = "flow:task:list")
    @GetMapping("/list/{instanceId}")
    public CommonResult<PageListDTO<Object>> list(@PathVariable("instanceId") String instanceId,
                                                  @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                  @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        if (StringUtils.isEmpty(instanceId)) {
            throw new ServiceException("instanceId 不能为空");
        }
        Page<FlowTaskComment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FlowTaskComment>()
                        .eq(FlowTaskComment::getInstanceId, instanceId)
                        .orderByDesc(FlowTaskComment::getCreatedTime));
        return CommonResult.ok(new PageListDTO<Object>(page.getRecords(), (int) page.getTotal()));
    }

    /**
     * 发表评论（组织/评论人显式带入——本表在组织拦截器忽略清单）
     */
    @Operation(summary = "发表评论")
    @PreAuthorize(hasPermi = "flow:task:list")
    @PostMapping("/save")
    public CommonResult<FlowTaskComment> save(@RequestBody FlowTaskComment comment) {
        if (comment == null || StringUtils.isEmpty(comment.getInstanceId())) {
            throw new ServiceException("instanceId 不能为空");
        }
        if (StringUtils.isEmpty(comment.getContent())) {
            throw new ServiceException("评论内容不能为空");
        }
        if (comment.getContent().length() > CONTENT_MAX_LENGTH) {
            throw new ServiceException("评论内容不能超过 " + CONTENT_MAX_LENGTH + " 字");
        }
        comment.setId(null);
        comment.setUserId(SecurityUtils.getUserId());
        comment.setUserName(SecurityUtils.getNickName());
        comment.setOrganizationId(SecurityUtils.getOrganizationId());
        comment.setDepartmentId(SecurityUtils.getDepartmentId());
        commentMapper.insert(comment);
        return CommonResult.ok(comment);
    }

    /**
     * 删除评论（本人或超管）
     */
    @Operation(summary = "删除评论")
    @PreAuthorize(hasPermi = "flow:task:list")
    @DeleteMapping("/{id}")
    public CommonResult<Integer> remove(@PathVariable("id") String id) {
        FlowTaskComment comment = commentMapper.selectById(id);
        if (comment == null) {
            return CommonResult.ok(0);
        }
        if (!SecurityUtils.isSuperRoleUser() && !SecurityUtils.getUserId().equals(comment.getUserId())) {
            throw new ServiceException("仅评论人或管理员可删除");
        }
        return CommonResult.ok(commentMapper.deleteById(id));
    }
}
