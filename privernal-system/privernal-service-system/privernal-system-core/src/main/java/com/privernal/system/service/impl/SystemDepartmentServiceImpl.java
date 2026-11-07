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

package com.privernal.system.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.SystemDepartment;
import com.privernal.system.domain.SystemDeptRelation;
import com.privernal.system.domain.SystemRoleUserDept;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.filter.SystemDepartmentFilterDTO;
import com.privernal.system.mapper.SystemDepartmentMapper;
import com.privernal.system.mapper.SystemDeptRelationMapper;
import com.privernal.system.mapper.SystemRoleUserDeptMapper;
import com.privernal.system.mapper.SystemUserMapper;
import com.privernal.system.service.ISystemDepartmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.List;

import static com.privernal.common.core.constant.DataConstants.DATA_DELETE_FLAG_NOT;
import static com.privernal.common.core.constant.DataConstants.DATA_TYPE_SYSTEM;

/**
 * 部门的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门的Service业务层处理
 */
@Slf4j
@Service
public class SystemDepartmentServiceImpl implements ISystemDepartmentService {
    @Autowired
    private SystemDepartmentMapper systemDepartmentMapper;

    @Autowired
    private SystemDeptRelationMapper systemDeptRelationMapper;

    @Autowired
    private SystemUserMapper systemUserMapper;

    @Autowired
    private SystemRoleUserDeptMapper systemRoleUserDeptMapper;

    /**
     * 查询部门
     *
     * @param id 部门主键
     * @return 部门
     */
    @Transactional(readOnly = true)
    @Override
    public SystemDepartment selectSystemDepartmentById(String id) {
        return systemDepartmentMapper.selectById(id);
    }

