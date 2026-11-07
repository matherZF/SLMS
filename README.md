# 备品备件全生命周期数字化管理系统

> 当前实现说明请以 [项目总览](docs/PROJECT-OVERVIEW.md)、[业务流程与审批规则](docs/operations-and-workflows.md) 和 [内网部署说明](docs/intranet-deployment.md) 为准。本文后续早期开发记录仅保留历史参考，其中关于 H2 默认运行、仅 5 张表、无登录权限及未实现出库等描述已经过时。

## 当前版本摘要

- 后端：Java 17、Spring Boot 3.2.5、Spring Security、JWT、Spring Data JPA。
- 前端：Vue 3、Vite、Pinia、Element Plus、`html5-qrcode`。
- 数据库：生产环境使用 MySQL 8.0+，自动化测试使用 H2。
- 已覆盖备件主数据、库存与库位、入库上架、领用出库、退库、盘点、报废、使用台账、设备运行数据、统一审批、角色权限、数据字典和统一审计。
- 审批入口统一称为“审批中心”；按钮权限从属于页面权限。

启动和部署请阅读 [内网部署说明](docs/intranet-deployment.md)，流程与审批规则请阅读 [业务流程与审批规则](docs/operations-and-workflows.md)，检查结论及优化顺序见 [项目整体检查报告](docs/PROJECT-REVIEW.md)。

***

## 历史开发记录（内容可能过时）

## 一、功能概览

### 🔧 基础数据（桌面端）

- **货品管理**：SKU / 名称 / 规格 / 单位 → 增删改查，SKU 全局唯一

- **库位管理**：库位编码 / 区域 / 类型（常温/冷藏/大件/危险品/其他）/ 备注 → 编码唯一，存在库存时禁止删除

- **上架单管理**：创建上架单（选择商品 + 计划数量）、状态筛选（待上架/已完成）、进度条、详情（含已上架明细）

### ✅ 上架作业（移动端优先，桌面通用，3 步）

1. **选择待上架单**（卡片列表显示剩余数量 + 进度）
2. **商品确认**：展示单据信息，可选输入 SKU 核对，不匹配禁止下一步
3. **库位 + 数量 + 确认上架**：调用接口事务内累加 上架单据 + 上架明细 + 库存

### 📊 库存查询（双 Tab）

- 按商品汇总：SKU / 名称 / 规格 / 单位 / 实存数量 / 拣货占用 / 可用数量

- 按库位明细：库位 / 区域 / SKU / 商品 / 数量 / 更新时间

### 🎨 双端 UI

- **桌面端（≥ 1024px）**：深色侧边栏 5 项菜单 + 顶部标题 + 栅格多列表单

- **移动端（< 768px）**：蓝色顶栏 + 底部 5 Tab 导航（上架作业加粗高亮）+ 主按钮 ≥ 44×44px

- 中间平板端：沿用桌面布局，宽度自适应

***

## 二、架构

```
┌────────────────────────────┐
│ Browser / 移动浏览器        │  桌面/移动自适应
│  Vue3 + Element Plus + Pinia│  Vite 端口 5173
└──────────┬─────────────────┘
           │ /api/** 开发代理
           ▼
┌────────────────────────────┐
│ Spring Boot 3.x + JDK17    │  端口 8080
│  Controller/Service/JPA    │  统一 Result{code,msg,data}
│  GlobalException + CORS    │  @Transactional 事务上架
└──────────┬─────────────────┘
           ▼
┌────────────────────────────┐
│ H2 (默认 file ./data/wms)  │  零安装即可运行
│ 或 MySQL 8 (切换 profile)  │  5 张表: product/location/
│                            │  putaway_order/putaway_item/inventory
└────────────────────────────┘
```

***

## 三、环境要求

| 组件      | 最低版本                    | 备注                                |
| ------- | ----------------------- | --------------------------------- |
| JDK     | 17                      | `java -version` 校验；后端声明 target 17 |
| Maven   | 3.8+                    | 构建后端 jar                          |
| Node.js | 18+                     | `node -v`；建议 20 LTS               |
| npm     | 9+                      | 随 Node 一起安装                       |
| 浏览器     | Chrome 90+ / Safari 14+ | 移动端兼容微信内置浏览器                      |

> 💡 沙箱/服务器如无法访问官方 npm，可加参数：`--registry=https://registry.npmmirror.com`；maven 同理建议提前配置国内 mirror。

***

## 四、启动方式（推荐：后端 jar + 前端 dev）

启动前请在运行环境设置 JWT 密钥（至少 32 字节）；不要把真实密钥写入项目配置：

