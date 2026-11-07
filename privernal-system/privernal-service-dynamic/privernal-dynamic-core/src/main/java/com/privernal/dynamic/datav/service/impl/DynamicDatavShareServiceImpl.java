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

package com.privernal.dynamic.datav.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.IdUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.datav.domain.DynamicDatavShare;
import com.privernal.dynamic.datav.mapper.DynamicDatavScreenMapper;
import com.privernal.dynamic.datav.mapper.DynamicDatavShareMapper;
import com.privernal.dynamic.datav.service.DatavShareSigns;
import com.privernal.dynamic.datav.service.IDynamicDatavShareService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 大屏外链分享服务实现
 *
 * @author saperliu
 */
@Service
public class DynamicDatavShareServiceImpl extends ServiceImpl<DynamicDatavShareMapper, DynamicDatavShare>
        implements IDynamicDatavShareService {

    /** 与 OpenApi 凭据、DatavSourceSecrets 共用的主密钥；未配置时分享链路整体 fail-closed */
    @Value("${openapi.secret-master-key:}")
    private String masterKey;

    @Autowired
    private DynamicDatavScreenMapper screenMapper;

    /** 失效文案统一为一句：把「不存在 / 已禁用 / 签名错 / 过期 / 来源不符」合成同一句，不给探测面 */
    private static final String INVALID_MSG = "分享链接不存在或已失效";

    /** 允许的有效期档位（小时）：1 小时 / 1 天 / 7 天 / 30 天 / 长期(0)。白名单挡住离谱值 */
    private static final List<Integer> ALLOWED_EXPIRE_HOURS = List.of(0, 1, 24, 168, 720);

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public CreateShareResult createShare(String screenCode, Integer expireHours, String refererWhitelist) {
        if (StringUtils.isBlank(screenCode)) {
            throw new ServiceException("缺少大屏编码");
        }
        LambdaPrivernalWrapper<DynamicDatavScreen> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavScreen::getScreenCode, screenCode);
        DynamicDatavScreen screen = screenMapper.selectOne(wrapper);
        // 分享的是发布快照：草稿屏不可分享，否则「投放出去的画面随时被编辑改动」
        if (screen == null || !Integer.valueOf(1).equals(screen.getStatus())) {
            throw new ServiceException("只有已发布的大屏才能生成外链");
        }
        if (expireHours == null || !ALLOWED_EXPIRE_HOURS.contains(expireHours)) {
            throw new ServiceException("有效期只支持：1 小时 / 1 天 / 7 天 / 30 天 / 长期");
        }
        if (masterKey == null || masterKey.isEmpty()) {
            throw new ServiceException("未配置主密钥（openapi.secret-master-key），外链分享不可用");
        }

        DynamicDatavShare share = new DynamicDatavShare();
        share.setShareCode("dvshare_" + IdUtils.simpleUUID() + IdUtils.simpleUUID());
        share.setScreenCode(screenCode);
        share.setExpireTime(expireHours > 0 ? hoursFromNow(expireHours) : null);
        share.setRefererWhitelist(DatavShareSigns.normalizeWhitelist(refererWhitelist));
        share.setEnabled("Y");
        share.setOrganizationId(screen.getOrganizationId() != null
                ? screen.getOrganizationId() : SecurityUtils.getOrganizationId());
        share.setDepartmentId(SecurityUtils.getDepartmentId());
        save(share);
        // 签名只此一次随创建返回：列表与详情接口都不带它
        return new CreateShareResult(share, DatavShareSigns.signOf(masterKey, share.getShareCode()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DynamicDatavShare> listShares(String screenCode) {
        LambdaPrivernalWrapper<DynamicDatavShare> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavShare::getScreenCode, screenCode);
        wrapper.orderByDesc(DynamicDatavShare::getCreatedTime);
        return list(wrapper);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void disableShare(String shareCode) {
        LambdaPrivernalWrapper<DynamicDatavShare> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavShare::getShareCode, shareCode);
        DynamicDatavShare share = getOne(wrapper);
        if (share == null) {
            throw new ServiceException(INVALID_MSG);
        }
        share.setEnabled("N");
        updateById(share);
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicDatavShare resolveShare(String shareCode, String sign, String referer) {
        // 校验顺序刻意「先签名后查库」的变体：签名的常量时间比较放在查库之前没有意义
        // （查库本身不泄露信息），但签名不匹配时直接拒绝，避免为一个伪造请求跑库
        if (StringUtils.isBlank(shareCode) || StringUtils.isBlank(sign)) {
            throw new ServiceException(INVALID_MSG);
        }
        String expected = DatavShareSigns.signOf(masterKey, shareCode);
        if (expected == null || !DatavShareSigns.constantEquals(expected, sign)) {
            throw new ServiceException(INVALID_MSG);
        }
        LambdaPrivernalWrapper<DynamicDatavShare> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavShare::getShareCode, shareCode);
        DynamicDatavShare share = getOne(wrapper);
        if (share == null || !"Y".equals(share.getEnabled())) {
            throw new ServiceException(INVALID_MSG);
        }
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date())) {
            throw new ServiceException(INVALID_MSG);
        }
        if (!DatavShareSigns.refererAllowed(share.getRefererWhitelist(), referer)) {
            throw new ServiceException(INVALID_MSG);
        }
        return share;
    }

    private static Date hoursFromNow(int hours) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, hours);
        return calendar.getTime();
    }

}
