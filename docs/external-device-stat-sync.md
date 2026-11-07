# 外部系统设备运行数据同步（WCS / WMS → SP）

SP 部署在与 WCS、WMS **同网段的虚拟化服务器**上，因此采用「后端 JDBC 直连源库只读视图」的方式拉取数据，
不走 DBLINK、不做数据库级双向依赖。

## 一、数据流

```
WCS 库明细表 WCS_SUBTASKINFOHISTORY
   └─(源库内：每天01:00汇总前一天)→ WHLD_WCS.DEVICE_STAT_DAILY
                                     └─(对外只读视图)→ VW_SP_DEVICE_STAT_DAILY ─┐
                                                                                │
WMS 库自带的每日汇总表 dws_device_daily_stat ──(SP 侧 query 取别名，不建视图)────┤
                                                                                │
DT 库设备运行状态表 dt_device_state（只取 DDJ）──(同上一节方式，取别名)──────────┤
                                                                                ├─ SP 后端每天01:30 ─→ SP 库 wms_device_stat_daily
                                                                                ┘
```

两条来源走的是**同一套同步代码**，差别只在配置里的 `query` 与 `date-param-format`；
WMS 侧不需要建视图、存储过程或定时任务（它已经有自己的汇总跑批），接入方式见第三节。

- **口径**：任务按 `CREATETIME` 归属统计日；WCS 侧只统计 `DEVICETYPE IN ('SC','AGV')` 且 `TASKSTATUS='1'` 的已完成任务。
- **指标**：`running_hours = SUM((FINISHEDTIME-SENDTIME)*24)`（单位**小时**，Oracle 日期相减得到的是天数），
  `task_count = COUNT(1)`（任务笔数），`total_qty = SUM(件数字段)`（处理件烟/料箱数量）。
- **为什么跑"前一天"而不是当天**：凌晨跑才能拿到完整一天，深夜完成的任务不会漏。
- **为什么区分 `source_sys`**：同一张表同时承载 WCS 与 WMS 的数据，后续再加第三方系统也不用改表结构。

## 二、源库侧要做的（WCS）

执行 [`docs/wcs-device-stat-daily.sql`](./wcs-device-stat-daily.sql)：

1. 建汇总表 `WHLD_WCS.DEVICE_STAT_DAILY`（含 `DEVICE_TYPE`）
2. 建汇总过程 `PRC_DEVICE_STAT_DAILY`（MERGE 幂等，可任意补跑某天）
3. 建 DBMS_SCHEDULER 任务 `JOB_DEVICE_STAT_DAILY`，每天 01:00 汇总前一天
4. 一次性历史补录
5. 建对外只读视图 `VW_SP_DEVICE_STAT_DAILY` + 只读账号 `SP_STAT_READER`（脚本里默认注释，需按实际密码放开）

> **对外契约（必须严格一致）**：视图列名与顺序 `stat_date / device_type / device_code / running_hours / task_count / total_qty`。
> 源库还没加件数列时，SP 侧配置里的 `query` 去掉 `total_qty` 即可（缺列按 0 处理），不影响其他指标。
> WMS 库只要按这四个数据列提供同名对象，SP 侧代码**零改动**。

## 三、WMS 现成表 `dws_device_daily_stat` 直连（不建视图）

WMS 侧已经有跑好的每日汇总表，不需要再建视图和存储过程，**在 SP 的 `query` 里取别名直接读**即可。
表结构（WMS 库，MySQL 8）：

```sql
CREATE TABLE dws_device_daily_stat (
  id          BIGINT UNSIGNED AUTO_INCREMENT,
  STAT_DATE   VARCHAR(8)   NOT NULL,   -- yyyyMMdd
  DEVICE_TYPE VARCHAR(20)  NOT NULL,   -- DDJ/TPX/LBJ/DK/FJX
  DEVICE_ID   VARCHAR(50)  NOT NULL DEFAULT '',
  SUM_HOURS   DECIMAL(10,2),           -- 运行时长(小时)
  SUM_QTY     DECIMAL(14,2),           -- 任务数量
  PIECE_QTY   DECIMAL(14,2),           -- 件烟数量
  create_time DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stat (STAT_DATE, DEVICE_TYPE, DEVICE_ID)
);
```

