# 甘露支教网站（Ganlu）

甘露支教网站是一套前后端分离的内容与团队管理系统，主要包含首页展示、团队风采、课件资料、新闻、轮播图、用户管理和留言回复等功能。

> 本仓库来自一次项目文件交接，不是完整的版本控制仓库。现有文件包含源码、历史构建产物和部分上传文件，但缺少可用的 Git 历史、线上数据库业务数据、Nginx/Tomcat 配置及完整部署文档。

## 技术架构

```text
浏览器
  │
  ├─ 前端：Vue 3 + Vite + Vue Router + Pinia + Element Plus + Tailwind CSS
  │          │
  │          └─ Axios 调用 HTTP API
  │
  └─ 后端：Spring Boot 2.4.4 + Spring MVC + MyBatis
             │
             ├─ Action/Controller：接收 HTTP 请求
             ├─ Service：业务与角色校验
             ├─ Mapper + XML：执行 SQL
             ├─ MySQL 8.0：保存业务数据
             └─ uploads：保存图片、课件、视频和临时分片
```

以留言板为例，完整调用链如下：

```text
MessageBoard.vue
  → apis/messageAPI.js
  → utils/http.js
  → MessageAction.java
  → MessageServiceImpl.java
  → MessageMapper / ReplyMapper / UserMapper
  → mapper/*.xml
  → MySQL
```

## 主要技术版本

| 部分 | 技术/版本 |
| --- | --- |
| 前端框架 | Vue 3.5、Vue Router 4.5、Pinia 3 |
| 前端构建 | Vite 7、npm |
| 前端组件 | Element Plus 2.10、Tailwind CSS 3.4 |
| 后端框架 | Spring Boot 2.4.4 |
| Java | Java 8 |
| 数据访问 | MyBatis 2.1.4、PageHelper 1.4.1 |
| 数据库 | MySQL 8.0；交接 SQL 来源版本为 8.0.43 |
| 后端构建 | Maven Wrapper 3.9.10 |
| 部署包 | WAR；交接目录中存在历史 `ROOT.war` |
| Servlet 容器 | Tomcat 9（外置部署方式需要进一步确认） |

## 目录结构

```text
ganlu_webpage/
├─ frontend/                     # Vue 前端
│  ├─ src/
│  │  ├─ apis/                   # 前端 API 封装
│  │  ├─ components/             # 通用组件
│  │  ├─ layouts/                # 页面布局
│  │  ├─ router/                 # 路由配置
│  │  ├─ stores/                 # Pinia 状态
│  │  ├─ utils/                  # HTTP、路径、日期、权限工具
│  │  └─ views/                  # 页面
│  ├─ public/                    # 静态资源
│  ├─ dist/                      # 历史生产构建产物
│  ├─ .env.development           # 开发环境前端变量
│  ├─ .env.production            # 历史生产环境前端变量
│  └─ package.json
├─ backend/                      # Spring Boot 后端
│  ├─ src/main/java/com/vihu/ganlu/
│  │  ├─ actions/                # HTTP 接口层
│  │  ├─ service/                # 业务层
│  │  ├─ mappers/                # MyBatis Mapper 接口
│  │  ├─ entitys/                # 实体类
│  │  ├─ configs/                # CORS、静态文件映射
│  │  └─ utils/                  # 返回值与文件存储工具
│  ├─ src/main/resources/
│  │  ├─ mapper/                 # MyBatis SQL XML
│  │  ├─ application.properties
│  │  ├─ application-dev.properties
│  │  └─ application-prod.properties
│  ├─ uploads/                   # 交接的上传文件副本
│  ├─ target/                    # 历史后端构建产物
│  ├─ mvnw.cmd                   # Windows Maven Wrapper
│  └─ pom.xml
└─ ganlu.sql                     # 数据库表结构，不含业务数据
```

## 功能模块

| 模块 | 前端位置 | 后端入口 | 数据表/存储 |
| --- | --- | --- | --- |
| 用户与团队 | `views/Login.vue`、`ManageUser.vue`、`ManageStudents.vue` | `UserAction.java` | `user`、`team` |
| 课件资料 | `ShowMaterials.vue`、`MaterialDetail.vue`、`MaterialManage.vue` | `CourseDetailAction.java` | `course`、`course_detail`、`uploads/materials` |
| 团队风采 | `FengCai.vue`、`FengCaiDetail.vue` | `FengCaiAction.java` | `team_page*`、`uploads/images` |
| 留言与回复 | `MessageBoard.vue` | `MessageAction.java` | `message`、`reply` |
| 新闻 | `ManageNews.vue` | `NewsAction.java` | `news` |
| 轮播图 | `BannerManagement.vue` | `BannerAction.java` | `banner`、`uploads/images` |

