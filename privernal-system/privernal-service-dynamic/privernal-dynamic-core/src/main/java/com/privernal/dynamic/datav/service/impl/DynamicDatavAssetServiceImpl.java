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
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.IdUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.datav.domain.DynamicDatavAsset;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.datav.mapper.DynamicDatavAssetMapper;
import com.privernal.dynamic.datav.mapper.DynamicDatavScreenMapper;
import com.privernal.dynamic.datav.service.IDynamicDatavAssetService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 大屏素材服务实现
 *
 * @author saperliu
 */
@Service
public class DynamicDatavAssetServiceImpl extends ServiceImpl<DynamicDatavAssetMapper, DynamicDatavAsset>
        implements IDynamicDatavAssetService {

    private static final String DEFAULT_CATEGORY = "通用";

    /** 素材类型白名单：决定面板展示形态，也顺带挡掉把可执行页面当素材登记的路数 */
    private static final Pattern ALLOWED_TYPES = Pattern.compile("image|video");

    /** 相对路径（本地后端 /statics/…）或 http(s) 绝对地址；其他协议一律拒绝登记 */
    private static final Pattern ALLOWED_URL = Pattern.compile("^(/[A-Za-z0-9_\\-./%]+|https?://\\S+)$");

    /** 引用保护提示里最多列几块屏的名字：够定位问题即可，不把错误文案变成大屏清单 */
    private static final int REF_SAMPLE_LIMIT = 3;

    @Autowired
    private DynamicDatavScreenMapper screenMapper;

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDatavAsset saveAsset(DynamicDatavAsset asset) {
        requireValid(asset);
        normalize(asset);
        if (StringUtils.isBlank(asset.getAssetCode())) {
            asset.setAssetCode("dvasst_" + IdUtils.simpleUUID());
        }
        asset.setOrganizationId(SecurityUtils.getOrganizationId());
        asset.setDepartmentId(SecurityUtils.getDepartmentId());
        save(asset);
        return asset;
    }

    /** 入参硬校验：URL 与类型白名单。静态方法是为了可单测（不碰 SecurityUtils/Spring 上下文） */
    static void requireValid(DynamicDatavAsset asset) {
        if (asset == null || StringUtils.isBlank(asset.getFileUrl()) || !ALLOWED_URL.matcher(asset.getFileUrl().trim()).matches()) {
            throw new ServiceException("素材地址必须是以 / 开头的相对路径或 http(s) 地址");
        }
        String type = StringUtils.isBlank(asset.getAssetType()) ? "image" : asset.getAssetType().trim();
        if (!ALLOWED_TYPES.matcher(type).matches()) {
            throw new ServiceException("素材类型只支持 image / video");
        }
    }

    /** 规范化：截断超长字段、补默认值。原地修改，调用前必须先过 requireValid */
    static void normalize(DynamicDatavAsset asset) {
        asset.setFileUrl(asset.getFileUrl().trim());
        asset.setAssetType(StringUtils.isBlank(asset.getAssetType()) ? "image" : asset.getAssetType().trim());
        String name = StringUtils.isBlank(asset.getAssetName())
                ? StringUtils.defaultString(asset.getOriginalName(), "未命名素材")
                : asset.getAssetName().trim();
        asset.setAssetName(StringUtils.substring(name, 0, 200));
        asset.setCategory(StringUtils.substring(
                StringUtils.isBlank(asset.getCategory()) ? DEFAULT_CATEGORY : asset.getCategory().trim(), 0, 50));
        if (asset.getFileSize() == null || asset.getFileSize() < 0) {
            asset.setFileSize(0L);
        }
    }

    /** 引用保护提示：最多列 3 块屏的名字，超出加「等 N 块」，避免错误文案变成大屏清单 */
    static String referenceDeniedMessage(List<String> screenNames) {
        String head = screenNames.stream()
                .limit(REF_SAMPLE_LIMIT)
                .collect(Collectors.joining("、"));
        if (screenNames.size() <= REF_SAMPLE_LIMIT) {
            return String.format("素材仍被「%s」引用，请先在大屏中移除后再删除", head);
        }
        return String.format("素材仍被「%s」等 %d 块大屏引用，请先在大屏中移除后再删除", head, screenNames.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DynamicDatavAsset> listAssets(String category, String keyword) {
        LambdaPrivernalWrapper<DynamicDatavAsset> wrapper = new LambdaPrivernalWrapper<>();
        if (StringUtils.isNotBlank(category)) {
            wrapper.eq(DynamicDatavAsset::getCategory, category.trim());
        }
        if (StringUtils.isNotBlank(keyword)) {
            String escaped = IDynamicDatavAssetService.escapeLike(keyword.trim());
            wrapper.and(w -> w.like(DynamicDatavAsset::getAssetName, escaped)
                    .or().like(DynamicDatavAsset::getOriginalName, escaped));
        }
        wrapper.orderByDesc(DynamicDatavAsset::getUpdatedTime);
        return list(wrapper);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteByCode(String assetCode) {
        if (StringUtils.isBlank(assetCode)) {
            throw new ServiceException("素材编码不能为空");
        }
        LambdaPrivernalWrapper<DynamicDatavAsset> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavAsset::getAssetCode, assetCode);
        DynamicDatavAsset asset = getOne(wrapper);
        if (asset == null) {
            throw new ServiceException("素材不存在：" + assetCode);
        }

        // 引用保护：URL 出现在任何（本组织，拦截器自动附加 organization_id）大屏的 schema 里就拒删。
        // 只拉 screen_name，不把几十 KB 的 schema_json 整份捞进内存。
        LambdaPrivernalWrapper<DynamicDatavScreen> refWrapper = new LambdaPrivernalWrapper<>();
        refWrapper.select(DynamicDatavScreen::getScreenName);
        refWrapper.like(DynamicDatavScreen::getSchemaJson, IDynamicDatavAssetService.escapeLike(asset.getFileUrl()));
        List<DynamicDatavScreen> refs = screenMapper.selectList(refWrapper);
        if (!refs.isEmpty()) {
            List<String> names = refs.stream().map(DynamicDatavScreen::getScreenName).collect(Collectors.toList());
            throw new ServiceException(referenceDeniedMessage(names));
        }
        return baseMapper.delete(wrapper);
    }
}
