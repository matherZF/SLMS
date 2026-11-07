# SP 备品备件管理系统 — 整体审查报告

> 审查日期：2026-10-29　｜　代码基线：后端 185 个类 / 前端 27 页面 + 5 组件 / Flyway V1~V11（修复后 V1~V13）
> 审查方式：四路并行静态审计（安全权限、库存事务、数据模型、前端），所有 P0/P1 结论均已回到源码人工复核，标注了文件行号。
> 本报告只记录**经源码验证**的问题，未经核实的一律不列入。

---

## 0. 结论速览

| 等级 | 数量 | 代表问题 |
|---|---|---|
| **P0 致命** | 6 | 盘点差异功能 100% 失败；出库全流程无权限校验可被任意登录用户扣库存；超管弱口令；登录态死循环；权限兜底无限重定向白屏；批次号 100 秒循环碰撞 |
| **P1 严重** | 8 | 盘点覆盖库存产生幽灵库存；电子标签按键跨单确认；上架无锁可超收货；台账按钮全员 403；CORS 硬编码通配；分配给库位后灯光状态显示错误 |
| **P2 缺陷** | 10 | 事务内同步调外部 HTTP/JDBC；多索引/唯一约束缺失；单号并发竞态；定时任务无集群锁；`page:*` 权限后端不校验 |
| **P3 缺漏** | 8 | 预警无持久化与通知；无 Excel 导入导出；无采购/补货建议；无数据删除保护；无健康检查 |

**当前系统最危险的一点**：P0-1（盘点）和 P0-2（出库越权）互为放大——盘点那条一旦被临时绕过/修复，库存调整权限同样缺少收口。

---

## 一、P0 致命问题（建议本周修复）

### P0-1　盘点提交差异必然报错 500，整条差异复核链路不可用

**验证过的证据链**

```sql
-- V1__baseline_schema.sql:459
`status` enum('COUNTING','DONE','CANCELLED') NOT NULL,
```
```java
// StocktakeStatus.java
COUNTING, PENDING_REVIEW, DONE, CANCELLED          // 4 个值
// StocktakeService.java:92
order.setStatus(StocktakeStatus.PENDING_REVIEW);   // 写第 4 个值
```

全量检索 `db/migration/` 的 V1~V11，**没有任何一条脚本 ALTER 过 `wms_stocktake_order.status`**（已执行 `grep -rn stocktake_order` 确认）。

**后果**：MySQL `sql_mode` 含 `STRICT_TRANS_TABLES`，写未声明的枚举值报 `ERROR 1265 Data truncated` → 整个事务回滚 → 盘点单永远卡在 `COUNTING`。**只要有 1 条差异就会触发，而真实盘点必然有差异。**
连带后果：`BusinessApprovalService` 的 `STOCKTAKE_DIFF` 审批分支成了死代码，`Stocktake.vue` 永远拿不到 `PENDING_REVIEW` 状态。

**为什么拖到现在没发现**：`application-h2.yml` 用 `ddl-auto: update`，Hibernate 自建 varchar，任何值都能写。

**修复**（已实现为 `V12__status_column_and_index_fixes.sql`）

⚠️ **这里有个必须注意的坑，实测踩到了**：不能把 status 改成 `VARCHAR(16)`。
Hibernate 6 对 `@Enumerated(EnumType.STRING)` 期望的列类型码就是 `Types#ENUM`，
项目 `ddl-auto=validate`，改成 VARCHAR 会导致**应用启动直接失败**：

```
Schema-validation: wrong column type encountered in column [status] in table [wms_putaway_order];
found [varchar (Types#VARCHAR)], but expecting [enum ('pending','done') (Types#ENUM)]
```

正确做法是**只扩展 ENUM 的值域**：
```sql
ALTER TABLE wms_stocktake_order
  MODIFY COLUMN `status` ENUM('COUNTING','PENDING_REVIEW','DONE','CANCELLED') NOT NULL;
```
V12 里用 `information_schema.columns` 判断"当前不是 ENUM 或值域里没有 PENDING_REVIEW"才执行，
因此对"已经是正确 enum"的库是幂等的。

---

### P0-2　出库全流程缺 `outbound:picking` 校验，`completePicking` 无归属校验 → 任意登录用户可扣减任意单据库存

`PermissionCatalog.java:10` 定义了 `outbound:picking`，`DataInitializer` 把它授给 WAREHOUSE，前端 `router/index.js:80` 用它控页面，**但后端 0 处 `@PreAuthorize` 使用它**。

