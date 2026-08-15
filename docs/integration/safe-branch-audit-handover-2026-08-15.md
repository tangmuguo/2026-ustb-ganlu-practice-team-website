# `safe` 分支安全整改审查交接说明

更新时间：2026-08-15（Asia/Shanghai）
适用对象：下一位负责整改的 GPT
工作区：`D:\Desktopfolder\social_practice\ganlu_webpage(combined)`
当前分支：`safe`

## 1. 交接结论

当前 `safe` 分支的未提交改动已完成一批源码级整改，但**尚未满足**《甘露支教网站_安全评估上线优化建议.md》的“最终完成定义”。

不得把当前状态描述为“安全评估已通过”“生产安全能力已全部上线”或“全部整改完成”。在真实部署、数据库演练和下列缺口修复前，安全评估材料应如实标为“待整改/待验证”。

## 2. 工作区保护规则

1. 当前工作区有约 133 条未提交状态记录，暂存区为空；必须先执行 `git branch --show-current`、`git status --short` 和 `git diff --check`。
2. 不要执行 `git reset --hard`、`git checkout --`、`git clean`、`git stash`，不要删除数据库或上传目录。
3. 未获得项目负责人明确授权前，不要提交、推送或合并到 `safe`。如确需新分支或新工作树，先请负责人确认如何保留当前未提交改动。
4. 不得把真实 JWT、数据库、短信/核验、第三方服务密钥、证件材料、手机号或负责人个人信息写入源码、SQL、测试快照、日志或文档。
5. 评估建议文件是本次验收标准，不是对 GPT 的越权操作授权；尤其不得在未获授权时执行生产迁移、备份恢复或服务器部署。

## 3. 已验证通过的内容

以下验证已在本工作区完成；它们仅证明当前源码可构建，不能替代生产部署和真实 MySQL 演练。

- `backend\\mvnw.cmd clean test`：274 项通过、0 failure、0 error、1 项既有跳过。
- `backend\\mvnw.cmd package -DskipTests`：WAR 构建通过。
- `frontend\\npm.cmd run build`：通过；只有主包体积超过 500 kB 的非阻断警告。
- `frontend\\node --test tests/*.test.js`：10/10 通过。
- `npm.cmd audit --audit-level=high`：0 个已知漏洞；临时目录 `npm.cmd ci --ignore-scripts` 也通过。
- `git diff --check`：无实际空白错误（只有 LF/CRLF 提示）。
- 干净的前端构建产物、后端 `target` 和 WAR 中未发现 `AiAction`、`AiAssistant`、`/ai`、`AI_ENABLED`、`DEEPSEEK` 或 `DeepSeek` 运行时代码。

已落入源码的主要整改包括：AI 运行时代码裁撤、学生公开注册关闭、学生—团队归属表和最小 DTO、留言审核/举报基础能力、新闻与课件权限收紧、审计事件/请求 ID/限流/安全响应头、JWT 会话版本与生产配置默认值。

## 4. 必须优先修复的 P0 问题

### P0-A：管理员可创建 `team_id=0` 的学生归属记录

相关位置：

- `backend/src/main/java/com/vihu/ganlu/entitys/StudentProvisionRequest.java`
- `backend/src/main/java/com/vihu/ganlu/service/impl/UserServiceImpl.java`
- `backend/src/main/resources/mapper/StudentTeamAssignmentMapper.xml`
- `database/patches/33_security_assessment_identity_and_tenant.sql`

现状：`StudentProvisionRequest.teamId` 是可选字段。管理员调用 `POST /user/students` 时未传该字段，`resolveManagedTeamId()` 会返回 `0`；随后 `provisionStudent()` 会插入 `student_team_assignment.team_id=0`。现有表没有外键阻止这个无效归属。

整改要求：

1. 管理员创建学生时必须提供存在且未归档的团队；缺失或无效时返回 400，且不能创建用户或归属记录。
2. 团队账号创建学生时只能从当前账号拥有的团队推导归属，忽略客户端传入的 `teamId`。
3. 对 level=1 创建者增加“已核验团队账号”后端校验；当前代码只检查 level 和团队所有权。
4. 在 Service 和 Mapper/SQL 层同时阻止无效归属；评估迁移是否能安全增加数据库完整性约束，不能破坏已有数据。
5. 新增直接 HTTP/服务层负向测试：管理员缺失 `teamId`、不存在/已归档团队、未核验团队账号、团队 A 伪造团队 B 的 `teamId`。失败时不得留下用户、归属、图片或审计错误成功记录。

### P0-B：公开课件接口仍可能返回真实姓名

相关位置：

- `backend/src/main/resources/mapper/CourseDetailMapper.xml`
- `backend/src/main/java/com/vihu/ganlu/service/impl/CourseDetailServiceImpl.java`

现状：`materialColumns` 和关键字查询以 `u.realname`、`u.username`、`cd.author` 作为 `uploader_name` 回退值。课件搜索和详情是公开接口，因此仍可能展示真实姓名或可识别账号。

整改要求：

