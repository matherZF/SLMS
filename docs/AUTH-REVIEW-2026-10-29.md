# 用户 / 角色 / 权限 专项审查

> 审查日期：2026-10-29　｜　范围：登录认证、JWT 过滤器、方法级授权、用户/角色管理面、前端权限一致性
> 方式：逐文件读源码 + 真库结构核对（MySQL 8.0，`spare_parts`）
> 结论：**整体模型是对的（DB 驱动 RBAC、权限每请求实时加载、写接口基本都有注解），
> 但管理面缺护栏、回调面缺鉴权、还有一处列宽不一致会直接报 500。**

---

## 0. 摘要

| 级别 | 问题 | 影响 |
|---|---|---|
| P0-1 | 没有"最后一个管理员"护栏 | 停用内置 ADMIN 角色 / 把自己改成 VIEWER / 删掉其他管理员，任一条都让全员进不去系统，只能改库恢复 |
| P0-2 | 电子标签按键回调零鉴权 | 任何能连上端口的人 POST 一个 mac 就能把明细标记"已按键"，而按键是完成出库的唯一物理校验 |
| P0-3 | `wms_user.role` varchar(16) 与角色码最长 32 冲突 | 自定义角色编码超过 16 字符能建出来，但分配用户时 500（已复现 `ERROR 1406`） |
| P1-1 | `UserUpdateReq` 无任何校验 | 管理员改用户时密码可设 1 位，绕过创建时的 ≥6；姓名超 64 直接 500 |
| P1-2 | `system:role` 可自我提权 | 改自己所属角色的权限集即可拿到 `system:user`，再造 ADMIN 账号 |
| P1-3 | 登录/改密/锁定完全无审计 | 只有"管理员重置密码"有记录；等保要求的登录日志缺失 |
| P1-4 | `/api/interface/inbound` 匿名可无限写入 | 接口日志表可被外部灌满 |
| P1-5 | 5 个点灯接口 + 设备运行视图无权限注解 | 只读用户可点灯/熄灯、可看设备产量数据 |
| P1-6 | 登录锁定可用来锁死别人 | 5 次错密码锁 15 分钟，无解锁入口；计数溢出会整体清空 |
| P2 | CORS 配置被硬编码覆盖、关闭 frameOptions、JWT 不可吊销、默认放行模型、移动端导航不过滤、死代码 | 见第 5、6 节 |

---

## 1. 认证链路核对

### 1.1 登录（`AuthController.login`）

顺序正确：**限流检查 → 查用户 → 恒定时间 BCrypt（不存在也跑 DUMMY_HASH）→ 密码校验 → 启用校验 → 角色启用校验 → 发 token**。

做对的地方：
- 用户不存在与密码错误返回同一句"用户名或密码错误"，时序上也做了抹平（`DUMMY_HASH`）
- 账号禁用、角色停用都拦在发 token 之前
- 角色停用检查放在登录时 + 过滤器里，两侧都覆盖

问题：
- **`LoginRes` 里带了完整权限集合**，这是前端菜单/按钮的依据，本身没问题；但权限是"登录时快照"，
  管理员改权限后必须等用户刷新页面（`App.vue` 挂载时 `fetchMe()`）才纠正。
- 登录成功/失败/被锁定 **没有任何审计落库**。`wms_operation_audit_log` 里查不到谁在什么时候登录过、
  谁被锁过。等保 2.0 的"登录日志留存 6 个月"这一条目前不满足。
- 自助改密（`PUT /api/auth/password`）同样无审计，只有管理员重置（`UserService.resetPassword`）有。
- 密码策略只有"长度 ≥ 6"，无复杂度要求，无首次登录强制改密。库里 `admin` 仍是初始弱口令（启动日志会持续告警）。

### 1.2 Token 与过滤器（`JwtUtil` / `JwtAuthFilter`）

