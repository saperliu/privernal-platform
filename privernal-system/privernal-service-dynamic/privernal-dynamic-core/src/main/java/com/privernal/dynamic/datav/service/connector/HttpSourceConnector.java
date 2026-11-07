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
import static com.privernal.dynamic.datav.service.connector.DatavSourceException.PARSE;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.net.OutboundHttpGuard;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.api.ISecretCipherService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * HTTP 数据源连接器（M3.2 + M3.3）。
 * <p>
 * 安全口径与功能同批实现，不允许「先上线后加固」：
 * <ul>
 *   <li>SSRF：拦截发生在**真正要建立连接的那批地址上**（自定义 {@link Dns}），
 *       而不是「先解析校验一遍、再让客户端自己重新解析一次」——后者会被 DNS rebinding
 *       在两次解析之间换成交换到内网的记录，等于校验形同虚设。
 *       重定向不自动跟随（跟随等于把校验结果作废）；域名白名单可配置，默认关闭（=只拦内网）</li>
 *   <li>响应体按字节流边读边限（默认 2MB）：先整体读进字符串再判长度，
 *       一个超大响应就能先把堆打满再触发上限，上限本身成了摆设；
 *       超时硬上限（默认 10s，配置里只能调小）</li>
 *   <li>凭据走 {@link DatavSourceSecrets}：headers/查询参数里的敏感项以可逆密文落库，
 *       只在本次调用的栈上还原成明文，接口下发时一律掩码</li>
 * </ul>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class HttpSourceConnector implements DatavSourceConnector {

    private static final int HARD_TIMEOUT_MS = 10_000;

    private static final long HARD_BODY_LIMIT = 2L * 1024 * 1024;

    private static final int SAMPLE_ROWS_FOR_COLUMNS = 20;

    /** 域名白名单；空 = 不启用（默认仅内网拦截）。配置项带安全开关，避免误配全放开 */
    @Value("${privernal.datav.http-host-whitelist:}")
    private String hostWhitelist;

    /** 凭据密文的解读者；未配置主密钥时对带凭据的数据源直接失败，不退化成明文 */
    @Autowired
    private ISecretCipherService secretCipher;

    /** 最近一次被 DNS 层拦下的原因，供调用点换成可读错误（OkHttp 会把 DNS 异常包成连接失败） */
    private final ThreadLocal<String> dnsBlocked = new ThreadLocal<>();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            // HTTPS 重定向同样要关：OkHttp 把 sslRedirects 当独立开关，只关一个仍能跳到内网
            .followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(5))
            .retryOnConnectionFailure(false)
            .dns(this::vetAddresses)
            .build();

    @Override
    public String type() {
        return "http";
    }

    @Override
    public List<Map<String, Object>> fetchRows(JSONObject sourceConfig, int maxRows) {
        JSONArray array = parseJsonArray(fetchBodyText(sourceConfig), sourceConfig);
        return normalizeRows(array, maxRows > 0 ? maxRows : 1000);
    }

    @Override
    public JSONObject sampleColumns(JSONObject sourceConfig) {
        JSONArray array = parseJsonArray(fetchBodyText(sourceConfig), sourceConfig);
        List<Map<String, Object>> rows = normalizeRows(array, SAMPLE_ROWS_FOR_COLUMNS);
        return inferColumns(rows);
    }

    // ---------------- 请求与安全 ----------------

    /**
     * 响应体的**原始字节**，走完全部安全闸门：URL 校验、白名单、IP 字面量否决、DNS 层否决、
     * 凭据还原、超时、字节上限、状态码分类。
     * <p>
     * 对同包的 {@link CsvSourceConnector} 开放（不对外）：CSV 源与 HTTP 源的差别只在「怎么解释响应体」，
     * 闸门必须只有一份实现——两处各写一遍，迟早漂移成一个能绕过的版本。
     * 返回字节而不是字符串，是因为 CSV 的编码写在文件里而不是 Content-Type 里。
     */
    byte[] fetchBodyBytes(JSONObject sourceConfig) {
        if (sourceConfig == null) {
            throw fail(CONFIG, "HTTP 数据源缺少 sourceConfig");
        }
        String url = StringUtils.trimToEmpty(sourceConfig.getString("url"));
        if (StringUtils.isBlank(url)) {
            throw fail(CONFIG, "HTTP 数据源缺少 url");
        }
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            throw fail(CONFIG, "HTTP 数据源 URL 无法解析：" + url);
        }
        if (!"http".equals(httpUrl.scheme()) && !"https".equals(httpUrl.scheme())) {
            throw fail(CONFIG, "HTTP 数据源只允许 http/https 协议");
        }
        requireAllowedHost(httpUrl.host());
        // IP 字面量必须在发请求前单独拦：OkHttp 只有在「主机名需要解析」时才调用 Dns 回调，
        // URL 里直接写 IP 时它把地址串原样交给 socket，DNS 层否决一行代码都不会执行。
        // 而 127.0.0.1 / 169.254.169.254 恰恰是配置表单里最常见的两种填法。
        vetoLiteralAddress(httpUrl.host());

        // 密文只在这里还原成明文，且只存在于本次调用的栈上：headers 里可能带 Authorization
        sourceConfig = DatavSourceSecrets.open(sourceConfig, secretCipher);

        String method = StringUtils.defaultIfBlank(sourceConfig.getString("method"), "GET").toUpperCase();
        int timeoutMs = clampTimeout(sourceConfig.getIntValue("timeoutMs"));

        Request.Builder builder = new Request.Builder().url(httpUrl);
        boolean hasContentType = false;
        JSONObject headers = sourceConfig.getJSONObject("headers");
        if (headers != null) {
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                try {
                    builder.header(entry.getKey(), String.valueOf(entry.getValue()));
                } catch (IllegalArgumentException e) {
                    // OkHttp 会拒绝含控制字符的头名/头值；这里明确报错，不静默丢掉一个鉴权头
                    throw fail(CONFIG, "HTTP 数据源请求头不合法：" + entry.getKey());
                }
                hasContentType = hasContentType || "content-type".equalsIgnoreCase(entry.getKey());
            }
        }
        if (!hasContentType) {
            builder.header("Content-Type", "application/json");
            hasContentType = true;
        }
        // Accept 只在显式配置时才发：默认不发，才不会让一个已有的 JSON 数据源因为内容协商换了返回格式
        String accept = StringUtils.trimToEmpty(sourceConfig.getString("accept"));
        if (StringUtils.isNotBlank(accept)) {
            builder.header("Accept", accept);
        }

        RequestBody requestBody = null;
        if (!"GET".equals(method) && !"HEAD".equals(method) && !"DELETE".equals(method)) {
            String raw = sourceConfig.getString("body");
            requestBody = RequestBody.create(raw == null ? "" : raw,
                    hasContentType ? MediaType.parse("application/json") : null);
        }
        builder.method(method, requestBody);

        dnsBlocked.remove();
        byte[] body;
        int statusCode;
        // 每次按配置的超时派生一个 call：OkHttp 的超时是客户端级属性，但 newBuilder 共享连接池，代价很小
        OkHttpClient scoped = client.newBuilder().callTimeout(Duration.ofMillis(timeoutMs)).build();
        try (Response response = scoped.newCall(builder.build()).execute()) {
            statusCode = response.code();
            if (statusCode / 100 != 2) {
                if (statusCode == 401 || statusCode == 403) {
                    throw fail(AUTH, "HTTP 数据源拒绝鉴权（状态码 " + statusCode + "），请重新填写凭据");
                }
                throw fail(NETWORK, "HTTP 数据源返回状态码 " + statusCode);
            }
            body = readBodyBytes(response.body());
        } catch (IOException e) {
            String blocked = blockedReason();
            if (blocked != null) {
                // DNS 层的拒绝会被 OkHttp 包成连接失败，原因必须还原成人看得懂的一句，
                // 否则运维只会看到「Unable to resolve host」而去查网络
                throw fail(CONFIG, "HTTP 数据源" + blocked);
            }
            throw fail(NETWORK, "HTTP 数据源请求失败：" + e.getMessage());
        } finally {
            dnsBlocked.remove();
        }
        if (body == null) {
            throw fail(PARSE, "HTTP 数据源返回空响应");
        }
        return body;
    }

    /** JSON 路径的读法：UTF-8，与原行为一致 */
    private String fetchBodyText(JSONObject sourceConfig) {
        return new String(fetchBodyBytes(sourceConfig), java.nio.charset.StandardCharsets.UTF_8);
    }

    private JSONArray parseJsonArray(String body, JSONObject sourceConfig) {
        Object root;
        try {
            root = JSON.parse(body);
        } catch (Exception e) {
            throw fail(PARSE, "HTTP 数据源响应不是合法 JSON");
        }
        String resultPath = StringUtils.trimToEmpty(sourceConfig.getString("resultPath"));
        Object data = StringUtils.isBlank(resultPath) ? root : com.alibaba.fastjson2.JSONPath.eval(root, resultPath);
        if (data instanceof JSONArray) {
            return (JSONArray) data;
        }
        if (data instanceof List) {
            JSONArray array = new JSONArray();
            array.addAll((List<?>) data);
            return array;
        }
        throw fail(PARSE, "resultPath 定位到的不是数组：" + (resultPath.isEmpty() ? "$" : resultPath));
    }

    private int clampTimeout(Integer configured) {
        if (configured == null || configured <= 0) {
            return 8000;
        }
        return Math.min(configured, HARD_TIMEOUT_MS);
    }

    /**
     * 主机名白名单（配置了才生效）。
     * <p>
     * 只在这里查白名单，**不在这里做内网拦截**：这里解析一次、OkHttp 连接时再解析一次的话，
     * 两次结果可以不同（DNS rebinding），拦的正是那批它真正会去拨的地址。
     */
    private void requireAllowedHost(String host) {
        if (StringUtils.isBlank(host)) {
            throw fail(CONFIG, "HTTP 数据源 URL 缺少主机名");
        }
        String whitelist = hostWhitelist == null ? "" : hostWhitelist.trim();
        if (whitelist.isEmpty()) {
            return;
        }
        List<String> allowed = new ArrayList<>();
        for (String item : whitelist.split(",")) {
            String trim = item.trim();
            if (!trim.isEmpty()) {
                allowed.add(trim);
            }
        }
        if (allowed.stream().noneMatch(host::equals)) {
            // 归 CONFIG 不归 AUTH：category 决定界面给的建议，报鉴权会把人支去改密码，而该改的是地址
            throw fail(CONFIG, "HTTP 数据源主机不在白名单内：" + host);
        }
    }

    /**
     * 连接前的地址闸门：解析出的每一个地址都过一遍内网/保留段拦截，只把放行后的地址交给连接层。
     * <p>
     * 部分被拦时保留可用地址（都已逐个校验过）并记一条日志；全部被拦才失败。
     * 这里抛 {@link UnknownHostException} 而不是业务异常：OkHttp 的 DNS 接口只声明它，
     * 且连接层会把运行期异常揉成「failed to connect」，原因就得靠 {@link #dnsBlocked} 带回去。
     */
    /**
     * 把「配置里写的本来就是 IP」这种情况解析出来；**绝不触发 DNS**——非字面量一律返回 null，
     * 主机名的解析与否决都由 {@link #vetAddresses} 在 OkHttp 的 Dns 回调里完成。
     */
    static InetAddress literalAddress(String host) {
        return OutboundHttpGuard.literalAddress(host);
    }

    void vetoLiteralAddress(String host) {
        InetAddress address = literalAddress(host);
        if (address != null && isBlocked(address)) {
            throw fail(CONFIG, "HTTP 数据源禁止访问内网/保留地址：" + host);
        }
    }

    List<InetAddress> vetAddresses(String hostname) throws UnknownHostException {
        // 入口先清：这是线程内的「上次为什么被拦」，留着会把下一次成功调用判成失败
        dnsBlocked.remove();
        InetAddress[] resolved = InetAddress.getAllByName(hostname);
        List<InetAddress> safe = new ArrayList<>(resolved.length);
        List<String> blocked = new ArrayList<>(2);
        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                blocked.add(address.getHostAddress());
            } else {
                safe.add(address);
            }
        }
        if (!blocked.isEmpty()) {
            log.warn("HTTP 数据源 {} 的内网/保留地址已拒绝：{}", hostname, blocked);
        }
        if (safe.isEmpty()) {
            dnsBlocked.set("禁止访问内网/保留地址：" + String.join(", ", blocked));
            throw new UnknownHostException("blocked resolver result for " + hostname);
        }
        return safe;
    }

    /**
     * 边读边限长度。
     * <p>
     * 先整体读成字符串再判上限等于没有上限：一个 5GB 响应会先把堆打满，
     * 判长的那行代码根本执行不到。声明长度能早退就早退，否则按字节累计。
     */
    private byte[] readBodyBytes(ResponseBody body) throws IOException {
        if (body == null) {
            return null;
        }
        return readBytesCapped(body.contentLength(), body.byteStream());
    }

    /** UTF-8 读法，保留给按字节流上限做回归的用例 */
    static String readCapped(long declared, InputStream in) throws IOException {
        return new String(readBytesCapped(declared, in), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 按字节流边读边限：先整体读成字符串再判长度，等于让一个超大响应先把堆打满、上限本身成了摆设。
     * <p>
     * 拆成 (declared, stream) 的包级方法是为了让这条上限能被离线回归——公网没有稳定的
     * 大于 2MB 的 JSON 端点可打，而「上限是否真的生效」属于安全承诺，不能只靠人眼读代码。
     */
    static byte[] readBytesCapped(long declared, InputStream in) throws IOException {
        if (declared > HARD_BODY_LIMIT) {
            throw fail(LIMIT, "HTTP 数据源响应声明 " + (declared / 1024 / 1024) + "MB，超过 "
                    + (HARD_BODY_LIMIT / 1024 / 1024) + "MB 上限");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(declared > 0 ? (int) declared : 8192);
        byte[] buffer = new byte[8192];
        long total = 0;
        try (InputStream stream = in) {
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > HARD_BODY_LIMIT) {
                    throw fail(LIMIT, "HTTP 数据源响应超过 " + (HARD_BODY_LIMIT / 1024 / 1024) + "MB 上限");
                }
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }

    /** 供调用点把 DNS 层的拒绝还原成人看得懂的错误信息 */
    String blockedReason() {
        return dnsBlocked.get();
    }

    /** 环回 / 私有 / 链路本地 / 唯一本地 / 保留 / 云元数据段全部拒绝 */
    public static boolean isBlocked(InetAddress address) {
        // 判定口径下沉到公共闸门：Webhook/OPENAPI 回调等出站方与此处共用同一份实现，防止漂移
        return OutboundHttpGuard.isBlocked(address);
    }

    // ---------------- 行规范化与类型推断 ----------------

    /** 嵌套对象拍平成「点号路径」，数组值 JSON 序列化为字符串——聚合层只见标量 */
    public static List<Map<String, Object>> normalizeRows(JSONArray array, int maxRows) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int count = Math.min(array.size(), maxRows);
        if (count < array.size()) {
            log.warn("HTTP 数据源行数 {} 超上限，截断为 {}", array.size(), maxRows);
        }
        for (int i = 0; i < count; i++) {
            Object item = array.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            flatten(item, "", row);
            rows.add(row);
        }
        return rows;
    }

    private static void flatten(Object item, String prefix, Map<String, Object> row) {
        if (item instanceof JSONObject) {
            JSONObject object = (JSONObject) item;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                Object value = entry.getValue();
                if (value instanceof JSONObject) {
                    flatten(value, key, row);
                } else if (value instanceof JSONArray || value instanceof List) {
                    row.put(key, JSON.toJSONString(value));
                } else {
                    row.put(key, value);
                }
            }
            return;
        }
        if (item instanceof Map) {
            flatten(new JSONObject((Map<String, Object>) item), prefix, row);
            return;
        }
        // 数组元素是标量：放进固定 _value 列，类型推断会把它当文本
        row.put(prefix.isEmpty() ? "_value" : prefix, item);
    }

    public static JSONObject inferColumns(List<Map<String, Object>> rows) {
        Map<String, Integer> numberHits = new LinkedHashMap<>();
        Map<String, Integer> dateHits = new LinkedHashMap<>();
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                total.merge(key, 1, Integer::sum);
                Object value = entry.getValue();
                if (value instanceof Number || (value instanceof String && isNumeric((String) value))) {
                    numberHits.merge(key, 1, Integer::sum);
                } else if (value instanceof String && looksLikeDate((String) value)) {
                    dateHits.merge(key, 1, Integer::sum);
                }
            }
        }
        JSONArray columns = new JSONArray();
        for (String key : total.keySet()) {
            String dataType = "text";
            if (numberHits.getOrDefault(key, 0) >= total.get(key)) {
                dataType = "number";
            } else if (dateHits.getOrDefault(key, 0) >= total.get(key)) {
                dataType = "date";
            }
            JSONObject column = new JSONObject();
            column.put("fieldCode", key);
            column.put("fieldName", key);
            column.put("dataType", dataType);
            columns.add(column);
        }
        JSONObject snapshot = new JSONObject();
        snapshot.put("columns", columns);
        return snapshot;
    }

    private static boolean isNumeric(String text) {
        try {
            new BigDecimal(text.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean looksLikeDate(String text) {
        String trim = text.trim();
        if (trim.length() < 8) {
            return false;
        }
        for (String pattern : new String[]{"yyyy-MM-dd", "yyyy/MM/dd"}) {
            try {
                LocalDate.parse(trim.length() >= 10 ? trim.substring(0, 10) : trim, DateTimeFormatter.ofPattern(pattern));
                return true;
            } catch (Exception ignored) {
                // 继续尝试下一个格式
            }
        }
        return false;
    }

    /** 供健康检查/测试复用的连接探测：不做数据解析，只回可达性与样本列 */
    private static DatavSourceException fail(String category, String message) {
        return new DatavSourceException(category, message);
    }
}