```java
// OutboundController.java —— 以下全部无权限注解
@PostMapping("/{orderId}/items/{itemId}/assign")   // L61  分配库位
@PostMapping("/{id}/start")                        // L71  开始拣货、占库存、点灯
@PostMapping("/{id}/scan-verify")                  // L79
@PostMapping("/{id}/complete")                     // L117 扣库存、建台账、灭灯
```

```java
// OutboundService.completePicking:484-560 —— 只校验状态，不校验人
OutboundOrder order = orderRepo.findDetailForUpdate(orderId).orElseThrow(...);
if (order.getStatus() != OutboundStatus.PICKING) throw new BizException("仅拣货中状态可完成拣货");
inv.setQty(afterQty);                              // L527 直接扣
sparePartUsageService.createFromOutbound(...);     // L549 建台账
```

**可利用性**：任意登录账号（哪怕只有 `page:workbench` 的只读角色）拿到/撞到 `orderId`：
1. `POST /api/outbound/{id}/complete` → 库存被扣、台账被建、灯被灭；
2. `POST /api/outbound/{id}/start` → 替别人占库存、点货位灯；
3. `GET /api/outbound/{id}` → 读取任意单据的申请人、领用人、联系电话、部门、审批意见（`OutboundService.java:715-723`）。

**对照**：同文件的 `cancel`（L461-466）和 `resubmit`（L442-444）做对了，是修复范式：
```java
boolean canManage = auth.getAuthorities().stream().anyMatch(a -> "outbound:manage".equals(a.getAuthority()));
if (!canManage && !Objects.equals(order.getApplicant(), currentOperator())) throw new BizException("...");
```

**修复**
1. `assign/start/scan-verify/complete` 四个方法加 `@PreAuthorize("hasAuthority('outbound:picking')")`；
2. `completePicking` 内补 `canManage || order.getApplicant().equals(currentOperator())`；
3. 读接口加 `@PreAuthorize("hasAnyAuthority('page:outbound','page:approval','outbound:manage')")`。

---

### P0-3　默认超管 `admin/admin123` 硬编码 + 登录无防爆破

```java
// DataInitializer.java:92-100
if (userRepo.count() == 0) {
    userRepo.save(new User("admin", passwordEncoder.encode("admin123"), "管理员", "ADMIN"));
    userRepo.save(new User("operator", passwordEncoder.encode("operator123"), "仓库业务员", "WAREHOUSE"));
}
```
`admin` 持有 `PermissionCatalog.ALL` 全权限（L179）。触发条件只是用户表为空——全新部署、迁移漏表、误删用户都会静默重建。
`Login.vue:26-29` 还把这两个账号密码**明文渲染在登录页**，生产构（`import.meta.env.PROD === true`）照样显示，另附一个诊断面板暴露内网地址 `http://10.157.111.213:8080/api`。

`AuthController.login`（L43-57）无失败计数、无锁定、无 IP 限流、无验证码。

**修复**：改环境变量强口令 + 首登强制改密；删除 operator 种子；登录页去掉账号提示与诊断面板；加 Bucket4j 限流（5 次/分钟 + 10 次锁 15 分钟）。

---

### P0-4　前端 token 过期死循环：清 localStorage 不清 Pinia，登录页↔工作台反复横跳

```js
// api/request.js:34-45
localStorage.removeItem('wms_token'); localStorage.removeItem('wms_user');
if (!window.location.hash.includes('/login')) window.location.hash = '#/login'
ElMessage.error(body.msg || '请求失败')
```
只清 localStorage，`useAuthStore` 的 `token/user` 两个 ref 仍是旧值，`isLoggedIn` 仍为 true；
跳到 `#/login` 后守卫 `router/index.js:148-152` 判定 `isLoggedIn && path==='/login'` → `next('/workbench')`；
workbench 再发请求 → 再 401 → 再跳 `/login`……每轮弹一次 toast，**用户既看不到登录页也退不出去**。

**修复**：request.js 里 `window.dispatchEvent(new CustomEvent('wms:unauthorized'))`，由 App.vue 监听并调 `authStore.logout()` + `router.replace('/login')`，加 `isRedirecting` 防重入。

---

### P0-5　权限不足兜底跳 `/workbench`，而 `/workbench` 自己也要权限 → 无限重定向白屏

```js
// router/index.js:159
if (to.meta.permission && !authStore.can(to.meta.permission)) return next('/workbench')
```
`/workbench` 的 meta 本身就是 `permission: 'page:workbench'`（L31）。vue-router 4.6.4 检测到 `possibly infinite redirection` 会中止导航 → 白屏。

