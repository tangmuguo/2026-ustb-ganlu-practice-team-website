# 课件中心接口、部署与联调说明

负责人：肖约宝
模块分支：`feature/material-center`

## 1. 可直接转发给赵友为的合并清单

请在总集成时处理或保留以下共享项：

1. 保留现有路由：公开页 `/showm`、详情 `/mdetail/:id`；`/uppt` 和 `/mmanage` 仅允许 `roles: [0, 1]`。
2. 顶部用户菜单中，`level=0/1` 均显示“课件上传、课件管理”；学生不显示。
3. 将 `frontend/src/utils/http.js` 的 `baseURL` 统一改为 `import.meta.env.VITE_API_BASE_URL`，不要保留生产 IP 硬编码。
4. 只保留封面映射 `/images/** -> ${file.upload-dir}/images/`，删除 `/materials/**` 静态映射。原文件和预览分别存放在 `${file.upload-dir}/protected/materials/`、`${file.upload-dir}/protected/material-previews/`，均只能通过鉴权接口读取，禁止增加公开映射。
5. 把 `backend/src/main/resources/application-material.properties.example` 中 `material.libreoffice.*`、`material.upload.*` 和 multipart 配置键合并到环境配置模板。
6. 在基线 `ganlu.sql` 导入后执行 `database/patches/30_material_center.sql`；全模块联调通过后再把表结构合并回根 SQL，不要用旧 SQL 覆盖补丁。
7. 生产服务器安装 LibreOffice，并通过环境变量 `LIBREOFFICE_EXECUTABLE` 指向 `soffice`；确认 Tomcat/Java 服务账号对上传目录有读写权限。
8. 合并 `backend/pom.xml` 时保留 Apache POI `poi-scratchpad` 和 `poi-ooxml` 5.5.1；课件模块依赖它们实际解析 PPT/PPTX，不能退回仅检查文件头、CFB 流名或 ZIP 条目名的实现。

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
| DELETE | `/courseDetail/uploadSession` | `0/1` | 取消分片或暂存上传，参数为 `purpose/identifier/token` |
| POST | `/courseDetail/materials` | `0/1` | 使用两个暂存 Token 创建课件记录 |
| DELETE | `/courseDetail/materials/{id}` | `0/1` | 逻辑删除记录并清理原文件、预览和封面 |
| GET | `/courseDetail/materials/{id}/preview` | `0/1/2` | 登录后读取 PDF/图片/PPT 转换预览 |
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
├─ protected/materials/    # 原文件，只能通过鉴权下载接口读取
├─ protected/material-previews/ # 预览文件，只能通过鉴权预览接口读取
├─ temp_chunks/<userId>/   # 带会话清单、配额和 TTL 的上传分片
├─ staging/materials/      # 带校验索引和 TTL 的待提交暂存文件
└─ office-work/            # LibreOffice 唯一临时工作目录
```

- 游客只能读取课件元数据和封面，不能取得预览或原文件字节。
- `level=0/1/2` 登录后可预览并下载原文件。
- `level=0/1` 可上传、删除、管理任意课件。
- 只有 `level=0` 可维护通识科目。
- 删除使用数据库逻辑删除；磁盘文件不存在时记录警告但不让接口崩溃。
- PPT 使用只读文件型 HSLF，PPTX 使用 `OPCPackage.open(File, READ)`，避免把最大 200MB 的容器整体缓冲到 Java 堆。
- 分片合并时保存暂存文件的已验证大小和最后修改时间；创建课件时状态未变化则复用校验结果，状态变化才重新执行完整内容与 MD5 校验。

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
4. 不要通过 Nginx 或 Spring 静态映射公开 `protected/`、`staging/`、`temp_chunks/`、`office-work/`；课件预览统一走 Bearer Token 鉴权接口。
5. 发布前备份数据库和整个上传目录；部署后分别测试 PDF、PPTX、PNG 上传与下载。

## 6. 回滚

1. 发布前备份数据库和上传目录。
2. 应用回滚时恢复上一版 WAR/前端构建物，并停止新的课件写入。
3. SQL 补丁只增加列和索引，不提供自动 DROP 回滚，避免破坏新数据；如必须回退表结构，由数据库管理员在完整备份后人工处理。
4. 新增文件位于 `images/materials`、`protected/material-previews`、`protected/materials`，回滚前先备份，不要直接递归删除整个上传根目录。

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
| M-11 | 团队 | 点击“保存课件”后尝试取消、关闭、遮罩、Esc，并立即浏览器返回或切换站内路由 | 弹窗四种关闭入口不可用；页面卸载也不发送上传取消请求；成功或失败只有一个确定结果 | 待测 |

M-01～M-11 必须等赵友为完成共享路由、认证、配置、静态资源映射和 SQL 合并后，在最终联调环境中填写；当前不具备真实验收条件，因此不提前填写“通过”。

## 8. PR #11 修改意见验证记录

### 第一轮（2026-08-01）

- 后端 `mvnw.cmd test`：32 项通过，0 失败、0 错误。其中包含真实 MVC 拦截器的游客 401、学生 403、团队/管理员允许访问测试。
- 文件真实性：增加了 DOC/CFB 改名 `.ppt`、普通 ZIP 改名 `.pptx` 的拒绝校验；第二轮进一步替换为可靠解析器，见下方记录。
- 上传临时存储：已覆盖超量分片拒绝、会话参数不可变、断点状态恢复、主动取消无残留、过期分片清理。
- 前端 `npm run build`：通过；保留基线已有的大体积 chunk 与 Browserslist 数据提示。
- MySQL 8.4 隔离实例：基线 SQL 加补丁连续执行两次成功；`course_detail` 为 22 列；`uk_course_name` 为唯一索引；重复插入“语文”返回 MySQL 1062。

### 第二轮（2026-08-02）

- PPT/PPTX 真实性：改用 Apache POI HSLF/XSLF 实际打开演示文稿，并要求至少存在一张可解析幻灯片；伪造 `PowerPoint Document` 流、空 PPTX、缺少幻灯片部件和损坏的幻灯片 XML 均被拒绝。
- 正向样例：测试资源中的 PPT、PPTX 由本机 LibreOffice 26.2.4.2 Impress 从同一份单页演示文稿导出，再交给生产校验器解析；不再用手工拼接的最小容器或同一解析库生成物冒充真实办公文件。
- 保存竞态：保存期间禁用取消按钮、关闭图标、点击遮罩和 Esc；创建成功后先把两个暂存 Token 标记为已消费，再关闭窗口，避免成功记录被异步取消清理。
- 后端 `mvnw.cmd test`：36 项通过，0 失败、0 错误；其中 `MaterialFileValidatorTests` 11 项全部通过。
- 前端 `npm run build`：通过；仍只有基线已有的大体积 chunk 与 Browserslist 数据提示。
- 上表 M-01～M-11 仍等待共享部分集成完成后实测，未把单模块测试结果冒充最终联调验收。

### 第三轮（2026-08-02）

- 大文件内存：旧 PPT 改为 `HSLFSlideShowFactory.create(File, ..., true)`，PPTX 改为 `OPCPackage.open(File, PackageAccess.READ)`；不再把演示文稿作为 `InputStream` 传给 POI。
- 暂存复验：元数据新增 `validatedLastModified`。创建阶段先比较服务器暂存文件的大小和最后修改时间；未变化时直接复用合并阶段的可信校验结果，变化时才重新解析并核对 MD5。自动测试确认未变化不重复调用校验器，修改时间变化会触发复验。
- 路由卸载竞态：两个上传组件在发出创建请求前进入“正在消费”状态；此时即使浏览器返回或路由切换导致卸载，也不调用取消接口。请求失败且组件仍存在时恢复取消能力，成功后清除已消费 Token。
- 默认后端 `mvnw.cmd test`：共 38 项，37 项通过、0 失败、0 错误；1 项为默认关闭的受限堆压测入口。
- 受限堆压测：`mvnw.cmd -Dtest=MaterialFileValidatorMemoryPressureTests -Dmaterial.memory.test=true -DargLine=-Xmx256m test`，接近 200MB 的 PPT 与约 190MB 的 PPTX 共 2 项通过，0 失败、0 错误。
- 前端 `npm run build`：通过；仍只有基线已有的大体积 chunk 与 Browserslist 数据提示。
- M-11 已加入“保存后立即浏览器返回/路由跳转”，实际结果仍须在赵友为完成共享集成后填写。
