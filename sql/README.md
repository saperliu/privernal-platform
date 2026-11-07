# sql/ 目录说明

| 文件/目录 | 用途 |
| --- | --- |
| `init.sql` | MySQL 全量建库脚本（pro）：97 表 + 最小种子（组织/部门/admin/双角色/390 菜单/字典/系统配置/脚本模板）+ 结构变更台账表 |
| `init-sqlite.sql` | SQLite 全量建库脚本（dev/local）：与 init.sql 同表同种子，零外部依赖自举 |
| `datav-examples.sql` | 官方示例大屏（可选导入） |
| `warm-flow-all.sql` | warm-flow 引擎引擎侧建表参考（平台库的 flow_* 表已并入 init.sql，仅引擎独立部署时用） |
| `script-templates/` | 表单脚本资产官方模板（编号规则/冲突计数/时间重叠等 Groovy） |
| `migrations/` | **增量迁移脚本**（基线之后的结构变更，见下） |

## 迁移台账（ROADMAP 1.14）

全新安装直接执行 `init.sql` / `init-sqlite.sql`，其中已包含全部结构；**存量库升级**按本节流程走：

1. **对脚本**：`migrations/` 下按 `V<时间戳>__<说明>.<方言>.sql` 命名（如 `V202611081900__f4_password_digest.mysql.sql`）。
2. **先核对**：apply 前 `shasum -a 256 sql/migrations/<脚本>` 记录校验值；脚本头部写有回滚语句。
3. **再执行**：
   - MySQL：`mysql -u<user> -p --default-character-set=utf8mb4 <库名> < sql/migrations/<脚本>.mysql.sql`
   - SQLite：`sqlite3 data/privernal.db < sql/migrations/<脚本>.sqlite.sql`
4. **后登记**：脚本自带向 `platform_schema_history` 的 INSERT（执行人/时间）；执行人可把第 2 步的校验值 UPDATE 进对应行的 `checksum` 列。
5. **防重复**：`platform_schema_history` 对 `(script_name, dialect)` 唯一——重复执行迁移脚本时先查这张表。

### 台账表

`platform_schema_history`：version / script_name / dialect / checksum / applied_by / applied_at。
init 脚本建库时自带一条 `baseline` 行（对应本次全量基线）；此后每个结构变更一条记录，随代码仓库提交，配合 `git log sql/migrations/` 即可完整追溯任意环境的结构版本。

### 双库口径

每个结构变更**必须同时提供 `.mysql.sql` 与 `.sqlite.sql` 两个方言变体**（参照 V202611081900 两份），并在改动 SQLite 侧语句时核对 MySQL 变体（ROADMAP §10 维护约定第 3 条）。