**触发路径真实存在**：`Roles.vue` 允许配置不含 `page:workbench` 的角色（`PermissionCatalog.validate` 只校验按钮隶属页面，不强制工作台）。内置角色恰好都带，所以只在自定义角色上暴露。

**修复**：兜底目标改为无权限要求的 `/403` 页，并加 `if (to.path === '/workbench') return next(false)` 中止保护。

---

### P0-6　批次号生成器每 100 秒循环，建入库单随机失败

```java
// PutawayOrderService.java:56-60
return prefix + System.currentTimeMillis() % 100000;   // 每 100 秒回到原点
```
同一天内两次建单只要间隔是 100 秒的整数倍就撞号 → `existsByItemKey` 为真 → 抛 `"批次号已存在：B20261029-xxxxx"`。**与用户输入无关，无法自行规避。**

**同类问题**：`OutboundService.generateOrderNo`（L66-78）`synchronized` 只包住"读最大值"，不包住 `insert + commit`，并发下必撞唯一键；且 `%04d` 在单日超过 9999 单后字符串排序失效，会永久撞号。

**修复**：单号改用号段表 `INSERT ... ON DUPLICATE KEY UPDATE next_val = LAST_INSERT_ID(next_val+1)`，序号宽度提到 6 位；批次号用单号派生。

---

## 二、P1 严重问题（两周内）

| # | 问题 | 位置 | 后果 |
|---|---|---|---|
| P1-1 | **盘点用实盘绝对数覆盖库存** | `StocktakeService.java:122-134` | 盘点期间发生的出入库被抹掉 → 幽灵库存。且零差异分支也走 `applyDifferences`，一样覆盖 |
| P1-2 | **电子标签按键按库位批量确认，不绑单据** | `OutboundService.java:573-591` | 一张单按键把该库位**所有** PICKING 明细置 `buttonPressed=true`；而扫码已降级为可选 → 未到现场也能完成出库 |
| P1-3 | **上架确认无行锁、无幂等** | `PutawayService.java:43-87` | 并发/重试→双倍入库、`putQty` 丢失更新、超计划收货（10 计划收进 15） |
| P1-4 | **台账刷新/结束按钮全员 403** | `SparePartUsages.vue:17,46,100` | 后端要求 `outbound:manage`，但该权限**无任何内置角色持有**；前端也没做权限判断 → 除 admin 外点了必失败 |
| P1-5 | **CORS 硬编码 `*`，配置白名单是死配置** | `SecurityConfig.java:104-116` | `corsProps` 参数接收了但没使用，yml 里精心配的白名单完全不生效 |
| P1-6 | **`assignLocation` 返回 `deviceNo=null`** | `OutboundService.java:240` | 前端 `{...it, ...updated}` 覆盖 → 已绑灯库位被显示成"未绑定" |
| P1-7 | **确认框未 catch** | `Outbound.vue:513`、`OutboundDetail.vue:251`、`Inventory.vue:446`、`Stocktake.vue:79`、`Roles.vue:11` | 点"取消"即产生未处理 promise rejection |
| P1-8 | **表单弹窗 watch 里 await 无 catch** | `ProductFormDialog.vue:92`、`LocationFormDialog.vue:129-151` | 字典加载失败时表单不回填 → **编辑态显示上一个对象的数据，保存即写错** |

> P1-4 说明：`DataInitializer` 只给 ADMIN 授 `outbound:manage`，而 `/spare-part-usages` 只需 `page:usage`，三个内置角色都有 → 这是"页面能用但按钮全废"的典型。

---

## 三、P2 设计缺陷

**可靠性**
- **事务内同步调用外部系统**：`OutboundService.java:271,335,533,763` 在持有库存行锁的情况下逐项调 ESL HTTP（超时 3s/5s）。墨屏服务器不可达时，一个 10 项单据会持有行锁 ~80s，10 个并发即打满 Hikari（`maximum-pool-size:10`）→ 全站 `Connection is not available`。改为 `AFTER_COMMIT` 事件或异步任务。
- **两个定时任务无集群锁**：`SparePartUsageScheduler`、`DeviceStatDailyScheduler` 都是裸 `@Scheduled`，多实例部署会重复拉取。需 ShedLock 或 Quartz。
- **"加锁再查"拿到的是一级缓存旧值**：`OutboundService.java:305-321` 先普通读再 `findWithGraphForUpdate`，实体已在持久化上下文时不刷新 → 正确性靠 `@Version` 兜底，正常并发变成 500 `OptimisticLockingFailureException`（用户会以为是系统故障而重试）。

