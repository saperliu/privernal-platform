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

package com.privernal.common.datascope.aspect;

import com.privernal.common.core.constant.RoleConstants;
import cn.dev33.satoken.exception.NotWebContextException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.dto.SystemRoleDataScopeDTO;
import com.privernal.system.dto.SystemUserServiceOrgDTO;
import com.privernal.system.dto.UserInfo;
import com.alibaba.fastjson2.JSON;
import com.alibaba.ttl.TransmittableThreadLocal;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.HexValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.util.cnfexpression.MultiAndExpression;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2022-02-13 21:01
 * @Description 数据权限过滤注解
 */
@Slf4j
@Aspect
@Component
public class PrivernalDataScopeAspect implements DataPermissionHandler {

    /**
     * 全部数据权限
     */
    public static final long DATA_SCOPE_ALL = 50;

    /**
     * 自定数据权限
     */
    public static final long DATA_SCOPE_CUSTOM = 40;

    /**
     * 部门及以下数据权限
     */
    public static final long DATA_SCOPE_ORG_AND_CHILD = 30;

    /**
     * 部门数据权限
     */
    public static final long DATA_SCOPE_ORG = 20;

    /**
     * 仅本人数据权限
     */
    public static final long DATA_SCOPE_SELF = 10;

    /**
     * 通过ThreadLocal记录权限相关的属性值
     */
    private static final ThreadLocal<DataScopeParam> threadLocal = new TransmittableThreadLocal<>();

    /**
     * 每一个mapper都做切面
     * DataScopeMapper 用来收入手写 SQL、无实体泛型的 mapper（动态表数据表）：
     * 它们此前不在切点内，只能走 getSqlSegment 的兜底分支，那里取不到表名，
     * 导致按表配置的角色数据范围（本部门/本人）被整段跳过
     */
    @Pointcut("execution(* com.privernal..PrivernalMapper+.*(..)) || execution(* com.privernal..DataScopeMapper+.*(..))")
    public void mapperPointcut() {
    }

    @Before("mapperPointcut()")
    public void beforeMapperMethodExecution(JoinPoint joinPoint) {
        handleDataScope(joinPoint);
    }

    /**
     * 增加组织ID的忽略注解切面。
     * 如果类上或是方法上存在IgnoreDataScope注解，则不进行组织的区分。
     * 阻止组织的条件的附加，并不能组织部门条件的附加。
     */
    @Pointcut("@annotation(com.privernal.common.datascope.annotation.IgnoreDataScope)")
    public void dataScopeIgnorePointCut() {
    }

    @Before("dataScopeIgnorePointCut()")
    public void doIgnoreBefore(JoinPoint point) {
        handleIgnore(point);
    }

    /**
     * 清空当前线程上次保存的权限信息
     */
    @After("dataScopeIgnorePointCut()")
    public void clearIgnoreThreadLocal() {
        threadLocal.remove();
    }

    @After("mapperPointcut()")
    public void clearMapperThreadLocal() {
        threadLocal.remove();
    }

    protected void handleIgnore(final JoinPoint joinPoint) {
        IgnoreDataScope ignoreDataScope = getAnnotationIgnore(joinPoint);
        if (ignoreDataScope != null) {
            Signature signature = joinPoint.getSignature();
            MethodSignature methodSignature = (MethodSignature) signature;
            Method method = methodSignature.getMethod();
            IgnoreStrategy cache = DataScopeHelper.initSqlParserInfoCache(signature.getDeclaringType());
            // 加入 注解过滤缓存
            DataScopeHelper.initSqlParserInfoCache(cache, signature.getDeclaringType().getName(), method);
        }
    }

