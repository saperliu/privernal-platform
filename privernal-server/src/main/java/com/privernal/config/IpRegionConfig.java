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

package com.privernal.config;

import com.privernal.common.core.utils.ip.AddressUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 加载 IP 归属地的离线 xdb 数据。
 *
 * <p>{@code privernal.ip-region.db-path} 未配置或文件不可读时，归属地查询保持不可用，
 * 登录流程照常工作，地址列记为「未知」。支持 {@code file:} 与 {@code classpath:} 两种前缀。</p>
 *
 * @author saperliu
 */
@Component
public class IpRegionConfig
{
    private static final Logger log = LoggerFactory.getLogger(IpRegionConfig.class);

    private final ResourceLoader resourceLoader;

    @Value("${privernal.ip-region.db-path:}")
    private String dbPath;

    public IpRegionConfig(ResourceLoader resourceLoader)
    {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init()
    {
        if (dbPath == null || dbPath.isBlank())
        {
            log.info("IP 归属地查询未启用：未配置 privernal.ip-region.db-path（地址列将记为「未知」）");
            return;
        }
        try
        {
            Resource resource = resourceLoader.getResource(dbPath.trim());
            if (!resource.exists())
            {
                log.warn("IP 归属地数据文件不存在：{}，查询保持不可用", dbPath);
                return;
            }
            try (InputStream in = resource.getInputStream())
            {
                AddressUtils.loadDatabase(in.readAllBytes(), resource.getDescription());
            }
        }
        catch (Exception e)
        {
            // 数据加载失败不能拖垮启动
            log.warn("IP 归属地数据读取失败：{}（{}），查询保持不可用", dbPath, e.getMessage());
        }
    }
}