- 密钥强制 ≥32 字节，缺失直接启动失败 —— 好。
- **权限每请求从库里读**（`user.role` → `wms_role` → `wms_role_permission`），改权限立即生效，不依赖 token 里的 claim。
- 用户被禁用、角色被停用，下一次请求立即失效 —— 好。
- **token 无法吊销**：改密码后旧 token 仍有效到 24 小时过期；没有 `tokenVersion` / `pwdChangedAt` 声明。
  被盗 token 的唯一止损手段是禁用账号（这个确实有效）。
- `JwtUtil.getUserIdFromToken()` / `getRoleFromToken()` 无调用点，是死代码。

### 1.3 登录限流（`LoginThrottleService`）

- 按 **用户名** 计数：5 次失败锁 15 分钟，成功后清零。
- 问题一：**可以拿来锁死别人**。知道用户名就能在 15 分钟内用 5 次错误请求把管理员锁住，反复刷可以长期锁。
  系统没有解锁入口（只能等，或重启服务清内存）。
- 问题二：`MAX_ENTRIES = 10000` 满了直接 `attempts.clear()` —— 攻击者刷 10001 个不同用户名即可
  **把所有账号的锁定记录一并清空**，锁定机制失效。
- 问题三：进程内计数，重启即清零、多实例不同步（注释已说明，属已知取舍）。

---

## 2. 授权模型核对

### 2.1 模型本身

`User.role`（单个字符串）→ `Role`（code/name/builtin/enabled）→ `Role.permissions`（`wms_role_permission`）。
权限码 `page:*`（页面）+ `xxx:*`（按钮），`PermissionCatalog` 维护全集与父子关系，
`validate()` 强制"按钮权限必须隶属已授权页面"。26 个权限码，4 个内置角色。

做对的地方：
- 权限码集中定义 + 保存时校验，不会写出拼错的码
- 内置角色的**编码**不可改（`RoleService.update` 校验）
- 内置角色不可删除、有用户在用的角色不可删除
- 管理动作（用户增删改、角色增改）都写了 `OperationAuditLog`

### 2.2 授权判定

- 过滤器装配 `ROLE_<code>` + 全部权限码为 authority，控制器用 `hasAuthority('xxx')` 判断。
- `@EnableMethodSecurity` 已开，`@PreAuthorize` 生效（真库验证过：VIEWER 调审批返回 `无权限访问`）。
- 25 个控制器中**只有 `UserController` 一个管理类控制器零注解**，靠 `SecurityConfig` 的
  `/api/users/**` 路径规则挡（`hasAuthority('system:user')`，目前生效）。
  一旦路径调整或新增 User 相关控制器，保护会静默失效，属纵深防御缺口。

### 2.3 权限模型的两个结构性判断

- **一人一角色**。`wms_user.role` 是单值列，没有 `wms_user_role` 关联表；`entity/UserRole.java`
  是早期硬编码角色的残留枚举，无人引用（死代码）。真出现"仓库员兼审批人"，现在只能新建一个合并角色。
- **`system:role` 的权力上限等于超管**。因为可以改自己所属角色的权限集，加上 `system:user` 就完成提权。
  如果按等保"三员分立"把角色管理单独授出去，这个授权等于给超管。

---

## 3. 接口放行面

`SecurityConfig.authorizeHttpRequests` 的规则：

| 路径 | 规则 | 判断 |
|---|---|---|
| `/api/auth/**` | permitAll | 合理（`/me`、`/password` 内部自校验） |
| `/api/interface/**` | permitAll | **过宽**：匿名可无限写接口日志 |
| `/api/esl/callback/**` | permitAll | 必须（设备回调），但**零校验**，见 P0-2 |
| `/h2-console/**` | permitAll | 生产（MySQL）下 console 未注册所以打不开，但 h2 是 runtime 依赖，误开开关就是零认证 DB 入口 |
| `/api/users/**` | `system:user` | 正确 |
| 其余 | `authenticated()` | **默认放行给所有登录用户**，新增管理接口漏注解即对只读用户开放 |

