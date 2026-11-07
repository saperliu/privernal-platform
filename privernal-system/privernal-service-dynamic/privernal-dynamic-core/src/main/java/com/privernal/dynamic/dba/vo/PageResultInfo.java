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

package com.privernal.dynamic.dba.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 数据查询的分页
 *
 * @author saperliu
 * @date 2023-02-12
 */
@Data
public class PageResultInfo implements Serializable {

    /**
     * 从1开始为是第一页
     */
    private long pageNum;
    private long pageSize;
    private long total;
    private long pages;
    private List<Object> list;
    private boolean isFirstPage = false;
    private boolean isLastPage = false;

}
