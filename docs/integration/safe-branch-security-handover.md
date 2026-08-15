# `safe` 分支安全整改交接文档

更新时间：2026-08-15（Asia/Shanghai）  
当前工作分支：`safe`  
提交状态：**未提交、未推送、未部署**。后续接手前先执行 `git status --short`，不要覆盖当前未提交改动。

## 1. 本节点已完成的源码整改

以下是已落入源码、且已经过本地自动测试的代码级能力。它们不等同于生产已经部署或安全评估已经通过。

- 完整裁撤 AI 小助手：前后端路由、页面、接口、配置、依赖和专项测试已删除；当前源码中没有 `/ai`、`AI_ENABLED`、`DEEPSEEK`、Pinia 持久化 Token 或公开学生注册入口。
- 默认使用生产配置；JWT 不再有仓库内开发回退密钥，访问令牌默认 30 分钟，令牌带 `session_version`，退出和学生核验状态变更会使旧令牌失效。
- 关闭学生自主注册。管理员或已绑定团队只能创建其可管理范围内的学生；学生—团队归属成为服务层和 SQL 层共同使用的权限事实来源。
- 学生核验/监护人授权字段和留痕已加入；未完成核验和授权的学生不能发布留言或回复。
- 留言和回复默认 `PENDING`，公开列表仅返回 `APPROVED`；管理员可审核、移除、查看待审队列并处理举报工单；公开对象不会返回学生真实姓名、手机号、学校或年级。
- 新闻管理仅限管理员；课件删除同时受 Controller、Service、Mapper 的所有者/管理员约束。
- 新增最小化审计事件、保全标记和 180 天保留策略；账号、学生、内容、举报、课件、新闻、审计导出和鉴权拒绝会写结构化事件，不记录请求体、密码、Token 或 Cookie。
- 新增请求 ID、限流、受控错误响应、安全响应头和基础 CSP。生产 CORS 现要求显式配置 `GANLU_ALLOWED_ORIGINS`，不得使用通配符。
- 页面已加入隐私与未成年人保护说明、内容审核/举报后台和 ICP 备案号 `苏ICP备2026055810号-1`；公安联网备案尚未出结果，页面没有伪造相关备案号。

## 2. 关键新增/修改文件

请先阅读下列文件，再改对应模块：

| 范围 | 主要位置 |
| --- | --- |
| 身份、租户与会话 | `backend/src/main/java/com/vihu/ganlu/actions/UserAction.java`、`service/impl/UserServiceImpl.java`、`mappers/UserMapper.java`、`mappers/StudentTeamAssignmentMapper.java`、`database/patches/33_security_assessment_identity_and_tenant.sql` |
| 内容审核与举报 | `backend/src/main/java/com/vihu/ganlu/actions/MessageAction.java`、`ContentReportAction.java`、`service/impl/MessageServiceImpl.java`、`service/ContentReportService.java`、`database/patches/34_security_assessment_content_moderation.sql`、`35_security_assessment_audit_and_reports.sql` |
| 审计与安全过滤器 | `backend/src/main/java/com/vihu/ganlu/audit/`、`service/AuditEventService.java`、`configs/AuditContextFilter.java`、`SensitiveEndpointRateLimitFilter.java`、`SecurityResponseHeadersFilter.java` |
| 权限收紧 | `CourseDetailAction.java`、`service/impl/CourseDetailServiceImpl.java`、`NewsAction.java`、`service/impl/NewsServiceImpl.java` 及对应 Mapper XML |
| 前端安全界面 | `frontend/src/views/ContentSafety.vue`、`PrivacyPolicy.vue`、`MessageBoard.vue`、`ManageStudents.vue`、`apis/contentSafetyAPI.js` |
| 配置/运行说明 | `backend/src/main/resources/application*.properties`、`database/patches/README.md`、`README.md` |

数据库补丁固定顺序现在是：

```text
00 → 10 → 11 → 12 → 13 → 14 → 15 → 20 → 30 → 31 → 32 → 33 → 34 → 35
```

补丁 33–35 只增不删、可重复运行。**没有在任何 MySQL 实例上执行它们。**

## 3. 已完成的验证

