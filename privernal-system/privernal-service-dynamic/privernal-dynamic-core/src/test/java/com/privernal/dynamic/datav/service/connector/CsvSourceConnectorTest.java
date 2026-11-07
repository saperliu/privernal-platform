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
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CSV 解析与编码判定测试（M3.7）
 * <p>
 * 引号、换行、编码三件事都属于「错了不报错、只是数据变奇怪」的类别：分隔符选错会把整行
 * 塞进一列，BOM 不剥会让第一列永远选不中，GBK 当 UTF-8 解会满屏替换字符。公网没有可控的
 * CSV/GBK 端点可打，所以这些判定必须能在完全离线的情况下被钉住。
 *
 * @author saperliu
 */
@DisplayName("CSV 数据源解析测试")
class CsvSourceConnectorTest {

    private static List<List<String>> parse(String text, char delimiter) {
        return CsvParser.parse(text, delimiter, 1000).getRecords();
    }

    @Nested
    @DisplayName("记录与字段")
    class Records {

        @Test
        @DisplayName("普通行列")
        void plainGrid() {
            assertThat(parse("a,b,c\n1,2,3\n", ','))
                    .containsExactly(List.of("a", "b", "c"), List.of("1", "2", "3"));
        }

        @Test
        @DisplayName("末行没有换行符也要收尾")
        void finalRecordWithoutNewline() {
            assertThat(parse("a,b\n1,2", ',')).hasSize(2);
        }

        @Test
        @DisplayName("尾随空行不产生空记录")
        void trailingBlankLinesDropped() {
            assertThat(parse("a,b\n1,2\n\n\n", ',')).hasSize(2);
        }

        @Test
        @DisplayName("CRLF 与单独 CR 都是记录分隔")
        void crlfAndLoneCr() {
            assertThat(parse("a,b\r\n1,2\r\n3,4\r5,6", ',')).hasSize(4);
        }
    }

    @Nested
    @DisplayName("引号规则")
    class Quotes {

        @Test
        @DisplayName("引号内的分隔符不切列")
        void delimiterInsideQuotes() {
            assertThat(parse("\"a,b\",c", ',')).containsExactly(List.of("a,b", "c"));
        }

        @Test
        @DisplayName("双写引号还原成一个引号")
        void doubledQuoteEscapes() {
            assertThat(parse("\"he said \"\"hi\"\"\",b", ',')).containsExactly(List.of("he said \"hi\"", "b"));
        }

        @Test
        @DisplayName("引号内的换行属于字段值，不是新记录")
        void newlineInsideQuotes() {
            assertThat(parse("\"line1\nline2\",b", ',')).containsExactly(List.of("line1\nline2", "b"));
        }

        @Test
        @DisplayName("引号内的换行不改变列宽判定")
        void quotedMultilineRowKeepsWidth() {
            List<List<String>> records = parse("x,y\n\"a\nb\",c", ',');
            assertThat(records).hasSize(2);
            assertThat(records.get(1)).containsExactly("a\nb", "c");
        }

        @Test
        @DisplayName("未闭合引号报「文件被截断」，不返回半条记录")
        void unclosedQuote() {
            assertThatThrownBy(() -> parse("a,\"b", ','))
                    .isInstanceOf(DatavSourceException.class)
                    .hasMessageContaining("未闭合的引号");
        }
    }

    @Nested
    @DisplayName("形状不符时报错而不是硬猜")
    class Shape {

        @Test
        @DisplayName("列数比表头多说明分隔符选错，报出文件行号与两个列数")
        void widerThanHeaderIsAnError() {
            assertThatThrownBy(() -> parse("a,b\n1,2\n3,4,5", ','))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("第 3 行")
                    .hasMessageContaining("3 列")
                    .hasMessageContaining("2 列");
        }

        @Test
        @DisplayName("短行不报错（尾列缺值很常见），补 null 由取数层做")
        void shorterRowAllowed() {
            assertThat(parse("a,b,c\n1,2", ',')).containsExactly(List.of("a", "b", "c"), List.of("1", "2"));
        }

        @Test
        @DisplayName("列数超过上限直接拒绝")
        void tooManyColumns() {
            StringBuilder wide = new StringBuilder();
            for (int i = 0; i <= CsvParser.MAX_COLUMNS; i++) {
                wide.append(i).append(i == CsvParser.MAX_COLUMNS ? "" : ",");
            }
            assertThatThrownBy(() -> parse(wide.toString(), ','))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("上限");
        }