**数据层**
- `wms_location.device_no` **无索引无唯一键**（V1:176），每次按键/亮灯回调全表扫；一个 MAC 可绑多个库位 → `findByDeviceNo` 返回多条抛 `IncorrectResultSizeDataAccessException`。
- `wms_es_tag` 用 `utf8mb4_unicode_ci`，其余表 `utf8mb4_0900_ai_ci` → 实测 JOIN 报 `ERROR 1267`，导致标签↔库位的外键/唯一约束至今加不上。
- `wms_pending_business_action` **除主键外零索引**，且 `existsBy...` 是 check-then-act → 并发双击可建两条待审批。
- V2 硬编码 Hibernate 生成索引名 `UKiv7vqmfga7amaagyfd5aqpenx`，历史库接入时必炸 → **应用启动失败**。
- 新表时间列用 `DATETIME`（秒级）而实体是 `Instant`（微秒），`validate` 放过但**写入静默截断**，`button_pressed_at` 等丢毫秒。

**权限模型**
- `page:*` 10 个页面权限后端**一处都没校验**，只在前端隐藏菜单：直接 `curl /api/outbound` 可拿全部单据（含手机号）。
- `ApprovalTaskController` 三个接口无注解；`ApprovalService.handle` 在 `approverType==null`（流程被停用）时跳过全部分配校验 → 任何登录用户可审批任意申请。
- 前端权限是登录时快照，后端是实时读库 → 管理员改权限后用户不刷新页面会出现"菜单有按钮无权限"。

**前端**
- `capacitorHttpAdapter.js` 是**完全未接入的死代码**（依赖未装、adapter 未注册），且若接入会丢失所有 `params`。建议直接删除。
- 桌面侧边栏 `:default-openeds` 只在 setup 读一次，从工作台快捷入口进二级页时菜单不展开。
- `auth.js:9` 的 `JSON.parse` 无保护 → localStorage 损坏直接整站白屏。
- 原生扫码期间组件卸载会残留 `body.native-scanning` → 全白；扫码会话可并发。
- 移动端 3 个宫格入口未按权限过滤 → 点了被静默弹回工作台。

---

## 四、P3 功能缺漏

| 缺失 | 说明 | 建议优先级 |
|---|---|---|
| **预警无持久化与通知** | `AlertService` 是即时查询，无告警表、无历史轨迹、无已读/处理状态、无推送。刷新即消失，没人盯就等于没有 | 高 — 这是"预警管理"页面的核心价值 |
| **无 Excel 导入导出** | 全项目无 `MultipartFile`/POI/EasyExcel。备件主数据批量维护、月度盘点表、台账导出全部只能手工 | 高 — 仓库系统必备 |
| **无采购/补货建议** | `Product.procurementLeadDays` 有字段但全项目无消费方，`minStock/safetyStock` 只用于告警文案 | 中 — 字段已建，实现补货建议即可闭环 |
| **删除主数据抛 500** | `ProductService.delete` 只校验 `putawayRepo`，其余靠 FK RESTRICT 兜底 → 弹原生 SQL 报错而非"该备件已被引用于 …" | 中 — 体验问题但影响观感 |
| **无备件更换履历** | 台账结束只记原因，未记录"换下来的旧件去哪了/新件序列号"，无法追溯单机历史配件谱系 | 中 |
| **无系统参数/备份** | 无在线参数配置、无数据库备份入口，阈值类配置全在 yml | 低 |
| **无健康检查端点** | 仅 Spring Boot 默认 actuator（若开启），没有针对外部数据源（WCS/WMS/ESL）的连通性自检页面 | 中 — 外部集成一旦故障只能看日志 |
| **roles/group 部门维度** | `User` 无部门字段，无法做"只能处理本部门单据"的数据隔离 | 中 |

---

## 五、建议修复顺序

