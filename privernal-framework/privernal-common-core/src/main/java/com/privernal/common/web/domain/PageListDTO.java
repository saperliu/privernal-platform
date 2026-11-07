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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页列表数据载体：{@code list + total}。
 *
 * <p>替代原 {@code web.page.TableData}：字段与 JSON 结构保持一致
 * （{@code {"list": [...], "total": n}}），不再挂在旧分页模型的包下。</p>
 *
 * @author saperliu
 */
public class PageListDTO<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 列表数据
     */
    private List<T> list;

    public PageListDTO() {
        this.list = new ArrayList<>();
    }

    /**
     * @param list  列表数据
     * @param total 总记录数
     */
    @SuppressWarnings("unchecked")
    public PageListDTO(List<? extends T> list, long total) {
        this.list = (List<T>) list;
        this.total = total;
    }

    /**
     * 空列表 + 指定总数（总数来自别处的统计时使用）
     */
    public PageListDTO(Long total) {
        this.list = new ArrayList<>();
        if (total != null) {
            this.total = total;
        }
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getList() {
        return list;
    }

    /**
     * 接受 {@code List<? extends T>}：调用方常见「先拿实体分页、再用 DTO 列表覆盖」的用法
     * （{@code PageListDTO<Object>} 场景下任意元素类型都能直接塞入），存入的是同一引用。
     */
    @SuppressWarnings("unchecked")
    public void setList(List<? extends T> list) {
        this.list = (List<T>) list;
    }

    /**
     * @param list  列表数据
     * @param total 总记录数
     */
    @SuppressWarnings("unchecked")
    public static <T> PageListDTO<T> of(List<? extends T> list, long total) {
        return new PageListDTO<>(list, total);
    }

    public static <T> PageListDTO<T> empty() {
        return new PageListDTO<>(0L);
    }
}
