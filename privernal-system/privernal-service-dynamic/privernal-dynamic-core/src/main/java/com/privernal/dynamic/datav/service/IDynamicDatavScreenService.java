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

import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.datav.domain.DynamicDatavVersion;

import java.util.List;

/**
 * 数据大屏服务
 *
 * @author saperliu
 */
public interface IDynamicDatavScreenService {

    /**
     * 保存大屏（编码为空则新增并生成编码），同时落一个历史版本
     */
    DynamicDatavScreen saveDatavScreen(DynamicDatavScreen screen);

    /**
     * 按编码查询（含 schema）
     */
    DynamicDatavScreen findByCode(String screenCode);

    /**
     * 列表（不含 schema，避免一次拖进全部画布 JSON）
     */
    List<DynamicDatavScreen> listScreens(String appId);

    /**
     * 删除
     */
    int deleteByCode(String screenCode);

    /**
     * 发布（1）/ 下线（2）/ 转草稿（0）
     */
    DynamicDatavScreen changeStatus(String screenCode, Integer status);

    /**
     * 复制为新草稿
     */
    DynamicDatavScreen copyScreen(String screenCode);

    /**
     * 版本列表（不含 schema）
     */
    List<DynamicDatavVersion> listVersions(String screenCode);

    /**
     * 按版本号取单个版本（含 schema，供版本对比用）；不存在抛业务异常
     */
    DynamicDatavVersion findVersion(String screenCode, Integer versionNo);

    /**
     * 最近一次发布快照的 schema；从未发布过返回 null（调用方回落实时 schema）
     */
    String findPublishedSchemaJson(String screenCode);

    /**
     * 回滚到指定版本（回滚本身也记一次新版本）
     */
    DynamicDatavScreen rollback(String screenCode, Integer versionNo);

    /**
     * 追加一块 AI 图表 widget（B8-M3-3b）：服务端读改写 schema_json（整包覆盖会让并发编辑
     * 互相丢组件），追加到画布空白区右侧顺排、不自动重排既有组件，走保存链落历史版本。
     * screenCode 精确定位；只给 screenName 时按名称精确匹配（重名拒绝）。datasetCode 必须已落库。
     */
    DynamicDatavScreen appendWidget(String screenCode, String screenName, String datasetCode,
                                    com.alibaba.fastjson2.JSONObject reportConfig, String reportType, String title);
}
