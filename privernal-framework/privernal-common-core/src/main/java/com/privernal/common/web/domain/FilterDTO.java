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

package com.privernal.common.web.domain;


import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.Serializable;
import java.util.Map;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2021-09-21 12:31
 * @Description 公共查询字段
 */
@Tag(name = "公共查询字段")
public abstract class FilterDTO extends BaseDTO implements Serializable {

    /**
     * 模糊关键字
     */
    @Schema(description = "模糊关键字多个")
    private String[] keywords;

    /**
     * 排序的字段
     */
    @Schema(description = "排序的字段 如：name,age 多个逗号相隔")
    private String orderBy;

    /**
     * 排序的方向 asc/desc
     */
    @Schema(description = "排序的方向：asc升序 desc 降序")
    private String direction;

    /**
     * 当前记录起始索引
     */
    @Schema(description = "当前记录起始索引")
    private Integer pageNum;

    /**
     * 每页显示记录数
     */
    @Schema(description = "每页显示记录数")
    private Integer pageSize;


    /**
     * 请求参数,非特定参数
     */
    private Map<String, Object> parameters;


    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String[] getKeywords() {
        return keywords;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

}