### 3.1 字段映射

| SP 契约列 | WMS 列 | 说明 |
| --- | --- | --- |
| `stat_date` | `STAT_DATE` | **varchar(8) `yyyyMMdd`**，不是 DATE —— 见 3.2 |
| `device_type` | `DEVICE_TYPE` | DDJ/TPX/LBJ/DK/FJX 原样透传，SP 侧不做枚举校验，只当分组维度 |
| `device_code` | `DEVICE_ID` | 必须与 `wms_equipment.code` 一致，否则件数口径取不到数（见 3.4） |
| `running_hours` | `SUM_HOURS` | 小时 |
| `task_count` | `SUM_QTY` | 表注释是"任务数量"，实际存的是任务笔数 |
| `total_qty` | `PIECE_QTY` | 件烟数量，备件寿命 QTY 口径的取数来源 |

`source_sys` 由 SP 配置里的 `code`（填 `WMS`）写入，源表不需要有这一列。

### 3.2 必须配 `date-param-format: yyyyMMdd`（最容易踩的坑）

`STAT_DATE` 是 varchar，SP 默认按 `java.sql.Date` 传两个日期参数。MySQL Connector/J 默认
（`useServerPrepStmts=false`，文本协议）会把 `java.sql.Date` 发成 `'2026-10-27'` 字面量，
与 `'20261027'` 做**字符串比较**，条件恒不成立 —— 表现是**同步成功、接口日志正常、但入库 0 条**。

同一条 SQL 在本地 MySQL 8.0.46 上实测：

| 参数形式 | 命中行数 | 执行计划 |
| --- | --- | --- |
| `java.sql.Date`（默认文本协议） | **0** | 条件恒假，扫全表不命中 |
| `java.sql.Date`（`useServerPrepStmts=true`） | 5 | `type=ALL`，列被隐式转换，**索引失效** |
| 字符串 `'20261027'`（`date-param-format: yyyyMMdd`） | 5 | `type=range`，命中 `uk_stat` |

所以正确做法是显式声明日期参数格式，让 SP 传 `yyyyMMdd` 字符串：既拿到数据，又走唯一索引。
源库日期列如果是 `DATE`/`DATETIME` 类型，则**不要**配这项，保持默认按日期类型传。

### 3.3 配置

```yaml
ext-stat:
  enabled: true
  cron: 0 30 1 * * *            # 必须晚于 WMS 自己的跑批时间
  sources:
    - code: WMS
      peer: WMS生产库/dws_device_daily_stat
      url: jdbc:mysql://<wms-db>:3306/wms?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
      username: sp_stat_reader  # 只给这张表 SELECT
      password: '********'
      driver-class-name: com.mysql.cj.jdbc.Driver
      date-param-format: yyyyMMdd
      query: >
        select DEVICE_TYPE as device_type,
               DEVICE_ID   as device_code,
               SUM_HOURS   as running_hours,
               SUM_QTY     as task_count,
               PIECE_QTY   as total_qty
        from dws_device_daily_stat
        where STAT_DATE >= ? and STAT_DATE < ? and DEVICE_ID <> ''
```

- 驱动**不用** `-Poracle`：`mysql-connector-j` 本来就是主库驱动，`runtime` 作用域足够。
- 若 SP 与 WMS 在同一个 MySQL 实例，`url` 连本实例、`query` 里写 `wms.dws_device_daily_stat` 也行，
  但账号必须有该库的 SELECT 权限。
- `and DEVICE_ID <> ''` 照抄：表定义允许空设备号（可能是线体级汇总），SP 会丢弃这类行
  （否则空串会被当成同一台设备累加件数），丢弃条数写在同步明细里，不会静默吞掉。