```
第 1 周（止血）✅ 已完成 2026-10-29
  ├─ V12 迁移：stocktake_order.status 扩展 ENUM 值域补 PENDING_REVIEW（不能改 VARCHAR）
  ├─ OutboundController 四个拣货接口加 @PreAuthorize + completePicking 归属校验
  ├─ DataInitializer 去掉硬编码弱口令 → 随机/环境变量强口令 + 存量弱口令自检告警
  ├─ 登录失败限流（5 次/15 分钟锁定）
  ├─ 前端 401 走 authStore.logout() + 兜底跳 /403（P0-4/P0-5 一起改）
  ├─ Login.vue 删除账号提示与诊断面板
  └─ Outbound.vue 的 .trim() 崩溃（7 处改可选链兜底）

第 2~3 周（正确性与并发）✅ 已完成 2026-10-29
  ├─ 盘点按"差异量"增量调整，零差异不再碰库存（P1-1、P0-1 的分支）
  ├─ 按键回调只确认"最近一次点灯"的那一条明细（P1-2）
  ├─ PutawayService 上架单行锁 + 库存行锁 + requestId 幂等键 + 超收拦截（P1-3）
  ├─ 单号/批次号改号段表 wms_number_sequence 原子发号，宽度 6 位（P0-6、P2）
  ├─ ESL 亮灯/灭灯移到 AFTER_COMMIT 事件，不再持锁调 HTTP（P2）
  ├─ 主库 JdbcTemplate 显式 @Primary，外部数据源必须 @Qualifier（消除注入歧义）
  └─ 顺手：assignLocation deviceNo、报废 afterQty、InterfaceLog 截断、5 处 confirm 未 catch、2 处表单 watch 回填顺序

第 4 周（健壮性）— 已完成
  ├─ 补 device_no / status / pending_action 索引与唯一约束（V12）
  ├─ es_tag 排序规则统一（V12）
  ├─ V2 索引删除条件化（评估后不改：V1 基线就带着同一个索引名，改 V2 会让已上线库 checksum 校验失败）
  ├─ 定时任务加集群锁（V14 + SchedulerLockService，ShedLock 拉不到改用等价实现）
  ├─ 读接口补 page:* 校验
  └─ ApprovalTaskController 补注解 + 无节点审批的越权兜底

后续规划
  ├─ 预警持久化 + 通知
  └─ Excel 导入导出 + 补货建议
```

---

## 六、顺手可以做的小修复（每条 < 5 分钟）

1. `OutboundService.java:240` — `null` → `stock.getLocation().getDeviceNo()`
2. `OutboundService.java:131-136` — 报废接口返回的 `afterQty` 恒等于 `beforeQty`，应为 `beforeQty - req.qty()`
3. `InterfaceLogService.java:110-113` — `truncate` 后缀未计入长度，500+6 超 `varchar(500)` → 超长异常时**整条日志丢失**
4. `Login.vue:112-114` — 未消费守卫写入的 `redirect`，深链登录后丢失目标页
5. `Inventory.vue:420-426` — 400ms 防抖定时器未在 `onBeforeUnmount` 清理
6. 5 处 `ElMessageBox.confirm` 包 `try/catch`
7. `capacitorHttpAdapter.js` 直接删除（含注释说明）
8. `UserService.resetPassword` 是无人调用的死代码，删除以免将来误暴露

---

## 七、第二周（正确性与并发）修复记录

> 全部改动均在本机 MySQL 8.0 真库上验证过（Flyway V13 + `ddl-auto=validate` 启动通过），
> 验证完把数据库还原到改动前的备份，未留下测试数据。

### 7.1 盘点不再覆盖库存（P1-1）

`StocktakeService.applyDifferences` 原来是 `inventory.setQty(item.getActualQty())` —— 用实盘绝对数覆盖。
账面数 `bookQty` 是**建单时**的快照，从建单到复核通过之间发生的出入库会被一笔抹掉，
盘点完就凭空多出/少掉一批货。

改为按差异量增量调整：`newQty = 当前库存 + (实盘 - 账面快照)`。零差异（delta=0）直接跳过，
零差异分支不再触碰库存。

验证（真库）：
- 建盘点单，库存 27 被快照为 `bookQty=27`
- 盘点期间模拟出库 2 件，库存变为 25
- 录入实盘 26（差异 -1），提交复核 → 管理员审批通过
- 结果：库存 **24** = 25 + (-1)，那 2 件出库被正确保留（旧逻辑会变成 26）
- 流水：`ADJUST before=25 after=24 change=1`，备注「盘点差异调整，账面快照：27，实盘：26，差异：-1」

### 7.2 电子标签按键只确认一条（P1-2）

按键回调报文只有 `{mac, result}`，没有 cmdtoken，所以原实现按"库位"批量把该库位上
**所有** PICKING 明细置为已按键。一张单按一次，别的单也一起被"确认"，
而扫码早已降级为可选 → 不扫码、不去现场也能出库。

改动：
- `wms_outbound_item` 新增 `lighted_at`（最近一次点灯时间），`startPicking` 点灯时写入
- 新增查询 `findPickingByLocationOrderByLightedDesc`（`lighted_at desc, id desc`，MySQL 的 NULL 在 DESC 里排最后，历史数据不抢占）
- `markButtonPressed` 只标记排序第一条未按键的明细，并写一条 `BUTTON_PRESSED` 审计

