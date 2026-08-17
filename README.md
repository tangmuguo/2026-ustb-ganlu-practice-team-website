# 甘露支教网站

这是一个前后端分离的支教团队网站，包含公共首页、团队风采、课件共享、受审核的互动留言和用户管理。

本文优先写给第一次运行项目的人。按顺序操作即可；不要把数据库密码、JWT 密钥或任何第三方服务密钥发到群里或提交到 Git。

## 当前交付状态

当前 `main` 分支已包含 `safe` 分支的安全整改，并纳入后续的公开内容、联系信息、数据库和 HTTPS 维护更新。当前源码具备以下能力：

- 已移除公开学生注册、AI 小助手及第三方大模型调用；
- 学生归属、核验和监护人授权门禁，留言/回复审核、举报和最小化审计；
- 文件隔离、扫描结果账本、图片受控重编码与“扫描通过后才可公开或下载”的门禁；
- 更正、删除评估和撤回授权的隐私权利工单；
- 团队风采、课件共享、新闻、互动留言和后台管理。

这不等于已经可以对外宣称完成安全评估或生产上线：数据库补丁、HTTPS/Nginx、日志轮转、备份恢复、真实文件扫描服务、线下身份核验和投诉处理流程仍须由实际部署负责人完成并留存证据。服务器证书续期步骤见 `SSL续期操作.md`。

## 你需要安装的软件

在 Windows 11 上安装：

1. Git for Windows；
2. Node.js 22 LTS（至少 22.12）或 Node.js 24 LTS；
3. 64 位 Eclipse Temurin JDK 8；
4. MySQL Server 8.x 和 MySQL Workbench；
5. VS Code；
6. 课件预览转换工具（仅需要将 PPT/PPTX 转为预览时配置；当前实现通过 `LIBREOFFICE_EXECUTABLE` 指定）；
7. Chrome 或 Edge。

项目自带 Maven，不需要单独安装 Maven。

打开 PowerShell，逐行检查：

```powershell
git --version
node -v
npm.cmd -v
java -version
```

`java -version` 应显示 1.8。如果提示找不到命令，先关闭并重新打开 PowerShell；仍然不行就检查安装时是否勾选了加入 `PATH`。

## 第一次运行

下面假设项目位于：

```text
E:\github\zhaoyouwei\2026-ustb-ganlu-practice-team-website
```

### 第一步：创建数据库

1. 打开 MySQL Workbench，用安装 MySQL 时设置的管理员账号连接本机数据库。
2. 点击新建 Schema，名称填写 `ganlu`，字符集选 `utf8mb4`。
3. 打开根目录 `ganlu.sql`，把默认 Schema 设为 `ganlu`，执行全部内容。
4. 再按顺序执行当前补丁：
   - `database/patches/00_user_security.sql`
   - `database/patches/10_team_core.sql`
   - `database/patches/11_team_content.sql`
   - `database/patches/12_team_owner_unique.sql`
   - `database/patches/13_public_image_quota.sql`
   - `database/patches/14_team_media_lifecycle.sql`
   - `database/patches/15_team_content_history_publish.sql`
   - `database/patches/20_message_board.sql`
   - `database/patches/30_material_center.sql`
   - `database/patches/31_material_file_lifecycle.sql`
   - `database/patches/32_material_general_subjects.sql`
   - `database/patches/33_security_assessment_identity_and_tenant.sql`
   - `database/patches/34_security_assessment_content_moderation.sql`
   - `database/patches/35_security_assessment_audit_and_reports.sql`
   - `database/patches/36_security_assessment_file_scan_and_quarantine.sql`
   - `database/patches/37_security_assessment_privacy_requests.sql`
5. `15_team_content_history_publish.sql` 不是可直接批量粘贴的普通补丁：它要求先用明确的 `@patch15_cutoff` 执行 dry-run、核对历史内容清单，再显式确认 apply；若存在历史图片文件，还要在同一维护窗口处理 `15_migrate_images_files.sh`。请严格按 `database/patches/README.md` 和脚本头部说明执行。
6. 最终固定顺序是 `00 → 10 → 11 → 12 → 13 → 14 → 15 → 20 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 37`。仍须在数据库备份副本中用真实 MySQL 完整重跑；没有数据库凭据时，不能把静态检查写成实测通过。