```bash
export JWT_SECRET='replace-with-a-random-secret-at-least-32-bytes'
# 生产环境还应设置：export CORS_ALLOWED_ORIGINS='https://your-frontend.example.com'
```

Windows PowerShell：`$env:JWT_SECRET='replace-with-a-random-secret-at-least-32-bytes'`。

本地 MySQL 连接变量（未设置时主机、端口、数据库名和用户名分别默认 `localhost`、`3306`、`wms`、`root`；密码必须设置）：

```powershell
$env:MYSQL_PASSWORD='你的 MySQL root 密码'
# 可选：$env:MYSQL_HOST='localhost'; $env:MYSQL_PORT='3306'
# 可选：$env:MYSQL_DATABASE='wms'; $env:MYSQL_USERNAME='root'
```

powershell 启动
$env:JWT\_SECRET='change-this-to-a-random-secret-at-least-32-bytes'
$env:MYSQL\_PASSWORD='root'
cd C:\Users\张峰\IdeaProjects\spare\_parts\backend
mvn spring-boot:run

### 方式 1：开发联调（前端热更新 + 后端已打包 jar）

```bash
# ① 启动后端（首次启动会落种子数据：3 商品、6 库位、1 待上架单、2 库存）
cd /workspace/backend
# 可选：清空老数据
rm -rf data
nohup java -jar target/wms-backend-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
# 验证
sleep 5
curl -s http://localhost:8080/api/products  # 应返回 code=0 totalElements=3

# ② 启动前端
cd /workspace/frontend
npm install --legacy-peer-deps --registry=https://registry.npmmirror.com  # 首次安装依赖
npm run dev
# 访问 http://localhost:5173 即可（/api 自动代理到 8080，无需 CORS 配置）
```

### 方式 2：前端打包生产版本

```bash
cd /workspace/frontend
npm install --legacy-peer-deps
npm run build      # 产物输出到 dist/
# 可用 vite preview 预览：
npm run preview -- --port 4173
# 或把 dist/ 交给 nginx / 任意静态服务器；同时需要后端仍在 8080，把 /api/* 反向代理过去
```

### 方式 3：后端从头构建 + MySQL 切换（进阶）

```bash
# 从头构建 jar
cd /workspace/backend
mvn clean package -DskipTests

# 切换 MySQL（application-mysql.yml 已提供模板）：
# ① 建库：CREATE DATABASE wms DEFAULT CHARACTER SET utf8mb4;
# ② 修改 application.yml 中 spring.profiles.active=mysql
# ③ 或启动时加参数：java -jar target/wms-backend.jar --spring.profiles.active=mysql \
#    --spring.datasource.url=jdbc:mysql://localhost:3306/wms?... \
#    --spring.datasource.username=xxx --spring.datasource.password=xxx
```

### 方式 4：一键脚本（Linux / Mac）

```bash
cd /workspace
chmod +x start.sh
./start.sh dev        # 后端 jar + 前端 dev（推荐开发）
./start.sh build      # 仅构建 jar + 前端 dist
```

***

## 五、接口速览（全部前缀 `/api`，返回 `{code:0, msg:"", data:...}`）

| 模块     | 方法                  | 路径                                      | 说明                                     |
| ------ | ------------------- | --------------------------------------- | -------------------------------------- |
| 商品     | GET                 | `/products?sku=&name=&page=&size=`      | 分页查询                                   |
| <br /> | GET                 | `/products/{id}`                        | 详情                                     |
| <br /> | POST                | `/products`                             | 新增（sku/name 必填）                        |
| <br /> | PUT                 | `/products/{id}`                        | 修改（SKU 不允许改）                           |
| <br /> | DELETE              | `/products/{id}`                        | 删除（已被上架单引用→失败）                         |
| 库位     | GET/POST/PUT/DELETE | `/locations[...]`                       | 字段 code/area/type/remark；有库存时删除失败      |
| 上架单    | GET                 | `/orders?status=&keyword=&page=&size=`  | 列表（状态 PENDING/DONE）                    |
| <br /> | GET                 | `/orders/pending`                       | 仅待上架（用于移动端选单，含剩余数量）                    |
| <br /> | GET                 | `/orders/{id}`                          | 详情（商品信息 + 进度 + 明细）                     |
| <br /> | POST                | `/orders`                               | 创建（productId + planQty>0）              |
| 上架     | POST                | `/putaway/confirm`                      | **核心事务** body:{orderId,locationId,qty} |
| 库存     | GET                 | `/inventory/summary?sku=`               | 按商品汇总（含 totalQty）                      |
| <br /> | GET                 | `/inventory/details?sku=&locationCode=` | 按库位明细                                  |