验证（真库）：两张单同时拣货都落在库位 `B-01-01`（MAC=3），POST 一次按键回调
→ `matchedItems=1`，只有**最近点灯**的 item 19 被置为已按键，item 18 保持未按键。

### 7.3 上架确认加锁 + 幂等（P1-3）

- `PutawayOrderRepository.findDetailForUpdate`：`PESSIMISTIC_WRITE` 锁住上架单再算 `putQty + qty`
- 库存行同样改成 `findWithGraphForUpdate`（原来是普通读 → read-modify-write 丢失更新）
- `wms_putaway_item.request_id` 唯一键 + 业务层先查后写：同一幂等键重复提交直接返回上次结果
- 前端 `Putaway.vue` 每次提交生成 `PW-xxxx` 键，**失败重试沿用同一个键**，成功后作废

验证（真库，计划 10 件）：
- 同键提交 4 件两次 → 第二次 `idempotent=true`，`putQty` 仍为 4（未重复入库）
- 提交 7 件 → `{"msg":"超过计划数量，剩余6件"}`
- 并发两笔各 6 件 → 一笔成功 `putQty=10 done=true`，另一笔 `该上架单已完成`；明细合计 10、库存 10，没有超收

### 7.4 单号/批次号改号段表（P0-6 / P2）

原实现"查当天最大值 + 1"：`synchronized` 只包住查询，没包住 insert+commit，并发必撞唯一键；
`%04d` 单日超 9999 单后字符串排序失效，会永久撞号；批次号用 `currentTimeMillis % 100000`，**每 100 秒循环一次**。

改动：
- 新表 `wms_number_sequence(seq_key, next_val, updated_at)`
- `NumberSequenceService` 用 `INSERT ... ON DUPLICATE KEY UPDATE next_val = LAST_INSERT_ID(next_val + 1)` 原子发号；
  取号与读回放在**同一个 JDBC 连接**上（`LAST_INSERT_ID` 是会话级的，跨连接读会读到别人的值）
- 序号宽度 6 位，格式 `OUT20261029-000001` / `PA20261029-000001` / `B20261029-000001` / `ST20261029-000001`
- V13 里把当天已有单号的最大序号回填进号段表（`REGEXP_SUBSTR(order_no,'[0-9]+$')`，兼容老 4 位格式）

验证（真库）：并发建 8 张领用单 → 8 个连续且互不重复的单号，0 失败；
重启后继续从最大序号 +1 往下发。

### 7.5 ESL 调用移出事务（P2）

原来 `startPicking` / `completePicking` / `releaseReservation` 在**持有库存行锁**的时候
同步调墨水屏 HTTP（超时 3s/5s）。墨屏服务器一慢，一个 10 项的单据能锁库存行近一分钟，
连接池只有 10 个连接，几个并发就把全站拖到 `Connection is not available`。

改为 `DeviceSignalPublisher` 发事件 + `DeviceSignalListener` 用 `@TransactionalEventListener(AFTER_COMMIT)` 执行：
- 锁已释放，HTTP 再慢也不占数据库
- 事务回滚就不再点灯，不会留下"单没建成灯却亮着"的残留
- 监听器吞掉全部异常：事务已提交，设备调不通不能返回失败给用户
- `deviceNo` 直接取 `Location.deviceNo`，不再依赖 HTTP 调用的返回值

### 7.6 主库 JdbcTemplate 显式化

`device.datasource.enabled=true` 时会同时存在 `deviceJdbcTemplate` 和自动配置的 `JdbcTemplate`，
业务代码按类型注入的结果取决于 Spring 的兜底匹配，很容易把 SQL 打到外部只读库上。
新增 `PrimaryJdbcTemplateConfig` 提供 `@Primary` 的 `primaryJdbcTemplate`，
`DeviceRuntimeViewService` 改为显式 `@Qualifier("deviceJdbcTemplate")`。

### 7.7 顺手修掉的小问题