### 3.4 设备档案要对齐，否则件数口径是空的

`PIECE_QTY` 进 SP 后按 `device_code` 累计，用于备件寿命的"处理件数"口径，匹配的是
`wms_equipment.code`。上线前需要把 SP 设备档案的编号改成与 WMS 设备号**完全一致**
（大小写、前导零都要一致），例如 `DDJ01` / `TPX01` / `FJX01`。

对不上的表现：`/api/spare-part-usages` 里该备件的 QTY 用量一直是"无数据"，不会报错。
核对 SQL：

```sql
-- SP 库：设备档案里有哪些编号在 WMS 统计表里找不到
SELECT e.code FROM wms_equipment e
WHERE NOT EXISTS (SELECT 1 FROM wms_device_stat_daily d
                  WHERE d.source_sys='WMS' AND d.device_code = e.code);
```

### 3.5 历史回填

`POST /api/device-stats/sync` 一次只拉一天，历史数据循环调用即可（重复拉是覆盖更新，可反复跑）：

```bash
D=2026-09-01
while [[ "$D" < "2026-10-29" ]]; do
  curl -s -X POST -H "Authorization: Bearer $TOKEN" \
    "http://<sp>:8080/api/device-stats/sync?date=$D" | head -c 300; echo
  D=$(date -d "$D +1 day" +%F)
done
```

### 3.6 测试库联调实测（2026-10-30）

对着 WMS 测试库（MySQL 8.0.39）跑通了全链路，结论与遗留项：

| 项 | 结果 |
| --- | --- |
| 库名 | **`ldwl_wms`（下划线）**，不是 `ldwl-wms`；JDBC url 与 GRANT 都要按实际写 |
| 只读账号 | `sp_stat_reader`，仅 `GRANT SELECT ON ldwl_wms.dws_device_daily_stat` |
| 表结构 | 与本文档 DDL 一致，`uk_stat (STAT_DATE, DEVICE_TYPE, DEVICE_ID)` 唯一键 |
| 同步结果 | 3 天 43 行，**逐字段与源表完全一致**（工时/任务数/件数） |
| 幂等 | 同一天重拉两次，行数与数值不变 |
| 默认日期 | 不传 `date` 拉昨天，源侧无数据时返回 0 条且不算失败 |
| 日期参数 | 必须 `date-param-format: yyyyMMdd`，否则 0 条（见 3.2） |

两个必须由现场决策的问题：

1. **`SUM_HOURS` 只有部分设备有值** —— WMS 侧对部分设备统计了运行时长，另一些恒为 0。
   现在 HOURS / TASKS 口径都能**回落到本表**累加（见 `DeviceCumulativeService`），
   优先级仍是 `vw_sp_equipment_runtime` 实时视图高于本表：
   - 该设备在本表里 `running_hours` 有非零记录 → 累加后作为累计运行小时
   - 全为 0 / NULL → 视为「未上报」，返回无数据而不是 0（0 会被当成真实累计值建基准）
   - 因此只接这张表也能跑 HOURS / TASKS 口径，只是覆盖不到不上报时长的那些设备
2. **设备编号体系不同** —— WMS 是 `5201`/`5160`/`SO20`/`LBJ1`/`MD3` 这类，SP 档案是 `SC01`（1号堆垛机）。
   件数口径按 `device_code` 精确匹配 `wms_equipment.code`，编号不统一则一条都匹配不上，
   且不报错（表现为该备件 QTY 用量长期"无数据"）。
   实测：`device_code='SC01'` 命中 0 行，`device_code='5201'` 命中 3 天共 2880 件。

### 3.7 设备档案对齐（2026-10-30 已执行）

按"SP 新系统、改动成本低"的思路，SP 设备档案统一采用 WMS 设备号，已建 19 台
（脚本 `docs/wms-equipment-seed.sql`，幂等，可重复执行）：

