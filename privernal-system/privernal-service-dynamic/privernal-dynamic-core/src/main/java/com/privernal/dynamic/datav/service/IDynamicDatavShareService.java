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

import com.privernal.dynamic.datav.domain.DynamicDatavShare;

import java.util.List;

/**
 * 大屏外链分享服务
 *
 * @author saperliu
 */
public interface IDynamicDatavShareService {

    /**
     * 创建分享（要求大屏已发布）。返回带 shareCode 的完整行，前端用
     * {@link DatavShareSigns#signOf(String, String)} 同口径拿不到签名——签名由
     * 创建响应一并返回（sign 字段是计算值，不落库）。
     */
    CreateShareResult createShare(String screenCode, Integer expireHours, String refererWhitelist);

    /**
     * 某块大屏的分享列表（管理视图，不含 sign——sign 只在创建时返回一次，
     * 丢了就重建一个分享，反正旧链接可随时禁用）
     */
    List<DynamicDatavShare> listShares(String screenCode);

    /**
     * 禁用分享（吊销）。禁用后链接立即失效，行保留作审计
     */
    void disableShare(String shareCode);

    /**
     * 免登校验链：存在 + enabled + 签名常量时间相等 + 未过期 + referer 白名单。
     * 任一失败统一抛同一句「分享链接不存在或已失效」——不区分原因，防探测枚举。
     *
     * @param shareCode 链接里的 sc
     * @param sign      链接里的 sign
     * @param referer   请求头 Referer（可为 null，配置了白名单时必须命中）
     */
    DynamicDatavShare resolveShare(String shareCode, String sign, String referer);

    /**
     * 创建结果：行 + 只回传一次的签名。sign 不落库（按 shareCode 随时可重算，
     * 但接口侧只发给创建者，避免列表接口把签名批量吐给任何有编辑权的人）
     */
    record CreateShareResult(DynamicDatavShare share, String sign) {
    }
}
