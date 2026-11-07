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

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 解析（M3.7，零依赖）。
 * <p>
 * 不引 commons-csv/opencsv：为一列文本格式再加一个传递依赖，换来的收益抵不上开源交付时
 * 多出一份要逐一核可的第三方许可证。规则按 RFC 4180 的子集实现，够覆盖 Excel 导出的产物：
 * <ul>
 *   <li>引号字段内可以含分隔符、换行；字段内的双写 {@code ""} 还原成一个引号</li>
 *   <li>换行 {@code \r\n} / {@code \n} / {@code \r} 都算记录分隔，引号内的换行不算</li>
 *   <li>行宽不一致时**报错**而不是补齐：列数比表头多几乎总是「分隔符选错了」，
 *       静默截断会让一张按 ; 存的表显示成一列，用户看不出错在哪</li>
 * </ul>
 * 单独成类是为了让这条解析规则能被离线回归——真实 CSV 要过网络与文件，而引号/换行的归属
 * 恰好是最容易写错、又最难在界面上看出来的部分。
 *
 * @author saperliu
 */
public final class CsvParser {

    /** 列数硬上限：一张表解析出上千列一定不是「宽表」，而是分隔符没对上 */
    public static final int MAX_COLUMNS = 200;

    private CsvParser() {
    }

    /**
     * 解析成等宽记录。
     *
     * @param delimiter  单字符分隔符
     * @param maxRecords 记录数上限，超出即截断并返回 {@code truncated}
     */
    public static Result parse(String text, char delimiter, int maxRecords) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>(8);
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldWasQuoted = false;
        int width = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"' && field.length() == 0 && !fieldWasQuoted) {
                inQuotes = true;
                fieldWasQuoted = true;
                continue;
            }
            if (c == delimiter) {
                current.add(field.toString());
                field.setLength(0);
                fieldWasQuoted = false;
                continue;
            }
            if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                width = endRecord(records, current, field, width);
                field.setLength(0);
                fieldWasQuoted = false;
                if (records.size() >= maxRecords) {
                    return new Result(records, width, true);
                }
                continue;
            }
            field.append(c);
        }
        if (inQuotes) {
            throw fail(DatavSourceException.PARSE,
                    "CSV 有未闭合的引号，请检查文件是否被截断或导出时选错格式");
        }
        // 最后一行没有换行符时循环里结不了尾；纯尾随空行由 endRecord 丢弃
        endRecord(records, current, field, width);
        return new Result(records, width, false);
    }

    /** 收尾一条记录；全空且只有一格视为空行丢弃。返回本表列宽（首条记录定宽） */
    private static int endRecord(List<List<String>> records, List<String> current, StringBuilder field, int width) {
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            if (records.isEmpty()) {
                width = current.size();
                if (width > MAX_COLUMNS) {
                    throw fail(DatavSourceException.PARSE,
                            "CSV 列数为 " + width + "，超过上限 " + MAX_COLUMNS + "，请确认分隔符是否正确");
                }
            } else if (current.size() > width) {
                // 补齐短行是宽容，多出列必须是错：多出来的列几乎总是「分隔符选错」，
                // 静默截断会让一张按 ; 存的表显示成一列，用户看不出错在哪
                throw fail(DatavSourceException.PARSE, "CSV 第 " + (records.size() + 1) + " 行有 "
                        + current.size() + " 列，比首行的 " + width + " 列多，请确认分隔符是否正确");
            }
            records.add(new ArrayList<>(current));
        }
        current.clear();
        return width;
    }

    /**
     * 未显式配置分隔符时按首行嗅探：CSV 的现实文件里 {@code ;}（德语区 Excel）与 {@code \t}
     * 出现频率不比 {@code ,} 低，只认逗号会让一半的导入变成「整行一列」。
     * 取首行里出现次数最多的候选；并列时按 , ; \t | 的固定顺序裁定，保证同输入同结果。
     */
    public static char sniffDelimiter(String text) {
        char[] candidates = {',', ';', '\t', '|'};
        char best = ',';
        int bestHits = 0;
        for (char candidate : candidates) {
            int hits = 0;
            boolean inQuotes = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (!inQuotes && c == candidate) {
                    hits++;
                } else if (!inQuotes && (c == '\n' || c == '\r')) {
                    break;
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = candidate;
            }
        }
        return best;
    }

    private static DatavSourceException fail(String category, String message) {
        return new DatavSourceException(category, message);
    }

    /** 解析结果：记录 + 列宽 + 是否因上限被截断 */
    public static final class Result {
        private final List<List<String>> records;
        private final int width;
        private final boolean truncated;

        Result(List<List<String>> records, int width, boolean truncated) {
            this.records = records;
            this.width = width;
            this.truncated = truncated;
        }

        public List<List<String>> getRecords() {
            return records;
        }

        public int getWidth() {
            return width;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }
}
