# 贡献指南

欢迎贡献。开始前请先读 [README.md](./README.md) 的快速开始部分，把服务跑起来。

## 环境要求

- JDK 21（项目以 `maven.compiler.source/target=21` 编译，低版本会失败）
- Maven 3.8+
- MySQL 8、Redis

## 构建与验证

```bash
mvn clean package      # 全量构建
mvn -pl privernal-server spring-boot:run   # 本地启动
```

提交前请确认：

1. `mvn clean package` 通过；
2. 若改动涉及数据库结构，同时提供 `sql/upgrade_*.sql` 增量脚本，
   并保证脚本可重复执行（幂等），不要在脚本里 DROP 生产可能已有数据的表；
3. 新增源文件带 Apache-2.0 许可头（与现有文件一致即可，CI 会检查）。

## 分支与提交

- 从 `main` 切出特性分支，命名建议 `feat/<范围>`、`fix/<范围>`。
- 提交信息用一动词开头的短句，说明**为什么改**而不只是改了什么。
  一次提交聚焦一件事；把无关格式化改动拆到单独提交。
- PR 里请写：问题背景、改动方案、如何验证（命令或截图）、影响范围。

## 代码约定

后端分层与命名有以下几条硬性要求：

- Mapper XML 里的 SQL，所有表都要加别名；
- 查询默认被注入 `organization_id = ?` 条件，取消注入需在 Mapper 接口上加
  `@IgnoreDataScope`（见开发者指南）；
- `@PathVariable` 必须显式写名字，否则参数解析会失败。

##  Issue 与功能请求

请尽量给出可复现的步骤、期望行为与实际行为。涉及安全漏洞请走
[SECURITY.md](./SECURITY.md)，不要直接公开发帖。

## 许可

向本项目提交贡献即表示你同意该贡献在 Apache License 2.0 下发布，
并确认你有权做出这一授权。