代码中的角色编号约定为：

- `0`：系统管理员，可使用管理员功能，并可发布、回复、删除留言；
- `1`：甘露团队账号，可管理学生账号和课件，可管理自己团队的风采内容，并可发布、回复、删除留言；
- `2`：学生账号，可发布和回复留言，但不能删除留言或使用管理功能。

完整权限矩阵以 `docs/任务分工/00-总览与协作约定.md` 的“统一业务口径”为准。学生账号注册为公开接口；所有后端管理接口必须根据 Bearer Token 再次校验角色，不能只依赖前端隐藏按钮。

## 本地环境要求（Windows 11）

推荐安装：

- Node.js 24 LTS x64（含 npm）；
- Eclipse Temurin JDK 8 x64，并正确设置 `JAVA_HOME` 和 `PATH`；
- MySQL Community Server 8.0.x；
- MySQL Workbench；
- VS Code；推荐扩展：Vue - Official、Extension Pack for Java、Tailwind CSS IntelliSense；
- Git；
- Bruno 或 Postman，用于调试 API；
- Apache Tomcat 9（仅在本机模拟正式 WAR 部署时需要）。

项目已经包含 `backend/mvnw.cmd`，通常不需要单独安装 Maven。

## 初始化本地数据库

`ganlu.sql` 只包含 11 张表的结构，没有 `CREATE DATABASE`、`USE` 或业务数据。应先创建数据库，再导入 SQL。

### 使用 MySQL Workbench

1. 使用安装 MySQL 时设置的 `root` 密码连接本机实例；
2. 创建 Schema，名称为 `ganlu`，字符集选择 `utf8mb4`；
3. 打开根目录的 `ganlu.sql`；
4. 确认默认 Schema 为 `ganlu`；
5. 执行全部 SQL；
6. 确认已生成 11 张表。

建议创建仅供本地应用使用的数据库账号，不要让网站长期使用 `root`：

```sql
CREATE USER 'ganlu_local'@'localhost' IDENTIFIED BY '<请替换为本地强密码>';
GRANT SELECT, INSERT, UPDATE, DELETE ON ganlu.* TO 'ganlu_local'@'localhost';
FLUSH PRIVILEGES;
```

## 启动后端

项目默认启用 `dev` Profile，连接 `127.0.0.1:3306/ganlu`，HTTP 端口默认为 `8080`。不要继续使用源码中保存的数据库密码，建议在当前 PowerShell 会话中覆盖配置：

```powershell
cd backend
$env:SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/ganlu?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
$env:SPRING_DATASOURCE_USERNAME="ganlu_local"
$env:SPRING_DATASOURCE_PASSWORD="<本地数据库密码>"
.\mvnw.cmd spring-boot:run
```

后端启动后可检查：

```text
http://localhost:8080/user/hello
```

开发环境上传目录由 `${user.dir}\uploads` 决定。应从 `backend` 目录启动后端，以便使用 `backend/uploads`。

## 启动前端

新开一个 PowerShell 窗口：

```powershell
cd frontend
npm ci
npm run dev
```

本地访问地址：

```text
http://localhost:5173/
```

开发模式 API 地址应为：

```text
http://localhost:8080/
```

## 构建

### 前端

```powershell
cd frontend
npm ci
npm run build
```

默认输出到 `frontend/dist`。

### 后端

```powershell
cd backend
.\mvnw.cmd clean package
```

默认生成 WAR 包到 `backend/target`。交接文件中存在历史 `ROOT.war`，但当前 `pom.xml` 没有明确配置 `ROOT` 作为最终名称，因此正式发布前必须确认原开发者使用的重命名或部署命令。

## 历史生产部署线索

以下信息来自交接源码和历史构建，不应直接视为仍然有效的正式运维文档：

| 项目 | 历史配置 |
| --- | --- |
| 前端地址 | `http://47.95.209.65/` |
| 后端 API | `http://47.95.209.65:8080/` |
| MySQL | `47.95.209.65:3306/ganlu` |
| 前端服务器 | Ubuntu + Nginx 1.18.0（历史实测响应头） |
| 后端部署 | 很可能为外置 Tomcat 9 + `ROOT.war` |
| 生产上传目录 | `/usr/share/ganlu/uploads`，可由 `UPLOAD_DIR` 覆盖 |
| HTTPS/域名 | 源码中未发现可用域名或完整 HTTPS 配置 |