| 位置 | 问题 | 处理 |
|---|---|---|
| `OutboundService.assignLocation` | 返回 `deviceNo=null`，前端 `{...it,...updated}` 覆盖后已绑灯库位显示"未绑定" | 取 `stock.getLocation().getDeviceNo()` |
| `InventoryService.adjust` | 报废返回的 `afterQty` 恒等于 `beforeQty` | 自动放行取最新库存，走审批取 `beforeQty - qty` |
| `InterfaceLogService.truncate` | 后缀没算进上限，500+6 超 `varchar(500)`，异常报文写日志时反而整条丢 | 后缀长度计入上限 |
| `Outbound.vue` / `OutboundDetail.vue` / `Inventory.vue` / `Stocktake.vue` / `Roles.vue` | `ElMessageBox.confirm` 未 catch，点"取消"产生未处理 rejection | 全部包 try/catch，取消即 return |
| `LocationFormDialog.vue` | 字典加载在回填**之前**，失败就跳过回填 → 弹窗留着上一个库位的数据，保存写错库位 | 先回填再加载字典，字典失败只降级下拉 |
| `ProductFormDialog.vue` | 同上，watch 里 await 无 catch | 加 try/catch + 提示 |
| `Putaway.vue.submitConfirm` | 接口异常直接冒泡成未处理 rejection | 加 catch 并保留幂等键 |

### 7.8 还剩什么

- **P1-4 已在第一周解决**：后端 `hasAnyAuthority('outbound:manage','outbound:picking')`，WAREHOUSE 角色持 `outbound:picking`，前后端一致
- **第四周**：`status`/`pending_action` 索引已补（V12），剩余 ShedLock、读接口 `page:*` 校验、`ApprovalTaskController` 补注解
- **待确认**：`sparePartUsageService.createFromOutbound` 在出库事务内读设备运行数据（外部 JDBC），
  默认 `DEVICE_DB_ENABLED=false` 时不会真连外部库；若开启，建议后续一并挪到提交后执行

---

## 八、第四周（健壮性）修复记录

> 同样在真库（MySQL 8.0 + Flyway V14 + `ddl-auto=validate`）验证，验证完把临时账号/角色/审批记录删干净，
> 数据库回到 2 个用户、4 个内置角色。

### 8.1 定时任务加集群锁（P2）

`SparePartUsageScheduler`、`DeviceStatDailyScheduler` 都是裸 `@Scheduled`。单机没问题，
但按虚拟化方案一旦出现两实例并存（滚动重启、主备切换、误扩容），同一天会被处理两遍：
设备运行数据是 upsert 尚可，**备件使用台账的 `refreshAll` 会重复写快照、重复推进寿命计数**。

原计划用 ShedLock，但本机与内网 Maven 镜像对 `net.javacr` 整组返回 404（`net.bytebuddy`、`flyway` 等均正常），
拉不到依赖。改为自建等价实现，零新依赖：

- `V14__scheduler_lock.sql` 建 `wms_scheduler_lock(task_name PK, locked_until, locked_by, updated_at)`
- `SchedulerLockService`：抢锁 = `INSERT`（主键冲突即被占）→ 冲突时
  `UPDATE ... WHERE locked_until < 当前时间`（只有租约过期才能接手）→ 执行完 `DELETE` 自己的锁行
- 两个任务分别用 `runIfLocked` / `callIfLocked` 包起来，租约 4 小时 / 2 小时（只兜底进程崩溃，正常结束即释放）

真库验证（把 cron 临时改成 20 秒一次，三种路径都跑了）：
- 正常路径：每轮 INSERT 成功，跑完 DELETE，**表里不留行、日志零输出**
- 别的实例持锁：手工插一条 `locked_until = now()+10min`、`locked_by=other-instance-9999` 的记录，
  下一轮日志输出「正由其他实例执行，本次跳过」，该行原封不动（说明没有硬抢）
- 过期租约：把上面的 `locked_until` 改到过去，下一轮输出 WARN「上一个租约已过期，由 xxx 接手」，
  执行完锁行被删除 —— 对应"上个进程被 kill 掉"的场景

### 8.2 读接口补 page:* 校验（P1）

`page:*` 十个页面权限此前后端**一处都没校验**，只在前端隐藏菜单，`curl` 直接就能拿全量单据。
按前端路由实际调用关系逐个补注解，取"最小可用集合"，避免误伤正常页面：

| 接口 | 权限 | 依据 |
|---|---|---|
| `GET /api/dashboard/summary` | `page:workbench` | 只有工作台调用 |
| `GET /api/alerts` | `page:alerts` | 只有预警页调用（工作台的预警数是 dashboard 自己算的） |
| `GET /api/inventory/{summary,details}` | `page:inventory` / `page:outbound` / `page:workbench` | 库存页、领用单选货、工作台统计三处 |
| `GET /api/inventory-logs` | 同上 | 库存日志页、领用单详情追溯、工作台最近流水 |
| `GET /api/stocktakes[/{id}]` | `page:stocktake` / `page:approval` | 盘点页 + 审批人复核差异 |
| `GET /api/orders[/pending,/{id}]` | `page:inbound` / `inbound:putaway` / `inbound:create` | 入库页 + 上架作业页 |
| `GET /api/spare-part-usages[/{id},/{id}/snapshots]` | `page:usage` | 台账页 |
| `GET /api/es-tags[/{id}]` | `page:master` | 电子标签页 |
| `GET /api/approval-tasks` | `page:approval` | 审批中心 |
| `POST /api/approval-tasks/{type}/{id}/{approve,reject}` | `outbound:approve` | 业务审批按钮 |