    protected void handleDataScope(final JoinPoint joinPoint) {

        try {
            // 当前线程设置了忽略部门权限过滤, 设置了忽略组织则部门也忽略掉
            // 这是忽略的是在代码层次里，直接使用 OrganizationContextHolder 进行组织ID和部门的忽略。
            if (DataScopeHelper.getIgnoreDataPermission() || DataScopeHelper.getIgnoreOrganization()) {
                return;
            }
            // 增加了忽略部门权限过滤注解
            IgnoreDataScope ignoreDataScope = getAnnotationIgnore(joinPoint);
            if (ignoreDataScope != null) {
                return;
            }
            UserInfo userInfo = SecurityUtils.getUserInfo();
            if (StringUtils.isNotNull(userInfo)) {
                // 获得注解
                PrivernalDataScopeDTO privernalDataScope = getAnnotationLog(joinPoint);
                // 没有获取注解，方法和类上面都没有，直接返回null, 直接注解忽略也直接返回
                if (privernalDataScope == null || privernalDataScope.ignoreDepartment) {
                    return;
                }
                log.debug("----  permissionOperation {} ", JSON.toJSONString(privernalDataScope));
                dataScopeFilter(joinPoint, userInfo, privernalDataScope.getMainAlias(), privernalDataScope.getPermission(), privernalDataScope.getDeptField());
            }
        } catch (NotWebContextException exception) {
            log.error(" not web env " + exception.getMessage());
        }
    }

    /**
     * 是否存在注解，如果存在就获取
     */
    private PrivernalDataScopeDTO getAnnotationLog(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        log.debug("----signature---  {}   ", JSON.toJSONString(signature));

        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();

        PrivernalDataScopeDTO privernalDataScopeDTO = new PrivernalDataScopeDTO();
        PrivernalDataScope dataScope = method.getAnnotation(PrivernalDataScope.class);
        if (dataScope != null) {
            // 方法上有注解，则以方法上优先，返回方法上的注解
            privernalDataScopeDTO.setPermission(dataScope.permission());
            privernalDataScopeDTO.setPermissionOperation(dataScope.permissionOperation());
            privernalDataScopeDTO.setPermissionMain(dataScope.permissionMain());
            privernalDataScopeDTO.setDeptField(dataScope.deptField());
            privernalDataScopeDTO.setMainAlias(dataScope.mainAlias());
            privernalDataScopeDTO.setIgnoreDepartment(dataScope.ignoreDepartment());
            return privernalDataScopeDTO;
        } else {
            // 获取接口上的统一的注解。
            PrivernalDataScope privernalDataScopeClass = AnnotationUtils.findAnnotation(signature.getDeclaringType(), PrivernalDataScope.class);
            log.debug("----privernalDataScope1---  {}   ", JSON.toJSONString(privernalDataScopeClass));
            if (privernalDataScopeClass != null) {

                Object[] args = joinPoint.getArgs(); // 获取所有参数
                Object firstArgument ="";
                if (args.length > 0) {
                     firstArgument = args[0]; // 获取第一个参数
                }
                return getDataScopeParam(privernalDataScopeDTO, privernalDataScopeClass, method.getName(), firstArgument.toString());
            }
        }
        return null;
    }

