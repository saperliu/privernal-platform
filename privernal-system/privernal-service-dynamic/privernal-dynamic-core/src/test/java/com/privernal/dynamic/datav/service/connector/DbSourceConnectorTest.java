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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外部数据库数据源的安全闸门与形状契约测试（M3.5）
 * <p>
 * 全部用例都不碰真库：校验顺序被刻意排成「拒绝在建立连接之前」，所以每个错误路径都能离线断言。
 * 这同时也是在测顺序本身——如果把建池挪到白名单之前，这些用例会变成去连一个不存在的地址。
 *
 * @author saperliu
 */
@DisplayName("外部数据库数据源闸门测试")
class DbSourceConnectorTest {

    private DbSourceConnector enabled() {
        DbSourceConnector connector = new DbSourceConnector();
        connector.dbSourceEnabled = true;
        connector.hostWhitelist = "10.0.0.1,db.internal";
        return connector;
    }

    private JSONObject config() {
        JSONObject config = new JSONObject();
        config.put("dbType", "mysql");
        config.put("host", "10.0.0.1");
        config.put("port", 3306);
        config.put("database", "warehouse");
        config.put("table", "sales_order");
        config.put("userName", "report_ro");
        return config;
    }

    @Test
    @DisplayName("总开关关闭时直接拒绝，且提示要配哪个键")
    void refusesWhenDisabled() {
        DbSourceConnector connector = new DbSourceConnector();
        assertThat(connector.dbSourceEnabled).isFalse();
        assertThatThrownBy(() -> connector.fetchRows(config(), 100))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未启用")
                .hasMessageContaining("privernal.datav.db-source-enabled");
    }

    @Test
    @DisplayName("白名单为空等于谁都不许连，而不是等于不限制")
    void emptyWhitelistDeniesEverything() {
        DbSourceConnector connector = enabled();
        connector.hostWhitelist = "";
        assertThat(connector.isHostAllowed("10.0.0.1")).isFalse();
        assertThat(connector.isHostAllowed("8.8.8.8")).isFalse();
    }

    @Test
    @DisplayName("白名单精确匹配主机名，不做前缀或子串放行")
    void whitelistMatchesWholeHostOnly() {
        DbSourceConnector connector = enabled();
        assertThat(connector.isHostAllowed("10.0.0.1")).isTrue();
        assertThat(connector.isHostAllowed("db.internal")).isTrue();
        // 子串/前缀放行等于把 db.internal 扩成任意 db.internal.evil.com
        assertThat(connector.isHostAllowed("db.internal.evil.com")).isFalse();
        assertThat(connector.isHostAllowed("10.0.0.10")).isFalse();
        assertThat(connector.isHostAllowed("evil-db.internal")).isFalse();
    }

    @Test
    @DisplayName("未支持的 dbType 显式报错，不去猜一个能连的驱动")
    void rejectsUnknownDbType() {
        DbSourceConnector connector = enabled();
        JSONObject config = config();
        config.put("dbType", "postgresql");
        assertThatThrownBy(() -> connector.sampleColumns(config))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂不支持");
    }

    @Test
    @DisplayName("注入尝试全部标识符都要被拒，且错误不回显原值")
    void rejectsInjectionAttempts() {
        List<String> payloads = List.of(
                "t`; DROP TABLE user; --",
                "t` WHERE 1=1 UNION SELECT password FROM user --",
                "t' OR '1'='1",
                "t`--x",
                "1t",
                "t-1",
                "t;SELECT",
                "t` LIMIT 999999 --",
                "",
                "   ");
        for (String payload : payloads) {
            assertThatThrownBy(() -> DbSourceConnector.requireIdentifier(payload, "表名"))
                    .as("payload=%s", payload)
                    .isInstanceOf(ServiceException.class);
        }
    }

