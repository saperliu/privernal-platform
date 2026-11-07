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

package com.privernal.dynamic.datav.service;

import com.privernal.dynamic.datav.domain.DynamicDatavAsset;

import java.util.List;

/**
 * 大屏素材服务
 *
 * @author saperliu
 */
public interface IDynamicDatavAssetService {

    /**
     * 登记素材元数据（文件已由前端上传到文件服务，这里只登记 URL 与分类）
     *
     * @return 带生成 assetCode 的完整行
     */
    DynamicDatavAsset saveAsset(DynamicDatavAsset asset);

    /**
     * 素材列表：分类与关键字（名称/原始文件名）可选过滤，按更新时间倒序
     */
    List<DynamicDatavAsset> listAssets(String category, String keyword);

    /**
     * 删除素材（带引用保护：file_url 仍出现在任何大屏 schema 里则拒绝）
     *
     * @return 删除行数
     * @throws com.privernal.common.core.exception.ServiceException 素材不存在或仍被引用
     */
    int deleteByCode(String assetCode);

    /**
     * 大屏 schema_json 里对 URL 做 LIKE 匹配时的转义：URL 自带 % _ \ 时
     * 不转义就会把通配符当字面量匹配，出现「删不掉也查不准」的引用判定。
     * 独立成静态方法是为了可单测——LIKE 转义是这类删除保护唯一容易悄悄写错的地方。
     */
    static String escapeLike(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
