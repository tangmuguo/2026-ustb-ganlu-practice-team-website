# 课件中心接口、部署与联调说明

负责人：肖约宝
模块分支：`feature/material-center`

## 1. 可直接转发给赵友为的合并清单

请在总集成时处理或保留以下共享项：

1. 保留现有路由：公开页 `/showm`、详情 `/mdetail/:id`；`/uppt` 和 `/mmanage` 仅允许 `roles: [0, 1]`。
2. 顶部用户菜单中，`level=0/1` 均显示“课件上传、课件管理”；学生不显示。
3. 将 `frontend/src/utils/http.js` 的 `baseURL` 统一改为 `import.meta.env.VITE_API_BASE_URL`，不要保留生产 IP 硬编码。
4. 保留 `/images/** -> ${file.upload-dir}/images/`，并把课件静态映射收窄为 `/materials/previews/** -> ${file.upload-dir}/materials/previews/`。不要继续公开整个 `materials/` 目录；原文件存放在 `${file.upload-dir}/protected/materials/`，禁止增加公开映射。
5. 把 `backend/src/main/resources/application-material.properties.example` 中两个 `material.libreoffice.*` 配置键合并到环境配置模板。
6. 在基线 `ganlu.sql` 导入后执行 `database/patches/30_material_center.sql`；全模块联调通过后再把表结构合并回根 SQL，不要用旧 SQL 覆盖补丁。
7. 生产服务器安装 LibreOffice，并通过环境变量 `LIBREOFFICE_EXECUTABLE` 指向 `soffice`；确认 Tomcat/Java 服务账号对上传目录有读写权限。

课件模块自身不直接修改 `router/index.js`、`Top.vue`、`http.js`、`path.js`、共享 Properties 或根 `ganlu.sql`。

## 2. API 合同

所有 JSON 接口统一返回：

```json
{
  "code": 200,
  "message": "操作结果",
  "content": {}
}
```

| 方法 | 地址 | 权限 | 用途 |
|---|---|---|---|
| GET | `/courseDetail/materials` | 公开 | 分页搜索，参数为 `keyword/courseType/courseId/year/page/pageSize` |
| GET | `/courseDetail/materials/{id}` | 公开 | 课件元数据和预览地址，不返回原文件路径 |
| POST | `/courseDetail/checkFileExist` | `0/1` | 查询当前账号已经上传的分片 |
| POST | `/courseDetail/uploadChunk` | `0/1` | 上传 5MB 分片 |
| POST | `/courseDetail/mergeChunks` | `0/1` | 合并并校验 MD5、大小、扩展名、文件头 |
| POST | `/courseDetail/materials` | `0/1` | 使用两个暂存 Token 创建课件记录 |
| DELETE | `/courseDetail/materials/{id}` | `0/1` | 逻辑删除记录并清理原文件、预览和封面 |
| GET | `/courseDetail/materials/{id}/download` | `0/1/2` | 登录后下载原文件 |
| GET | `/courseCategory/list` | 公开 | 启用的通识科目 |
| GET | `/courseCategory/manage` | `0` | 包含停用项的科目管理列表 |
| POST | `/courseCategory` | `0` | 新增科目；启用项最多 12 个 |
| PUT | `/courseCategory/{id}` | `0` | 改名、启用或停用 |

上传分片表单字段：`file`、`chunkNumber`、`totalChunks`、`identifier`、`filename`、`expectedSize`、`purpose`。`purpose` 只能是 `COVER` 或 `MATERIAL`。

创建课件示例：

```json
{
  "title": "小学数学活动课",
  "courseType": 1,
  "courseId": 2,
  "customSubject": null,
  "year": 2026,
  "coverToken": "服务端合并封面后返回的 UUID",
  "fileToken": "服务端合并课件后返回的 UUID"
}
```

前端不得提交可信 `author` 或 `userId`。后端从 Bearer Token 中取得上传者。

## 3. 文件和权限模型

```text
${file.upload-dir}/
├─ images/materials/       # 公开封面
├─ materials/previews/     # 公开 PDF/图片预览
├─ protected/materials/    # 原文件，只能通过鉴权下载接口读取
├─ temp_chunks/<userId>/   # 上传分片
├─ staging/materials/      # 合并完成、等待提交表单的暂存文件
└─ office-work/            # LibreOffice 唯一临时工作目录
```