**特意没动的**：`/api/products`、`/api/locations`、`/api/equipments` 的读接口保持登录即可访问 ——
它们是货品/库位/设备这些基础参照数据，被入库、出库、移库、盘点、台账几乎所有页面拉取，
按页面权限收紧只会造成"某页面打不开下拉框"，而数据本身不含个人信息。

真库验证（建了 4 个临时账号逐页跑）：
- 只读用户 VIEWER：`dashboard / inventory / inventory-logs / spare-part-usages / alerts` 全部 200；
  `stocktakes / orders / es-tags / approval-tasks` 全部 `{"code":1,"msg":"无权限访问"}`
- 仓库业务员 WAREHOUSE：入库单、待上架、领用单、库存、盘点、台账、预警、工作台全部 200；审批中心 403
- 审批人 APPROVER：审批中心、盘点、库存、台账、预警、工作台 200；入库单 403（该角色本就无入库页）

> 注：本项目统一用 `Result{code,msg}` 包装，方法级鉴权被拒绝时是 **HTTP 200 + `code=1`**（`GlobalExceptionHandler`），
> 只有过滤器链路（未登录、`/api/users/**`）才返回真实 401/403。这是既有约定，本轮没有改动状态码语义。

### 8.3 审批接口补注解 + 无节点越权（P1）

`ApprovalTaskController` 三个接口原先**没有任何注解**，登录即可列出并审批任意待办。
更隐蔽的一条：`ApprovalService.handle` 写的是 `if (a.getApproverType() != null) { 校验是否分配给你 }` ——
流程未配置或被停用时，`submit` 会建一条 `approver_type = NULL` 的兜底记录，
这种记录**谁都能审批**。

改动：
- 控制器三个接口补 `page:approval` / `outbound:approve`
- `ApprovalService` 注入 `RoleRepository`，新增 `permissionsOf(user)`；
  `approverType == null` 时必须持有 `outbound:approve` 才允许处理，否则 `BizException`
- 同步收紧 `canHandle`：无指定审批人的记录不再推送给所有登录用户（原来 `return true`），
  否则审批中心会对每个人显示同一批"待办"

真库验证（造了 `approver_type = NULL` 和 `approver_type = ROLE/APPROVER` 两条待办）：
- 新角色 `TMPAPPROVER`（只有 `page:approval`，无 `outbound:approve`）：
  待办列表为空（修复前会把 NULL 节点那条推给他）；调 approve → `无权限访问`
- `admin`（持 `outbound:approve`）：能看到并处理 NULL 节点那条
- VIEWER 调 approve → `无权限访问`

### 8.4 V2 索引删除为什么没改

报告里提到 `V2` 硬编码了 Hibernate 生成的索引名 `UKiv7vqmfga7amaagyfd5aqpenx`，
"历史库接入时必炸"。复核后确认：`V1__baseline_schema.sql` 里建的就是这个名字（第 490 行），
也就是说**任何按 V1 建的库都带着它**，V2 必然成功。

反而是改成条件化 SQL 会让 checksum 变化 → 已经跑过 V2 的库（包括本地和未来的生产库）
启动时 `Validate failed`。用一次真实的启动失败换一个假设性问题不划算，保持原样并在报告里留档。

真正会遇到这个问题的场景是"把 SP 接到一个由其它 Hibernate 版本 ddl-auto 建出来的老库"，
这种情况本来就不该直接跑迁移，应该先 `flyway baseline` 再做结构比对。

### 8.5 还剩什么

- 出库事务内读设备运行数据（`sparePartUsageService.createFromOutbound`）：默认 `DEVICE_DB_ENABLED=false`，
  开启外部库后建议同样挪到提交后
- `GlobalExceptionHandler` 把所有异常（含 500）都包成 HTTP 200，监控层面看不出服务端错误，建议后续给 5xx 单独留状态码
- 前端权限是登录时快照、后端实时读库：管理员改权限后需要用户刷新页面才一致