### P0-2：按键回调是出库的唯一物理关卡，但谁都能敲

- `OutboundService.completePicking` 第 522 行：`if (!item.isButtonPressed()) throw ...`，且扫码已降级为可选。
- `EslController.buttonPressCallback`（第 235 行）收 `{mac}` → `locationRepo.findByDeviceNo` → 标记"最近点灯且未按键"的那条明细。
- 该路径在 `SecurityConfig` 第 65 行 permitAll，**报文里没有任何密钥、签名、来源校验**。

结果：同网段任何人都能用 `curl -X POST /api/esl/callback/button-press -d '{"mac":"3"}'` 绕过"到现场按按键"，
再调 `/{id}/complete` 完成出库。`light-result` 回调同理可伪造亮灯确认。

### P1-5：无注解但非只读的接口

| 接口 | 现状 | 建议 |
|---|---|---|
| `POST /api/esl/light`、`/light/items`、`/light/moving`、`/light/{mac}`、`/light/{mac}/off` | 登录即可 | `page:master` 或 `outbound:picking` |
| `GET /api/equipment-runtime` | 登录即可（需 `device.datasource.enabled=true` 才注册） | `page:usage` / `page:master` |
| `GET /api/products`、`/api/locations` | 登录即可 | 保持（全站下拉框参照数据，不含个人信息） |

---

## 4. 管理面护栏

### P0-1：没有"最后一个管理员"约束

当前库里只有 1 个 ADMIN 用户（`admin`），三条路径都能把系统锁死：

1. `UserService.update` 允许把**自己**的 `role` 改成 VIEWER，或 `enabled=false`（前端编辑弹窗也允许）；
2. `UserService.delete` 只挡"不能删当前登录用户"，但可以删掉其他管理员 —— 删到最后一个再退出即无管理员；
3. `RoleService.update` 允许把内置 `ADMIN` 角色 `enabled=false`，或把它的权限清空
   （前端"配置角色"弹窗对内置角色只锁了编码，**启用开关和权限树都能改**）。

第 3 条最危险：管理员在界面上把 ADMIN 的启用开关一关，所有人下一次请求就变匿名，
连登录都进不来（`AuthController` 明确拒绝"用户角色已停用"），只能改库。

### P0-3：列宽不一致（已复现）

```
wms_role.code   varchar(32)   ← RoleService.normalize 允许 2~32 位
wms_user.role   varchar(16)   ← V1__baseline_schema.sql，User.role 也是 length=16
```

真库验证：
```sql
insert into wms_user(...,role,...) values(...,'ABCDEFGHIJKLMNOPQRST',...);
-- ERROR 1406 (22001): Data too long for column 'role' at row 1
```
即：管理员建一个 `MAINTENANCE_SUPERVISOR`（21 字符）这样的角色能成功，
但给任何用户分配时接口 500。修法：V15 把 `wms_user.role` 扩到 32 并同步实体的 `length`。

### P1-1：`UserUpdateReq` 完全没有校验

```java
public record UserUpdateReq(String displayName, String role, Boolean enabled, String password) {}
```
- `password` 无长度约束 → 管理员（或任何拿到 `system:user` 的人）可以把密码设成 1 个字符，
  绕过 `UserCreateReq` 的 `@Size(min = 6)`；
- `displayName` 无长度/trim → 超过 64 字符触发 1406；
- 前端的 `min: 6` 只是前端校验，接口直连不受约束。

### P1-2：`system:role` → 超管

`RoleService.update` 不校验"授予的权限是否超出操作者自身权限"。持 `system:role` 的人：
把自己的角色加上 `system:user` → 保存 → 立即生效（过滤器每请求读库）→ 新建 ADMIN 用户 → 完成提权。

### P1-3：管理动作审计的缺口