        @Test
        @DisplayName("记录数超上限时截断并标记，而不是把整张表读进堆")
        void truncatesAtLimit() {
            CsvParser.Result result = CsvParser.parse("a,b\n1,2\n3,4\n5,6", ',', 2);
            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.isTruncated()).isTrue();
            assertThat(result.getWidth()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("分隔符")
    class Delimiter {

        @Test
        @DisplayName("未配置时按首行嗅探，逗号/分号/制表符/竖线都认")
        void sniffsCommonDelimiters() {
            assertThat(CsvParser.sniffDelimiter("a;b;c\n1;2;3")).isEqualTo(';');
            assertThat(CsvParser.sniffDelimiter("a\tb\tc\n1\t2\t3")).isEqualTo('\t');
            assertThat(CsvParser.sniffDelimiter("a|b|c")).isEqualTo('|');
            assertThat(CsvParser.sniffDelimiter("a,b,c")).isEqualTo(',');
        }

        @Test
        @DisplayName("引号里的分隔符不参与嗅探")
        void quotedTextDoesNotSkewSniffing() {
            assertThat(CsvParser.sniffDelimiter("name,note\n\"x;y;z\",\"ok\"")).isEqualTo(',');
        }

        @Test
        @DisplayName("配置里写反斜杠 t 也认（表单里填不出真制表符）")
        void tabLiteral() {
            assertThat(CsvSourceConnector.resolveDelimiter(config("delimiter", "\\t"), "a,b")).isEqualTo('\t');
        }

        @Test
        @DisplayName("显式配置优先于嗅探")
        void explicitWinsOverSniff() {
            assertThat(CsvSourceConnector.resolveDelimiter(config("delimiter", ";"), "a,b,c")).isEqualTo(';');
        }

        @Test
        @DisplayName("多字符分隔符报错，不静默取第一个字符")
        void multiCharDelimiterRejected() {
            assertThatThrownBy(() -> CsvSourceConnector.resolveDelimiter(config("delimiter", ",,"), "a,b"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("单个字符");
        }
    }

    @Nested
    @DisplayName("编码判定")
    class Encoding {

        @Test
        @DisplayName("UTF-8 BOM 剥掉：不剥会变成第一列的列名，那一列永远选不中")
        void stripsUtf8Bom() {
            byte[] withBom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] body = "姓名,分数\n张三,90".getBytes(Charset.forName("UTF-8"));
            byte[] all = new byte[withBom.length + body.length];
            System.arraycopy(withBom, 0, all, 0, withBom.length);
            System.arraycopy(body, 0, all, withBom.length, body.length);

            assertThat(CsvSourceConnector.decode(all, null)).startsWith("姓名");
        }

        @Test
        @DisplayName("UTF-16 BOM 也认")
        void detectsUtf16Bom() {
            byte[] text = "a,b".getBytes(Charset.forName("UTF-16LE"));
            byte[] all = new byte[]{(byte) 0xFF, (byte) 0xFE};
            byte[] merged = new byte[all.length + text.length];
            System.arraycopy(all, 0, merged, 0, all.length);
            System.arraycopy(text, 0, merged, all.length, text.length);

            assertThat(CsvSourceConnector.decode(merged, null)).isEqualTo("a,b");
        }

        @Test
        @DisplayName("中文 Excel 直接另存的 CSV 是 GBK：按 GBK 配才解得开")
        void honoursConfiguredCharset() {
            byte[] gbk = "班级,课程\n初一(1)班,数学".getBytes(Charset.forName("GBK"));
            assertThat(CsvSourceConnector.decode(gbk, "GBK")).contains("初一(1)班");
        }

        @Test
        @DisplayName("GBK 内容按默认严格 UTF-8 解不开时，提示改 charset 而不是吐一串替换字符")
        void strictUtf8FailurePointsAtCharset() {
            byte[] gbk = "班级,课程\n初一(1)班,数学".getBytes(Charset.forName("GBK"));

            assertThatThrownBy(() -> CsvSourceConnector.decode(gbk, null))
                    .isInstanceOf(DatavSourceException.class)
                    .hasMessageContaining("GBK");
        }

        @Test
        @DisplayName("编码名写错归 config：那是配置项，不是数据问题")
        void unknownCharsetIsConfig() {
            assertThatThrownBy(() -> CsvSourceConnector.decode("a,b".getBytes(Charset.forName("UTF-8")), "NOPE-8"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("编码不支持");
        }

        @Test
        @DisplayName("空字节数组解成空串，由上层报「CSV 内容为空」")
        void emptyBody() {
            assertThat(CsvSourceConnector.decode(new byte[0], null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("列类型推断")
    class Columns {

        @Test
        @DisplayName("CSV 的值全是字符串，数值列仍要判成 number")
        void numericStringsInferAsNumber() {
            List<Map<String, Object>> rows = List.of(
                    Map.of("课程", "数学", "分数", "90"),
                    Map.of("课程", "英语", "分数", "85.5"));

            JSONObject snapshot = HttpSourceConnector.inferColumns(rows);
            List<JSONObject> columns = snapshot.getJSONArray("columns").toJavaList(JSONObject.class);

            assertThat(columns).anySatisfy(column -> assertThat(column.getString("fieldCode"))
                    .isEqualTo("分数"));
            assertThat(columns.stream().filter(c -> "分数".equals(c.getString("fieldCode"))).findFirst()
                    .orElseThrow().getString("dataType")).isEqualTo("number");
            assertThat(columns.stream().filter(c -> "课程".equals(c.getString("fieldCode"))).findFirst()
                    .orElseThrow().getString("dataType")).isEqualTo("text");
        }
    }

    private static JSONObject config(String key, Object value) {
        JSONObject config = new JSONObject();
        config.put(key, value);
        return config;
    }
}
