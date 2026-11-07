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

package com.privernal.dynamic.dbs.util;

import com.privernal.dynamic.dba.vo.PageResultInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.ArrayList;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-09-06 15:00
 * @Description
 */
public class PageUtil {

    public static void transferPageObject(PageResultInfo pageResultInfo, IPage<?> page) {

        pageResultInfo.setList(new ArrayList<>(page.getRecords()));
        pageResultInfo.setTotal(page.getTotal());
        pageResultInfo.setPageNum(page.getCurrent());
        pageResultInfo.setPageSize(page.getSize());

        long pageSize = page.getSize();
        if (pageSize <= 0) {
            return;
        }
        long totalPage = (page.getTotal() + pageSize - 1) / pageSize;

        if (totalPage == page.getCurrent()) {
            pageResultInfo.setLastPage(true);
        }
        if (page.getCurrent() == 1) {
            pageResultInfo.setFirstPage(true);
        }
    }
}
