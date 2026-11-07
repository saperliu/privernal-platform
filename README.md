# 春祈平台服务端（Privernal Studio）

[English](./README.en.md) | 简体中文

春祈平台（Privernal Studio）是开源自部署的企业级低代码平台，本仓库为其服务端。以**动态表单**定义数据模型（运行期自动建物理表，支持发布、版本化与回滚），用**工作流**驱动审批与经办（warm-flow 引擎，节点级权限、票签、超时处置），在**多组织数据权限**之上提供报表图表、数据大屏、单据打印（HTML→PDF）、定时任务工作台与 OpenAPI / Webhook / SSO 开放集成；内置 **AI 对话式搭建与 AI 问数**，用自然语言生成表单、流程与图表。Java 21 + Spring Boot 3.5 + MyBatis-Plus，支持 MySQL 与 SQLite 双数据库，Maven 多模块，不依赖任何 SaaS 即可完整运行。

配套项目：前端 [privernal-platform-ui](https://github.com/saperliu/privernal-platform-ui) · 移动端 `privernal-app` · 表单设计器 [PhoenixDesigner](https://github.com/saperliu/phoenix-designer)（npm）。

> 本仓库只含后端。它是可自部署的完整服务，不依赖任何 SaaS 或外部账号即可跑通。

## 能力

| 领域 | 说明 |
| --- | --- |
| 动态表单 | 运行期按定义建物理表；表单定义支持发布、版本化与回滚；支持子表、关联数据、字段级默认值与软删除 |
| 工作流 | 基于 warm-flow；节点办理人/抄送/票签/超时跳转，节点级字段与按钮权限，流程与表单联动 |
| 数据权限 | 全局按组织隔离，另支持部门范围与自定义数据范围注解；多数据源路由 |
| 定时调度 | 数据库租约选主 + 秒级扫描 CAS，支持在途改配置暂存、报警与指标快照 |
| 消息 | 站内信（Socket.IO 长连接推送）、短信、企业微信与钉钉通道 |
| 文件 | 本地磁盘 / 阿里云 OSS / MinIO 三种存储后端可切换 |
| 报表与打印 | 自定义数据集与图表；单据打印走 HTML→PDF 通道，支持离线模板与中文字体 |
| 集成 | OpenAPI 签名调用、Webhook 出站订阅、SSO（OIDC）、第三方授权登录 |
| 应用包 | 表单/流程/菜单/脚本等按应用打包，跨环境导入导出重建 |
| 脚本资产 | Groovy 脚本模板中心，用于动作节点里的业务逻辑扩展 |

## 技术栈

- Java 21、Spring Boot 3.5.4
- Sa-Token 1.45.0（认证鉴权）、MyBatis-Plus 3.5.12、warm-flow 1.8.9（工作流）
- MySQL 8、Redis、可选 RocketMQ / MinIO / 阿里云 OSS
- springdoc-openAPI（接口文档）、Hutool、EasyPOI / POI

依赖许可清单见 [NOTICE.md](./NOTICE.md)。

## 模块结构

```
com.privernal:privernal-platform
├── privernal-framework                     // 框架层，与业务无关
│   ├── privernal-common-core               // 基础工具、统一返回、常量与异常、文件与 Excel 能力
│   ├── privernal-common-web                // Web 层配置、分页、Swagger 自动装配
│   ├── privernal-common-security           // 安全上下文、Token 与 JWT
│   ├── privernal-common-mybatis            // Mapper 基类、SQL 注入器、方言扩展
│   ├── privernal-common-datasource         // 动态数据源路由
│   ├── privernal-common-datascope          // 组织/部门数据权限拦截
│   ├── privernal-common-redis              // 缓存
│   ├── privernal-common-redisson           // Redisson、分布式锁与幂等
│   ├── privernal-common-rocketmq           // 消息队列
│   ├── privernal-common-log                // 操作日志
│   ├── privernal-common-dingtalk           // 钉钉通道
│   └── privernal-common-wx                 // 企业微信通道
├── privernal-system                        // 业务层，每个域拆成 *-api（对外契约）与 *-core（实现）
│   ├── privernal-service-system            // 组织、部门、用户、角色、菜单、字典、登录
│   ├── privernal-service-dynamic           // 动态表单、数据表、报表、打印、高级设置
│   ├── privernal-service-flow              // 工作流
│   ├── privernal-service-file              // 文件服务
│   ├── privernal-service-message           // 站内信与推送
│   └── privernal-service-job               // 定时任务与调度内核
├── privernal-server                        // 启动模块：装配上面所有层，端口 8600
└── sql/                                 // 建库脚本与增量迁移
```

## 快速开始

### 一、起依赖

```bash
docker compose up -d          # MySQL 8 + Redis
```

或自行准备 MySQL 8 与 Redis，记下连接参数。

### 二、建库

```bash
mysql -u root -p < sql/init.sql
```

脚本创建 `dynamic_platform` 库、97 张平台表，并写入运行所需的最小种子数据。
细节与注意事项见 [sql/README.md](./sql/README.md)。

### 三、配置

配置文件不入库，从示例复制一份：

```bash
cp privernal-server/src/main/resources/application-local.yaml.example \
   privernal-server/src/main/resources/application-local.yaml
```

打开 `application-local.yaml`，填 `MYSQL_HOST` / `MYSQL_PASSWORD` 等值。
所有环境相关参数都支持同名环境变量覆盖，容器化部署时不必改文件。

可选：登录日志里的 IP 归属地。平台用 ip2region 离线查询，不把 IP 发给任何外部服务；
需要该字段时自备 xdb 数据文件并设置 `privernal.ip-region.db-path`
（支持 `file:` 与 `classpath:` 前缀），未配置时地址列记为「未知」，其余功能不受影响。

### 四、启动

```bash
mvn clean package
java -jar privernal-server/target/privernal-server.jar
```

开发期可直接跑 `com.privernal.PrivernalServerApplication`。

### 五、登录

- 接口文档：`http://localhost:8600/swagger-ui`
- 内置账号：`admin` / `admin123`

> ⚠️ **首次登录后必须立即修改口令。** 默认口令仅为首次进入而存在，
> 任何对外可达的部署沿用它都等同于把管理员权限公开。

## 接口约定

| 前缀 | 用途 |
| --- | --- |
| `/back/**` | 管理后台接口 |
| `/app/**` | 用户端接口 |
| `/inner/**` | 内部服务间调用，不对外暴露 |
| `/openapi/**` | 机器调用，由独立验签过滤器处理，不建立登录态 |

二次开发涉及的注解（`@PreAuthorize`/`@IgnoreDataScope`/`@DS`）与数据权限机制，见对应注解与切面的源码注释。

## 文档

- [CONTRIBUTING.md](./CONTRIBUTING.md) —— 贡献流程
- [SECURITY.md](./SECURITY.md) —— 安全漏洞报告方式
- [NOTICE.md](./NOTICE.md) —— 许可与第三方声明
- [CHANGELOG.md](./CHANGELOG.md) —— 版本变更记录

## 维护者

主要维护者：`saperliu`。贡献者名单见仓库的 Contributors 页面；
完整开发过程记录在提交历史中。

安全漏洞请按 [SECURITY.md](./SECURITY.md) 的私密渠道报告，不要公开发帖。

## 许可

Apache License 2.0，见 [LICENSE](./LICENSE)。
再分发时须同时保留 [NOTICE.md](./NOTICE.md)。