- 游客只能读取元数据、封面和预览。
- `level=0/1/2` 登录后可下载原文件。
- `level=0/1` 可上传、删除、管理任意课件。
- 只有 `level=0` 可维护通识科目。
- 删除使用数据库逻辑删除；磁盘文件不存在时记录警告但不让接口崩溃。

## 4. Windows 11 本地配置

需要 Git、Node.js 22.12+、Temurin JDK 8、MySQL 8、LibreOffice 64 位。项目根目录不保存真实密码。

确认软件：

```powershell
git --version
node --version
java -version
mysql --version
& 'C:\Program Files\LibreOffice\program\soffice.exe' --version
```

从三个 `.example` 文件复制本地 Properties 文件，并在当前 PowerShell 窗口设置：

```powershell
$env:GANLU_DB_URL='jdbc:mysql://127.0.0.1:3306/ganlu?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:GANLU_DB_USERNAME='ganlu_local'
$env:GANLU_DB_PASSWORD='<本地数据库密码>'
$env:GANLU_JWT_SECRET='<至少32字节的本地随机密钥>'
$env:LIBREOFFICE_EXECUTABLE='C:\Program Files\LibreOffice\program\soffice.exe'
```

数据库执行顺序：

1. 创建空 Schema `ganlu`，字符集 `utf8mb4`。
2. 导入根目录 `ganlu.sql`。
3. 执行 `database/patches/30_material_center.sql`。
4. 创建管理员、团队、学生测试账号；不要把账号密码提交到仓库。

启动：

```powershell
cd frontend
npm ci
npm run dev

cd ..\backend
.\mvnw.cmd spring-boot:run
```

## 5. Linux/服务器部署要点

1. 安装 LibreOffice，并确认 `soffice --headless --version` 可由 Java 服务账号执行。
2. 设置 `LIBREOFFICE_EXECUTABLE=/usr/bin/soffice`（以服务器实际路径为准）。
3. 为 `${UPLOAD_DIR}` 创建上述子目录，目录所有者应是 Tomcat/Java 服务账号，最小权限建议目录 `750`、文件 `640`。
4. 不要通过 Nginx 或 Spring 静态映射公开 `protected/`、`staging/`、`temp_chunks/`、`office-work/`。
5. 发布前备份数据库和整个上传目录；部署后分别测试 PDF、PPTX、PNG 上传与下载。

## 6. 回滚

1. 发布前备份数据库和上传目录。
2. 应用回滚时恢复上一版 WAR/前端构建物，并停止新的课件写入。
3. SQL 补丁只增加列和索引，不提供自动 DROP 回滚，避免破坏新数据；如必须回退表结构，由数据库管理员在完整备份后人工处理。
4. 新增文件位于 `images/materials`、`materials/previews`、`protected/materials`，回滚前先备份，不要直接递归删除整个上传根目录。

## 7. 手工测试记录模板

完成真实环境后填写“实际结果”和截图/响应编号：

| 编号 | 角色 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| M-01 | 管理员 | 上传 PDF、PPTX、PNG 各一份 | 三次成功；PPTX 显示转换 PDF | 待测 |
| M-02 | 团队 | 上传课件并删除另一账号上传的课件 | 均成功 | 待测 |
| M-03 | 学生/游客 | 打开上传或管理入口 | 页面不可进入；接口为 403/401 | 待测 |
| M-04 | 游客/学生 | 点击下载 | 游客去登录；学生下载成功 | 待测 |
| M-05 | 管理员 | 新增第 13 个启用科目 | 后端拒绝并提示上限 | 待测 |
| M-06 | 游客 | 按关键词、科目、类型、年份筛选 | 结果与分页总数正确 | 待测 |
| M-07 | 团队 | 上传 DOC、MP4、伪造 MIME 的文件 | 前后端均拒绝 | 待测 |
| M-08 | 团队 | 上传大于 50MB 的合法文件，中断后重试 | 显示进度并续传；MD5/大小一致 | 待测 |
| M-09 | 管理员 | 删除缺少某个磁盘文件的课件 | 记录删除成功，不发生 500 | 待测 |
| M-10 | 运维环境 | 暂时配置错误的 soffice 路径后上传 PPTX | 原文件保留，预览状态为失败，可下载 | 待测 |
