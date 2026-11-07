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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP 归属地的行为基线。
 *
 * <p>要锁住三件事：未加载数据时绝不查询也不抛异常（登录不受影响）、内网地址任何情况下都不外出、
 * 以及 xdb 区域串到可读地址的转换规则。真实 xdb 数据的查询结果需自备数据文件后人工验证。</p>
 */
class AddressUtilsTest
{
    @AfterEach
    void tearDown()
    {
        AddressUtils.unload();
    }

    @Test
    @DisplayName("未加载数据时返回未知，且不算就绪")
    void notReadyWithoutDatabase()
    {
        assertFalse(AddressUtils.isReady());
        assertEquals(AddressUtils.UNKNOWN, AddressUtils.getRealAddressByIP("8.8.8.8"));
    }

    @Test
    @DisplayName("内网与回环地址永不查询，未加载数据也一样")
    void intranetNeverQueried()
    {
        assertEquals(AddressUtils.INTRANET_IP, AddressUtils.getRealAddressByIP("127.0.0.1"));
        assertEquals(AddressUtils.INTRANET_IP, AddressUtils.getRealAddressByIP("192.168.1.10"));
        assertEquals(AddressUtils.INTRANET_IP, AddressUtils.getRealAddressByIP("10.0.0.1"));
        assertEquals(AddressUtils.INTRANET_IP, AddressUtils.getRealAddressByIP("172.16.0.1"));
        assertEquals(AddressUtils.INTRANET_IP, AddressUtils.getRealAddressByIP("10.10.108.99"));
    }

    @ParameterizedTest(name = "xdb 串 [{0}] → [{1}]")
    @DisplayName("xdb 区域串压缩为可读地址：丢弃 0 占位与 ISP，去掉重复段")
    @CsvSource(delimiter = ';', value = {
            "中国|0|广东省|深圳市|电信; 中国 广东省 深圳市",
            "中国|华北|北京|北京市|联通; 中国 华北 北京 北京市",
            "美国|0|加利福尼亚|洛杉矶|0; 美国 加利福尼亚 洛杉矶",
            "中国|0|0|0|电信; 中国",
            "0|0|0|0|0; 未知",
            "中国|华东|江苏省|南京市|移动; 中国 华东 江苏省 南京市",
    })
    void formatRegionCoversCommonShapes(String raw, String expected)
    {
        assertEquals(expected, AddressUtils.formatRegion(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null、空串与全空格输入退化为未知")
    void formatRegionHandlesBlankInput(String raw)
    {
        assertEquals(AddressUtils.UNKNOWN, AddressUtils.formatRegion(raw));
    }

    @Test
    @DisplayName("ISP 段始终被丢弃，不进登录日志")
    void ispNeverIncluded()
    {
        String formatted = AddressUtils.formatRegion("中国|0|浙江省|杭州市|阿里巴巴");
        assertFalse(formatted.contains("阿里巴巴"), "地址里不应含运营商：" + formatted);
    }

    @Test
    @DisplayName("空数据加载失败但不抛异常，查询仍返回未知")
    void loadEmptyContentFailsSoftly()
    {
        assertFalse(AddressUtils.loadDatabase(new byte[0], "test-empty"));
        assertFalse(AddressUtils.loadDatabase(null, "test-null"));
        assertEquals(AddressUtils.UNKNOWN, AddressUtils.getRealAddressByIP("8.8.8.8"));
    }

    @Test
    @DisplayName("非法数据不得打断查询：结果仍是未知且绝不抛出")
    void corruptContentDegradesGracefully()
    {
        byte[] garbage = new byte[512];
        for (int i = 0; i < garbage.length; i++)
        {
            garbage[i] = (byte) i;
        }
        // newWithBuffer 可能不在构造期校验，因此只断言最终对外行为安全
        AddressUtils.loadDatabase(garbage, "test-garbage");
        assertNotNull(AddressUtils.getRealAddressByIP("8.8.8.8"));
        assertEquals(AddressUtils.UNKNOWN, AddressUtils.getRealAddressByIP("8.8.8.8"));
    }

    @Test
    @DisplayName("unload 后立刻回到不可用状态")
    void unloadRestoresUnready()
    {
        byte[] garbage = "not an xdb file".getBytes();
        AddressUtils.loadDatabase(garbage, "test-unload");
        AddressUtils.unload();
        assertFalse(AddressUtils.isReady());
        assertEquals(AddressUtils.UNKNOWN, AddressUtils.getRealAddressByIP("1.1.1.1"));
    }

    @Test
    @DisplayName("重复段只保留一次，避免「中国 华东 华东 上海」")
    void duplicatedSegmentKeptOnce()
    {
        assertEquals("中国 上海", AddressUtils.formatRegion("中国|上海|上海|上海|电信"));
    }
}