最近一次验证命令：

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm.cmd run build
node --test tests/*.test.js
```

结果：

- 后端：274 项测试通过，1 项既有跳过；
- 前端：生产构建通过，10 项前端测试通过；
- `npm.cmd install --package-lock-only --ignore-scripts` 通过，报告 0 个已知漏洞；
- 静态检查确认活跃源码中不存在 AI、持久化 Token 插件或学生公开注册引用。

测试日志会包含刻意构造的异常/损坏 PPTX 场景日志，这是负向测试预期，不是本轮失败。

## 4. 不得误报为已完成的事项

以下事项仍需要真实部署负责人或后续工作包完成，安全评估材料必须如实写“待整改/待验证”：

1. 在 MySQL 8 备份副本执行并恢复演练 33–35 补丁；
2. Nginx HTTPS、可信代理 IP、访问日志轮转、至少 180 天日志保留、数据库最小权限、上传目录隔离和备份恢复；
3. ClamAV/签约扫描服务、上传隔离和扫描失败保持 `PENDING`；图片 EXIF/GPS 清理或受控重编码；
4. 安全负责人、内容审核人、投诉处理人的真实姓名、职责、受理时段和联系方式；
5. 线下身份核验和监护人授权的实际流程、脱敏证据样本及责任人；
6. 个人资料更正/删除/撤回授权的独立工单闭环；
7. 依赖升级评估（当前仍是 Java 8 / Spring Boot 2.4 基线）。

不要填入不存在的公安联网备案号、电话、邮箱、地址或负责人信息；ICP备案号可以继续保留在页脚。

## 5. 推荐的并行工作包（避免文件冲突）

下表按“一个 GPT 一个工作树/分支”设计。每个 GPT 只改自己拥有的文件；若必须触及共享入口，先向总集成 GPT 报告，再由总集成统一合并。

| 工作包 | 目标 | 独占文件/目录 | 禁止并行修改 |
| --- | --- | --- | --- |
| A：上传扫描与图片隐私 | 接入可配置的恶意文件扫描、隔离路径、扫描失败门禁；对公开图片重编码或剥离 EXIF；补 `36_security_assessment_file_scan_and_quarantine.sql` | 新建 `backend/src/main/java/com/vihu/ganlu/security/file/`、`backend/src/test/java/com/vihu/ganlu/security/file/`、`database/patches/36_*`、`docs/integration/file-safety.md` | 不改 33–35、审计 Mapper、学生/内容审核前端。需要接入上传入口时，仅由 A 修改上传相关 Action/Service。 |
| B：隐私权利工单 | 新增更正、删除、撤回同意请求及管理员处理闭环，日志/保全规则明确 | 新建 `database/patches/37_security_assessment_privacy_requests.sql`、`backend/.../entitys/privacy/`、`mappers/PrivacyRequest*`、`actions/PrivacyRequestAction.java`、`service/PrivacyRequestService.java`、`frontend/src/views/PrivacyRequests.vue`、`frontend/src/apis/privacyRequestAPI.js` | 不改 `UserAction.java`、`UserServiceImpl.java`、`PrivacyPolicy.vue`；由总集成在最后接路由/菜单。 |
| C：部署与证据 | 产出可审查的 Nginx、logrotate、systemd/环境变量、备份恢复和演练清单；由有服务器权限的人实际执行 | 新建 `deploy/nginx/`、`deploy/logrotate/`、`deploy/systemd/`、`docs/operations/` | 不改 Java、Vue、SQL。不能把真实域名、证书、密钥、数据库密码或个人联系方式写入仓库。 |
| D：安全测试与独立审查 | 增加/执行黑盒 HTTP 授权、审计脱敏、限流、审核、举报和 AI 404 测试；形成只读审查报告 | 仅新增 `backend/src/test/java/com/vihu/ganlu/security/`、`backend/src/test/java/com/vihu/ganlu/actions/` 中的新测试文件、`frontend/tests/security*.test.js`、`docs/integration/security-test-report.md` | 不改生产 Java/Vue/SQL；发现问题用报告和最小复现交给总集成。 |
| E：依赖升级（串行） | 评估并逐步升级 Spring/前端依赖，记录兼容性和回滚 | `backend/pom.xml`、`frontend/package*.json` 及专门兼容测试 | 必须在 A–D 合并稳定后开始；不要与任何前端或后端功能工作包并行。 |

### 总集成 GPT 的合并顺序

1. 先接收 D 的测试/审查报告，不直接改实现；
2. 合并 A 与 B（两者新增文件为主，进入共享入口前先人工处理冲突）；
3. 合并 C 的文档与模板，等待实际运维负责人授权后执行；
4. 运行全部测试、真实 MySQL/隔离上传目录演练；
5. 最后才安排 E 的依赖升级。

## 6. 接手时的必做检查

```powershell
git branch --show-current
git status --short
git diff --check

cd backend
.\mvnw.cmd test

cd ..\frontend
npm.cmd ci
npm.cmd run build
node --test tests/*.test.js
```

- 保留 `safe` 分支的全部未提交改动；不要使用 `git reset --hard`、`git checkout --`、删除数据库或上传目录。
- 不要执行 33–35 补丁，除非用户/实际数据库负责人明确授权且已确认备份副本目标。
- 不要提交或推送，除非用户另行授权。
- 若后续 GPT 修改了同一共享文件，先把该文件的未合并 diff 交给总集成 GPT 决策，避免“最后写入者覆盖”安全修复。

## 7. 对外说明的安全边界

当前可以如实描述为“源码已具备身份核验门禁、学生租户隔离、内容审核/举报、最小化审计和基础接口加固的技术基础”。在真实迁移、部署、负责人流程和运维演练完成前，不能描述为“安全评估已通过”或“生产安全能力已全部上线”。
