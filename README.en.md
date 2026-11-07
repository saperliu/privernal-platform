# Privernal Studio Server

[简体中文](./README.md) | English

Privernal Studio is an open-source, self-hostable low-code platform for enterprise applications; this repository is its backend. Data models are defined as **dynamic forms** (physical tables generated at runtime, with publish / versioning / rollback); approvals run on the **warm-flow** workflow engine (node-level permissions, countersign, timeout handling) — all on top of **organization-level data permissions**, with reporting & charts, big-screen dashboards, document printing (HTML→PDF), a scheduled-task workbench and open integrations (OpenAPI / Webhook / SSO). Built-in **AI assistants** generate forms, workflows and charts from natural language. Java 21 + Spring Boot 3.5 + MyBatis-Plus, works with both MySQL and SQLite, Maven multi-module — no SaaS dependency required to run.

Companion projects: web frontend [privernal-platform-ui](https://github.com/saperliu/privernal-platform-ui) · mobile app `privernal-app` · form designer [PhoenixDesigner](https://github.com/saperliu/phoenix-designer) (npm).

> This repository contains the backend only. It is a complete, self-deployable service that runs without any SaaS or external accounts.

## Capabilities

| Area | Description |
| --- | --- |
| Dynamic forms | Physical tables are created at runtime from form definitions; definitions support publish, versioning and rollback; sub-tables, related data, field-level defaults and soft delete |
| Workflow | Based on warm-flow; assignee / CC / countersign / timeout routing, node-level field & button permissions, form-workflow linkage |
| Data permissions | Organization-wide isolation, plus department-scope and custom-scope annotations; dynamic datasource routing |
| Scheduling | Database-lease leader election + second-level scanning with CAS; in-flight config changes are staged safely, with alerting and metric snapshots |
| Messaging | In-app messages (Socket.IO push), SMS, WeCom and DingTalk channels |
| Files | Local disk / Alibaba Cloud OSS / MinIO — switchable storage backends |
| Reports & printing | Custom datasets and charts; document printing via an HTML→PDF channel with offline templates and CJK fonts |
| Integration | OpenAPI signed calls, outbound Webhook subscriptions, SSO (OIDC), third-party OAuth login |
| Application packages | Forms / workflows / menus / scripts packaged per app; export and rebuild across environments on import |
| Script assets | Groovy script template center for business-logic extensions inside action nodes |

## Tech Stack

- Java 21, Spring Boot 3.5.4
- Sa-Token 1.45.0 (authentication & authorization), MyBatis-Plus 3.5.12, warm-flow 1.8.9 (workflow)
- MySQL 8, Redis, optional RocketMQ / MinIO / Alibaba Cloud OSS
- springdoc-openAPI (API docs), Hutool, EasyPOI / POI

See [NOTICE.md](./NOTICE.md) for the dependency license inventory.

## Module Structure

```
com.privernal:privernal-platform
├── privernal-framework                     // framework layer, business-agnostic
│   ├── privernal-common-core               // base utilities, unified response, constants & exceptions, file and Excel support
│   ├── privernal-common-web                // web-layer configuration, paging, Swagger auto-configuration
│   ├── privernal-common-security           // security context, token & JWT
│   ├── privernal-common-mybatis            // mapper base classes, SQL injectors, dialect extensions
│   ├── privernal-common-datasource         // dynamic datasource routing
│   ├── privernal-common-datascope          // organization/department data-permission interception
│   ├── privernal-common-redis              // caching
│   ├── privernal-common-redisson           // Redisson, distributed locks & idempotency
│   ├── privernal-common-rocketmq           // message queue
│   ├── privernal-common-log                // operation logging
│   ├── privernal-common-dingtalk           // DingTalk channel
│   └── privernal-common-wx                 // WeCom channel
├── privernal-system                        // business layer, each domain split into *-api (contract) and *-core (implementation)
│   ├── privernal-service-system            // organization, department, user, role, menu, dictionary, login
│   ├── privernal-service-dynamic           // dynamic forms, data tables, reports, printing, advanced settings
│   ├── privernal-service-flow              // workflow
│   ├── privernal-service-file              // file service
│   ├── privernal-service-message           // in-app messaging & push
│   └── privernal-service-job               // scheduled tasks & scheduling kernel
├── privernal-server                        // boot module: assembles all layers above, port 8600
└── sql/                                    // database bootstrap scripts and incremental migrations
```

## Quick Start

### 1. Start the dependencies

```bash
docker compose up -d          # MySQL 8 + Redis
```

Or prepare MySQL 8 and Redis yourself and note the connection parameters.

### 2. Create the database

```bash
mysql -u root -p < sql/init.sql
```

The script creates the `dynamic_platform` database with 97 platform tables and the minimal seed data required to run.
See [sql/README.md](./sql/README.md) for details and caveats.

### 3. Configure

Configuration files are not committed; copy one from the example:

```bash
cp privernal-server/src/main/resources/application-local.yaml.example \
   privernal-server/src/main/resources/application-local.yaml
```

Open `application-local.yaml` and fill in `MYSQL_HOST` / `MYSQL_PASSWORD` etc.
Every environment-dependent parameter can be overridden by an environment variable of the same name, so containerized deployments don't need file edits.

Optional: IP geolocation in login logs. The platform uses ip2region for offline lookups and never sends IPs to external services;
to populate that field, provide your own xdb data file and set `privernal.ip-region.db-path`
(supports `file:` and `classpath:` prefixes). When unset, the address column records "unknown" and everything else is unaffected.

### 4. Start

```bash
mvn clean package
java -jar privernal-server/target/privernal-server.jar
```

During development you can run `com.privernal.PrivernalServerApplication` directly.

### 5. Sign in

- API docs: `http://localhost:8600/swagger-ui`
- Built-in account: `admin` / `admin123`

> ⚠️ **Change the password immediately after the first login.** The default password exists only for first entry;
> keeping it on any externally reachable deployment is equivalent to publishing admin access.

## API Conventions

| Prefix | Purpose |
| --- | --- |
| `/back/**` | Admin backend endpoints |
| `/app/**` | End-user endpoints |
| `/inner/**` | Internal service-to-service calls, never exposed externally |
| `/openapi/**` | Machine calls, handled by a dedicated signature-verification filter, no login session |

For secondary development, the annotations involved (`@PreAuthorize` / `@IgnoreDataScope` / `@DS`) and the data-permission mechanism are documented in the source comments of the corresponding annotations and aspects.

## Documentation

- [CONTRIBUTING.md](./CONTRIBUTING.md) — contribution guide
- [SECURITY.md](./SECURITY.md) — how to report security vulnerabilities
- [NOTICE.md](./NOTICE.md) — licenses and third-party notices
- [CHANGELOG.md](./CHANGELOG.md) — release notes

## Maintainer

Primary maintainer: `saperliu`. See the repository's Contributors page for the contributor list;
the full development history lives in the commit log.

Security vulnerabilities must be reported privately via [SECURITY.md](./SECURITY.md) — please do not open public posts.

## License

Apache License 2.0, see [LICENSE](./LICENSE).
When redistributing, [NOTICE.md](./NOTICE.md) must be included as well.