分页接口 `data` 是 Spring `Page`：`{content:[...], totalElements:N, totalPages:N, number:0-based}`。

错误：`code!=0` 时 `msg` 为中文友好提示（sku 已存在 / 超过计划数量，剩余40件 / 库位存在库存记录，无法删除 …）。

***

## 六、典型流程：一次完整上架

管理员桌面端：

1. **商品管理 → 新增商品**：`SP-100` 法兰盘 DN50 个
2. **库位管理 → 新增库位**：`D-03-02` D区 常温
3. **上架单管理 → 新建上架单**：选择 SP-100，计划 100 → 生成 `PA202609040001`（待上架）

作业员移动端：
4\. 浏览器打开 `http://<host>:5173/` → 底部 Tab 切换「上架作业」
5\. 步骤 1：点击单据 `PA202609040001`
6\. 步骤 2：确认商品信息；可选输入 SKU `SP-100` 核对（显示"核对通过"）→ 下一步
7\. 步骤 3：选择库位 `D-03-02`，数量默认 100 → 点击"确认上架"
8\. 弹窗提示"全部上架完成，单据已结束"→ 返回选单

管理员：
9\. **库存查询** Tab1：SP-100 总库存 100；Tab2：D-03-02 → SP-100 数量 100 ✅

***

## 七、项目目录

```
/workspace
├── README.md                  本文档
├── start.sh                   一键启动脚本（build / dev）
├── backend/
│   ├── pom.xml
│   ├── target/wms-backend-0.0.1-SNAPSHOT.jar  （已构建产物，可直接 java -jar）
│   ├── data/wms.mv.db          H2 文件数据库（首次启动自动生成）
│   └── src/main/java/com/wms
│       ├── WmsApplication.java
│       ├── config  (CORS / WebConfig)
│       ├── common  (Result / BizException)
│       ├── exception (GlobalExceptionHandler)
│       ├── entity  (Product/Location/PutawayOrder/PutawayItem/Inventory + Status 枚举)
│       ├── repository (5 个 JpaRepository)
│       ├── dto     (9 个 record)
│       ├── service (Product/Location/PutawayOrder/Putaway/Inventory + DataInitializer)
│       └── controller (5 个 RestController)
└── frontend/
    ├── package.json
    ├── vite.config.js
    ├── dist/                   （npm run build 产物）
    └── src
        ├── main.js / App.vue
        ├── api/    (request.js axios 拦截器 + 5 个模块 API)
        ├── router/ (hash 模式，7 路由)
        ├── styles/ (index.scss + variables.scss 断点 $md=768 $lg=1024)
        ├── layouts/ (DesktopLayout.vue / MobileLayout.vue)
        ├── components/ (PageHeader + 3 个 Dialog)
        └── pages/ (Products / Locations / Orders / OrderDetail / Putaway / Inventory)
```

***

## 八、常见问题

1. **端口 8080 被占用？** → `lsof -i :8080 | grep LISTEN` 找到 PID 后 kill；或改 `application.yml server.port`
2. **前端 5173 端口占用？** → `vite.config.js server.port = 5174` 同时保持后端 CORS 允许端口也调整（或使用 /api 代理可不用 CORS）
3. **H2 数据库文件损坏？** → 停后端 → 删除 `/workspace/backend/data/` 目录 → 重启后端（种子数据自动重建）
4. **H2 控制台？** → `application.yml` 中 `spring.h2.console.enabled=true`，重启后访问 `http://localhost:8080/h2-console`，JDBC URL 填 `jdbc:h2:file:./data/wms`，用户名 `sa`，密码空
5. **切换 MySQL 后第一次启动报错表不存在？** → 确认账号具备建库建表权限；项目启动时由 Flyway 自动执行版本化迁移，Hibernate 使用 `ddl-auto: validate` 校验结构。
6. **上架接口报错"超过计划数量"但还想再上？** → 重新创建一张上架单，补齐需要的数量（系统不支持超量上架，避免账实不符）

***

## 九、非目标与后续路线图

本期 **不包含**（如需要可后续迭代）：

- 出库 / 拣货 / 盘点 / 调拨流程

- 用户登录 + 角色权限（当前单用户共享）

- 扫码枪硬件对接 / 摄像头扫码（代码中 Putaway 页 Step2 已预留输入框占位）

- 多仓库 / 多组织 / 批次 / 序列号管理

后续路线图：

1. Spring Security + 登录 JWT
2. 出库 / 盘点模块
3. 摄像头扫码集成（html5-qrcode）
4. Flyway 数据迁移 + 单元测试覆盖率 60%+
5. Docker Compose 一键部署（后端 jar + MySQL + Nginx 静态）

