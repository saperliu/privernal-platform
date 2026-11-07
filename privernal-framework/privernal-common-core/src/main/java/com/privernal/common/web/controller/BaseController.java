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

package com.privernal.common.web.controller;

import com.privernal.common.core.utils.DateUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.sql.SqlUtil;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.FilterDTO;
import com.privernal.common.web.domain.PageListDTO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;
import java.util.Date;
import java.util.List;

/**
 * web层通用数据处理。分页入口统一为 {@link FilterDTO} 载体
 * （pageNum/pageSize/orderBy/direction），分页结果统一为
 * {@link CommonResult} 包装的 {@link PageListDTO}。
 *
 * @author saperliu
 */
public class BaseController {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 将前台传递过来的日期格式的字符串，自动转化为Date类型
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // Date 类型转换
        binder.registerCustomEditor(Date.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(DateUtils.parseDate(text));
            }
        });
    }

    /**
     * 按 {@link FilterDTO} 载体启动 PageHelper 分页。
     *
     * <p>pageNum/pageSize 任一缺省时不动分页（返回全量），与前端「不传分页参数即查全量」的约定一致。</p>
     */
    protected void startPage(FilterDTO filterDTO) {
        if (filterDTO != null
                && StringUtils.isNotNull(filterDTO.getPageNum())
                && StringUtils.isNotNull(filterDTO.getPageSize())) {
            PageHelper.startPage(filterDTO.getPageNum(), clampPageSize(filterDTO.getPageSize()),
                    resolveOrderBy(filterDTO.getOrderBy(), filterDTO.getDirection()));
        }
    }

    /**
     * 显式参数启动分页：供查询载体未继承 {@link FilterDTO} 的过渡期端点使用，
     * 字段名与方向仍走同一道注入闸门。
     */
    protected void startPage(Integer pageNum, Integer pageSize, String orderBy, String direction) {
        if (StringUtils.isNotNull(pageNum) && StringUtils.isNotNull(pageSize)) {
            PageHelper.startPage(pageNum, clampPageSize(pageSize), resolveOrderBy(orderBy, direction));
        }
    }

    /**
     * 新-6：pageSize 无上限等于把"分页查询"变成"全表拉取"——一个请求即可拖垮服务。
     * 前端合法分页远小于该值；确有大批量导出需求的走导出链路。
     */
    private static final int MAX_PAGE_SIZE = 500;

    protected Integer clampPageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return pageSize;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 包装分页查询结果：{@code {"code":200,"message":"查询成功","data":{"list":[...],"total":n}}}。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected CommonResult<PageListDTO<Object>> getDataTable(List<?> list) {
        PageListDTO<Object> page = new PageListDTO<>();
        page.setList((List<Object>) list);
        page.setTotal(new PageInfo<>(list).getTotal());
        return CommonResult.ok(page, "查询成功");
    }

    /**
     * 响应返回结果
     *
     * @param rows 影响行数
     * @return 操作结果
     */
    protected CommonResult<Object> toAjax(int rows) {
        return rows > 0 ? CommonResult.ok() : CommonResult.fail();
    }

    /**
     * 响应返回结果
     *
     * @param result 结果
     * @return 操作结果
     */
    protected CommonResult<Object> toAjax(boolean result) {
        return result ? success() : error();
    }

    /**
     * 返回成功
     */
    public CommonResult<Object> success() {
        return CommonResult.ok();
    }

    /**
     * 返回失败消息
     */
    public CommonResult<Object> error() {
        return CommonResult.fail();
    }

    /**
     * 返回成功消息
     */
    public CommonResult<Object> success(String message) {
        return CommonResult.ok(message);
    }

    /**
     * 返回失败消息
     */
    public CommonResult<Object> error(String message) {
        return CommonResult.fail(message);
    }

    /**
     * 拼出 order by 片段并整体校验。
     *
     * <p>字段名与方向必须<b>合成一个串再过滤</b>：两者都是请求带来的输入，
     * 只校验字段名而把方向原样拼进去等于绕过闸门——{@code order by} 无法用占位符绑定，
     * 这里是注入链路上唯一的机会。</p>
     */
    protected static String resolveOrderBy(String orderBy, String direction) {
        return SqlUtil.escapeOrderBySql(StringUtils.isEmpty(direction)
                ? orderBy
                : orderBy + " " + direction);
    }
}