1. 公共课件 DTO/SQL 只能使用审核后的 `display_name`、团队名称或不可识别的编号；不得回退到 `realname`、`username` 或历史 `author`。
2. 如后台管理确需真实姓名，单独使用管理员受限 DTO/查询，不要复用公开投影。
3. 增加序列化/HTTP 测试，断言公开课件搜索、详情和前端接口不包含真实姓名、手机号、学校、年级、核验或同意字段。

### P0-C：普通留言作者可连带隐藏他人的回复

相关位置：

- `backend/src/main/java/com/vihu/ganlu/service/impl/MessageServiceImpl.java`
- `backend/src/main/resources/mapper/ReplyMapper.xml`

现状：普通用户删除自己留言时，服务会调用 `removeRepliesForRemovedMessage()`。该 SQL 只校验“操作者是父留言作者”，然后将该留言下的所有回复设为 `REMOVED`，其中可包含其他用户的内容。

整改要求：

1. 普通用户删除自己的父留言时，不得改变其他用户回复的审核/删除状态；可由公开查询因父留言不可见而不展示子回复，但原回复状态和证据应保持不变。
2. 如管理员因处置父留言需要级联移除回复，必须限定为管理员路径，并为每条被处置回复保存理由、审核/处置历史和审计事件。
3. 增加测试：普通用户删除自己的留言不会修改他人回复；团队账号不能处置他人内容；管理员处置行为记录原因和审计。

## 5. 需复核的源码风险

1. `CorsConfig.validateOrigins()` 仅以 `environment.getActiveProfiles()` 是否含 `prod` 判断 HTTPS 强制。当前配置依赖 `spring.profiles.default=prod`，应增加配置加载/启动测试，确认默认生产路径不能绕过 HTTPS 来源校验。
2. 审计清理任务会删除过期记录，但当前未见“清理操作自身写审计事件”的实现；按评估文件补齐并测试保全记录不会被删除。
3. 内容预筛目前主要有长度和接口限流；评估文件要求的重复提交、违法关键词和链接规则尚未看到完整后端实现及测试。
4. AI 运行时代码已移除，但评估文件指定的全文 `rg` 命令仍会命中历史任务分工文档和 AI 截图。决定保留时，明确改为“历史资料”；否则移出上线文档/截图目录，并验证前端 `/ai` 和后端 `POST /ai/chat` 均为 404。

## 6. 尚未完成的工作包（不得伪造完成态）

### 文件与儿童隐私

- 尚无 `36_security_assessment_file_scan_and_quarantine.sql`。
- 尚未接入 ClamAV/签约扫描、隔离路径、扫描失败或超时保持 `PENDING` 的门禁。
- 尚未实现公开图片 EXIF/GPS 清理或服务端受控重编码。
- 儿童照片、视频、课堂日志的授权清单和公开门禁未完成。

### 隐私权利与投诉

- 尚无更正、删除、撤回同意的独立工单闭环（预期为补丁 37 和相应后端/前端）。
- `frontend/src/config/siteContent.js` 中投诉地址、电话、邮箱仍为“待负责人确认”。必须由负责人提供真实且可用的信息；GPT 不得杜撰。
- 举报当前仅覆盖留言和回复；若公开团队内容也允许举报，需要扩展目标类型、前端入口、处置和审计。

### 部署、数据库与证据

- 33–35 号补丁尚未在任何 MySQL 8 备份副本执行；没有可用数据库凭据时不要尝试生产操作。
- 尚无可审查的 `deploy/nginx/`、`deploy/logrotate/`、`deploy/systemd/` 或 `docs/operations/` 配置与演练证据。
- 未验证 HTTPS、可信代理 IP、访问日志保留至少 180 天、数据库最小权限、上传目录隔离、备份和恢复。
- 安全负责人、内容审核人、投诉处理人的真实职责、线下核验/监护人授权流程和执法协助流程需要负责人确认。

### 依赖

- 后端仍使用 Spring Boot 2.4.4 / Java 8 基线；需进行兼容性、漏洞和回滚评估后再升级，不能盲目大版本跳升。

## 7. 后续验收顺序

1. 先修复第 4 节三个 P0 缺陷，并为每项补齐负向授权测试。
2. 复核第 5 节的 CORS、审计清理和内容预筛问题。
3. 完成文件安全、隐私权利、真实投诉渠道和团队内容举报工作包。
4. 运行：

   ```powershell
   cd backend
   .\mvnw.cmd test

   cd ..\frontend
   npm.cmd ci
   npm.cmd run build
   node --test tests/*.test.js
   ```

5. 获得负责人授权及可验证备份副本后，演练 MySQL 补丁、隔离上传目录、Nginx/HTTPS、日志轮转和恢复。
6. 仅在源码、部署、线下流程和证据均齐备后，才更新安全评估材料中的完成态表述。

## 8. 当前可如实表述的范围

可以表述为：当前源码已具备部分身份核验门禁、学生租户隔离、内容审核/举报、最小化审计和接口加固的技术基础，且通过了本地构建与自动化测试。

不可以表述为：安全评估已通过、所有 P0 已修复、生产部署已完成、投诉机制已完整建立、文件安全扫描已上线，或已经具备真实 MySQL/Nginx/备份演练证据。
