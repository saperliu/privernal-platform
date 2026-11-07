# 许可与第三方声明

本仓库（`privernal-platform`，Java 包名 `com.privernal`，模块前缀 `privernal-*`，品牌 **Privernal Studio** / 春祈平台）以
**Apache License 2.0** 发布，全文见 [LICENSE](./LICENSE)。
权利主体与署名口径见下文第七节。

## 一、本仓库自身的许可

- 自研代码：Apache-2.0。每个源文件头部带完整的 Apache-2.0 许可声明与版权行。
- Apache-2.0 要求再分发者保留许可文本与 NOTICE，并声明对文件所做的修改，
  因此本文件同时承担 Apache-2.0 §4(b) 的 NOTICE 职责。

## 二、源码来源与归属声明

本仓库全部源码为原创实现。 随源码一并分发的第三方来源文件如下表（均为 Apache-2.0，保留来源说明）。

| 本仓库位置 | 上游项目 | 上游许可 | 处理方式 |
| --- | --- | --- | --- |
| `privernal-framework/privernal-common-redisson` 的 `protection/lock4j` 包 | Lock4j（<https://gitee.com/baomidou/lock4j>） | Apache-2.0 | 内嵌副本，保留上游包名与来源说明 |
| `sql/warm-flow-all.sql` | Warm-Flow 1.8.9 官方 MySQL 建表脚本（<https://github.com/dromara/warm-flow>） | Apache-2.0 | 取自上游脚本，文件头已声明来源与许可证并指向本文件的 LICENSE。与上游的确切差异以本仓库 git 记录为准；后续同步上游版本时须在此登记基线 commit/tag |

前端仓 `privernal-platform-ui` 另内嵌 Warm-Flow 官方流程设计器的构建产物（同为 Apache-2.0），
其许可证副本与来源、变更声明随该目录分发，详见该仓库 `NOTICE.md` 第五节。

对上游作者的致谢：本平台的早期骨架受益于 RuoYi-Cloud、yudao 等社区项目的工程实践。

## 三、第三方依赖许可

按许可证归类，依据各构件 pom 的 `licenses` 声明核实。本节只列归类时需要特别注意的构件，
**不宣称完备**——完整传递依赖（实测运行时 457 个构件）以第六节的 SBOM 命令输出为准。

### 需要特别关注的四项

1. **`com.mysql:mysql-connector-j`**（版本由 Spring Boot 依赖管理）
   GPL-2.0-only，附 Oracle Universal FOSS Exception 与链接许可段落。
   本项目以 Maven 坐标依赖、不分发驱动源码；使用者自行决定其部署组合的合规性。
   需要规避 GPL 链接争议时，可换用 `org.mariadb.jdbc:mariadb-java-client`（LGPL-2.1）。

2. **`com.xkcoding.justauth:justauth-spring-boot-starter:1.4.0`**
   LGPL-3.0-only（已从 Maven Central pom 核实）。要求提供该库的 LGPL-3.0 文本，
   并保证使用者可以替换该 jar；不得将其源码并入本仓库发布。
   注：被封装的核心库 `me.zhyd.oauth:JustAuth` 为 MIT，不受此限制。

3. **钉钉相关 SDK** —— 本项目不将其作为发布阻塞项。
   记录事实供下游自行判断：`com.aliyun:alibaba-dingtalk-service-sdk:2.0.0` 与
   `com.dingtalk.open:app-stream-client:1.3.7` 的 pom 均未声明 `<licenses>`。
   新版 `com.aliyun:dingtalk:2.1.90` 声明为 Apache-2.0。
   这三个构件都是钉钉/阿里云官方发布的开放平台 SDK，本仓库以 Maven 坐标依赖、不分发其源码，
   仅用于对接钉钉开放平台；项目方的判断是「作为官方 SDK 的正常使用，不需要额外许可」。
   旧版 SDK 之所以仍在用，是引入处注释所写的「有些接口还不支持新版」（5 个源文件依赖其接口）。
   若你把本项目的源码或制品再分发到自有产品，请自行确认与钉钉开放平台协议的匹配性。

4. **`ch.qos.logback:logback-classic` / `logback-core`**
   EPL-1.0 与 LGPL-2.1 双许可（由使用者择一）。本项目选择 **EPL-1.0**。

### 按许可证归类

