# 安全策略

## 报告漏洞

请不要在公开 Issue 里报告安全问题。

优先在仓库的「Security」页发起 private vulnerability report（GitHub 私密漏洞报告），
这样报告内容只有维护者可见。若该功能未开启，请联系维护者，
联系方式见仓库首页 README 的贡献与许可章节。
报告时请附上受影响版本或 Git commit hash，以便定位。

我们会在 2 个工作日内确认收到，并在 7 个工作日内给出初步评估。

## 支持版本

| 版本 | 是否接受安全修复 |
| --- | --- |
| 最新 `main` | 是 |
| 上一发布分支 | 是 |
| 更早版本 | 否 |

## 部署者必须处理的安全项

以下是本项目**有意**保留的默认行为，它们只在受控内网成立。
任何对外可达的部署都必须逐条确认：

1. **改默认管理员口令。** `sql/init.sql` 写入的 `admin` / `admin123` 仅为首次登录。
2. **设置 OpenAPI/Webhook/OIDC 的存储主密钥。**
   基础配置里 `application.yaml` 的 `openapi.secret-master-key` 默认**留空**：
   未用环境变量 `OPENAPI_SECRET_MASTER_KEY` 设置时，创建凭证会直接报错——
   这是刻意的 fail-closed 兜底，防止机读密钥退化成明文入库。生产环境必须显式设置。
3. **设置口令摘要主密钥（F4 挑战登录，建议启用）。**
   未用环境变量 `PASSWORD_DIGEST_MASTER_KEY`（32 字节 hex 或 ≥32 字符短语）设置时，
   登录走旧口径 bcrypt 校验，线摘要仍可重放；配置后客户端自动升级为挑战-应答
   （一次性 nonce + AES-GCM 封存摘要，重放面归零），存量账号在下次登录时静默迁移。
   全量客户端升级后可把 `privernal.security.password-digest.require-challenge` 置 `true`
   收掉旧口径。改主密钥可自愈：用户按旧口径引导一次即重新封存。
   注意这些密钥必须可逆存储（HMAC 验签要求服务端能还原同一密钥重算签名），
   因此库里存的是 AES/GCM 密文而非 hash；更换主密钥会导致既有密钥解不开，需要轮换。
3. **生产环境关闭接口文档与 Druid 监控页。**
   `application-pro.yaml` 默认已关（`SPRINGDOC_ENABLED=false`、`DRUID_STAT_ENABLED=false`），
   不要用 `local`/`dev` profile 对外部署。
4. **登录接口当前不校验图形验证码（确定不恢复）。** 前端登录页与后端 `checkCaptcha`
   校验已同步下线（`LoginController` 中两处调用被注释），`CAPTCHA_ENABLED` 是预留开关、
   当前不生效。撞库防线由应用内兜底：账号口令登录 15 分钟内 5 次失败锁 15 分钟、
   短信验证码登录一次性消费、短信发送端点内置频控（见第 6 条）。
   **公网部署必须在接入层（nginx/WAF）对 `/auth/login`、`/auth/sms/{phone}` 等认证端点
   配置限流与防撞库规则，此为强制项而非可选项。**
5. **`/inner/**` 与 `/actuator/**` 不得暴露到公网。**
   默认 profile 已是 `pro`，且接口文档（doc.html/v3/api-docs/swagger-ui）与 `/actuator/**`
   不再出现在基础免登清单中：`/actuator/**` 的免登由 pro 显式开启（仅供 Prometheus 抓取），
   网络暴露面仍须由运维侧控制；local/dev 本地调试需自行补充。
   `/inner/**` 的"内部调用"目前只按请求头 `SERVICE_REQUEST: INNER` 判定，**不构成可信边界**：
   任何已登录用户都能伪造该头。**部署前置：后端端口不直接对外，必须经 nginx 反代**——
   nginx 默认（`underscores_in_headers off`）会丢弃带下划线的客户端请求头，`SERVICE_REQUEST`
   由此无法穿透网关（**切勿开启 `underscores_in_headers on`**）；自建网关须按后端读取的全名
   剥头（`proxy_set_header SERVICE_REQUEST "";`，与 `Constants.SERVICE_REQUEST` 同名）
   并阻断 `/inner/**`（前端经 `/api/` 反代时即 `location /api/inner/ { return 403; }`）。
   高危端点已按最小权限收口：组织切换/删除用户限当前登录用户、按手机号改密在端点内
   自行校验并消费短信验证码，但其余内部端点仍按此头判定，网关剥头不可省略。
6. **`/pub/**` 是有意免登的外链填写入口。** 它自带 token 摘要定位、IP 限流、
   密码校验、回执码摘要与访问审计；只放行 5 个端点。若你不需要外链填写，
   请在网关层直接封掉该前缀。
   **注意**：外链查看（`/pub/view/list/{token}`）的筛选条件已改为白名单解析 + 参数绑定
   （SQL 注入面已封堵）；短信验证码不再随响应返回，验证通过即作废。
   这些端点仍属免登面，公网部署请保留接入层限流与验证码校验。
   短信发送端点（`/auth/sms/{phone}`）已内置频控：同号 60 秒一条、24 小时 10 条；
   账号口令登录已内置失败锁定（15 分钟内 5 次失败即锁 15 分钟）。
   初始密码（手机号后 6 位）默认拒绝登录，改密走"忘记密码"短信链路；
   存量迁移期可设 `privernal.auth.block-default-password=false` 临时放行。
7. **表单数据源凭证。** 动态表单可登记外部业务库（`dynamic_database`），
   其连接口令的存储与轮换由部署方负责。

8. **IP 归属地是可选的离线查询。** 平台不会把登录 IP 发给外部服务；
   启用需自行放置 xdb 数据文件（`privernal.ip-region.db-path`）。
   若改接任何在线归属地接口，请先就「登录 IP 属个人信息」完成合规评估。

## 已知不在范围内的风险

低代码平台允许管理员编写 Groovy 脚本与自定义 SQL，这本质上是把代码执行能力
交给后台用户。**权限模型的正确性依赖组织/角色/数据权限配置本身**——
请只把脚本与数据源配置权限授予可信人员。
