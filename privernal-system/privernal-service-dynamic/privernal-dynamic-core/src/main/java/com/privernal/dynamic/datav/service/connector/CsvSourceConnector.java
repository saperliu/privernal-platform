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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.datav.service.IDynamicDatavFileService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 数据源连接器（M3.7）。
 * <p>
 * 两种传输形态：URL（取数与闸门完全复用 {@link HttpSourceConnector#fetchBodyBytes}）与
 * upload（字节来自 {@link IDynamicDatavFileService}）。分支只取到字节为止——之后的
 * 解码、嗅探分隔符、解析、列推断两条路共用同一份实现，否则同一个文件走 URL 能出图、
 * 走上传出错表，两个「CSV 源」迟早变成两种方言。
 * <p>
 * URL 形态的闸门留一份实现也是刻意的：两处各写一遍 SSRF/上限/超时，迟早漂移出一个能绕过的
 * 版本（HTTP 侧的 IP 字面量绕过就是这么来的）。upload 形态的存在理由恰恰是私有化现场
 * 内网地址被这道闸门拦住——放开闸门不是解法，让数据跟着平台走才是。
 * <p>
 * sourceConfig 里没有 storage 键时按 URL 解释，历史配置因此一字不用迁移。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class CsvSourceConnector implements DatavSourceConnector {

    private static final int SAMPLE_ROWS_FOR_COLUMNS = 20;

    private static final String DEFAULT_ACCEPT = "text/csv, text/plain;q=0.9, */*;q=0.8";

    /** sourceConfig.storage 的上传形态取值 */
    static final String STORAGE_UPLOAD = "upload";

    private final HttpSourceConnector httpSourceConnector;

    private final IDynamicDatavFileService datavFileService;

    public CsvSourceConnector(HttpSourceConnector httpSourceConnector, IDynamicDatavFileService datavFileService) {
        this.httpSourceConnector = httpSourceConnector;
        this.datavFileService = datavFileService;
    }

    @Override
    public String type() {
        return "csv";
    }

    @Override
    public List<Map<String, Object>> fetchRows(JSONObject sourceConfig, int maxRows) {
        return toRows(read(sourceConfig, maxRows > 0 ? maxRows : 1000));
    }

    @Override
    public JSONObject sampleColumns(JSONObject sourceConfig) {
        Table table = read(sourceConfig, SAMPLE_ROWS_FOR_COLUMNS);
        return HttpSourceConnector.inferColumns(toRows(table));
    }

    // ---------------- 取数与解释 ----------------

    /** 一份 CSV 表：列名与数据行（行按列名对齐，缺列补 null） */
    private static final class Table {

        private final List<String> columns;
        private final List<List<String>> rows;

        Table(List<String> columns, List<List<String>> rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }

    private Table read(JSONObject sourceConfig, int maxRecords) {
        byte[] raw = isUploadMode(sourceConfig) ? readUploaded(sourceConfig) : fetchRemoteBytes(sourceConfig);
        String text = decode(raw, sourceConfig == null ? null : sourceConfig.getString("charset"));
        if (StringUtils.isBlank(text)) {
            throw fail(DatavSourceException.PARSE, "CSV 内容为空");
        }
        char delimiter = resolveDelimiter(sourceConfig, text);
        CsvParser.Result parsed = CsvParser.parse(text, delimiter, maxRecords);
        List<List<String>> records = parsed.getRecords();
        if (records.isEmpty()) {
            throw fail(DatavSourceException.PARSE, "CSV 没有任何数据行");
        }
        Boolean configuredHeader = sourceConfig == null ? null : sourceConfig.getBoolean("headerRow");
        boolean headerRow = configuredHeader == null || configuredHeader;
        List<String> columns;
        List<List<String>> rows;
        if (headerRow) {
            columns = namedColumns(records.get(0), parsed.getWidth());
            rows = records.subList(1, records.size());
            if (rows.isEmpty()) {
                throw fail(DatavSourceException.PARSE, "CSV 只有表头，没有数据行");
            }
        } else {
            columns = anonymousColumns(parsed.getWidth());
            rows = records;
        }
        if (parsed.isTruncated()) {
            log.warn("CSV 数据源超过 {} 行已截断：{}", maxRecords, describeSource(sourceConfig));
        }
        return new Table(columns, new ArrayList<>(rows));
    }

    /**
     * 是否上传形态。公开给数据集服务复用：判定只写一处，否则「保存时清理旧文件」和
     * 「取数时读文件」会对 storage 这个键各认一种写法。
     */
    public static boolean isUploadMode(JSONObject sourceConfig) {
        if (sourceConfig == null) {
            return false;
        }
        return STORAGE_UPLOAD.equalsIgnoreCase(StringUtils.trimToEmpty(sourceConfig.getString("storage")));
    }

    /** 上传形态：字节来自本组织的文件表，其余解释与 URL 形态走同一条路径 */
    private byte[] readUploaded(JSONObject sourceConfig) {
        String fileCode = StringUtils.trimToNull(sourceConfig.getString("fileId"));
        if (fileCode == null) {
            throw fail(DatavSourceException.CONFIG, "CSV 上传源缺少 fileId");
        }
        byte[] raw = datavFileService.readContent(fileCode);
        if (raw == null || raw.length == 0) {
            // 归 CONFIG 不归 NETWORK：要动的是数据集配置（重新上传），查网络查不出任何东西
            throw fail(DatavSourceException.CONFIG, "上传的 CSV 文件不存在或已被清理，请重新上传");
        }
        return raw;
    }

    private byte[] fetchRemoteBytes(JSONObject sourceConfig) {
        // accept 只在未显式配置时补：把闸门参数留给调用方，默认值只影响这一条取数路径
        if (sourceConfig != null && StringUtils.isBlank(sourceConfig.getString("accept"))) {
            JSONObject withAccept = new JSONObject();
            withAccept.putAll(sourceConfig);
            withAccept.put("accept", DEFAULT_ACCEPT);
            sourceConfig = withAccept;
        }
        return httpSourceConnector.fetchBodyBytes(sourceConfig);
    }

    private List<Map<String, Object>> toRows(Table table) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (List<String> record : table.rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < table.columns.size(); i++) {
                row.put(table.columns.get(i), i < record.size() ? trim(record.get(i)) : null);
            }
            rows.add(row);
        }
        return rows;
    }

    private static String trim(String value) {
        return value == null || value.isEmpty() ? value : value.trim();
    }

    /** 表头里的空列名与重名列都要改名：下游用 fieldCode 取数，两列同名会让其中一列永远选不到 */
    private static List<String> namedColumns(List<String> header, int width) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            String name = i < header.size() ? trim(header.get(i)) : null;
            if (StringUtils.isBlank(name)) {
                name = "col" + (i + 1);
            }
            if (columns.contains(name)) {
                String suffix = name;
                int round = 2;
                while (columns.contains(suffix)) {
                    suffix = name + "_" + round++;
                }
                name = suffix;
            }
            columns.add(name);
        }
        return columns;
    }

    private static List<String> anonymousColumns(int width) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            columns.add("col" + (i + 1));
        }
        return columns;
    }

    /** 包级可见：分隔符嗅探与配置解析要在没有网络的情况下被回归 */
    static char resolveDelimiter(JSONObject sourceConfig, String text) {
        String configured = sourceConfig == null ? null : sourceConfig.getString("delimiter");
        configured = StringUtils.trimToNull(configured);
        if (configured == null) {
            return CsvParser.sniffDelimiter(text);
        }
        // 允许配置里写 \t 的字面量：JSON 表单里填真制表符几乎不可能
        if ("\\t".equals(configured)) {
            return '\t';
        }
        if (configured.length() != 1) {
            throw fail(DatavSourceException.CONFIG, "CSV 分隔符只能是单个字符：， ; \\t | 之一");
        }
        return configured.charAt(0);
    }

    /**
     * 字节 → 文本。
     * <p>
     * 先认 BOM 再认配置，最后才按 UTF-8 严格解：Excel 导出的「CSV UTF-8」带 BOM，
     * 「CSV(逗号分隔)」在中文 Windows 上是 GBK，而 BOM 字符如果不剥掉会变成第一列的列名，
     * 于是那张表的第一列永远选不中。解不开时报编码而不是替换成乱码——
     * 大屏上一片 &#65533; 比一句「请改 charset」难排查得多。
     */
    // 包级可见：中文编码的判定要在没有公网 GBK 端点的情况下也能被离线回归
    static String decode(byte[] raw, String configuredCharset) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        if (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF) {
            return new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8);
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xFE) {
            return new String(raw, 2, raw.length - 2, StandardCharsets.UTF_16LE);
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFE && (raw[1] & 0xFF) == 0xFF) {
            return new String(raw, 2, raw.length - 2, StandardCharsets.UTF_16BE);
        }
        if (StringUtils.isNotBlank(configuredCharset)) {
            Charset charset;
            try {
                charset = Charset.forName(configuredCharset.trim());
            } catch (Exception e) {
                throw fail(DatavSourceException.CONFIG, "CSV 编码不支持：" + configuredCharset);
            }
            return new String(raw, charset);
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException e) {
            throw fail(DatavSourceException.PARSE,
                    "CSV 不是 UTF-8（Excel 中文另存为的「CSV」通常是 GBK），请把 charset 改成 GBK 重试");
        }
    }

    /** 日志里只留主机与路径，不带 query —— 凭据常放在查询参数里 */
    private static String describeSource(JSONObject sourceConfig) {
        if (sourceConfig == null) {
            return "(未配置数据源)";
        }
        if (isUploadMode(sourceConfig)) {
            // 上传形态只打文件编码：文件名由提交方提供，原样进日志等于给它一次写日志的机会
            return "upload:" + StringUtils.trimToEmpty(sourceConfig.getString("fileId"));
        }
        try {
            okhttp3.HttpUrl url = okhttp3.HttpUrl.parse(StringUtils.trimToEmpty(sourceConfig.getString("url")));
            return url == null ? "(URL 无法解析)" : url.scheme() + "://" + url.host() + url.encodedPath();
        } catch (Exception e) {
            return "(URL 无法解析)";
        }
    }

    private static DatavSourceException fail(String category, String message) {
        return new DatavSourceException(category, message);
    }
}
