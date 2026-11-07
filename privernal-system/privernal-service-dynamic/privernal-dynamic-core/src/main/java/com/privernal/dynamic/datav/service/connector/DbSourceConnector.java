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

import static com.privernal.dynamic.datav.service.connector.DatavSourceException.AUTH;
import static com.privernal.dynamic.datav.service.connector.DatavSourceException.CONFIG;
import static com.privernal.dynamic.datav.service.connector.DatavSourceException.LIMIT;
import static com.privernal.dynamic.datav.service.connector.DatavSourceException.NETWORK;
import static com.privernal.dynamic.datav.service.connector.DatavSourceException.UNKNOWN;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.api.ISecretCipherService;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 外部数据库数据源连接器（M3.4 + M3.5）。
 * <p>
 * 刻意**不接**平台的 {@code @DS} 动态数据源路由：那套路由只在启动时从 {@code dynamic_database}
 * 批量建池，且 {@code DialectAutoConfiguration} 会把探测到的方言写进静态 {@code DynamicTypeContext}。
 * 为一个 BI 数据源去注册/切换全局数据源，等于让大屏配置页有能力改写主业务库的 SQL 生成口径。
 * 因此这里自持一个按指纹缓存的只读小池，只负责「一条受控 SQL → 行集合」，
 * 聚合/过滤/排序仍走 {@link com.privernal.dynamic.datav.service.DatavRowAggregator}，与 HTTP 源完全对称。
 * <p>
 * 安全口径（与功能同批，不做「先上线后加固」）：
 * <ul>
 *   <li>能力默认关闭，且主机白名单为空时一律拒绝——外部库直连天生是内网可达能力，
 *       不能套用 HTTP 源的「拒内网」逻辑（那会让功能毫无用处），改成运维显式放行哪些主机</li>
 *   <li>SQL 由连接器拼出来，不接受用户提交的 SQL 文本；表名/列名/库名一律过标识符白名单，
 *       所以注入在结构上无从谈起，而不是「校验挡住了」</li>
 *   <li>三重只读：池 defaultReadOnly、连接 setReadOnly、语句只允许 SELECT 形态</li>
 *   <li>行数与超时都是硬上限：setMaxRows + LIMIT + queryTimeout + 驱动 socketTimeout，
 *       少任何一层都存在「一个慢查询挂住整个大屏取数线程」的窗口</li>
 *   <li>JDBC 异常消息会带主机、库名甚至 SQL 片段，一律翻译成分类提示，原文只进日志</li>
 * </ul>
 * <p>
 * 驱动面：本模块 classpath 只有 MySQL 驱动（{@code mssql-jdbc} 声明在 system/message 模块，
 * PostgreSQL 驱动整个仓库都没有），所以其他 dbType 显式报「暂不支持」而不是猜一个能连上。
 *
 * @author saperliu
 */
@Slf4j
@Component
public class DbSourceConnector implements DatavSourceConnector {

    public static final String TYPE = "db";

    /** 与 DynamicReportQueryService 同一口径：不匹配就拒绝，不做「看起来安全就放行」 */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    /**
     * 主机名单独一套规则，不能复用 {@link #IDENTIFIER}：那个要求首字符是字母，
     * 会把 {@code 10.0.0.1} 这类 IP 字面量全判成非法——外部库源就只剩域名能填了。
     * 主机不进 SQL（只进 JDBC URL），要防的是 URL 参数注入，所以禁掉 ? & # / @ 空格 引号 反斜杠即可。
     */
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    private static final int HARD_TIMEOUT_MS = 15_000;

    private static final int DEFAULT_PORT = 3306;

    /** 从池里等一个连接的最长时间：坏源最坏卡这么久，而不是 Druid 默认的无穷等待 */
    private static final int POOL_WAIT_MS = 8_000;

    /** 连接失败后的冷却时长：挡住「一块屏几十个组件轮着撞同一个坏密码」的取数风暴 */
    private static final long CONNECT_COOLDOWN_MS = 30_000L;

    /** 单个外部源的池上限：大屏取数是串行批量，4 个连接足够，且绝不许把别人的库打满 */
    private static final int MAX_POOL_SIZE = 4;

