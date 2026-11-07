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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CSV 上传形态的取数测试（私有化交付）
 * <p>
 * 这一批只钉两件事：形态分支选得对（没有 storage 键的历史配置必须继续走 URL），
 * 以及两条传输在字节之后共用同一份解释逻辑——否则同一个文件走 URL 能出图、走上传出错表，
 * 而界面上一模一样。不起 Spring、不连库：文件服务与 HTTP 连接器都用同包内的桩替掉。
 *
 * @author saperliu
 */
@DisplayName("CSV 上传形态取数测试")
class CsvUploadSourceTest {

    /** 只回一份固定字节；被调用就说明分支走错了 */
    private static final class StubHttpConnector extends HttpSourceConnector {

        private final byte[] body;
        private final boolean[] called;

        StubHttpConnector(byte[] body, boolean[] called) {
            this.body = body;
            this.called = called;
        }

        @Override
        byte[] fetchBodyBytes(JSONObject sourceConfig) {
            called[0] = true;
            return body;
        }
    }

    /** 按 fileId 给字节；null 表示查不到行（不存在、已删除或不属于当前组织） */
    private static final class StubFileService implements com.privernal.dynamic.datav.service.IDynamicDatavFileService {

        private final Map<String, byte[]> store;
        private final java.util.List<String> requested = new java.util.ArrayList<>();

        StubFileService(Map<String, byte[]> store) {
            this.store = store;
        }

        @Override
        public com.privernal.dynamic.datav.domain.DynamicDatavFile store(org.springframework.web.multipart.MultipartFile file,
                                                                   String datasetCode) {
            throw new UnsupportedOperationException("上传落库不在本测试范围");
        }

        @Override
        public byte[] readContent(String fileCode) {
            requested.add(fileCode);
            return store.get(fileCode);
        }

        @Override
        public int bindDataset(String fileCode, String datasetCode) {
            return 0;
        }

        @Override
        public int deleteByFileCode(String fileCode) {
            return 0;
        }

        @Override
        public int deleteByDatasetCode(String datasetCode) {
            return 0;
        }
    }

    private static final String CSV = "课程,分数\n数学,90\n英语,85.5\n";

    private static CsvSourceConnector uploadConnector(byte[] bytes) {
        boolean[] touched = {false};
        return new CsvSourceConnector(new StubHttpConnector(new byte[0], touched),
                new StubFileService(Map.of("f1", bytes)));
    }

    @Nested
    @DisplayName("形态判定")
    class Storage {

        @Test
        @DisplayName("没有 storage 键就是 URL 形态：历史配置一字不用迁移")
        void absentStorageMeansUrl() {
            assertThat(CsvSourceConnector.isUploadMode(config("url", "https://example.com/a.csv"))).isFalse();
            assertThat(CsvSourceConnector.isUploadMode(null)).isFalse();
            assertThat(CsvSourceConnector.isUploadMode(config("storage", "url"))).isFalse();
        }

        @Test
        @DisplayName("storage=upload 才走文件表，大小写与空格不敏感")
        void uploadIsCaseInsensitive() {
            assertThat(CsvSourceConnector.isUploadMode(config("storage", "upload"))).isTrue();
            assertThat(CsvSourceConnector.isUploadMode(config("storage", " UPLOAD "))).isTrue();
        }

        @Test
        @DisplayName("上传形态一行网络都不碰；URL 形态一行文件表都不碰")
        void branchesAreExclusive() {
            boolean[] touched = {false};
            StubFileService files = new StubFileService(Map.of("f1", CSV.getBytes(Charset.forName("UTF-8"))));
            CsvSourceConnector connector = new CsvSourceConnector(
                    new StubHttpConnector(CSV.getBytes(Charset.forName("UTF-8")), touched), files);

            assertThat(connector.fetchRows(config("storage", "upload", "fileId", "f1"), 100)).hasSize(2);
            assertThat(touched).containsExactly(false);
            assertThat(files.requested).containsExactly("f1");

            touched[0] = false;
            files.requested.clear();
            JSONObject urlConfig = new JSONObject();
            urlConfig.put("url", "https://example.com/a.csv");
            assertThat(connector.fetchRows(urlConfig, 100)).hasSize(2);
            assertThat(touched).containsExactly(true);
            assertThat(files.requested).isEmpty();
        }
    }

    @Nested
    @DisplayName("上传字节照样走完 CSV 解释")
    class Interpretation {

        @Test
        @DisplayName("表头 + 数值列推断：列名与类型都来自共用的一份逻辑")
        void parsesRowsAndInfersColumns() {
            List<Map<String, Object>> rows = uploadConnector(CSV.getBytes(Charset.forName("UTF-8")))
                    .fetchRows(config("storage", "upload", "fileId", "f1"), 100);
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsEntry("课程", "数学");
            // CSV 里数值是字符串，推断必须仍然认成 number，否则上传源一个度量都拖不进图表
            assertThat(rows.get(1).get("分数")).isEqualTo("85.5");

            JSONArray columns = uploadConnector(CSV.getBytes(Charset.forName("UTF-8")))
                    .sampleColumns(config("storage", "upload", "fileId", "f1")).getJSONArray("columns");
            assertThat(columns.getJSONObject(0).getString("fieldCode")).isEqualTo("课程");
            assertThat(columns.getJSONObject(1).getString("dataType")).isEqualTo("number");
        }