**Apache-2.0**
Spring Boot 全家桶、`org.lionsoul:ip2region`（IP 归属地离线查询 2.7.0）、`org.dromara.warm:warm-flow-*`（工作流引擎 1.8.9）、
`cn.dev33:sa-token-*`（认证鉴权 1.45.0）、`com.baomidou:mybatis-plus-*`（3.5.12）、
`com.baomidou:lock4j-*`（2.2.7）、`org.redisson:redisson`（3.23.2）、
`org.apache.rocketmq:rocketmq-*`（5.2.0）、`org.apache.shardingsphere:shardingsphere-jdbc-core`（5.4.0）、
`io.minio:minio`（8.2.2）、`com.aliyun.oss:aliyun-sdk-oss`、`com.aliyun:dingtalk`（2.1.90）、
`com.corundumstudio.socketio:netty-socketio`、`com.google.zxing:core`（3.5.3）、
`com.alibaba.fastjson2`、`com.squareup.okhttp3`、
`io.jsonwebtoken:jjwt-*`、`com.google.guava`、`io.github.resilience4j`、
`org.apache.poi:poi-ooxml`、
`com.openhtmltopdf:openhtmltopdf-core` / `openhtmltopdf-pdfbox`（0.0.1-RC9）
及其传递的 `org.apache.pdfbox:pdfbox` 2.0.22 / `fontbox`、
`pro.fessional:kaptcha`（2.3.3，验证码，上游 penggle/kaptcha 以 Apache-2.0 发布）

Apache 基础库：`commons-io` 2.20.0、`commons-lang3` 3.17.0、`commons-codec` 1.18.0、
`commons-collections4` 4.4、`commons-pool2`、`commons-compress`、`commons-logging`

**MIT**
`me.zhyd.oauth:JustAuth`（1.16.7）、`com.auth0:java-jwt`、`org.projectlombok:lombok`、
`org.jsoup:jsoup`（1.17.2）、`com.microsoft.sqlserver:mssql-jdbc`

**New BSD**
`eu.bitwalker:UserAgentUtils`（User-Agent 解析，1.21）

**MIT（GraalVM）**
`org.graalvm.sdk:graal-sdk`（24.2.2，GraalVM 脚本上下文预留依赖）

**MulanPSL-2.0**（木兰宽松许可证，允许闭源商用，需保留声明）
`cn.hutool:hutool-all`（5.8.47；注：该构件 pom 未声明 license 节点，分类依据 hutool 官方仓库口径）

**双许可（择一，本项目选择宽松项）**
- `com.github.jsqlparser:jsqlparser` —— LGPL-2.1 / Apache-2.0，选 **Apache-2.0**
- `jakarta.persistence:jakarta.persistence-api` —— EPL-2.0 / EDL-1.0，选 **EDL-1.0**
- `com.sun.xml.bind:jaxb-*` —— EPL-2.0 / EDL-1.0，选 **EDL-1.0**

## 四、IP 归属地数据文件

`privernal-common-core` 的 `AddressUtils` 用 ip2region 的 xdb 离线数据做 IP 归属地查询。
查询代码是 Apache-2.0 的 Maven 依赖，已登记在上一节；
**xdb 数据文件不随本仓库分发**，由部署方自备并放到 `privernal.ip-region.db-path` 指向的位置。

这一点需要留意：ip2region 仓库里 `data/` 目录提供的 xdb **仅供测试**，
其内容聚合自多个第三方公开数据源。生产使用请自行确认数据来源与授权范围，
或改用你有明确商用授权的 IP 地理数据（自制 xdb 的工具由 ip2region 提供）。
本仓库因为不分发数据文件，不承担该数据的许可义务；部署方分发产品时需自行评估这一层。

## 五、单据打印的字体

`privernal-dynamic-core` 的 PDF 打印通道（`DocPrintPdfService`）在运行期按以下顺序查找
中文字体：`/fonts/docprint.ttf` → `/fonts/NotoSansSC-Regular.ttf` →
`/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf`。

字体文件**不随源码仓库分发**。自行部署时需把字体放入上述路径之一，并遵守其许可：
Noto Sans SC 为 SIL Open Font License 1.1，要求保留许可文本、不得单独售卖字体本身，
允许随作品嵌入分发。注意 openhtmltopdf 只接受 TTF，不接受 TTC/OTF。
许可文本见 <https://openfontlicense.org/open-font-license-official-text/>。

## 六、可复现的权威清单

本清单按直接依赖人工核对，传递依赖以下列命令生成的结果为准：

```bash
# 全部依赖的许可文本与清单
mvn org.codehaus.mojo:license-maven-plugin:aggregate-download-licenses \
    -DlicensesOutputFile=target/licenses.xml

# SBOM（CycloneDX）
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
```

派生工作（二次开发、集成进自有产品）在分发时应一并携带：
本文件、`LICENSE`，以及第五节字体的许可文本与第三节 copyleft 组件（JustAuth starter）的许可文本。

## 七、署名口径

- 源码 `@author` 统一使用公开 handle：`saperliu`（项目主要维护者）。
- 版权行统一为：`Copyright 2025-2026 saperliu.`
- 项目品牌：**Privernal Studio**（平台中文名「春祈平台」），用于 README、对外文档与界面文案；
  技术标识为仓库名 `privernal-platform`、模块前缀 `privernal-*`、Java 包名与 Maven groupId `com.privernal`。
- 表单设计器 **PhoenixDesigner** 是独立维护的开源产品（MIT），
  由前端以 npm 依赖方式集成，与本仓库没有源码分发关系；
  其上游来源与第三方清单由该 npm 包内自带的 LICENSE / NOTICE 载明。
