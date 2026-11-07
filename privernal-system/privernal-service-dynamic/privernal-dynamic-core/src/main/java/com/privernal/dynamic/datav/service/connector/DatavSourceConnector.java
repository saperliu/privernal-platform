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

package com.privernal.dynamic.datav.service.connector;

import com.privernal.common.core.exception.ServiceException;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 多源数据源连接器契约（M3.1）。
 * <p>
 * 各连接器只负责两件事：把源配置解释成「行集合」，以及产出与动态表同形状的列快照。
 * 聚合、过滤、排序全部交给 {@link com.privernal.dynamic.datav.service.DatavRowAggregator}，
 * 因此下游 ReportConfig / AggResult / 图表组件对源类型完全无感。
 * <p>
 * 行的键必须是列快照里的 fieldCode；值只允许 String / Number / Boolean / null——
 * 嵌套结构在连接器里拍平，别把 Map 塞进聚合层。
 *
 * @author saperliu
 */
public interface DatavSourceConnector {

    /** 对应 dynamic_dataset.source_type 的取值 */
    String type();

    /**
     * 拉取原始行。maxRows 是硬上限（防一个接口把整个库吐给大屏），
     * 超出的行直接截断并在日志里留痕。
     */
    List<Map<String, Object>> fetchRows(JSONObject sourceConfig, int maxRows);

    /**
     * 产出列快照结构：{"columns":[{fieldCode,fieldName,dataType}]}。
     * 健康检查与「预览并生成列快照」共用此方法；实现方必须做真实抽样而不是静态猜测。
     */
    JSONObject sampleColumns(JSONObject sourceConfig);

    /**
     * 让实现方丢弃按 sourceConfig 缓存的资源（连接池等）。
     * <p>
     * 数据集改了连接参数或被删除时必须调用：池是按连接指纹缓存的，旧指纹不会被自动回收，
     * 攒够上限后新配置的数据源会直接报「连接池数量已达上限」——症状离成因很远。
     * 无缓存资源的连接器不用管。
     */
    default void invalidate(JSONObject sourceConfig) {
        // 默认无资源可回收
    }

    /**
     * 健康检查（M3.9）：真实走一遍取数路径，回报可达性与分类结果。
     * <p>
     * 形状固定成 {@code {ok, category, message, columns}}。category 让前端能按类别给处置建议——
     * 「检查主机与端口」和「重新填写凭据」不是一回事，把异常文案原样贴上去等于没分类。
     * <p>
     * 放在接口上而不是各连接器里重复写：三个源的判定形状完全一致，只有异常来源不同。
     */
    default JSONObject health(JSONObject sourceConfig) {
        JSONObject result = new JSONObject();
        try {
            JSONArray columns = sampleColumns(sourceConfig).getJSONArray("columns");
            result.put("ok", true);
            result.put("category", "ok");
            result.put("columns", columns);
            result.put("message", "连接正常，抽样到 " + (columns == null ? 0 : columns.size()) + " 个字段");
        } catch (DatavSourceException e) {
            result.put("ok", false);
            result.put("category", e.getCategory());
            result.put("message", e.getMessage());
        } catch (ServiceException e) {
            // 没走分类路径的异常（比如连接器之外的校验）照实归 unknown，不硬套一个类别
            result.put("ok", false);
            result.put("category", DatavSourceException.UNKNOWN);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
