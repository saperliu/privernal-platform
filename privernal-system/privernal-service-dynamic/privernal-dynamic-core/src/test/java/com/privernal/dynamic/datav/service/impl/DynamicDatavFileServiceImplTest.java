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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CSV 上传准入校验测试
 * <p>
 * 判据是扩展名与字节数，不是浏览器给的 MIME：按 MIME 拦会把 .csv 报成 application/octet-stream
 * 的正常文件挡在门外，按 MIME 放又等于让提交方自证清白。这里把这两条口径同时钉住——
 * 顺带钉住展示名的清洗，它会进日志与列表标题，留一个换行就能把一条日志写成两条。
 *
 * @author saperliu
 */
@DisplayName("CSV 上传准入校验测试")
class DynamicDatavFileServiceImplTest {

    @Nested
    @DisplayName("扩展名")
    class Extension {

        @Test
        @DisplayName("只认 .csv 与 .txt，大小写无所谓")
        void acceptsCsvAndTxt() {
            assertThatCode(() -> DynamicDatavFileServiceImpl.requireAllowedExtension("订单.csv"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> DynamicDatavFileServiceImpl.requireAllowedExtension("EXPORT.TXT"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Excel 原表、脚本与压缩包一律拒绝")
        void rejectsOtherExtensions() {
            for (String name : new String[]{"数据.xlsx", "payload.jsp", "archive.csv.zip", "run.sh"}) {
                assertThatThrownBy(() -> DynamicDatavFileServiceImpl.requireAllowedExtension(name))
                        .as(name)
                        .isInstanceOf(ServiceException.class)
                        .hasMessageContaining("csv");
            }
        }

        @Test
        @DisplayName("没有扩展名与隐藏文件名都算不合法")
        void rejectsNameWithoutRealExtension() {
            assertThat(DynamicDatavFileServiceImpl.extensionOf("data")).isEmpty();
            assertThat(DynamicDatavFileServiceImpl.extensionOf(".csv")).isEmpty();
            assertThat(DynamicDatavFileServiceImpl.extensionOf(null)).isEmpty();
            assertThatThrownBy(() -> DynamicDatavFileServiceImpl.requireAllowedExtension(".csv"))
                    .isInstanceOf(ServiceException.class);
        }
    }

    @Nested
    @DisplayName("体积")
    class Size {

        @Test
        @DisplayName("2MB 整可收，多一字节就拒：与 HTTP 取数同一个天花板")
        void capsAtTwoMegabytes() {
            assertThat(DynamicDatavFileServiceImpl.MAX_UPLOAD_BYTES).isEqualTo(2L * 1024 * 1024);
            assertThatCode(() -> DynamicDatavFileServiceImpl.requireWithinSizeLimit(DynamicDatavFileServiceImpl.MAX_UPLOAD_BYTES))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> DynamicDatavFileServiceImpl.requireWithinSizeLimit(
                    DynamicDatavFileServiceImpl.MAX_UPLOAD_BYTES + 1))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("2MB");
        }
    }

    @Nested
    @DisplayName("展示名清洗")
    class FileName {

        @Test
        @DisplayName("剥掉路径分隔与控制字符，只留文件名")
        void stripsPathAndControlChars() {
            assertThat(DynamicDatavFileServiceImpl.displayName("/etc/passwd.csv")).isEqualTo("passwd.csv");
            assertThat(DynamicDatavFileServiceImpl.displayName("C:\\temp\\a.csv")).isEqualTo("a.csv");
            assertThat(DynamicDatavFileServiceImpl.displayName("a\r\n.csv")).isEqualTo("a.csv");
        }

        @Test
        @DisplayName("超长截到列宽、清洗后为空给个兜底名：这一列只用于展示，不该因为脏名字落不进库")
        void truncatesAndFallbacks() {
            String longName = "x".repeat(300) + ".csv";
            assertThat(DynamicDatavFileServiceImpl.displayName(longName)).hasSize(255);
            assertThat(DynamicDatavFileServiceImpl.displayName("   ")).isEqualTo("data.csv");
            assertThat(DynamicDatavFileServiceImpl.displayName(null)).isEqualTo("data.csv");
        }
    }
}