    /**
     * 查询部门列表
     *
     * @param systemDepartment 部门
     * @return 部门
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartment> selectSystemDepartmentList(SystemDepartmentFilterDTO systemDepartment) {
        return systemDepartmentMapper.selectSystemDepartmentList(systemDepartment);
    }

    /**
     * 新增部门
     *
     * @param systemDepartment 部门
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemDepartment(SystemDepartment systemDepartment) {
        // 未指定上级时挂到组织根（顶级部门的父 ID 即组织 ID）。否则 updateChildDept 不会写 dept_relation，
        // 部门树按 relation 组装会永远看不到这条部门，而 list 接口又能查到，两处数据不一致
        if (StringUtils.isEmpty(systemDepartment.getParentId())) {
            String organizationId = StringUtils.isNotEmpty(systemDepartment.getOrganizationId())
                    ? systemDepartment.getOrganizationId()
                    : SecurityUtils.getOrganizationId();
            systemDepartment.setParentId(organizationId);
        }
        int deptNum = systemDepartmentMapper.insert(systemDepartment);
        updateChildDept(systemDepartment.getId(), systemDepartment.getParentId());
        return deptNum;
    }

    /**
     * 修改部门
     *
     * @param systemDepartment 部门
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemDepartment(SystemDepartment systemDepartment) {
        if (systemDepartment.getId().equals(systemDepartment.getParentId())) {
            throw new ServiceException("父级不能是本部门");
        }
        SystemDepartment oldSystemDepartment = systemDepartmentMapper.selectById(systemDepartment.getId());
        if (DATA_TYPE_SYSTEM.equals(oldSystemDepartment.getSystemType())) {
            // 部门是系统创建，不允许删除的
            oldSystemDepartment.setOrderNum(systemDepartment.getOrderNum());
            oldSystemDepartment.setDeptName(systemDepartment.getDeptName());
            oldSystemDepartment.setLeaderId(systemDepartment.getLeaderId());
            oldSystemDepartment.setRemark(systemDepartment.getRemark());
            oldSystemDepartment.setStatus(systemDepartment.getStatus());
            return systemDepartmentMapper.updateById(oldSystemDepartment);
        }
        // 非系统创建的部门
        SystemDeptRelation systemDeptRelation = systemDeptRelationMapper.selectUpParent(systemDepartment.getId());

        if (systemDeptRelation != null && !systemDeptRelation.getParentId().equals(systemDepartment.getParentId())) {
            // 父级有变化
            // 要先删除原有父级
            List<SystemDeptRelation> relationList = systemDeptRelationMapper.selectAllParents(systemDepartment.getId());
            for (SystemDeptRelation systemDeptRelation1 : relationList) {
                systemDeptRelationMapper.deleteById(systemDeptRelation1);
            }
            // 更新所有父级
            updateChildDept(systemDepartment.getId(), systemDepartment.getParentId());
        }
        return systemDepartmentMapper.updateById(systemDepartment);
    }

    /**
     * 批量删除部门
     *
     * @param ids 需要删除的部门主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDepartmentByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemDepartmentById(id);
        }
        return ids.length;
    }

    /**
     * 删除部门信息
     *
     * @param id 部门主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemDepartmentById(String id) {
        // 校验人员
        LambdaPrivernalWrapper<SystemUser> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(SystemUser::getDepartmentId, id);
        wrapper.eq(SystemUser::getDeletedFlag, DATA_DELETE_FLAG_NOT);
        Long size = systemUserMapper.selectCount(wrapper);
        Assert.isTrue(size == 0, "部门存在人员,不允许删除");

        // 校验是否有子部门
        LambdaPrivernalWrapper<SystemDeptRelation> wrapperDept = new LambdaPrivernalWrapper<>();
        wrapperDept.eq(SystemDeptRelation::getParentId, id);
        wrapperDept.eq(SystemDeptRelation::getDeletedFlag, DATA_DELETE_FLAG_NOT);
        Long sizeDept = systemDeptRelationMapper.selectCount(wrapperDept);
        Assert.isTrue(sizeDept == 0, "部门存在子部门,不允许删除");

        SystemDepartment oldSystemDepartment = systemDepartmentMapper.selectById(id);

        Assert.isTrue(!DATA_TYPE_SYSTEM.equals(oldSystemDepartment.getSystemType()), "部门为系统默认,不允许删除");

        // 删除服务部门
        List<SystemRoleUserDept> roleUserDeptList = systemRoleUserDeptMapper.selectSystemRoleUserDeptByDeptId(id);

        roleUserDeptList.forEach(roleUserDept -> {
            systemRoleUserDeptMapper.deleteFillById(roleUserDept.getId());
        });

        // 删除该部门自身的层级关系行（dept_id = 当前部门）：不清理会留下孤儿 relation，
        // 部门树按 relation inner join 组装虽不受影响，但行级数据范围与统计口径会被这些死行污染
        LambdaPrivernalWrapper<SystemDeptRelation> wrapperSelf = new LambdaPrivernalWrapper<>();
        wrapperSelf.eq(SystemDeptRelation::getDeptId, id);
        wrapperSelf.eq(SystemDeptRelation::getDeletedFlag, DATA_DELETE_FLAG_NOT);
        systemDeptRelationMapper.selectList(wrapperSelf).forEach(relation -> {
            systemDeptRelationMapper.deleteFillById(relation.getId());
        });

        return systemDepartmentMapper.deleteFillById(id);
    }

    /**
     * 更新父子关系
     *
     * @param id       当前部门ID
     * @param parentId 父ID
     */
    private void updateChildDept(String id, String parentId) {
        if (StringUtils.isNotEmpty(parentId)) {
            // 父节点不为空，更新所有父部门的子
            List<SystemDeptRelation> relationList = systemDeptRelationMapper.selectAllParents(parentId);
            if (relationList != null && !relationList.isEmpty()) {
                // 顶级部门的父ID查询不到部门，因为是组织的ID
                for (SystemDeptRelation relation : relationList) {
                    SystemDeptRelation target = new SystemDeptRelation();
                    BeanUtils.copyProperties(relation, target);
                    target.setId(null);
                    target.setDeptId(id);
                    target.setLevel(relation.getLevel() + 1);
                    target.setUpdatedBy(null);
                    target.setUpdatedTime(null);
                    target.setCreatedTime(new Date());
                    systemDeptRelationMapper.insert(target);
                }
            }
            // 设置父子关系
            SystemDeptRelation target = new SystemDeptRelation();
            target.setDeptId(id);
            target.setLevel(1L);
            target.setParentId(parentId);
            systemDeptRelationMapper.insert(target);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartmentDTO> selectSystemDepartmentList(List<String> deptList) {
        return systemDepartmentMapper.selectSystemDepartmentListByIds(deptList);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartment> selectDepartmentList(SystemDepartmentFilterDTO systemDepartment) {
        return systemDepartmentMapper.selectDepartmentList(systemDepartment);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartmentDTO> selectDepartmentByOrganizationId(SystemDepartmentFilterDTO systemDepartmentFilter) {
        return systemDepartmentMapper.selectDepartmentByOrganizationId(systemDepartmentFilter);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartmentDTO> selectSystemParentDeptById(String departmentId) {
        return systemDeptRelationMapper.selectAllParentList(departmentId);
    }
}
