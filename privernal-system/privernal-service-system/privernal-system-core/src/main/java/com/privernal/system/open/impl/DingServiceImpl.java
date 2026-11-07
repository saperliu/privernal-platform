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

package com.privernal.system.open.impl;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.dingtalk.service.DingTalkService;
import com.privernal.common.wx.service.WorkWxService;
import com.privernal.system.domain.SystemDepartment;
import com.privernal.system.dto.SystemSocialUserDTO;
import com.privernal.system.filter.SystemDepartmentFilterDTO;
import com.privernal.system.open.DingService;
import com.privernal.system.service.ISystemDepartmentService;
import com.privernal.system.service.ISystemSocialUserService;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.request.OapiV2UserListRequest;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse;
import com.dingtalk.api.response.OapiV2UserListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.privernal.common.core.constant.DataConstants.DATA_STATUS_YES;

@Slf4j
@Service
public class DingServiceImpl implements DingService {


    @Autowired
    private ISystemDepartmentService iSystemDepartmentService;

    @Autowired
    private ISystemSocialUserService iSystemSocialUserService;

    @Autowired
    private DingTalkService dingTalkService;

    @Autowired
    private WorkWxService workWxService;

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public void synRootDept(String accessToken, String organizationId, Integer synEmployee) throws Exception {
        OapiV2DepartmentListsubRequest req = new OapiV2DepartmentListsubRequest();
        req.setDeptId(1L);
        req.setLanguage("zh_CN");
//        OapiV2DepartmentListsubResponse rsp = deptClient.execute(req, accessToken);
        List<OapiV2DepartmentListsubResponse.DeptBaseResponse> depts = dingTalkService.findDeptList(req, accessToken);
        if (depts != null && !depts.isEmpty()) {
            SystemDepartmentFilterDTO filterDTO = new SystemDepartmentFilterDTO();
            filterDTO.setSynFlag(1L);
            for (OapiV2DepartmentListsubResponse.DeptBaseResponse baseResponse : depts) {

                // 存储一级部门
                SystemDepartment systemDepartment = new SystemDepartment();
                systemDepartment.setDeptCode(RandomUtil.randomString(10));
                systemDepartment.setDeptName(baseResponse.getName());
                systemDepartment.setOrganizationId(organizationId);
                systemDepartment.setSynFlag(1L);
                systemDepartment.setParentId(organizationId);
                systemDepartment.setSynId(baseResponse.getDeptId() + "");

                // 如果已经存在，则是更新
                filterDTO.setSynId(baseResponse.getDeptId() + "");
                List<SystemDepartment> deptList = iSystemDepartmentService.selectSystemDepartmentList(filterDTO);
                if (deptList != null && !deptList.isEmpty()) {
                    SystemDepartment systemDepartment1 = deptList.get(0);
                    systemDepartment.setId(systemDepartment1.getId());
                    iSystemDepartmentService.updateSystemDepartment(systemDepartment);
                } else {
                    iSystemDepartmentService.insertSystemDepartment(systemDepartment);
                }

                if (synEmployee != null && synEmployee == DATA_STATUS_YES.intValue()) {
                    // 同步人员
                    findPersonByDept(0L, accessToken, baseResponse.getDeptId(), systemDepartment.getId(), organizationId);
                }


                this.findSynDept(baseResponse.getDeptId(), systemDepartment.getId(), organizationId, accessToken, synEmployee);
            }
        }
        log.info("同步部门成功. {} ", JSON.toJSONString(depts));
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void findSynDept(long deptId, String departmentId, String organizationId, String accessToken, Integer synEmployee) throws Exception {
        OapiV2DepartmentListsubRequest req = new OapiV2DepartmentListsubRequest();
        req.setDeptId(deptId);
        req.setLanguage("zh_CN");
//        OapiV2DepartmentListsubResponse rsp = deptClient.execute(req, accessToken);
        SystemDepartmentFilterDTO filterDTO = new SystemDepartmentFilterDTO();
        filterDTO.setSynFlag(1L);
        List<OapiV2DepartmentListsubResponse.DeptBaseResponse> depts = dingTalkService.findDeptList(req, accessToken);
        if (depts != null && !depts.isEmpty()) {
            for (OapiV2DepartmentListsubResponse.DeptBaseResponse baseResponse : depts) {

                // 存储子部门
                SystemDepartment systemDepartment = new SystemDepartment();
                systemDepartment.setDeptCode(RandomUtil.randomString(10));
                systemDepartment.setDeptName(baseResponse.getName());
                systemDepartment.setOrganizationId(organizationId);
                systemDepartment.setParentId(departmentId);
                systemDepartment.setSynFlag(1L);
                systemDepartment.setSynId(baseResponse.getDeptId() + "");

                // 如果已经存在，则是更新
                filterDTO.setSynId(baseResponse.getDeptId() + "");
                List<SystemDepartment> deptList = iSystemDepartmentService.selectSystemDepartmentList(filterDTO);
                if (deptList != null && !deptList.isEmpty()) {
                    SystemDepartment systemDepartment1 = deptList.get(0);
                    systemDepartment.setId(systemDepartment1.getId());
                    iSystemDepartmentService.updateSystemDepartment(systemDepartment);
                } else {
                    iSystemDepartmentService.insertSystemDepartment(systemDepartment);
                }

                if (synEmployee != null && synEmployee == DATA_STATUS_YES.intValue()) {
                    // 同步人员
                    findPersonByDept(0L, accessToken, baseResponse.getDeptId(), systemDepartment.getId(), organizationId);
                }

                this.findSynDept(baseResponse.getDeptId(), systemDepartment.getId(), organizationId, accessToken, synEmployee);
            }
        }
    }

    /**
     * 查询部门里的人员
     *
     * @param cursor         游标 0开始，
     * @param accessToken    认证的token
     * @param deptId         钉钉端的部门ID
     * @param departmentId   平台端的部门Id
     * @param organizationId 平台端的组织ID
     * @throws Exception
     */
    private void findPersonByDept(Long cursor, String accessToken, long deptId, String departmentId, String organizationId) throws Exception {

        OapiV2UserListRequest req = new OapiV2UserListRequest();
        req.setDeptId(deptId);
        req.setCursor(cursor);
        req.setSize(100L);
        req.setOrderField("modify_desc");
        req.setContainAccessLimit(false);
        req.setLanguage("zh_CN");
//        OapiV2UserListResponse rsp = userClient.execute(req, accessToken);
        OapiV2UserListResponse.PageResult result = dingTalkService.findDeptUserList(req, accessToken);
        if (result == null || result.getList() == null || result.getList().isEmpty()) {
            return;
        }

        for (OapiV2UserListResponse.ListUserResponse userResponse : result.getList()) {

            SystemSocialUserDTO sysSocialUserDTO = new SystemSocialUserDTO();
            sysSocialUserDTO.setNickName(userResponse.getName());
            sysSocialUserDTO.setAvatarUrl(userResponse.getAvatar());
            sysSocialUserDTO.setGender("2");
            sysSocialUserDTO.setUnionId(userResponse.getUnionid());
            sysSocialUserDTO.setUid(userResponse.getUserid());
            sysSocialUserDTO.setUuid(userResponse.getUserid());
            sysSocialUserDTO.setEmail(userResponse.getEmail());
            sysSocialUserDTO.setPurePhoneNumber(userResponse.getMobile());

            sysSocialUserDTO.setDepartmentId(departmentId);
            sysSocialUserDTO.setOrganizationId(organizationId);

            iSystemSocialUserService.saveSysSocialUser(sysSocialUserDTO);
        }
        // 还有更多
        if (result.getHasMore()) {
            findPersonByDept(result.getNextCursor(), accessToken, deptId, departmentId, organizationId);
        }
    }


    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public void synWorkRootDept(String accessToken, String organizationId, String source, Integer synEmployee) throws Exception {
        List<JSONObject> deptList = workWxService.getDeptList(accessToken);
        if (deptList != null && !deptList.isEmpty()) {
            SystemDepartmentFilterDTO filterDTO = new SystemDepartmentFilterDTO();
            filterDTO.setSynFlag(1L);

            Boolean ignore = OrganizationContextHolder.isIgnore();
            try {
                // 同步时没有登录，没有组织信息
                OrganizationContextHolder.setOrganizationId(organizationId);

                for (JSONObject jsonObject : deptList) {

                    if (jsonObject.getString("parentid") != null && jsonObject.getString("parentid").equals("1")) {
                        //顶级部门
                        String departmentId = "";
                        // 如果已经存在，则是更新
                        filterDTO.setSynId(jsonObject.getString("id"));
                        List<SystemDepartment> deptOldList = iSystemDepartmentService.selectSystemDepartmentList(filterDTO);
                        if (deptOldList != null && !deptOldList.isEmpty()) {
                            SystemDepartment systemDepartment1 = deptOldList.get(0);
                            systemDepartment1.setDeptName(jsonObject.getString("name"));
                            systemDepartment1.setOrganizationId(organizationId);
                            systemDepartment1.setSynFlag(1L);
                            systemDepartment1.setParentId(organizationId);
                            systemDepartment1.setSynId(jsonObject.getString("id"));
                            iSystemDepartmentService.updateSystemDepartment(systemDepartment1);

                            departmentId = systemDepartment1.getId();
                        } else {
                            SystemDepartment systemDepartment = new SystemDepartment();
                            systemDepartment.setDeptCode(RandomUtil.randomString(10));
                            systemDepartment.setDeptName(jsonObject.getString("name"));
                            systemDepartment.setOrganizationId(organizationId);
                            systemDepartment.setSynFlag(1L);
                            systemDepartment.setParentId(organizationId);
                            systemDepartment.setSynId(jsonObject.getString("id"));
                            iSystemDepartmentService.insertSystemDepartment(systemDepartment);
                            departmentId = systemDepartment.getId();
                        }

                        if (synEmployee != null && synEmployee == DATA_STATUS_YES.intValue()) {
                            // 同步人员
                            findEmployeeByDept(accessToken, jsonObject.getString("id"), departmentId, organizationId, source);
                        }
                        // 存储此顶级部门的子部门
                        saveChildDept(accessToken, organizationId, jsonObject.getString("id"), deptList, source, synEmployee);
                    }
                }
            } finally {
                OrganizationContextHolder.setIgnore(ignore);
                OrganizationContextHolder.clear();
            }
        }
    }

    private void saveChildDept(String accessToken, String organizationId, String parentId, List<JSONObject> childDeptList, String source, Integer synEmployee) {
        SystemDepartmentFilterDTO filterDTO = new SystemDepartmentFilterDTO();
        filterDTO.setSynFlag(1L);
        for (JSONObject jsonObject : childDeptList) {
            if (jsonObject.getString("parentid") != null && jsonObject.getString("parentid").equals(parentId)) {
                // 如果已经存在，则是更新
                filterDTO.setSynId(jsonObject.getString("id"));
                List<SystemDepartment> deptOldList = iSystemDepartmentService.selectSystemDepartmentList(filterDTO);

                String departmentId = "";

                if (deptOldList != null && !deptOldList.isEmpty()) {
                    SystemDepartment systemDepartment1 = deptOldList.get(0);
                    systemDepartment1.setDeptName(jsonObject.getString("name"));
                    systemDepartment1.setOrganizationId(organizationId);
                    systemDepartment1.setSynFlag(1L);
                    systemDepartment1.setParentId(organizationId);
                    systemDepartment1.setSynId(jsonObject.getString("id"));
                    iSystemDepartmentService.updateSystemDepartment(systemDepartment1);
                    departmentId = systemDepartment1.getId();
                } else {
                    SystemDepartment systemDepartment = new SystemDepartment();
                    systemDepartment.setDeptCode(RandomUtil.randomString(10));
                    systemDepartment.setDeptName(jsonObject.getString("name"));
                    systemDepartment.setOrganizationId(organizationId);
                    systemDepartment.setSynFlag(1L);
                    systemDepartment.setParentId(organizationId);
                    systemDepartment.setSynId(jsonObject.getString("id"));
                    iSystemDepartmentService.insertSystemDepartment(systemDepartment);
                    departmentId = systemDepartment.getId();
                }
                if (synEmployee != null && synEmployee == DATA_STATUS_YES.intValue()) {
                    // 同步人员
                    findEmployeeByDept(accessToken, jsonObject.getString("id"), departmentId, organizationId, source);
                }
                // 存储此顶级部门的子部门
                saveChildDept(accessToken, organizationId, jsonObject.getString("id"), childDeptList, source, synEmployee);
            }
        }
    }

    private void findEmployeeByDept(String accessToken, String deptId, String departmentId, String organizationId, String source) {
        List<JSONObject> userList = workWxService.findDeptUserList(deptId, accessToken);
        for (JSONObject jsonObject : userList) {

            SystemSocialUserDTO sysSocialUserDTO = new SystemSocialUserDTO();
//            JSONObject resultUser = workWxService.findUserDetailByUserId(jsonObject.getString("userid"), accessTokenEmployee);

            sysSocialUserDTO.setNickName(jsonObject.getString("name"));

            //本系统：0=男,1=女,2=未知
            //企业微信 0表示未定义，1表示男性，2表示女性。

            if (jsonObject.getString("gender") == null) {
                sysSocialUserDTO.setGender("2");
            } else {
                if (jsonObject.getString("gender").equals("0")) {
                    sysSocialUserDTO.setGender("0");
                } else {
                    if (jsonObject.getString("gender").equals("1")) {
                        sysSocialUserDTO.setGender("1");
                    } else {
                        sysSocialUserDTO.setGender("2");
                    }
                }
            }

            String wxUserId = jsonObject.getString("userid");
            String openId = workWxService.findOpenIdByUserId(wxUserId, accessToken);
            // 有企业微信，但是没有微信，这个账号的openId就是null
            if(StringUtils.isNotEmpty(openId)){
                sysSocialUserDTO.setOpenId(openId);
            }else {
                sysSocialUserDTO.setOpenId(wxUserId);
            }

            sysSocialUserDTO.setUid(wxUserId);
            sysSocialUserDTO.setUuid(wxUserId);

            sysSocialUserDTO.setDepartmentId(departmentId);
            sysSocialUserDTO.setOrganizationId(organizationId);

            sysSocialUserDTO.setSource(source);

            iSystemSocialUserService.saveSysSocialUser(sysSocialUserDTO);
        }
    }
}