| 动作 | 是否审计 |
|---|---|
| 用户新增/修改/删除/管理员重置密码 | 有 |
| 角色新增/修改 | 有 |
| **角色删除** | **无**（`RoleService.delete` 未写 audit） |
| **登录成功/失败/锁定** | **无** |
| **自助改密 / 改姓名** | **无** |

### 其它管理面小问题

- `UserService.delete` 不检查该用户是否有未完成的审批节点，删除后 `approver_value` 悬空
  （admin 仍可代审，所以不致命，但待办会指向不存在的人）。
- `RoleService.apply` 里 `permissions` 传 null 会被当成空集合 → **PUT 时漏传字段等于清空该角色全部权限**。
- `UserCreateReq.role` 不做大小写归一 —— 前端传小写 `admin` 会得到"角色无效"。
- 用户名唯一性靠先查后插，并发下会撞唯一键抛 500（无友好提示）。
- `UserService.resetPassword` 没有任何调用点（未暴露接口），死代码。

---

## 5. 前端一致性

- 路由 `meta.permission` + 守卫 + `/403` 落点已形成闭环，`firstAccessiblePath` 按菜单顺序找第一个可访问页，
  不会死循环。逐个核对 20 条路由的权限声明与后端 `page:*` 一致，**未发现错配**。
- 桌面菜单（`DesktopLayout`）逐项用 `authStore.can()` 过滤，正确。
- **移动端底部导航（`AppLayout`）只在"是否仓库作业/是否审批"两档上分支，兜底分支固定显示
  库存 / 台账 / 预警**，不看 `page:*`。自定义角色（比如只有 `page:approval`）在手机上点"库存"会落到 /403。
- token 与用户信息存 `localStorage`，页面刷新时 `App.vue` 会 `fetchMe()` 纠正权限快照 —— 刷新即可，
  但"管理员改权限 → 用户不刷新"的窗口里，前端按钮的显示与后端的实际判定会不一致（后端不会漏，只是按钮白点）。
- `api/users.js` 注释写"仅 ADMIN 角色可访问"，实际是 `system:user` 权限，措辞过时。

---

## 6. 配置层

### 6.1 CORS 配置项被硬编码覆盖

`application.yml` 写了 `app.cors.allowed-origin-patterns`（localhost / 10.x / 192.168.x / capacitor / file），
但 `SecurityConfig.corsConfigurationSource()` **完全没用这个参数**，直接：

```java
cfg.setAllowedOriginPatterns(List.of("*"));
cfg.setAllowedHeaders(List.of("*"));
cfg.setAllowedMethods(List.of("*"));
cfg.setAllowCredentials(true);
```

即任何站点都能带凭证跨域调接口。因为认证走 Bearer 头而不是 Cookie，实际可利用面有限，
但"配置写了却无效"比"配置写错"更难排查，建议按配置生效（或至少去掉 `allowCredentials`）。

### 6.2 点击劫持

`http.headers(h -> h.frameOptions(f -> f.disable()))` 是为 h2-console 加的，生产（MySQL）也生效 ——
页面可以被任意站点 iframe 嵌套。建议改成 `SAMEORIGIN`，h2 profile 里再单独放开。

### 6.3 默认放行模型

`anyRequest().authenticated()` 意味着**新加的接口默认对全部登录用户开放**。P1-5 的两个例子就是这么来的。
更稳的做法是显式白名单 + 其余 `denyAll`，或约定"管理类控制器必须加类级 `@PreAuthorize`"（`WorkflowController`、
`OperationAuditController`、`InterfaceLogController`、`DeviceStatDailyController` 已经是这个写法，可作为模板）。

---

## 7. 建议的修复顺序

**第一批（P0，必须先做，改动都很小）**
1. V15：`wms_user.role` → varchar(32)，`User.role` 的 `length` 同步为 32
2. 加"最后一个管理员"护栏：
   - `UserService.update` 拒绝"把自己改成无 `system:user` 的角色"、拒绝禁用自己
   - 拒绝禁用/删除/降权最后一个启用状态的管理员
   - `RoleService.update` 拒绝对内置 `ADMIN` 做 `enabled=false` 或移除 `system:user` + `system:role`
