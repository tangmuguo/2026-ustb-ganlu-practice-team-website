# 甘露支教网站

这是一个前后端分离的支教团队网站，包含公共首页、团队风采、课件共享、互动留言、AI 小助手、用户管理和志愿者报名。

本文优先写给第一次运行项目的人。按顺序操作即可；不要把数据库密码、JWT 密钥或 DeepSeek Key 发到群里或提交到 Git。

## 当前交付状态

公共架构和已收到的成员模块已经集成到分支 `zhaoyouwei/public-architecture-integration`。后端 100 项自动测试、前端正式构建和留言板 3 项测试均已通过。

当前仍缺李嘉辉负责的团队风采内容管理和 `database/patches/11_team_content.sql`，所以本分支可以本地联调，但不能宣称九人任务已经全部完成，也不能直接部署生产环境。详情见 `docs/integration/最终联调记录.md`。

## 你需要安装的软件

在 Windows 11 上安装：

1. Git for Windows；
2. Node.js 22 LTS（至少 22.12）或 Node.js 24 LTS；
3. 64 位 Eclipse Temurin JDK 8；
4. MySQL Server 8.x 和 MySQL Workbench；
5. VS Code；
6. LibreOffice（只有课件转预览图时需要）；
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
4. 再依次打开并执行当前已有补丁：
   - `database/patches/00_user_security.sql`
   - `database/patches/10_team_core.sql`
   - `database/patches/20_message_board.sql`
   - `database/patches/30_material_center.sql`
   - `database/patches/40_volunteer_application.sql`
5. `11_team_content.sql` 当前缺失。本地调试可暂时跳过，但最终验收必须等该文件到位后，在新建的数据库备份副本中按完整顺序重跑。

补丁的完整说明和注意事项见 `database/patches/README.md`。不要在已有正式数据库上直接试脚本。

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

建议按下面顺序点一遍，并把截图和问题写进 `docs/integration/最终联调记录.md`：

1. 游客：打开首页、关于甘露、联系我们、加入甘露、团队风采、课件和留言板；确认不存在白屏和死链接。
2. 志愿者：在“加入甘露”提交一条报名，故意重复手机号，确认第二次会提示重复。
3. 管理员：用刚创建的 `ganlu-admin` 登录；进入志愿报名管理并更新状态；创建一个团队账号。
4. 团队账号：退出管理员后用团队账号登录；确认只能看到团队允许的管理入口，并创建或查看学生账号。
5. 学生账号：访问 `/regs` 注册学生，登录后发布留言、回复留言；确认不能进入管理员页面。
6. 权限：未登录直接访问 `/applications` 应跳到登录页；学生访问管理地址应被拒绝。
7. 课件：用团队账号上传一份无敏感信息的样例课件，检查预览和下载。
8. AI：只有配置有效 DeepSeek Key 后再测试；Key 只能放环境变量，不能写进源码。

正式的角色编号为：

| level | 角色 | 主要权限 |
| --- | --- | --- |
| 0 | 管理员 | 用户、团队、报名和全站管理 |
| 1 | 团队 | 学生、课件、本团队内容和互动功能 |
| 2 | 学生 | 课件浏览、留言和 AI 等普通功能 |
| 未登录 | 游客 | 公共页面、公开列表和志愿报名 |

前端隐藏菜单只是方便使用，真正的权限会由后端根据 Bearer Token 再检查。

## 日常启动

首次创建管理员后，以后只需要两个窗口。

后端窗口：

```powershell
cd E:\github\zhaoyouwei\2026-ustb-ganlu-practice-team-website\backend
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

PowerShell 窗口关闭后，以上 `$env:` 临时变量会自动消失，这是正常现象。可以参考 `backend/src/main/resources/application-local.example.properties`，但不要提交含真实密码的本地配置。

## 修改正式介绍和联系方式

目前负责人没有提供经过确认的团队历史、服务地区、电话和邮箱。为了避免编造，这些文字统一放在：

```text
frontend/src/config/siteContent.js
```

孙木文确认正式内容后，只改这一个文件，再运行前端构建检查。不要把个人手机号随意复制到多个页面。

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
node --test tests/messageAuthor.test.js tests/messageState.test.js
```

后端生成可部署 WAR：

```powershell
cd backend
.\mvnw.cmd clean package
```

前端结果在 `frontend/dist`，后端结果在 `backend/target`。构建成功不等于允许上线。

## 可选：启用 AI 小助手

先向负责人申请开发环境 Key，再在启动后端的 PowerShell 中设置：

```powershell
$env:AI_ENABLED='true'
$env:DEEPSEEK_API_KEY='你的开发环境Key'
$env:DEEPSEEK_MODEL='deepseek-v4-flash'
```

不要把 Key 写入 `.env`、Properties、截图、聊天记录或 Git 提交。没有 Key 时 AI 默认关闭，其他模块仍可运行。

## 常见问题

### 后端提示无法连接数据库

打开 Windows“服务”，确认 MySQL 服务正在运行；再检查 Schema 是否叫 `ganlu`，以及 `GANLU_DB_USERNAME`、`GANLU_DB_PASSWORD` 是否和 Workbench 中一致。

### 端口被占用

Vite 默认用 5173，Spring Boot 默认用 8080。不要同时在 8080 启动旧 Tomcat 和本项目后端。关闭占用窗口后重试。

### 前端能开但没有数据

确认后端窗口仍在运行，并且 `frontend/.env.development` 指向 `http://localhost:8080/`。全新数据库本来就没有新闻、团队和课件业务数据，需要用相应账号添加。

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
docs/integration/         各成员接口说明与最终联调记录
docs/任务分工/            九人任务单和统一业务约定
ganlu.sql                 数据库基线结构
```

主要技术为 Vue 3、Vite、Pinia、Element Plus、Spring Boot 2.4、MyBatis、MySQL 8 和 Java 8。

## 提交与上线边界

- 当前工作只在集成分支完成，没有自动推送、合并主分支或部署生产环境。
- 提交 Pull Request 前，让孙木文审查公共接口、正式文字、数据库补丁和权限矩阵。
- 上线前必须备份并验证恢复数据库与上传文件，轮换曾暴露的密码和 Key，配置 HTTPS，并准备回滚方案。
- `npm ci` 当前报告 16 个依赖安全问题；不要直接运行可能破坏兼容性的自动修复，需安排升级分支和完整回归。

项目采用 [MIT License](LICENSE)。
