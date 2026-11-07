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

package com.privernal.common.core.utils.ip;

import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * IP 归属地查询，基于 ip2region 的离线 xdb 数据（https://github.com/lionsoul2014/ip2region ，Apache-2.0）。
 *
 * <p>完全离线：查询在进程内完成，不会把登录 IP 交给任何外部服务，因此在内网与私有化环境里照样可用。
 * 未加载数据时一律返回 {@link #UNKNOWN}；加载失败只记日志，不影响启动与登录。</p>
 *
 * <p>xdb 数据文件需部署方自备，放到 {@code privernal.ip-region.db-path} 指向的位置。
 * 本仓库不附带该数据文件：体积大，且更新节奏与代码无关。</p>
 *
 * @author saperliu
 */
public class AddressUtils
{
    private static final Logger log = LoggerFactory.getLogger(AddressUtils.class);

    /** 未加载数据或查询失败时的占位结果 */
    public static final String UNKNOWN = "未知";

    /** 内网地址不查询 */
    public static final String INTRANET_IP = "内网IP";

    /** xdb 中表示该字段无数据的占位值 */
    private static final String NO_DATA = "0";

    /** volatile 读 + synchronized 写：读侧无锁，允许在线重载 */
    private static volatile Searcher searcher;

    private AddressUtils()
    {
    }

    /**
     * 用 xdb 文件内容建立查询索引。全量缓存在内存，查询为微秒级且线程安全。
     *
     * @param content xdb 文件的完整字节内容
     * @param origin  来源描述，仅用于日志定位
     * @return 是否加载成功
     */
    public static synchronized boolean loadDatabase(byte[] content, String origin)
    {
        if (content == null || content.length == 0)
        {
            log.warn("IP 归属地数据为空（来源 {}），查询保持不可用", origin);
            return false;
        }
        try
        {
            Searcher loaded = Searcher.newWithBuffer(content);
            Searcher previous = searcher;
            searcher = loaded;
            closeQuietly(previous);
            log.info("IP 归属地查询已就绪：来源 {}，数据 {} 字节", origin, content.length);
            return true;
        }
        catch (Exception e)
        {
            // 版本不符、文件被截断、拿成了别的文件，都会落到这里
            log.warn("IP 归属地数据加载失败（来源 {}）：{}；查询保持不可用", origin, e.getMessage());
            return false;
        }
    }

    /**
     * 关闭并清空已加载的索引，供在线重载与测试使用。
     */
    public static synchronized void unload()
    {
        closeQuietly(searcher);
        searcher = null;
    }

    /**
     * 是否已加载可用的归属地数据。
     */
    public static boolean isReady()
    {
        return searcher != null;
    }

    /**
     * 内网地址返回「内网IP」，未加载数据或查询失败返回 {@link #UNKNOWN}。
     */
    public static String getRealAddressByIP(String ip)
    {
        if (IpUtils.internalIp(ip))
        {
            return INTRANET_IP;
        }
        Searcher current = searcher;
        if (current == null)
        {
            return UNKNOWN;
        }
        try
        {
            return formatRegion(current.search(ip));
        }
        catch (Exception e)
        {
            // 不记录 IP 原文，它属于个人信息
            log.warn("IP 归属地查询失败：{}", e.getMessage());
            return UNKNOWN;
        }
    }

    /**
     * 把 xdb 的「国家|区域|省份|城市|ISP」压成可读地址。
     *
     * <p>只取前四段（丢弃 ISP：对排障无用，又属可识别信息），跳过 0 与空段，
     * 并去掉重复段以免出现「中国 华东 华东 上海」。全为占位时退化为 {@link #UNKNOWN}。</p>
     */
    static String formatRegion(String region)
    {
        if (region == null || region.isBlank())
        {
            return UNKNOWN;
        }
        String[] parts = region.split("\\|", -1);
        List<String> readable = new ArrayList<>(4);
        for (int i = 0; i < Math.min(4, parts.length); i++)
        {
            String part = parts[i].trim();
            if (part.isEmpty() || NO_DATA.equals(part) || readable.contains(part))
            {
                continue;
            }
            readable.add(part);
        }
        return readable.isEmpty() ? UNKNOWN : String.join(" ", readable);
    }

    private static void closeQuietly(Searcher target)
    {
        if (target == null)
        {
            return;
        }
        try
        {
            target.close();
        }
        catch (Exception ignored)
        {
            // buffer 模式不持文件句柄，关闭失败无副作用
        }
    }
}