3. 回调鉴权：`/api/esl/callback/**` 加共享密钥头（`X-ESL-Token`，配置项可留空=不校验以兼容现场），
   失败返回 `received:false` 并写审计

**第二批（P1）**
4. `UserUpdateReq` 补 `@Size`、`displayName` trim+限长
5. 登录/失败/锁定/改密写审计（新建 `AUTH` 业务类型即可，复用现有表）
6. `EslController` 点灯接口 + `DeviceRuntimeViewController` 补注解
7. `LoginThrottleService`：溢出改成按 LRU 淘汰而不是 `clear()`；补一条"账号被锁"的运维提示
8. `/api/interface/inbound` 加 token 或 IP 白名单

**第三批（P2，可选）**
9. CORS 按配置生效、`frameOptions` 改 SAMEORIGIN、h2-console 只在 h2 profile 放行
10. `UserController` 加类级注解；`RoleService.delete` 补审计；`RoleService.apply` 对 null 权限做保护
11. 移动端底部导航按 `can()` 过滤
12. 清理死代码：`UserRole` 枚举、`JwtUtil` 两个未用方法、`UserService.resetPassword`

**需要拍板的策略问题**（改法取决于你的管理要求）
- 是否允许"一人多角色"（要加 `wms_user_role` 表，改动较大）
- `system:role` 是否只给超管，还是允许"安全管理员"独立持有（后者需要加"不能授予自己没有的权限"约束）
- 是否要求密码复杂度 / 首次登录强制改密
- JWT 是否要可吊销（加 tokenVersion，代价是每次改密后需要前端重新登录）

---

## 8. 已核对、没有问题的点

- 密码从不明文返回：`User.password` 有 `@JsonProperty(WRITE_ONLY)`，`UserDTO` 不含密码字段
- BCrypt 存储，DUMMY_HASH 抹平用户名枚举时序差
- 权限每请求实时读库，改权限/停用角色立即生效，无需等 token 过期
- `PermissionCatalog.validate` 强制父子关系，写不出"只有按钮没有页面"的角色
- 内置角色编码不可改、不可删除，有用户在用的角色不可删除
- 用户新增/修改/删除、角色新增/修改都落了操作审计（带操作人）
- 前端路由守卫 + 403 落点无死循环，20 条路由权限声明与后端一致
- 桌面菜单逐项按权限过滤
- 无 actuator 依赖，不存在 `/actuator/shutdown` 之类的运维口子
- `device.datasource.enabled=false` 时外部数据源的控制器与同步服务根本不装配

---

## 9. 三条 P0 的修复记录（2026-10-29）

> 全部在真库（MySQL 8.0 + Flyway V15 + `ddl-auto=validate`）上验证，验证完已把测试用户/角色/审计记录清掉，
> 数据库回到 3 个用户、4 个内置角色。

### 9.1 P0-3 列宽对齐

- 新增 `V15__user_role_column_width.sql`：`wms_user.role` → `varchar(32)`
- `User.role` 的 `@Column(length)` 同步改成 32（`ddl-auto=validate` 下两边必须一致）

验证：建 `MAINTENANCE_SUPERVISOR`（21 字符）角色成功，分配给用户也成功。
修复前同样的插入会直接 `ERROR 1406 (22001): Data too long for column 'role'`。

### 9.2 P0-1 最后一个管理员护栏

判定"管理员"用的是**权限**而不是角色编码：角色启用 + 同时持有 `system:user` 与 `system:role`。
这样有人另建一个等权角色时护栏同样生效。

`UserService`：
- `update` 新增 `currentOperator` 参数（控制器从 SecurityContext 取）
- 拒绝停用当前登录用户；拒绝把自己改成没有用户/角色管理权限的角色
- `assertAtLeastOneAdmin(...)`：把待改动的这条按改动后状态代入重算，"能管理用户和角色的启用账号"为 0 就拒绝
- `delete` 同样走这条不变式