| WMS 设备类型 | 设备号 |
| --- | --- |
| DDJ | 5201、5220、5239、5259、5279、5298 |
| TPX | 5160、5161、5168、5174、5180、5186 |
| DK | MD3、MD4、MD7、MD8 |
| FJX | SO20 |
| LBJ | LBJ1、LBJ2 |

- `code` = `name` = WMS 设备号；`model` = WMS 设备类型代码；`remark` 记录来源表
- 原 `SC01`（1号堆垛机）按要求**保留不动**，它与 WMS 任一设备号都不匹配，取不到 WMS 的 QTY 数据；
  但 DT 源接入后（`001` → `SC01`）它能拿到工时与任务数，HOURS / TASKS 口径可用，见第四节
- 类型中文名（DDJ/TPX/DK/FJX/LBJ）现场确认后一条 UPDATE 批量补进 `name`，示例见脚本末尾
- 验证：43 行统计数据 **全部匹配到设备档案**（对齐前为 0 行匹配）
- 未做：设备与备件的 `wms_equipment_part` 关联（目前只有 SC01 有 1 条），QTY 口径要出数必须先挂备件

上线前还需确认 WMS 侧 `dws_device_daily_stat` 的跑批时间，SP 的 `ext-stat.cron` 必须晚于它。

## 四、DT 系统 `dt_device_state` 直连（堆垛机，2026-10-31 接入）

数字孪生系统 10.156.191.33 / `dt-system.dt_device_state`，只读账号 `readonly`。
**只取 `device_type_key='DDJ'`**：源库里其他类型（AGV/SSJ/DSYZJ/BZJ...）的数据不真实，接进来会污染预测。

### 4.1 字段映射

| SP 契约列 | DT 列 | 说明 |
| --- | --- | --- |
| `device_type` | `device_type_key` | 固定 `DDJ`（堆垛机） |
| `device_code` | `device_number` | **需转换**：`001` → `SC01`，用 `concat('SC', right(device_number, 2))` |
| `running_hours` | `work_duration` | **单位是秒**，必须 `/ 3600`；见 4.2 |
| `task_count` | `task_count` | 原样 |
| `total_qty` | — | 该表没有件数字段，不 select，SP 侧按 0 处理 |

- `date` 是 **varchar(10) 存 `yyyyMMdd`**（如 `20261031`），必须配 `date-param-format: yyyyMMdd`，理由同 3.2。
- **每设备每天恰好一行**（实测 7464 行 = 7464 个「设备×日期」组合），不需要 `GROUP BY`。
- DDJ 共 12 台，`device_number` 为 `001`~`012`；SP 侧对应 `SC01`~`SC12`，由 V27 迁移补齐（原只有 `SC01`）。

### 4.2 为什么用 `work_duration` 而不是 `run_duration`

两个字段单位都是秒，但量级差 7 倍多（12 台合计/天）：

| 字段 | 含义 | 12 台合计 | 单台日均 |
| --- | --- | --- | --- |
| `work_duration` | 工作时长（实际作业） | 约 33 小时 | 约 2.7 小时 |
| `run_duration` | 运行时长（含待机/空转） | 约 254 小时 | 约 21 小时 |

备件磨损由实际作业驱动，故选 **`work_duration`**。额定寿命若按 5000 小时填，
两种口径算出来的更换周期差 7 倍（约 5 年 vs 约 8 个月），**填额定值时必须按「纯作业小时」填**。
要改用运行时长，把 query 里的 `work_duration` 换成 `run_duration` 即可，其余不用动。

### 4.3 配置

```yaml
ext-stat:
  enabled: true
  sources:
    - code: DT
      peer: 数字孪生系统/dt_device_state
      url: jdbc:mysql://10.156.191.33:3306/dt-system?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useCursorFetch=true
      username: readonly
      password: ${EXT_STAT_DT_PASSWORD:...}     # 生产环境用环境变量注入，不要写死在配置文件里
      driver-class-name: com.mysql.cj.jdbc.Driver
      date-param-format: yyyyMMdd
      query: >
        select device_type_key                       as device_type,
               concat('SC', right(device_number, 2)) as device_code,
               round(work_duration / 3600, 2)        as running_hours,
               task_count                            as task_count
        from dt_device_state
        where device_type_key = 'DDJ'
          and `date` >= ? and `date` < ?
          and device_number <> ''
```