补丁的完整说明和注意事项见 `database/patches/README.md`。不要在已有正式数据库上直接试脚本。

后端启动时会校验关键表和字段。若提示“数据库结构不完整”，说明补丁未完整执行；按上面的顺序在备份副本完成迁移后再启动。不要用 `GANLU_SCHEMA_VALIDATION_ENABLED=false` 代替迁移，它只适合临时诊断。

在 Workbench 中创建一个只给本地网站使用的账号。把示例密码换成你自己的强密码：

```sql
CREATE USER 'ganlu_local'@'localhost' IDENTIFIED BY '请替换为本地强密码';
GRANT SELECT, INSERT, UPDATE, DELETE ON ganlu.* TO 'ganlu_local'@'localhost';
FLUSH PRIVILEGES;
```

如果提示账号已经存在，不要重复创建；确认你知道原密码，或者在 Workbench 中重设该本地账号密码。

### 第二步：首次启动后端并创建管理员

打开一个新的 PowerShell 窗口，逐行执行。三处“请替换”必须改成你自己的值；密码至少 8 位。

```powershell
cd E:\github\zhaoyouwei\2026-ustb-ganlu-practice-team-website\backend
$env:SPRING_PROFILES_ACTIVE='dev'
$env:GANLU_DB_URL='jdbc:mysql://127.0.0.1:3306/ganlu?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:GANLU_DB_USERNAME='ganlu_local'
$env:GANLU_DB_PASSWORD='请替换为刚才创建的数据库密码'
$env:GANLU_JWT_SECRET='仅本地使用-请替换成至少32字符的随机内容-不要提交'
$env:GANLU_BOOTSTRAP_ADMIN_USERNAME='ganlu-admin'
$env:GANLU_BOOTSTRAP_ADMIN_PASSWORD='请替换为管理员登录密码'
$env:GANLU_BOOTSTRAP_ADMIN_PHONE='13800138000'
.\mvnw.cmd spring-boot:run
```

看到“已创建首次管理员账号”以及 Spring Boot 启动成功后：

1. 按 `Ctrl+C` 停止后端；
2. 清除一次性管理员变量；
3. 重新启动后端。

```powershell
Remove-Item Env:GANLU_BOOTSTRAP_ADMIN_USERNAME
Remove-Item Env:GANLU_BOOTSTRAP_ADMIN_PASSWORD
Remove-Item Env:GANLU_BOOTSTRAP_ADMIN_PHONE
.\mvnw.cmd spring-boot:run
```

密码会由后端使用 BCrypt 加密后写入数据库，日志不会打印密码。数据库中已经有 level=0 管理员时，初始化程序也不会创建第二个管理员。

公共图片默认每个上传账号最多保存 100 张、累计 500MB，并要求上传磁盘至少保留 1GB 可用空间。生产部署可通过 `TEAM_PUBLIC_IMAGE_MAX_PERMANENT_FILES`、`TEAM_PUBLIC_IMAGE_USER_PERMANENT_QUOTA_MB` 和 `TEAM_PUBLIC_IMAGE_MIN_FREE_DISK_MB` 调整；修改前需由孙木文结合服务器磁盘容量确认。

团队视频/附件默认每个账号最多 50 个、累计 2GB，服务器总计最多 2000 个、20GB；正式上传目录和 Multipart 临时目录都必须至少保留 1GB。上传账号会在 Multipart 解析前认证，系统通过数据库跨实例登记在途字节，并默认限制全站同时 4 个上传、单账号每分钟 12 次。生产环境应把 `GANLU_MULTIPART_TEMP_DIR` 指向独立、有限额且不可公开访问的挂载目录。普通“归档”仍保留文件并占额度；管理员彻底清理或默认 30 天保留期结束后，系统才通过持久化删除任务释放空间和额度。

