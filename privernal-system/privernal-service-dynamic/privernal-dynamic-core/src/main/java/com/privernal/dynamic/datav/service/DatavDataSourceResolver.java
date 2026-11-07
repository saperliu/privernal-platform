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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.datav.service.connector.DatavSourceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源分派（M3.1）：source_type → 连接器。
 * <p>
 * 动态表（dynamic_table / 空）不走这里——那是报表内核的 SQL 路径；
 * 注册表里没有的类型一律显式报错，不做「猜一个默认源」的静默兜底。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DatavDataSourceResolver {

    /** 非 SQL 源单次拉取的硬行数上限：内存聚合的量保护 */
    public static final int MAX_ROWS = 1000;

    private final Map<String, DatavSourceConnector> connectors = new HashMap<>();

    @Autowired(required = false)
    private void collectConnectors(List<DatavSourceConnector> list) {
        for (DatavSourceConnector connector : list) {
            connectors.put(connector.type(), connector);
        }
    }

    public boolean isExternalSource(String sourceType) {
        return StringUtils.isNotBlank(sourceType) && connectors.containsKey(sourceType);
    }

    public List<Map<String, Object>> fetchRows(String sourceType, com.alibaba.fastjson2.JSONObject sourceConfig) {
        DatavSourceConnector connector = connectors.get(sourceType);
        if (connector == null) {
            throw new ServiceException("不支持的数据源类型：" + sourceType);
        }
        return connector.fetchRows(sourceConfig, MAX_ROWS);
    }

    public com.alibaba.fastjson2.JSONObject sampleColumns(String sourceType,
                                                          com.alibaba.fastjson2.JSONObject sourceConfig) {
        DatavSourceConnector connector = connectors.get(sourceType);
        if (connector == null) {
            throw new ServiceException("不支持的数据源类型：" + sourceType);
        }
        return connector.sampleColumns(sourceConfig);
    }

    /**
     * 健康检查（M3.9）：分派到连接器，真实访问一次并回报可达性与错误分类。
     * <p>
     * 动态表不在这里——它的列快照来自表单模型，没有「连不上」这种状态，等价检查是刷新列快照。
     */
    public com.alibaba.fastjson2.JSONObject health(String sourceType,
                                                   com.alibaba.fastjson2.JSONObject sourceConfig) {
        DatavSourceConnector connector = connectors.get(sourceType);
        if (connector == null) {
            throw new ServiceException("不支持的数据源类型：" + sourceType);
        }
        return connector.health(sourceConfig);
    }

    /**
     * 数据集换了连接参数或被删除时释放连接器缓存的资源。
     * 类型不认识时静默跳过：这是一条清理路径，不是取数路径，报错只会让删数据集的接口失败。
     */
    public void invalidate(String sourceType, com.alibaba.fastjson2.JSONObject sourceConfig) {
        DatavSourceConnector connector = connectors.get(sourceType);
        if (connector != null) {
            connector.invalidate(sourceConfig);
        }
    }
}