### 4.4 首次接入：只拉最近 5 天

历史数据从 20250119 开始，但**只需最近 5 天**，更早的不拉（基准机制用差值，起步晚不影响准确度）：

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "http://<sp>:8080/api/device-stats/sync-range?days=5"
```

- `days=N`：拉「到昨天为止」的最近 N 天。**不含今天**——当天数据还没跑完，拉进来是残缺值。
- 也可以传区间：`?from=2026-10-26&to=2026-10-30`
- 限制：一次最多 366 天，避免手滑填错把源库扫爆
- 入库是 upsert，可反复执行，随时补任意日期

### 4.5 之后每天自动拉一天

与 WMS 共用同一个定时任务 `DeviceStatDailyScheduler`，`ext-stat.cron`（默认 01:30）跑
`syncYesterday()`，每个源各拉各的「前一天」，**不需要为新源加任务**。
今天的数据会在明天 01:30 被拉全，不会漏。

## 五、SP 库侧

Flyway 自动执行 `V9__device_stat_daily.sql` 建表 `wms_device_stat_daily`，唯一键：

```
uk_device_stat_daily (stat_date, source_sys, device_type, device_code)
```

重复拉取是「覆盖更新」，不会插重，随时可以手动补跑任意日期。

## 六、SP 后端配置

`application.yml` 中 `ext-stat.enabled` 默认 **true**（DT 源已在 `sources` 里配好）；退回未接线状态用 `EXT_STAT_ENABLED=false` 或 `EXT_STAT_DT_ENABLED=false` 关掉单个源。某个源的库连不上不会拖垮启动——Hikari 是懒连接，只在同步时报错并记进接口日志。

> 数据源是数组结构，环境变量不好表达。DT 的账号密码仍支持用环境变量覆盖（`EXT_STAT_DT_URL` / `EXT_STAT_DT_USERNAME` / `EXT_STAT_DT_PASSWORD`），生产环境请用这种方式，不要把口令留在配置文件里。
数据源是数组结构，环境变量不好表达，部署时用**外部配置文件挂载**：

```bash
java -jar wms-backend.jar \
  --spring.datasource.url=jdbc:mysql://<sp-db>:3306/spare_parts \
  --spring.config.additional-location=/opt/sp/conf/ext-stat.yml \
  --ext-stat.enabled=true
```

`/opt/sp/conf/ext-stat.yml`：

```yaml
ext-stat:
  cron: 0 30 1 * * *          # 每天 01:30
  zone: Asia/Shanghai
  sources:
    - code: WCS
      peer: WCS生产库
      url: jdbc:oracle:thin:@//10.157.111.20:1521/wcs
      username: SP_STAT_READER
      password: '********'
      driver-class-name: oracle.jdbc.OracleDriver
      query: >
        select device_type, device_code, running_hours, task_count, total_qty
        from vw_sp_device_stat_daily
        where stat_date >= ? and stat_date < ?
    - code: WMS
      enabled: false          # 接好了再打开
      peer: WMS生产库
      url: jdbc:oracle:thin:@//10.157.111.30:1521/wms
      username: SP_STAT_READER
      password: '********'
      query: >
        select device_type, device_code, running_hours, task_count, total_qty
        from vw_sp_device_stat_daily
        where stat_date >= ? and stat_date < ?