    private PrivernalDataScopeDTO getDataScopeParam(PrivernalDataScopeDTO privernalDataScopeDTO, PrivernalDataScope privernalDataScopeClass, String methodName,String tableCode) {
        privernalDataScopeDTO.setPermission(privernalDataScopeClass.permission());
        privernalDataScopeDTO.setPermissionOperation(privernalDataScopeClass.permissionOperation());
        privernalDataScopeDTO.setPermissionMain(privernalDataScopeClass.permissionMain());
        privernalDataScopeDTO.setDeptField(privernalDataScopeClass.deptField());
        privernalDataScopeDTO.setMainAlias(privernalDataScopeClass.mainAlias());
        privernalDataScopeDTO.setIgnoreDepartment(privernalDataScopeClass.ignoreDepartment());
        // 类上有注解，取此注解
        String permissionMain = privernalDataScopeClass.permissionMain();
        String permissionOne = privernalDataScopeClass.permissionOne();
        log.debug("----privernalDataScope1--permissionMain-  {}   ", permissionMain);
        if (StringUtils.isNotEmpty(permissionMain)) {
            if (StringUtils.isNotEmpty(privernalDataScopeClass.permissionOperation())) {
                privernalDataScopeDTO.setPermission(permissionMain + ":" + privernalDataScopeClass.permissionOperation());
            } else {
                // 没有注解，权限标识，需要组装
                if (StringUtils.isNotEmpty(methodName)) {
                    if (methodName.contains("select") || methodName.contains("Page") || methodName.contains("check")) {
                        privernalDataScopeDTO.setPermission(permissionMain + ":list");
                    }
                    if (methodName.contains("update")) {
                        privernalDataScopeDTO.setPermission(permissionMain + ":edit");
                    }
                    if (methodName.contains("insert")) {
                        privernalDataScopeDTO.setPermission(permissionMain + ":add");
                    }
                    if (methodName.contains("delete")) {
                        privernalDataScopeDTO.setPermission(permissionMain + ":remove");
                    }
                }
            }
            return privernalDataScopeDTO;
        }
        if (StringUtils.isNotEmpty(permissionOne)) {
            // 权限码格式必须与菜单授权一致：{permissionOne}{表名}:{操作}
            // tableCode 由切面从 mapper 方法的第一个参数取到（动态表数据 mapper 各方法均为 @Param("tableName")）
            // 方法名不含关键字时按 list 兜底，否则 countData/countByColumnValue/querySql 这类统计方法会漏掉数据范围，
            // 出现「列表已按本部门过滤、总数却是全组织」的分页错乱
            if (StringUtils.isNotEmpty(methodName)) {
                String operation;
                if (methodName.contains("update")) {
                    operation = "edit";
                } else if (methodName.contains("insert")) {
                    operation = "add";
                } else if (methodName.contains("delete")) {
                    operation = "remove";
                } else {
                    operation = "list";
                }
                privernalDataScopeDTO.setPermission(permissionOne + tableCode + ":" + operation);
            }

            return privernalDataScopeDTO;
        }
        return null;
    }

    /**
     * 是否存在注解，如果存在就获取
     */
    private IgnoreDataScope getAnnotationIgnore(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();

        if (method != null) {
            return method.getAnnotation(IgnoreDataScope.class);
        }
        return null;
    }