    /** 外部库直连总开关。默认关闭：它是横向访问内网数据库的能力，开启必须是运维的显式动作 */
    @Value("${privernal.datav.db-source-enabled:false}")
    boolean dbSourceEnabled;

    /** 允许连接的主机，逗号分隔；为空表示谁都不许连（即使总开关已开） */
    @Value("${privernal.datav.db-host-whitelist:}")
    String hostWhitelist;

    /** 包级可见：端到端测试要塞一个假实现，验证密文凭据在外部库源上同样走得通 */
    @Autowired
    ISecretCipherService secretCipher;

    final Map<String, ManagedPool> pools = new ConcurrentHashMap<>();

    /** 指纹 → 冷却截止时间。只记失败时间，不记异常内容，避免把内部拓扑攒在内存里 */
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public List<Map<String, Object>> fetchRows(JSONObject sourceConfig, int maxRows) {
        Vetting vet = vet(sourceConfig);
        String sql = buildSelect(vet, maxRows);
        List<Map<String, Object>> rows = new ArrayList<>();
        long started = System.currentTimeMillis();
        // try-with-resources 关连接即归还池：Druid 的 Connection.close() 就是回池动作，
        // 不需要也别调 releaseConnection(包装连接)——传 null 只会炸在 finally 里
        try (Connection connection = vet.pool.dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // 池的 defaultReadOnly 只在新建连接时生效，借来的连接再显式设一次做双层防护
            connection.setReadOnly(true);
            statement.setMaxRows(maxRows);
            statement.setQueryTimeout(toSeconds(vet.timeoutMs));
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData meta = resultSet.getMetaData();
                int columnCount = meta.getColumnCount();
                while (resultSet.next() && rows.size() < maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), normalize(resultSet.getObject(i)));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw describe(e);
        }
        if (rows.size() >= maxRows) {
            log.warn("外部库数据源取数已达行数上限并被截断：table={} maxRows={} cost={}ms",
                    vet.table, maxRows, System.currentTimeMillis() - started);
        }
        return rows;
    }

    @Override
    public JSONObject sampleColumns(JSONObject sourceConfig) {
        Vetting vet = vet(sourceConfig);
        // 列类型取自 JDBC 元数据而不是抽样值推断：这是权威信息，比 HTTP 源那套字符串猜测更准。
        // LIMIT 1 而不是 LIMIT 0 是为了满足连接器契约里的「真实抽样」——顺带证明这张表能查。
        String sql = buildSelect(vet, 1);
        JSONArray columns = new JSONArray();
        try (Connection connection = vet.pool.dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // 池的 defaultReadOnly 只在新建连接时生效，借来的连接再显式设一次做双层防护
            connection.setReadOnly(true);
            statement.setMaxRows(1);
            statement.setQueryTimeout(toSeconds(vet.timeoutMs));
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData meta = resultSet.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    JSONObject column = new JSONObject();
                    column.put("fieldCode", meta.getColumnLabel(i));
                    column.put("fieldName", meta.getColumnLabel(i));
                    column.put("dataType", toDatavType(meta.getColumnType(i)));
                    columns.add(column);
                }
            }
        } catch (SQLException e) {
            throw describe(e);
        }
        JSONObject snapshot = new JSONObject();
        snapshot.put("columns", columns);
        return snapshot;
    }

    // ---------------- 配置解释与安全校验 ----------------

    /**
     * 把 sourceConfig 解释成可执行的受控查询描述：全程不信任任何一个字段。
     * 明文密码只在本方法栈内存在（{@link DatavSourceSecrets#open} 还原后即丢弃）。
     */
    Vetting vet(JSONObject sourceConfig) {
        if (!dbSourceEnabled) {
            throw fail(CONFIG, "外部数据库数据源未启用（需配置 privernal.datav.db-source-enabled=true）");
        }
        if (sourceConfig == null) {
            throw fail(CONFIG, "外部数据库数据源缺少 sourceConfig");
        }
        String dbType = StringUtils.defaultIfBlank(sourceConfig.getString("dbType"), "mysql").toLowerCase();
        if (!"mysql".equals(dbType)) {
            throw fail(CONFIG, "外部数据库数据源暂不支持该类型：" + dbType + "（当前仅支持 mysql）");
        }
        String host = requireHost(sourceConfig.getString("host"));
        if (!isHostAllowed(host)) {
            // 归 CONFIG 不归 AUTH：category 决定界面给的建议，报鉴权会把人支去改密码，而该改的是地址
            throw fail(CONFIG, "外部数据库主机不在允许连接清单内：" + host);
        }
        int port = sourceConfig.getIntValue("port");
        if (port <= 0 || port > 65535) {
            port = DEFAULT_PORT;
        }
        String database = requireIdentifier(sourceConfig.getString("database"), "数据库名");
        String table = requireIdentifier(sourceConfig.getString("table"), "表名");
        List<String> columns = resolveColumns(sourceConfig.getJSONArray("columns"));
        String userName = StringUtils.trimToEmpty(sourceConfig.getString("userName"));
        if (StringUtils.isBlank(userName)) {
            throw fail(CONFIG, "外部数据库数据源缺少用户名");
        }

        JSONObject plain = DatavSourceSecrets.open(sourceConfig, secretCipher);
        String password = StringUtils.defaultString(plain.getString("password"));
        // 默认走明文（内网 MySQL 常见形态）；要求加密的连接必须在配置里显式写 useSsl=true
        boolean useSsl = plain.getBooleanValue("useSsl");

        ManagedPool pool = acquirePool(dbType, host, port, database, userName, password, useSsl);
        Vetting vetting = new Vetting();
        vetting.pool = pool;
        vetting.table = table;
        vetting.columns = columns;
        vetting.timeoutMs = clampTimeout(sourceConfig.getIntValue("timeoutMs"));
        return vetting;
    }

    /** 白名单为空 ⇒ 全拒。配置项误留空时，后果必须是功能不可用，而不是等于没设限 */
    boolean isHostAllowed(String host) {
        String whitelist = hostWhitelist == null ? "" : hostWhitelist.trim();
        if (whitelist.isEmpty()) {
            return false;
        }
        for (String item : whitelist.split(",")) {
            if (host.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 列白名单为空表示整表；非空时逐列校验标识符，拼进 SQL 的就是校验过的那批 */
    private List<String> resolveColumns(JSONArray configured) {
        List<String> columns = new ArrayList<>();
        if (configured == null || configured.isEmpty()) {
            return columns;
        }
        for (Object item : configured) {
            String column = requireIdentifier(item == null ? null : String.valueOf(item), "列名");
            if (!columns.contains(column)) {
                columns.add(column);
            }
        }
        if (columns.size() > 100) {
            throw fail(CONFIG, "外部数据库数据源列白名单过长（上限 100 列）");
        }
        return columns;
    }

    static String requireHost(String raw) {
        String value = StringUtils.trimToEmpty(raw);
        if (value.isEmpty()) {
            throw fail(CONFIG, "外部数据库数据源缺少主机地址");
        }
        if (!HOST.matcher(value).matches()) {
            throw fail(CONFIG, "外部数据库主机地址不合法（只允许字母、数字、点、下划线与连字符）");
        }
        return value;
    }

    static String requireIdentifier(String raw, String label) {
        String value = StringUtils.trimToEmpty(raw);
        if (value.isEmpty()) {
            throw fail(CONFIG, "外部数据库数据源缺少" + label);
        }
        if (!IDENTIFIER.matcher(value).matches()) {
            // 不回显原值：主机名/库名本身属于内部拓扑，且这里正是注入的尝试点
            throw fail(CONFIG, "外部数据库" + label + "不合法（只允许字母、数字、下划线，且需以字母开头）");
        }
        return value;
    }

    String buildSelect(Vetting vet, int limit) {
        String projection = vet.columns.isEmpty() ? "*" : quotedJoin(vet.columns);
        return "SELECT " + projection + " FROM `" + vet.table + "` LIMIT " + Math.max(1, limit);
    }

    private static String quotedJoin(List<String> columns) {
        StringBuilder builder = new StringBuilder();
        for (String column : columns) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append('`').append(column).append('`');
        }
        return builder.toString();
    }

    // ---------------- 池生命周期 ----------------

    /**
     * 按「连接指纹」复用池。指纹含密码的哈希，所以改密码会换出一个新池而不是继续用旧连接。
     * <p>
     * {@code init()} 在锁内做真实建连，冷启动最坏阻塞一次超时并拖住其他外部源。这是有意取舍：
     * 用 {@code computeIfAbsent} 在桶锁里做 IO 更糟（可能长期占住 map），而冷启动每源只发生一次。
     */
    private ManagedPool acquirePool(String dbType, String host, int port, String database,
                                    String userName, String password, boolean useSsl) {
        String fingerprint = fingerprint(dbType, host, port, database, userName, password);
        ManagedPool existing = pools.get(fingerprint);
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        Long blocked = blockedUntil.get(fingerprint);
        if (blocked != null && blocked > now) {
            // 冷却期内直接失败：一块大屏 30 个组件指向同一个坏源时，逐个等超时等于把整页取数拖死
            throw fail(NETWORK, "外部数据库刚刚连接失败过，请在 "
                    + Math.max(1, (blocked - now) / 1000) + " 秒后重试，或先修正数据源配置");
        }
        synchronized (pools) {
            existing = pools.get(fingerprint);
            if (existing != null) {
                return existing;
            }
            if (pools.size() >= MAX_TRACKED_POOLS) {
                throw fail(LIMIT, "外部数据库数据源连接池数量已达上限，请先清理不再使用的数据源");
            }
            DruidDataSource dataSource = new DruidDataSource();
            dataSource.setName("datav-db-" + Integer.toHexString(fingerprint.hashCode()));
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull"
                    // 超时写进驱动参数：queryTimeout 管不到建连阶段，只有这里能兜住主机不可达
                    + "&connectTimeout=" + HARD_TIMEOUT_MS + "&socketTimeout=" + HARD_TIMEOUT_MS
                    // 白名单已排除 ? 与 &，主机段塞不进额外参数；这里再显式关死几个已知高危项，
                    // 因为只要允许连白名单内的任意主机，就等于允许对端服务器发起 local_infile/反序列化攻击
                    + "&allowLoadLocalInfile=false&allowUrlInLocalInfile=false&autoDeserialize=false"
                    // 多语句必须关：它是把「一条 SELECT」变成「SELECT ...; DROP TABLE」的唯一途径
                    + "&allowMultiQueries=false"
                    // allowPublicKeyRetrieval 只在非 SSL 下有意义（caching_sha2 取公钥）；
                    // 开了 SSL 就不该再允许明文回退，否则中间人可以直接把连接降级
                    + "&useSSL=" + useSsl + (useSsl ? "" : "&allowPublicKeyRetrieval=true"));
            dataSource.setUsername(userName);
            dataSource.setPassword(password);
            dataSource.setDefaultReadOnly(true);
            // initialSize=1 就是「建池即验证」。设 0 时 init() 一个连接都不建、永远不失败，于是密码
            // 写错的源也会被缓存，报错推迟到 getConnection——实测白等 30s，而且抛的是 Druid 取连接
            // 超时（错误码 0），「账号密码错误」这一类根本分不出来。端到端跑真库才暴露这一点。
            dataSource.setInitialSize(1);
            dataSource.setMinIdle(0);
            dataSource.setMaxActive(MAX_POOL_SIZE);
            dataSource.setMaxWait(POOL_WAIT_MS);
            // 外部库探活失败要立刻失败，不能重试到把取数线程拖死；也不能 testOnBorrow
            // 让每次取数都多付一个往返
            dataSource.setBreakAfterAcquireFailure(true);
            dataSource.setConnectionErrorRetryAttempts(0);
            dataSource.setFailFast(true);
            dataSource.setTestOnBorrow(false);
            dataSource.setTestOnReturn(false);
            dataSource.setTestWhileIdle(true);
            dataSource.setValidationQuery("SELECT 1");
            dataSource.setTimeBetweenEvictionRunsMillis(60_000L);
            dataSource.setMinEvictableIdleTimeMillis(180_000L);
            try {
                dataSource.init();
            } catch (SQLException e) {
                // 坏配置绝不入缓存：否则这个源每次取数都要重新撞一遍超时，且永远学不乖
                dataSource.close();
                blockedUntil.put(fingerprint, System.currentTimeMillis() + CONNECT_COOLDOWN_MS);
                throw describe(e);
            } catch (RuntimeException e) {
                // Druid 的 init 在部分失败分支上抛的是 RuntimeException 包装，两种都要归类，
                // 否则用户看到的是一句类名而不是「密码错了」
                dataSource.close();
                blockedUntil.put(fingerprint, System.currentTimeMillis() + CONNECT_COOLDOWN_MS);
                throw describe(unwrapSql(e));
            }
            blockedUntil.remove(fingerprint);
            ManagedPool pool = new ManagedPool();
            pool.dataSource = dataSource;
            pool.fingerprint = fingerprint;
            pools.put(fingerprint, pool);
            log.info("外部数据库数据源连接池已建立：host={} port={} database={} user={}",
                    host, port, database, userName);
            return pool;
        }
    }

    /** 取连接失败时 Druid 会把真实 SQLException 挂在 cause 上，分类要用cause 而不是包装异常 */
    private static SQLException unwrapSql(RuntimeException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SQLException sqlException) {
            return sqlException;
        }
        return new SQLException(StringUtils.defaultString(e.getMessage()));
    }

    /** 指纹只用密文/明文的哈希，绝不把密码本身放进 map key 或名称里 */
    private static String fingerprint(String dbType, String host, int port, String database,
                                      String userName, String password) {
        return String.join("|", dbType, host, String.valueOf(port), database, userName,
                String.valueOf(password == null ? 0 : password.hashCode()));
    }

    /** 同时最多托管多少个外部库连接池：防着一堆废弃数据源把连接和内存攒起来 */
    private static final int MAX_TRACKED_POOLS = 20;

    /**
     * 按「不含密码的连接坐标」驱逐池：改密码、改表名、删数据集都要能命中旧池，
     * 而旧池的 key 尾部带着当时那个密码的哈希，所以这里只匹配前缀。
     * <p>
     * 表/列白名单不进指纹——它们不参与建连，换表不该重连。
     */
    @Override
    public void invalidate(JSONObject sourceConfig) {
        if (sourceConfig == null) {
            return;
        }
        String coordinates = String.join("|", "mysql",
                StringUtils.trimToEmpty(sourceConfig.getString("host")),
                String.valueOf(readPort(sourceConfig)),
                StringUtils.trimToEmpty(sourceConfig.getString("database")),
                StringUtils.trimToEmpty(sourceConfig.getString("userName")));
        pools.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(coordinates + "|")) {
                return false;
            }
            entry.getValue().dataSource.close();
            // 冷却也要一起清：用户改完配置后不该再被上一次失败挡 30 秒
            blockedUntil.keySet().removeIf(key -> key.startsWith(coordinates + "|"));
            log.info("外部数据库数据源连接池已释放：{}", coordinates);
            return true;
        });
    }

    private static int readPort(JSONObject sourceConfig) {
        int port = sourceConfig.getIntValue("port");
        return port > 0 && port <= 65535 ? port : DEFAULT_PORT;
    }

    @PreDestroy
    public void closeAll() {
        pools.values().forEach(pool -> pool.dataSource.close());
        pools.clear();
    }

    // ---------------- 异常分类与脱敏 ----------------

    /**
     * 把 SQLException 翻译成人能看懂、且不泄露内部拓扑的话。
     * <p>
     * 按 MySQL 的 vendor error code 判而不是猜异常文本：文本随驱动版本和服务器语言变（中文
     * locale 下 'doesn't exist' 直接变成中文），而 1045/1146 这些码不会。原始异常只进日志——
     * JDBC 异常不带凭据，但会带主机、库名和 SQL 片段。
     */
    private static DatavSourceException fail(String category, String message) {
        return new DatavSourceException(category, message);
    }

    ServiceException describe(SQLException e) {
        int code = e.getErrorCode();
        String sqlState = StringUtils.trimToEmpty(e.getSQLState());
        log.warn("外部数据库数据源访问失败：errorCode={} sqlState={} detail={}",
                code, sqlState, StringUtils.defaultString(e.getMessage()));
        if (code == 1045) {
            return fail(AUTH, "外部数据库账号或密码不正确，或该账号无权访问目标库");
        }
        if (code == 1044 || code == 1142) {
            return fail(AUTH, "该账号无权访问目标表/库");
        }
        if (code == 1049 || code == 1146) {
            return fail(CONFIG, "外部数据库里找不到这个库或表，请检查库名与表名");
        }
        // CommunicationsException 的 errorCode 是 0，只能靠 SQLState 认
        if ("08S01".equals(sqlState) || "08001".equals(sqlState) || code == 1042 || code == 1053) {
            return fail(NETWORK, "无法连接外部数据库，请检查主机、端口与网络可达性");
        }
        // 池已建好但借不到连接：Druid 抛的是 GetConnectionTimeoutException，错误码 0，
        // 真实原因在 cause 上——不认它就只能落到兜底文案，用户看不出是连不上
        if (e.getClass().getSimpleName().contains("GetConnectionTimeout")
                || StringUtils.defaultString(e.getMessage()).contains("wait millis")) {
            return fail(NETWORK, "无法连接外部数据库，请检查主机、端口与网络可达性");
        }
        if (code == 3024 || (sqlState.startsWith("HY000")
                && StringUtils.defaultString(e.getMessage()).toLowerCase().contains("timeout"))) {
            return fail(NETWORK, "外部数据库查询超时，请缩小数据范围或调大超时时间");
        }
        return fail(UNKNOWN, "外部数据库访问失败，请联系管理员查看服务日志");
    }

    // ---------------- 值归一化 ----------------

    /**
     * 连接器契约只允许 String / Number / Boolean / null。
     * JDBC 的日期时间一律转字符串（与 HTTP 源同形，聚合层的时间粒度解析按字符串走），
     * BigDecimal 原样保留（它本身就是 Number，聚合层能直接求和）。
     */
    static Object normalize(Object raw) {
        if (raw == null || raw instanceof Number || raw instanceof Boolean || raw instanceof String) {
            return raw;
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATETIME);
        }
        if (raw instanceof java.sql.Date date) {
            return date.toLocalDate().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (raw instanceof java.sql.Time time) {
            return time.toLocalTime().format(TIME);
        }
        // Connector/J 8/9 对 DATETIME/DATE/TIME 的 getObject() 直接返回 java.time 类型，
        // 不落到上面三个分支。漏了它们就会走 String.valueOf，输出 "2026-01-05T10:00"——
        // 带 T、且秒为 0 时被整体省略，这种字符串会原样出现在图表 X 轴上。
        if (raw instanceof java.time.LocalDateTime dateTime) {
            return dateTime.format(DATETIME);
        }
        if (raw instanceof java.time.LocalDate dateOnly) {
            return dateOnly.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (raw instanceof java.time.LocalTime timeOnly) {
            return timeOnly.format(TIME);
        }
        if (raw instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime().format(DATETIME);
        }
        if (raw instanceof java.sql.Clob clob) {
            return readClob(clob);
        }
        if (raw instanceof byte[]) {
            // 二进制列对大屏没有意义，也不该把 Blob 整个读进堆
            return null;
        }
        return String.valueOf(raw);
    }

    private static final java.time.format.DateTimeFormatter DATETIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final java.time.format.DateTimeFormatter TIME =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int CLOB_LIMIT = 8192;

    private static String readClob(java.sql.Clob clob) {
        try {
            return clob.getSubString(1, (int) Math.min(clob.length(), CLOB_LIMIT));
        } catch (SQLException e) {
            return null;
        }
    }

    static String toDatavType(int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT,
                    Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> "number";
            case Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "date";
            default -> "text";
        };
    }

    int clampTimeout(Integer configured) {
        if (configured == null || configured <= 0) {
            return HARD_TIMEOUT_MS;
        }
        return Math.min(configured, HARD_TIMEOUT_MS);
    }

    static int toSeconds(int timeoutMs) {
        return Math.max(1, (int) Math.ceil(timeoutMs / 1000.0));
    }

    static final class Vetting {
        ManagedPool pool;
        String table;
        List<String> columns;
        int timeoutMs;
    }

    static final class ManagedPool {
        DruidDataSource dataSource;
        String fingerprint;
    }
}