```

Oracle 驱动（`ojdbc11` + `orai18n`）已经放在 pom 的**默认依赖**里，直接打包即可，不需要额外 profile：

```bash
mvn clean package -DskipTests
```

> 注：早期版本把驱动放进 `oracle` profile，用 `-Poracle` 激活。在 IDE 里直接 Run 走的是默认 profile，
> 会抛 `Failed to load driver class oracle.jdbc.OracleDriver` 导致启动失败，所以改成默认依赖。
> 如果源库是 MySQL/SQL Server，把 `driver-class-name` 换掉即可，同样不用 profile。

## 七、编码实现清单

| 文件 | 作用 |
| --- | --- |
| `entity/DeviceStatDaily.java` | 本地表实体 |
| `repository/DeviceStatDailyRepository.java` | 查询接口 |
| `config/ExternalStatProperties.java` | 多源数据源配置绑定（含 `date-param-format`） |
| `config/ExternalStatDataSourceConfig.java` | 每个源一个只读 Hikari 池，`Map<code, JdbcTemplate>` |
| `service/DeviceStatDailySyncService.java` | 拉取 + MySQL upsert + 接口日志记录 |
| `service/DeviceStatDailyScheduler.java` | 每天定时拉前一天 |
| `service/DeviceStatDailyQuery.java` | 本地数据查询 |
| `controller/DeviceStatDailyController.java` | 查看 + 手动补拉接口 |

## 八、验证与运维

```bash
# 手动补拉（需要 system:interface 权限）；不传 date 默认拉昨天
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "http://<sp>:8080/api/device-stats/sync?date=2026-10-26"
# 查询结果
curl -H "Authorization: Bearer $TOKEN" \
  "http://<sp>:8080/api/device-stats?sourceSys=WCS&from=2026-10-26&to=2026-10-26"
```

返回体里的 `details` 每个源一行，例如：

```
WMS：4 条
WMS_RAW：0 条（丢弃 1 条设备号为空的数据）
WMS_DOWN：失败 - Failed to obtain JDBC Connection
```

单源失败不影响其他源（`failedSources` 计数 + 接口日志记错误），所以「部分源接好」也能先跑起来。
注意「0 条」不等于失败：源侧那天没跑批、或日期参数格式不对，都会是 0 条 —— 后者看第 3.2 节。

```sql
-- SP 库：当天有没有拉到数据
SELECT source_sys, COUNT(*) devices, SUM(task_count) tasks, SUM(total_qty) pieces,
       ROUND(SUM(running_hours),2) hours
FROM wms_device_stat_daily WHERE stat_date = '2026-10-26' GROUP BY source_sys;
-- SP 库：某设备近期趋势
SELECT stat_date, task_count, total_qty, running_hours FROM wms_device_stat_daily
WHERE source_sys='WCS' AND device_code='S01' ORDER BY stat_date DESC LIMIT 30;
```

失败信息会写进「接口管理」页面（`DEVICE_STAT_DAILY_SYNC`），或者直接看后端日志关键字 `[设备运行数据同步]`。

## 九、这批数据还会用到哪

除趋势展示外，`total_qty` 是备件寿命 **QTY（处理件数）口径** 的取数来源之一：

- 设备运行视图 `vw_sp_equipment_runtime` 提供了可选的 `total_piece_qty` 列时，优先用视图的实时累计值；
- 没有该列时，自动回落到本表按 `device_code` 累加 `total_qty`。

所以「断续运行的设备按什么算寿命」这件事，不要求源库一定要改视图——只要这条同步链路跑通，件数口径就能用。细节见 `docs/spare-part-life-metric.md`。

同一设备号在 WCS 与 WMS 都存在时，为避免跨源混算，可指定来源：

```yaml
spare-part:
  usage-sync:
    piece-source: WCS   # 留空 = 跨源累加
```


常见故障：

- `ORA-00942 表或视图不存在` → 源库只读账号没授权视图，或视图名大小写（`SELECT` 不需要 schema 前缀时依赖同义词）。
- 拉到 0 条 → 源侧任务还没跑（等 01:00 之后），或日期口径不一致；手动跑一次源侧过程即可。
- 连接超时 → 检查同网段 1521 是否放通，不要用跳板机地址写进 jdbc url。