    /**
     * @param where             原SQL Where 条件表达式
     * @param mappedStatementId Mapper接口方法ID
     * @return
     */
    @SneakyThrows
    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {

        // 平台数据权限只作用于自家 mapper（com.privernal 包）；
        // 第三方库 mapper（如 warm-flow 工作流引擎 org.dromara.warm.flow）无平台注解体系，
        // 直接放行，避免 WHERE 被替换为 1=2 以及线程残留权限参数污染引擎 SQL
        if (mappedStatementId == null || !mappedStatementId.startsWith("com.privernal.")) {
            return where;
        }

        DataScopeParam dataScopeParam = threadLocal.get();

        log.debug("DataScopePermissionHandler  mappedStatementId {}  dataScopeParam: {}", mappedStatementId, JSON.toJSONString(dataScopeParam));

        try {
            // 未获取到权限注解。4种情况。因为第1种情况的存在，所以需要再次校验一遍。
            // 1、PrivernalMapper里的方法没有添加数据范围注解。需要重新获取。实际的Mapper直接使用PrivernalMapper的方法，则会获取不到注解。
            // 2、实际的mapper的方法和类上都没有注解。这时通过再次获取注解来判断。因为如果是第1种情况是存在。如果这里判断还是同样情况，则返回权限不足。代码是有问题的。
            // 3、代码层次增加了忽略组织和部门
            // 4、方法或是类上面注解了 IgnoreDataScope
            // 如果不为null, 则是调用的mapper的方法或类上肯定增加了 PrivernalDataScope 注解
            if (dataScopeParam == null) {

                log.debug("----DataScopeHelper.getIgnoreDataPermission()---  [{}]  [{}]  ", DataScopeHelper.getIgnoreDataPermission(), Thread.currentThread().getId());
                log.debug("----DataScopeHelper.getIgnoreOrganization()---  [{}]   ", DataScopeHelper.getIgnoreOrganization());
                log.debug("----OrganizationContextHolder.getIgnoreOrganization()---  [{}]   ", OrganizationContextHolder.isIgnore());
                // 当前线程设置了忽略部门权限过滤, 设置了忽略组织则部门也忽略掉
                // 代码层次的处理，再次放在这里，优先处理
                if (DataScopeHelper.getIgnoreDataPermission() || DataScopeHelper.getIgnoreOrganization()) {
                    return where;
                }
                //获取执行的方法
                String mapperName = mappedStatementId.substring(0, mappedStatementId.lastIndexOf("."));
                log.debug("----mapperName---  [{}]   ", JSON.toJSONString(mapperName));

                String methodName = mappedStatementId.substring(mappedStatementId.lastIndexOf(".") + 1);

                // 获取类对象
                Class<?> clazz = Class.forName(mapperName);
                log.debug("----clazz--- [{}]   ", JSON.toJSONString(clazz));
                log.debug("----methodName--- [{}]   ", JSON.toJSONString(methodName));
                // 获取方法上是否有注解 IgnoreDataScope
                // 如果mapper直接调用的PrivernalMapper的方法，则会报错 NoSuchMethodException
                Method method = getClassMethod(clazz, methodName);
                if(method == null){
                    // 获取对象的方法不存在，
                    // 1、list方法的分页时候，会有一个方法名_COUNT的方法，这个是不存的，但mybatis_plus会有。
                    // 去掉方法名_COUNT的_COUNT后缀
                    methodName = methodName.substring(0, methodName.lastIndexOf("_"));
                    method = getClassMethod(clazz, methodName);
                }
                // 增加了忽略部门权限过滤注解，直接返回where 条件，不再增加附加的部门条件
                IgnoreDataScope ignoreDataScope = method.getAnnotation(IgnoreDataScope.class);
                if (ignoreDataScope != null) {
                    return where;
                }

                // mapper类上增加了IgnoreDataScope的注解，也直接返回where 条件，不再增加附加的部门条件
                if (clazz.isAnnotationPresent(IgnoreDataScope.class)) {
                    return where;
                }

                // 检查是否有指定的注解
                if (clazz.isAnnotationPresent(PrivernalDataScope.class)) {
                    // 获取注解实例
                    PrivernalDataScope privernalDataScopeClass = clazz.getAnnotation(PrivernalDataScope.class);

                    log.debug("----annotation---  {}   ", JSON.toJSONString(privernalDataScopeClass));

                    //如果类上注解了忽略部门权限过滤，则直接返回where 条件，不再增加附加的部门条件
                    if (privernalDataScopeClass.ignoreDepartment()) {
                        return where;
                    }

                    log.debug("----methodName--- [{}]   ", JSON.toJSONString(methodName));
                    method = getClassMethod(clazz, methodName);
                    // 使用注解，根据方法名称，判断是否使用了注解。
                    // 有注解并且是忽略部门，则直接返回where 条件，不再增加附加的部门条件
                    // 如果mapper直接调用的PrivernalMapper的方法，则会报错 NoSuchMethodException
                    PrivernalDataScope ignoreDept = method.getAnnotation(PrivernalDataScope.class);
                    if (ignoreDept != null && ignoreDept.ignoreDepartment()) {
                        return where;
                    }

                    PrivernalDataScopeDTO privernalDataScopeDTO = new PrivernalDataScopeDTO();
                    privernalDataScopeDTO = getDataScopeParam(privernalDataScopeDTO, privernalDataScopeClass, methodName,"");
                    if (privernalDataScopeDTO != null) {
                        try {
                            UserInfo userInfo = SecurityUtils.getUserInfo();
                            if (userInfo != null) {
                                log.debug("----privernalDataScopeDTO  2 ---  {}   ", JSON.toJSONString(privernalDataScopeDTO));
                                dataScopeFilter(null, userInfo, privernalDataScopeDTO.getMainAlias(), privernalDataScopeDTO.getPermission(), privernalDataScopeDTO.getDeptField());
                                dataScopeParam = threadLocal.get();
                            } else {
                                // 类上注解没问题，但是未获取到session上的用户信息
                                where = new HexValue(" 1 = 4 ");
                                return where;
                            }
                        } catch (NotWebContextException e) {
                            log.error("---------NotWebContextException privernalDataScope--------  {}", e.getMessage());
                            // 消息的自动任务，这时取不到web环境，会报错。直接返回where,不处理。
                            return where;
                        }
                    } else {
                        // 类上的注解没有配置 permissionMain 的值
                        where = new HexValue(" 1 = 3 ");
                        return where;
                    }

                } else {
                    // 类上没有注解，代码错误，直接返回不成立的条件。
                    where = new HexValue(" 1 = 2 ");
                    return where;
                }
            }
        } catch (Exception e) {
            // S19：数据范围装配异常时必须 fail-closed——吞掉异常后放行原 where，
            // 等于一次装配故障即绕过行级权限。回绝（1=4）与既有 deny 口径一致；
            // NotWebContextException（无 web 环境的系统任务）在上方分支单独按原样放行
            log.error("---------dataScopeParam == null--------", e);
            where = new HexValue(" 1 = 4 ");
        }

        log.debug("----dataScopeParam  2 ---  {}   ", JSON.toJSONString(dataScopeParam));
        if (dataScopeParam == null || dataScopeParam.dataScope == DATA_SCOPE_ALL) {
            // 如果是全部权限，则直接返回条件，不再另加部门权限的过滤条件，相当于查询当前组织的所有部门的数据
            return where;
        }

        if (where == null) {
            where = new HexValue(" 1 = 1 ");
        }

        String deptSql = "".equals(dataScopeParam.orgAlias) ? dataScopeParam.orgField : dataScopeParam.orgAlias + "." + dataScopeParam.orgField;
        String userSql = "".equals(dataScopeParam.orgAlias) ? dataScopeParam.userField : dataScopeParam.orgAlias + "." + dataScopeParam.userField;

        // 把集合转变为JSQLParser需要的元素列表
        InExpression inExpression = null;
        OrExpression orSelfExpression = null;
        AndExpression andExpression = null;
        if (CollectionUtils.isNotEmpty(dataScopeParam.allOrg)) {
            // 组织的数据权限；看到这些部门的数据，被授权的所有部门。
            //默认添加组织的ID。因为部门为组织ID的数据是共享的数据，所有部门都可以看到。
            dataScopeParam.allOrg.add(SecurityUtils.getOrganizationId());

            ExpressionList itemsList = new ExpressionList(dataScopeParam.allOrg.stream().map(StringValue::new).collect(Collectors.toList()));
            // 给表达式2边加括号
            ParenthesedExpressionList parenthesis = new ParenthesedExpressionList(itemsList);
            inExpression = new InExpression(new Column(deptSql), parenthesis);
        }
        if (dataScopeParam.dataScope == DATA_SCOPE_SELF) {
            List<Expression> list = new ArrayList<>();
//            在这些部门里，只能看到自己创建的数据
            if (CollectionUtils.isNotEmpty(dataScopeParam.selfOrg)) {
                ExpressionList itemsList = new ExpressionList(dataScopeParam.selfOrg.stream().map(StringValue::new).collect(Collectors.toList()));
                // 给表达式2边加括号
                ParenthesedExpressionList parenthesis = new ParenthesedExpressionList(itemsList);
                InExpression inSelfExpression = new InExpression(new Column(deptSql), parenthesis);
                list.add(inSelfExpression);
            }

            // 只有自己创建的数据
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column(userSql));
            equalsTo.setRightExpression(new StringValue(dataScopeParam.getUserId()));
            list.add(equalsTo);
            Expression expression = new MultiAndExpression(list);

            if (inExpression != null) {
                // 组织里可以看部门的、自己创建的且在授权的部门里的。共享数据放在了部门里。
                orSelfExpression = new OrExpression(inExpression, expression);
                log.debug("orSelfExpression = {}", orSelfExpression);

                return new AndExpression(where, orSelfExpression);
            } else {
                log.debug("andExpression = {}", andExpression);
                // 组织里只能看自己的数据，没有其它权限。但包含一个共享的数据权限
                List<Expression> listTwo = new ArrayList<>();

                // 部门等于组织ID的数据为共享数据【这里是系统默认的特殊处理。将部门的ID设置为组织ID就是全组织共享的数据。组织与部门并不是同一张表，ID不会冲突】
                EqualsTo equalsToCommon = new EqualsTo();
                equalsToCommon.setLeftExpression(new Column(deptSql));
                equalsToCommon.setRightExpression(new StringValue(SecurityUtils.getOrganizationId()));
               // 或者创建人是自己的数据。
                orSelfExpression = new OrExpression(equalsToCommon, expression);

                // 两个条件或关系。
                listTwo.add(orSelfExpression);
                // 两个条件再加上括号。
                Expression expressionTwo = new MultiAndExpression(listTwo);
                // 再与where条件做and 关系。
                return new AndExpression(where, expressionTwo);
            }
        }