        @Test
        @DisplayName("中文 Excel 另存的 GBK：上传形态同样按 charset 解，与 URL 形态同一条解码路径")
        void gbkUpload() {
            byte[] gbk = "班级,课程\n初一(1)班,数学".getBytes(Charset.forName("GBK"));
            List<Map<String, Object>> rows = uploadConnector(gbk)
                    .fetchRows(config("storage", "upload", "fileId", "f1", "charset", "GBK"), 100);
            assertThat(rows.get(0)).containsEntry("班级", "初一(1)班");
        }

        @Test
        @DisplayName("分隔符嗅探与首行表头对两种形态结果一致")
        void sameInterpretationForBothTransports() {
            byte[] semicolon = "a;b;c\n1;2;3".getBytes(Charset.forName("UTF-8"));
            boolean[] touched = {false};
            CsvSourceConnector connector = new CsvSourceConnector(new StubHttpConnector(semicolon, touched),
                    new StubFileService(Map.of("f1", semicolon)));

            JSONObject upload = config("storage", "upload", "fileId", "f1");
            JSONObject url = config("url", "https://example.com/a.csv");
            assertThat(connector.sampleColumns(upload)).isEqualTo(connector.sampleColumns(url));
        }

        @Test
        @DisplayName("没有表头时按 colN 命名列")
        void anonymousHeaderRow() {
            byte[] body = "1,2\n3,4".getBytes(Charset.forName("UTF-8"));
            List<Map<String, Object>> rows = uploadConnector(body)
                    .fetchRows(config("storage", "upload", "fileId", "f1", "headerRow", false), 100);
            assertThat(rows.get(0)).containsKeys("col1", "col2");
        }
    }

    @Nested
    @DisplayName("缺文件与坏配置")
    class Failure {

        @Test
        @DisplayName("上传形态没给 fileId 归 config，不去碰文件表也不去碰网络")
        void missingFileId() {
            boolean[] touched = {false};
            CsvSourceConnector connector = new CsvSourceConnector(new StubHttpConnector(new byte[0], touched),
                    new StubFileService(Map.of()));
            assertThatThrownBy(() -> connector.fetchRows(config("storage", "upload"), 100))
                    .isInstanceOf(DatavSourceException.class)
                    .hasMessageContaining("fileId");
            assertThat(touched).containsExactly(false);
        }

        @Test
        @DisplayName("fileId 查不到行时报「重新上传」而不是「连不上」：该动的是配置，不是网络")
        void unknownFileCode() {
            CsvSourceConnector connector = new CsvSourceConnector(
                    new StubHttpConnector(new byte[0], new boolean[1]), new StubFileService(Map.of()));
            assertThatThrownBy(() -> connector.fetchRows(config("storage", "upload", "fileId", "gone"), 100))
                    .isInstanceOf(DatavSourceException.class)
                    .hasMessageContaining("重新上传");
        }

        @Test
        @DisplayName("错误文案里不带文件名与内容：日志与响应都不是看这些的地方")
        void messageDoesNotLeakFileContent() {
            CsvSourceConnector connector = new CsvSourceConnector(
                    new StubHttpConnector(new byte[0], new boolean[1]),
                    new StubFileService(Map.of("f1", "秘密,9001".getBytes(Charset.forName("UTF-8")))));
            assertThatThrownBy(() -> connector.fetchRows(
                    config("storage", "upload", "fileId", "f1", "charset", "NOPE-8"), 100))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("编码不支持")
                    .hasMessageNotContaining("9001");
        }
    }

    @Nested
    @DisplayName("健康检查与凭据判定")
    class HealthAndSecrets {

        @Test
        @DisplayName("health() 走 sampleColumns，上传形态不用另写一遍")
        void healthWorksForUpload() {
            JSONObject result = uploadConnector(CSV.getBytes(Charset.forName("UTF-8")))
                    .health(config("storage", "upload", "fileId", "f1"));
            assertThat(result.getBooleanValue("ok")).isTrue();
            assertThat(result.getString("category")).isEqualTo("ok");
            assertThat(result.getJSONArray("columns")).hasSize(2);
        }

        @Test
        @DisplayName("health() 对缺文件也是 ok:false + config，而不是抛出去")
        void healthReportsMissingFile() {
            CsvSourceConnector connector = new CsvSourceConnector(
                    new StubHttpConnector(new byte[0], new boolean[1]), new StubFileService(Map.of()));
            JSONObject result = connector.health(config("storage", "upload", "fileId", "gone"));
            assertThat(result.getBooleanValue("ok")).isFalse();
            assertThat(result.getString("category")).isEqualTo(DatavSourceException.CONFIG);
        }

        @Test
        @DisplayName("fileId 不是凭据：seal/mask 都不动这份配置")
        void uploadConfigHasNoSecrets() {
            JSONObject upload = config("storage", "upload", "fileId", "csv_abc", "fileName", "订单.csv");
            assertThat(DatavSourceSecrets.hasSecrets(upload)).isFalse();
            assertThat(DatavSourceSecrets.isSecretKey("fileId")).isFalse();
            // 主密钥传 null：配置里没有任何凭据时 seal 不该碰它，否则这条路径在没有主密钥的部署里连保存都做不到
            assertThat(DatavSourceSecrets.seal(upload, null, null)).isEqualTo(upload);
            assertThat(DatavSourceSecrets.mask(upload)).isEqualTo(upload);
        }
    }

    private static JSONObject config(Object... pairs) {
        JSONObject config = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            config.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return config;
    }
}