推测的生产流量路径：

```text
浏览器 :80
  → Nginx 提供 frontend/dist
  → 前端直接请求 :8080
  → Tomcat 中的 ROOT.war
  → MySQL + /usr/share/ganlu/uploads
```

生产部署前必须确认：

- 云服务器归属、实例 ID、地域和到期日期；
- Nginx 站点配置及 Vue History 路由回退；
- Tomcat 版本、目录、服务名、`server.xml` 和日志路径；
- `SPRING_PROFILES_ACTIVE=prod` 的实际设置方式；
- `ROOT.war` 的构建、发布、重启和回滚命令；
- 数据库及上传目录的完整备份；
- 域名、DNS、备案和 SSL 证书账号。

## 端口占用说明

| 服务 | 默认端口 |
| --- | --- |
| Vite 前端开发服务器 | `5173` |
| Spring Boot 后端 | `8080` |
| Tomcat 9 | `8080` |
| MySQL | `3306` |
| MySQL X Protocol | `33060` |

Spring Boot 与 Tomcat 默认都会占用 `8080`，本地开发时只能同时启动其中一个，或者修改其中一方端口。

## 安全注意事项

当前交接版本存在需要优先处理的安全问题：

1. 配置文件包含明文数据库凭据。README 不复述这些值；应立即轮换并改为环境变量或外部配置；
2. 历史生产数据库使用高权限账号。应创建最小权限应用账号，并限制 `3306` 只能由受信任主机访问；
3. 用户密码当前以明文方式查询和保存，应迁移到 BCrypt 或 Argon2，并安排密码重置；
4. 项目引入了 JWT 依赖，但没有形成完整的服务端身份验证流程；部分权限判断依赖请求中传入的用户 ID，存在越权风险；
5. 历史站点仅使用 HTTP。重新上线前应配置域名、HTTPS 和安全响应头；
6. Spring Boot 2.4.4、Fastjson 1.2.54、Nginx 1.18.0 等版本较旧，应在完成备份和回归测试后安排升级；
7. 上传目录中包含用户文件和临时分片，应校验文件类型、大小、路径和访问权限，并建立备份与清理机制。

## 已知配置问题

- `application.properties` 默认固定启用 `dev` Profile，生产环境必须显式覆盖；
- `application-prod.properties` 中存在 `server.port=-1`，与当前后端 `:8080` 的历史部署不一致，需核实外置 Tomcat 配置；
- 前端 API 地址同时存在于 `.env.*` 和 `src/utils/http.js`，容易出现环境配置不一致；
- `MaterialDetail.vue` 中仍有硬编码的 `http://localhost:8080/...`；
- `App.vue` 引用了 `AdminLayout`，但当前没有对应导入；
- 前端页面标题仍为默认的 `Vite App`；
- `ganlu.sql` 不含业务数据，本地导入后登录、新闻、留言等内容为空；
- 当前交接目录包含 `node_modules`、`dist`、`target` 和 `uploads`，不应把这些内容直接提交到新的源码仓库。

## 常见问题

### 前端能打开，但没有数据

确认后端已经启动、MySQL 中存在 `ganlu` 表结构，并检查浏览器开发者工具中的 API 请求是否指向 `http://localhost:8080/`。

### 后端提示无法连接数据库

确认 Windows 服务 `MySQL80` 已启动，检查数据库名、端口、用户名和当前 PowerShell 中的环境变量。

### 端口 8080 被占用

检查是否同时启动了 Tomcat 和 Spring Boot。日常源码开发使用 Spring Boot；测试 WAR 时停止 Spring Boot 后再启动 Tomcat。

### 页面直接刷新后出现 404

Vue Router 使用 History 模式。生产 Nginx 必须将不存在的静态路径回退到 `index.html`。

## 交接完成标准

在认为项目已经完成接管前，至少应具备：

- 可用的 Git 仓库及完整历史；
- 可以在新电脑上重复执行的构建步骤；
- 数据库全量备份及经过验证的恢复步骤；
- 上传文件全量备份；
- 云服务器、域名、DNS 和证书控制权；
- Nginx、Tomcat、环境变量和服务配置备份；
- 新的生产数据库账号及已经轮换的密码；
- 管理员账号创建/重置流程；
- 日志、监控、告警、定期备份与回滚方案；
- 已知问题清单和最基本的验收测试。

## 许可证

本项目采用 [MIT License](LICENSE) 开源。