`RoleService.update`（`assertAdminRoleRemains`）：
- 内置 ADMIN 改完必须仍是"启用 + 持有 system:user + system:role"，否则拒绝
- 其它角色若被停用或抽掉这两项权限，系统里必须还有另一个"启用 + 已分配给启用用户"的管理员角色顶上
- 顺带补上 `delete` 的操作审计（原来删除角色不落审计）

真库验证（4 条拒绝路径 + 3 条放行路径）：

| 操作 | 结果 |
|---|---|
| `PUT /users/1 {"role":"VIEWER"}`（把自己降级） | `不能把当前登录用户改成无用户/角色管理权限的角色，请让另一位管理员操作` |
| `PUT /users/1 {"enabled":false}`（把自己停用） | `不能停用当前登录用户` |
| `PUT /roles/1`（ADMIN，enabled=false） | `内置 ADMIN 角色必须保持启用，并保留「用户管理」与「角色管理」权限` |
| `PUT /roles/1`（ADMIN，去掉 system:user） | 同上 |
| `PUT /roles/1`（ADMIN 原样回存 27 项权限） | success（不误伤） |
| `PUT /roles/{自定义}` 加 `page:workbench` | success |
| `PUT /users/10 {"enabled":false}`（停用普通用户） | success |

> 顺带说明：`RoleSaveReq.permissions` 传 null 会被当成空集合（等于清空该角色），
> 上面的 ADMIN 规则同时也是这个坑的兜底。

### 9.3 P0-2 电子标签回调鉴权

`/api/esl/callback/**` 仍然是 permitAll（设备端带不了 JWT），但加了共享密钥：

- `EslProperties` 新增 `callback-token`（环境变量 `ESL_CALLBACK_TOKEN`）与 `callback-header`（默认 `X-ESL-Token`）
- `EslController`：两个回调入口先过 `callbackAllowed()`，用 `MessageDigest.isEqual` 定长比较
- 失败时回 `received:false`，写接口日志（success=false）+ 一条 `ESL_CALLBACK/REJECT` 审计（操作人记 `esl-callback`，含来源 IP）
- **留空表示不校验**，兼容现场还没配的设备端；启动日志会 warn 提示"当前不做任何鉴权"

真库验证（配置 `ESL_CALLBACK_TOKEN=verify-esl-token-2026`）：

| 请求 | 结果 |
|---|---|
| button-press 不带 `X-ESL-Token` | `{"received":false,"message":"回调鉴权失败：缺少或错误的请求头 X-ESL-Token"}` |
| button-press 带错误 token | 同上 |
| light-result 不带 token | 同上 |
| button-press 带正确 token | `{"received":true,"matchedItems":1,...}` 正常进入业务逻辑 |

**现场升级注意**：设备端（墨水屏服务器）需要能配自定义请求头才能开这个开关。
如果它只会发固定报文，退路是保留密钥为空、靠网络层隔离（只放行墨水屏服务器 IP 到本端口）。

### 9.4 顺手修掉的一个用户列表 bug

`UserService.page` 原来把两个条件一起拼进 OR 查询，只填一个时另一个是空串：

```sql
where username like '%tmplong%' or display_name like '%%'   -- 第二条恒真
```

表现就是"按用户名搜索"把所有人都列出来了。改成按实际填写的条件分别查
（新增 `findByUsernameContaining` / `findByDisplayNameContaining`）。
验证：`?username=tmplong` → 1 条；`?displayName=管理` → admin；不带条件 → 4 条。

### 9.5 仍未处理

P1、P2 两批（`UserUpdateReq` 无校验、`system:role` 可自我提权、登录/改密无审计、
`/api/interface/inbound` 匿名写入、点灯接口无注解、限流可被用来锁死别人、
CORS 配置被覆盖、frameOptions 关闭、移动端导航不过滤等）见第 0～6 节，尚未改动。
