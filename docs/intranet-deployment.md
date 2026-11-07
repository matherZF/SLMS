# 内网部署约定

系统使用内网 IP 访问，不依赖公网域名。管理端和移动端前端由 Nginx 提供，后端仅在内网开放 API。

## 运行基线

- Java 17、MySQL 8.0+、Node.js 18+。
- 业务数据库默认连接 `jdbc:mysql://localhost:3306/spare_parts?createDatabaseIfNotExist=true`，默认本地开发账号和密码均为 `root`。
- 后端默认端口 8080；前端开发端口 5173。
- 数据库结构由 Flyway 管理；空库执行 V1 基线建表，已有业务库首次接入时自动登记 V1 基线。
- Hibernate 使用 `ddl-auto=validate` 校验实体与数据库结构，不再自动修改生产表。
- 正式生产必须使用环境变量覆盖数据库密码和 `JWT_SECRET`。

## 启动检查

1. 确认 Windows MySQL 服务已启动且 3306 端口可访问。
2. 在 `backend` 目录执行 `mvn spring-boot:run`，检查 Flyway 迁移和 Hibernate 校验均成功。
3. 禁止在生产环境将 `JPA_DDL_AUTO` 改回 `update`；结构变化必须新增迁移文件。
4. 在 `frontend` 目录执行 `npm install` 和 `npm run dev`。
5. 登录后检查审批中心、库存台账和流程配置是否正常加载。
6. 设备数据接入启用时，确认只读账号仅对约定视图拥有 SELECT 权限。

## 推荐访问方式

- 前端：`https://<服务器内网IP>/`
- 后端：由 Nginx 将 `/api` 反向代理到 `http://127.0.0.1:8080`
- MySQL：仅允许应用服务器访问，不向普通客户端网段开放
- 设备数据库：仅允许应用服务器使用只读账号访问指定视图

## 手机摄像头要求

移动端网页调用手机摄像头需要安全上下文。即使没有域名，也应使用 HTTPS：

1. 使用企业内部 CA 为服务器内网 IP 签发证书，证书 SAN 中必须包含该 IP。
2. 在需要扫码的手机上安装并信任内部 CA 根证书。
3. Nginx 使用该证书提供 HTTPS；禁止将扫码页面部署为普通 HTTP 内网 IP。

如果企业策略不允许在手机上信任内部 CA，应申请内部 DNS 域名并签发可信证书，或改为原生/混合应用封装；纯 HTTP 网页不能作为稳定扫码方案。