        log.debug("where = {}", where);
        log.debug("inExpression = {}", inExpression);

        if (inExpression == null) {
            // 有部门的权限，但没有一个部门数据。此情况：在用户的权限上设置的是自己，然后其角色上设置的是部门，但并没有授权相关部门。
            return where;
        }
        return new AndExpression(where, inExpression);
    }

    Method getClassMethod(Class<?> clazz, String methodName) {
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                log.debug("----Found methodName------------- [{}]   ", method.getName());
                return method;
            }
        }
        return null;
    }

    /**
     * 数据范围过滤
     *
     * @param joinPoint 切点
     * @param userInfo  用户
     * @param mainAlias 主表别名
     */
    public void dataScopeFilter(JoinPoint joinPoint, UserInfo userInfo, String mainAlias, String permission, String deptField) {
        Set<String> orgAllSet = new HashSet<>();
        Set<String> orgSelfSet = new HashSet<>();

        long dataScope = DATA_SCOPE_SELF;//默认只查询自己的数据
        // 查询所有授权的组织，组织是全表单的,查找当前组织的授权情况
        // 组织授权列表可能为空（临时 token、未经 getInfo 刷新的旧会话等），为空时按默认"只查自己"处理，不能直接 NPE 500
        List<SystemUserServiceOrgDTO> userServiceOrgDTOList = userInfo.getUserServiceOrgDTOList();
        if (userServiceOrgDTOList != null) {
            for (SystemUserServiceOrgDTO userServiceOrgDTO : userServiceOrgDTOList) {
                if (DATA_SCOPE_ALL == userServiceOrgDTO.getDataScope() && userServiceOrgDTO.getOrganizationId().equals(userInfo.getOrganizationId())) {
                    dataScope = DATA_SCOPE_ALL;
                }
                // 本部门及以下部门
                if (DATA_SCOPE_ORG_AND_CHILD == userServiceOrgDTO.getDataScope() && userServiceOrgDTO.getOrganizationId().equals(userInfo.getOrganizationId())) {
                    // 如果是此数据范围，在查询UserServiceOrgDTOList时已经查询子部门，将部门权限转为 DATA_SCOPE_ORG
                    orgAllSet.add(userServiceOrgDTO.getDepartmentId());
                    dataScope = DATA_SCOPE_ORG_AND_CHILD;
                }
                // 本部门
                if (DATA_SCOPE_ORG == userServiceOrgDTO.getDataScope() && userServiceOrgDTO.getOrganizationId().equals(userInfo.getOrganizationId())) {
                    orgAllSet.add(userServiceOrgDTO.getDepartmentId());
                    dataScope = DATA_SCOPE_ORG;
                }
                // 只看自己数据
                if (DATA_SCOPE_SELF == userServiceOrgDTO.getDataScope() && userServiceOrgDTO.getOrganizationId().equals(userInfo.getOrganizationId())) {
                    orgSelfSet.add(userServiceOrgDTO.getDepartmentId());
                    dataScope = DATA_SCOPE_SELF;
                }
            }
        }

        log.debug("DataScopePermissionHandler  permission {} dataScope {} userInfo.getFunctionDeptList(): {}", permission, dataScope, JSON.toJSONString(userInfo.getFunctionDeptList()));
        // 查询拥有的角色，且角色有的模块，然后模块给予的组织数据权限。
        // 如果用户本身设置了ALL，则不再拼接部门的数据权限
        if (userInfo.getFunctionDeptList() != null && dataScope != DATA_SCOPE_ALL) {
            for (SystemRoleDataScopeDTO systemRoleDataScopeDTO : userInfo.getFunctionDeptList()) {
                if (RoleConstants.SUPER_ALL_PERMISSION.equals(systemRoleDataScopeDTO.getPermission())) {
                    dataScope = DATA_SCOPE_ALL;
                    break;
                }

                // 同一个功能【permission】不会出现多个数据范围。
                // 因为多个应用，会造成同一个【permission】出现多个数据范围，以最大的范围为准
                // 如果是全部，则直接是全部。
                // 如果是本部门，及子部门。则是所有部门的叠加。
                if (systemRoleDataScopeDTO.getPermission() != null && systemRoleDataScopeDTO.getPermission().equals(permission) && systemRoleDataScopeDTO.getOrganizationId() != null && systemRoleDataScopeDTO.getOrganizationId().equals(userInfo.getOrganizationId())) {
                    // 此功能的数据范围是“全部”
                    if (systemRoleDataScopeDTO.getDataScope() != null && DATA_SCOPE_ALL == systemRoleDataScopeDTO.getDataScope()) {
                        dataScope = DATA_SCOPE_ALL;
                        break;
                    }
                    if (systemRoleDataScopeDTO.getDataScope() != null && DATA_SCOPE_ORG == systemRoleDataScopeDTO.getDataScope()) {
                        // 此功能单独设置了数据范围权限
                        dataScope = systemRoleDataScopeDTO.getDataScope();
                        if (StringUtils.isNotEmpty(systemRoleDataScopeDTO.getDepartmentId())) {
                            orgAllSet.add(systemRoleDataScopeDTO.getDepartmentId());
                        }
                    }
                    if (systemRoleDataScopeDTO.getDataScope() != null && DATA_SCOPE_SELF == systemRoleDataScopeDTO.getDataScope()) {
                        if (dataScope != DATA_SCOPE_ORG) {
                            dataScope = systemRoleDataScopeDTO.getDataScope();
                            if (StringUtils.isNotEmpty(systemRoleDataScopeDTO.getDepartmentId())) {
                                orgSelfSet.add(systemRoleDataScopeDTO.getDepartmentId());
                            }
                        }
                    }
                }
            }
        }

        DataScopeParam dataScopeParam = new DataScopeParam(dataScope, mainAlias, deptField, "created_by", userInfo.getUserId(), orgSelfSet, orgAllSet);
        threadLocal.set(dataScopeParam);

    }

    /**
     * ThreadLocal存储对象
     */
    @Data
    @AllArgsConstructor
    static class DataScopeParam {

        /**
         * 数据权限范围
         */
        private Long dataScope;
        /**
         * 组织表的别名
         */
        private String orgAlias;

        /**
         * 组织字段名
         */
        private String orgField;

        /**
         * 用户字段名
         */
        private String userField;

        /**
         * 当前登录用户的ID
         */
        private String userId;

        /**
         * 数据权限范围
         */
        private Set<String> selfOrg;

        /**
         * 数据权限范围
         */
        private Set<String> allOrg;
    }

}