    /**
     * 主机名走独立规则的最初原因：{@code ^[A-Za-z]} 会把 IP 字面量全判成非法，
     * 于是外部库源只能填域名——而内网库大多只有 IP。这条用例就是钉住这个回归的。
     */
    @Test
    @DisplayName("IP 字面量与域名都是合法主机，但带 URL 结构的不是")
    void acceptsIpAndDomainButNotUrlPayloads() {
        assertThat(DbSourceConnector.requireHost("10.0.0.1")).isEqualTo("10.0.0.1");
        assertThat(DbSourceConnector.requireHost("192.168.10.10")).isEqualTo("192.168.10.10");
        assertThat(DbSourceConnector.requireHost("db.internal")).isEqualTo("db.internal");
        assertThat(DbSourceConnector.requireHost("mysql-8.prod-svc")).isEqualTo("mysql-8.prod-svc");
        // 首尾空白会被 trim 掉再校验：从表格复制地址常带空格，这里宽容是对的
        assertThat(DbSourceConnector.requireHost("  10.0.0.1  ")).isEqualTo("10.0.0.1");

        // 这些都能往 JDBC URL 里塞参数或改写主机段
        for (String payload : List.of("10.0.0.1?allowLoadLocalInfile=true", "10.0.0.1&x=1",
                "10.0.0.1#frag", "10.0.0.1/db", "10.0.0.1:3306", "user@10.0.0.1",
                "10.0.0.1 10.0.0.2", "10.0.0.1\nHost: evil", "", "   ")) {
            assertThatThrownBy(() -> DbSourceConnector.requireHost(payload))
                    .as("payload=%s", payload)
                    .isInstanceOf(ServiceException.class);
        }
        assertThatThrownBy(() -> DbSourceConnector.requireHost(null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("主机地址");
    }

    @Test
    @DisplayName("合法标识符原样通过，含下划线与数字后缀")
    void acceptsLegalIdentifiers() {
        assertThat(DbSourceConnector.requireIdentifier("sales_order", "表名")).isEqualTo("sales_order");
        assertThat(DbSourceConnector.requireIdentifier("T1", "表名")).isEqualTo("T1");
        assertThat(DbSourceConnector.requireIdentifier("  T1  ", "表名")).isEqualTo("T1");
    }

    @Test
    @DisplayName("缺表名/库名/用户名都在建连之前失败")
    void rejectsMissingRequiredFields() {
        DbSourceConnector connector = enabled();
        JSONObject noTable = config();
        noTable.remove("table");
        assertThatThrownBy(() -> connector.fetchRows(noTable, 10))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("表名");

        JSONObject noUser = config();
        noUser.remove("userName");
        assertThatThrownBy(() -> connector.fetchRows(noUser, 10))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("用户名");

        JSONObject badHost = config();
        badHost.put("host", "192.168.1.9");
        assertThatThrownBy(() -> connector.fetchRows(badHost, 10))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在允许连接清单内");
    }

    @Test
    @DisplayName("列白名单逐项校验，超长直接拒绝")
    void validatesColumnWhitelist() {
        DbSourceConnector connector = enabled();
        JSONObject bad = config();
        bad.put("columns", JSONArray.of("amount", "user_id", "name; DROP TABLE x"));
        assertThatThrownBy(() -> connector.fetchRows(bad, 10))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("列名");

        JSONObject tooMany = config();
        List<Object> columns = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            columns.add("c" + i);
        }
        tooMany.put("columns", new JSONArray(columns));
        assertThatThrownBy(() -> connector.fetchRows(tooMany, 10))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("上限");
    }

    @Test
    @DisplayName("SQL 只由连接器拼：整表走 *，白名单列逐个反引号包裹")
    void buildsProjectionFromWhitelistOnly() {
        DbSourceConnector connector = enabled();
        DbSourceConnector.Vetting vetting = new DbSourceConnector.Vetting();
        vetting.table = "sales_order";
        vetting.timeoutMs = 1000;

        vetting.columns = List.of();
        assertThat(connector.buildSelect(vetting, 1000)).isEqualTo("SELECT * FROM `sales_order` LIMIT 1000");

        vetting.columns = List.of("order_date", "amount");
        assertThat(connector.buildSelect(vetting, 1))
                .isEqualTo("SELECT `order_date`, `amount` FROM `sales_order` LIMIT 1");
        // limit 下限为 1：LIMIT 0 会让列快照拿不到任何行，抽样契约要求真实抽样
        assertThat(connector.buildSelect(vetting, 0)).endsWith("LIMIT 1");
    }

    @Test
    @DisplayName("超时取配置值与硬上限的较小者，非法值回落到硬上限")
    void clampsTimeout() {
        DbSourceConnector connector = enabled();
        assertThat(connector.clampTimeout(null)).isEqualTo(15_000);
        assertThat(connector.clampTimeout(0)).isEqualTo(15_000);
        assertThat(connector.clampTimeout(-3)).isEqualTo(15_000);
        assertThat(connector.clampTimeout(30_000)).isEqualTo(15_000);
        assertThat(connector.clampTimeout(2000)).isEqualTo(2000);
        assertThat(DbSourceConnector.toSeconds(1)).isEqualTo(1);
        assertThat(DbSourceConnector.toSeconds(1501)).isEqualTo(2);
    }

    @Test
    @DisplayName("JDBC 值归一：日期时间转字符串、数值原样、二进制丢弃")
    void normalizesJdbcValues() {
        assertThat(DbSourceConnector.normalize(null)).isNull();
        assertThat(DbSourceConnector.normalize(new BigDecimal("12.50"))).isEqualTo(new BigDecimal("12.50"));
        assertThat(DbSourceConnector.normalize(42)).isEqualTo(42);
        assertThat(DbSourceConnector.normalize(Boolean.TRUE)).isEqualTo(Boolean.TRUE);
        assertThat(DbSourceConnector.normalize("abc")).isEqualTo("abc");

        java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf("2026-10-18 08:49:54");
        // 必须归一到秒且无尾随 .0：聚合层的时间粒度解析取前 10 位，两种形态都能解，
        // 但作为维度标签输出时 .0 会直接出现在图表 X 轴上
        assertThat(DbSourceConnector.normalize(timestamp)).isEqualTo("2026-10-18 08:49:54");
        assertThat(DbSourceConnector.normalize(java.sql.Date.valueOf("2026-10-18"))).isEqualTo("2026-10-18");
        assertThat(DbSourceConnector.normalize(java.sql.Time.valueOf("08:49:54"))).isEqualTo("08:49:54");
        assertThat(DbSourceConnector.normalize(new byte[]{1, 2, 3})).isNull();

        // 新版驱动返回的就是这几个 java.time 类型，必须和 java.sql 版本同形
        assertThat(DbSourceConnector.normalize(java.time.LocalDateTime.of(2026, 10, 18, 8, 49, 54)))
                .isEqualTo("2026-10-18 08:49:54");
        assertThat(DbSourceConnector.normalize(java.time.LocalDateTime.of(2026, 10, 18, 8, 0, 0)))
                .isEqualTo("2026-10-18 08:00:00");
        assertThat(DbSourceConnector.normalize(java.time.LocalDate.of(2026, 10, 18)))
                .isEqualTo("2026-10-18");
        assertThat(DbSourceConnector.normalize(java.time.LocalTime.of(8, 49, 54)))
                .isEqualTo("08:49:54");
    }

    @Test
    @DisplayName("列类型映射到平台的 number/date/text 口径")
    void mapsSqlTypesToDatavTypes() {
        assertThat(DbSourceConnector.toDatavType(Types.INTEGER)).isEqualTo("number");
        assertThat(DbSourceConnector.toDatavType(Types.DECIMAL)).isEqualTo("number");
        assertThat(DbSourceConnector.toDatavType(Types.DOUBLE)).isEqualTo("number");
        assertThat(DbSourceConnector.toDatavType(Types.TIMESTAMP)).isEqualTo("date");
        assertThat(DbSourceConnector.toDatavType(Types.DATE)).isEqualTo("date");
        assertThat(DbSourceConnector.toDatavType(Types.VARCHAR)).isEqualTo("text");
        // Boolean 归 text：它是维度不是度量，聚合层对非 Number 求和会明确报错
        assertThat(DbSourceConnector.toDatavType(Types.BIT)).isEqualTo("text");
    }

    @Test
    @DisplayName("异常按 MySQL 错误码分类，且响应文案不含主机/库名/SQL")
    void classifiesAndMasksSqlErrors() {
        DbSourceConnector connector = enabled();

        assertThat(connector.describe(new SQLException(
                "Access denied for user 'report_ro'@'10.42.7.13' (using password: YES)", "28000", 1045)))
                .hasMessageContaining("账号或密码不正确")
                .hasMessageNotContaining("report_ro")
                .hasMessageNotContaining("10.42.7.13");

        assertThat(connector.describe(new SQLException(
                "Table 'warehouse.sales_order' doesn't exist", "42S02", 1146)))
                .hasMessageContaining("找不到这个库或表")
                .hasMessageNotContaining("warehouse");

        assertThat(connector.describe(new SQLException(
                "Communications link failure to db.internal:3306", "08S01", 0)))
                .hasMessageContaining("无法连接")
                .hasMessageNotContaining("db.internal");

        assertThat(connector.describe(new SQLException(
                "Lock wait timeout exceeded; try restarting transaction", "HY000", 1205)))
                .hasMessageContaining("超时");

        assertThat(connector.describe(new SQLException("boom", "HY000", 9999)))
                .hasMessageContaining("服务日志")
                .hasMessageNotContaining("boom");
    }

    /**
     * 健康检查靠 category 分派处置建议，所以分类必须能被读回来（M3.9）。
     * 文案给人看、category 给程序判断，两者一旦分叉前端就会给错建议。
     */
    @Test
    @DisplayName("错误分类带 category：鉴权、配置、网络、未知各归其类")
    void errorsCarryCategory() {
        DbSourceConnector connector = enabled();

        assertThat(categoryOf(connector, 1045, "28000", "Access denied"))
                .isEqualTo(DatavSourceException.AUTH);
        // 1044/1142 是没授权，1146/1049 是名字写错——两者处置完全不同，不能合成一句
        assertThat(categoryOf(connector, 1044, "42000", "Access denied for database"))
                .isEqualTo(DatavSourceException.AUTH);
        assertThat(categoryOf(connector, 1146, "42S02", "Table doesn't exist"))
                .isEqualTo(DatavSourceException.CONFIG);
        assertThat(categoryOf(connector, 1049, "42000", "Unknown database"))
                .isEqualTo(DatavSourceException.CONFIG);
        assertThat(categoryOf(connector, 0, "08S01", "Communications link failure"))
                .isEqualTo(DatavSourceException.NETWORK);
        assertThat(categoryOf(connector, 3024, "HY000", "Query timeout"))
                .isEqualTo(DatavSourceException.NETWORK);
        assertThat(categoryOf(connector, 9999, "HY000", "something else"))
                .isEqualTo(DatavSourceException.UNKNOWN);
    }

    @Test
    @DisplayName("配置类错误也要带 category，且主机名不外泄")
    void configErrorsCarryCategory() {
        DbSourceConnector connector = enabled();
        JSONObject config = config();
        config.put("dbType", "postgresql");
        assertThatThrownBy(() -> connector.fetchRows(config, 10))
                .isInstanceOf(DatavSourceException.class)
                .extracting(e -> ((DatavSourceException) e).getCategory())
                .isEqualTo(DatavSourceException.CONFIG);

        JSONObject offWhitelist = config();
        offWhitelist.put("host", "192.168.1.9");
        // 这里刻意把主机回显给用户：它是用户自己刚填的、且已过 HOST 校验，
        // 告诉他「哪一项没被放行」比模糊成「不允许连接」更有用。
        // 与之相对，JDBC 带回来的异常一律不回显（那里面有账号、库名和 SQL 片段）。
        assertThatThrownBy(() -> connector.fetchRows(offWhitelist, 10))
                .isInstanceOf(DatavSourceException.class)
                .hasMessageContaining("192.168.1.9")
                // 归 config 不归 auth：category 决定界面给的建议，报鉴权会把人支去改密码，而该改的是地址
                .extracting(e -> ((DatavSourceException) e).getCategory())
                .isEqualTo(DatavSourceException.CONFIG);
    }

    @Test
    @DisplayName("健康检查返回分类而不是裸异常，且失败时不炸调用方")
    void healthReportsCategoryInsteadOfThrowing() {
        // 用 127.0.0.1:1 —— 一定没人监听，操作系统立刻拒连，几毫秒就有结果。
        // 换成一个不可路由的地址会让这个用例挂 15 秒等超时，测试套件里不该有这种隐性依赖。
        DbSourceConnector connector = enabled();
        connector.hostWhitelist = "127.0.0.1";
        JSONObject broken = config();
        broken.put("host", "127.0.0.1");
        broken.put("port", 1);
        // 连不上时 sampleColumns 会抛，health 必须把它变成一次结构化返回而不是把异常丢给调用方
        JSONObject result = connector.health(broken);
        assertThat(result.getBoolean("ok")).isFalse();
        assertThat(result.getString("category")).isIn(
                DatavSourceException.NETWORK, DatavSourceException.AUTH,
                DatavSourceException.CONFIG, DatavSourceException.UNKNOWN);
        assertThat(result.getString("message")).isNotBlank();

        JSONObject disabled = new DbSourceConnector().health(config());
        assertThat(disabled.getString("category")).isEqualTo(DatavSourceException.CONFIG);
        assertThat(disabled.getString("message")).contains("未启用");
    }

    private static String categoryOf(DbSourceConnector connector, int code, String state, String message) {
        return ((DatavSourceException) connector.describe(new SQLException(message, state, code))).getCategory();
    }

    @Test
    @DisplayName("连接器按 db 类型注册，能被分派器识别为外部源")
    void registersAsExternalSourceType() {
        assertThat(new DbSourceConnector().type()).isEqualTo(DbSourceConnector.TYPE);
        assertThat(DbSourceConnector.TYPE).isEqualTo("db");
    }

    @Test
    @DisplayName("空 sourceConfig 与缺字段的配置都在建连前失败")
    void rejectsEmptyConfig() {
        DbSourceConnector connector = enabled();
        assertThatThrownBy(() -> connector.fetchRows(null, 10))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("sourceConfig");

        JSONObject empty = new JSONObject();
        assertThatThrownBy(() -> connector.fetchRows(empty, 10))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("invalidate 只按不含密码的连接坐标匹配，null 配置不炸")
    void invalidatesByCoordinatesWithoutThrowing() {
        DbSourceConnector connector = enabled();
        connector.invalidate(null);
        connector.invalidate(config());
        assertThat(connector.pools).isEmpty();
    }
}