旧公共图片不能由 SQL 猜测大小。执行补丁后必须在维护窗口调用管理员公共图片预检，处理共享路径、缺失文件、未知所有者和非标准路径；预检无阻断项后临时开启迁移开关，按磁盘真实字节登记 Banner、News、User 和团队风采图片，完成一致性断言后立即关闭开关。课件封面属于独立生命周期：`images/materials/` 和有效课件引用的历史封面会被保护性排除，不能当作公共图片孤儿清理。

上传文件默认采用 fail-closed 策略：未配置或不可用的扫描服务不会把文件视为安全，文件会保持隔离/待处理状态，不能公开或下载。要做完整上传验收，必须由部署负责人配置可用的 `FILE_SECURITY_SCAN_ENABLED=true` 和 `FILE_SECURITY_SCAN_ENDPOINT`；不要为了临时测试把逻辑改为放行未扫描文件。

不要关闭这个 PowerShell 窗口。后端默认运行在 `http://localhost:8080`。

如果需要课件预览转换，在启动后端前额外设置 LibreOffice 路径：

```powershell
$env:LIBREOFFICE_EXECUTABLE='C:\Program Files\LibreOffice\program\soffice.exe'
```

### 第三步：启动前端

再打开一个新的 PowerShell 窗口：

```powershell
cd E:\github\zhaoyouwei\2026-ustb-ganlu-practice-team-website\frontend
npm.cmd ci
npm.cmd run dev
```

浏览器打开：

```text
http://localhost:5173/
```

这里使用 `npm.cmd` 是为了绕过部分 Windows 电脑对 `npm.ps1` 的执行策略限制。

## 第一次手工验收

建议按下面顺序点一遍，并把截图和问题写入本次验收记录；不要把当前验证结果混写进已归档的历史材料：

1. 游客：打开首页、关于甘露、联系我们、隐私说明、团队风采、课件和留言板；确认不存在白屏和死链接。
2. 管理员：用刚创建的 `ganlu-admin` 登录并创建一个团队账号。
3. 团队账号：退出管理员后用团队账号登录；确认只能看到团队允许的管理入口，并创建或查看学生账号。
4. 学生账号：只能由管理员或归属团队创建；核验和监护人授权未完成时，确认不能发布留言或回复。
5. 内容审核和隐私：用管理员在“内容安全”页审核一条待处理留言，再确认公开留言板只显示审核通过的内容；提交一条举报，并创建一条隐私权利请求，确认管理员可查看相应工单。
6. 权限：未登录直接访问受保护的管理地址应跳到登录页；学生访问管理员地址、团队 A 操作团队 B 学生均应被拒绝。
7. 课件：在文件扫描服务已配置并返回 CLEAN 结果的前提下，用团队账号上传一份无敏感信息的样例课件，检查预览和下载；再验证团队不能删除其他团队的课件。

正式的角色编号为：

| level | 角色 | 主要权限 |
| --- | --- | --- |
| 0 | 管理员 | 用户、团队、全站新闻、内容审核、举报、隐私工单与审计查询 |
| 1 | 团队 | 仅本团队已归属学生、本人上传课件、互动功能与隐私权利请求 |
| 2 | 学生 | 课件浏览；完成核验和监护人授权后可提交互动内容与隐私权利请求 |
| 未登录 | 游客 | 公共页面和公开列表 |

前端隐藏菜单只是方便使用，真正的权限会由后端根据 Bearer Token 再检查。

## 日常启动

首次创建管理员后，以后只需要两个窗口。

后端窗口：

```powershell
cd E:\github\zhaoyouwei\2026-ustb-ganlu-practice-team-website\backend
$env:SPRING_PROFILES_ACTIVE='dev'
$env:GANLU_DB_URL='jdbc:mysql://127.0.0.1:3306/ganlu?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:GANLU_DB_USERNAME='ganlu_local'
$env:GANLU_DB_PASSWORD='你的本地数据库密码'
$env:GANLU_JWT_SECRET='你的本地JWT随机密钥'
.\mvnw.cmd spring-boot:run
```

前端窗口：

```powershell
cd E:\github\zhaoyouwei\2026-ustb-ganlu-practice-team-website\frontend
npm.cmd run dev
```

PowerShell 窗口关闭后，以上 `$env:` 临时变量会自动消失，这是正常现象。可以参考 `backend/src/main/resources/application-dev.properties.example` 和 `application-prod.properties.example`，但不要提交含真实密码的本地配置。

## 维护公开文字和联系方式

“关于甘露”页面的正式介绍、服务地区、发展历程和当前公开联系方式统一维护在：

```text
frontend/src/config/siteContent.js
```

负责人补充或更正这些公开信息时，只改这一个文件，再运行前端构建检查。变更前应确认信息真实、可用且获得公开授权；不要把个人手机号随意复制到多个页面。

## 运行自动检查

后端：

```powershell
cd backend
.\mvnw.cmd test
```

前端正式构建：

```powershell
cd frontend
npm.cmd ci
npm.cmd run build
node --test tests/*.test.js
```

后端生成可部署 WAR：

```powershell
cd backend
.\mvnw.cmd clean package
```

前端结果在 `frontend/dist`，后端结果在 `backend/target`。构建成功不等于允许上线。

## 常见问题

### 后端提示无法连接数据库

打开 Windows“服务”，确认 MySQL 服务正在运行；再检查 Schema 是否叫 `ganlu`，以及 `GANLU_DB_USERNAME`、`GANLU_DB_PASSWORD` 是否和 Workbench 中一致。

### 端口被占用

Vite 默认用 5173，Spring Boot 默认用 8080。不要同时在 8080 启动旧 Tomcat 和本项目后端。关闭占用窗口后重试。

### 前端能开但没有数据

确认后端窗口仍在运行，并且 `frontend/.env.development` 指向 `http://localhost:8080/`。全新数据库本来就没有新闻、团队和课件业务数据，需要用相应账号添加。

### 上传后文件始终处于待处理状态

这是未配置、超时或扫描失败时的预期安全行为。检查 `FILE_SECURITY_SCAN_ENABLED`、`FILE_SECURITY_SCAN_ENDPOINT` 和扫描服务日志；不要绕过扫描门禁或直接把隔离目录暴露为静态文件。

### 登录一直失败

确认首次启动日志出现过管理员创建提示；账号是 `GANLU_BOOTSTRAP_ADMIN_USERNAME` 的值。不要直接在数据库中手写明文密码。

### PowerShell 不允许运行 npm

使用文档里的 `npm.cmd`，不要改 Windows 全局安全策略。

### 刷新页面出现 404

本地 Vite 一般不会出现。正式 Nginx 必须把 Vue History 路由回退到 `index.html`，这属于上线配置，需由负责人审核。

## 项目结构

```text
frontend/                 Vue 3 前端页面、路由和 API
backend/                  Spring Boot 后端、权限、业务和测试
database/patches/         数据库增量脚本和执行说明
deploy/                   Nginx、systemd 与日志轮转模板
docs/任务分工/            九人任务单和统一业务约定
SSL续期操作.md            ganlu.site 证书续期操作说明
ganlu.sql                 数据库基线结构
```

主要技术为 Vue 3、Vite、Pinia、Element Plus、Spring Boot 2.4、MyBatis、MySQL 8 和 Java 8。

## 提交与上线边界

- 当前代码位于 `main` 分支；本说明更新不包含自动提交、推送或生产部署。
- 提交 Pull Request 前，让安全负责人审查公共接口、正式文字、数据库补丁和权限矩阵。
- 上线前必须备份并验证恢复数据库与上传文件，轮换曾暴露的密码和 Key，配置 HTTPS、日志轮转、可信代理和回滚方案。
- `deploy/nginx/renew-ganlu-tls.sh` 只用于在服务器上安全更换证书；每次执行仍须遵循 `SSL续期操作.md` 并完成站点验证。
- 备案号只能在已核验的情况下展示；公安联网备案或负责人信息未确认前，不能伪造或填入页面/安全评估材料。

项目采用 [MIT License](LICENSE)。
