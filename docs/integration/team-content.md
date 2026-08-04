# 团队风采内容管理 — 集成文档

## 1. 概述

本模块为甘露网站"团队风采"模块构建完整的内容管理闭环（上传 → 待审核 → 发布/驳回），并新增视频/附件表 `team_media`。

**核心设计原则：**
- 不 DROP 旧表，增量 patch 新增列
- 团队端接口不含 teamId，从 Token 推导（防越权）
- 普通删除 = 逻辑归档（status=ARCHIVED），保留审计且继续占用文件额度；物理 purge 走持久化任务
- 视频/附件强制下载（Content-Disposition: attachment），不在网页在线播放

---

## 2. 接口清单

### 团队端 @RequireRoles({0,1}) — teamId 从 Token 推导

| Method | Path | 说明 |
|--------|------|------|
| GET | `/team-content/mine` | 当前用户所属团队全部内容（含所有状态） |
| POST | `/team-content/members` | 上传队员照（type=1），multipart/form-data |
| POST | `/team-content/photos` | 上传支教/地区照片（type=2/3），multipart/form-data |
| POST | `/team-content/logs` | 上传日志（type=4） |
| POST | `/team-content/honors` | 上传荣誉（type=3） |
| POST | `/team-content/media` | 上传视频/附件，multipart/form-data |
| POST | `/team-content/{type}/{id}/delete` | 逻辑删除（→ARCHIVED），type=image/word/media |

### 公开端 @PublicEndpoint

| Method | Path | 说明 |
|--------|------|------|
| GET | `/team-content/public/{teamId}` | 仅 PUBLISHED 内容 |
| GET | `/team-content/media/{mediaId}/download` | 视频/附件下载（强制 attachment） |
| GET | `/team-content/image/{imageId}` | PUBLISHED 可公开读取；私有状态必须用 Authorization 获取 Blob |

### 管理员端 @RequireRoles({0})

| Method | Path | 说明 |
|--------|------|------|
| GET | `/admin/team-content?teamId=&status=` | 按团队+状态筛选 |
| POST | `/admin/team-content/{type}/{id}/publish` | 发布 |
| POST | `/admin/team-content/{type}/{id}/reject?reason=` | 驳回（reason 必填） |
| POST | `/admin/team-content/{type}/{id}/archive` | 归档 |
| POST | `/admin/team-content/image/{id}/purge` | 图片进入持久化物理删除队列 |
| POST | `/admin/team-content/media/{id}/purge` | 已归档附件进入持久化物理删除队列 |
| GET | `/admin/file-deletion-tasks` | 查询待处理/失败任务、次数和最后错误 |
| POST | `/admin/file-deletion-tasks/{id}/retry` | 手动立即重试 |
| GET | `/admin/public-image-migration/preflight` | 扫描四类业务引用、资产账本和磁盘，输出阻断清单 |
| POST | `/admin/public-image-migration/migrate` | 仅维护窗口启用；按真实文件大小迁移并重建精确配额 |

---

## 3. 数据库表结构

### team_media（新建）

```sql
CREATE TABLE `team_media` (
  `id` INT AUTO_INCREMENT,
  `filename` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `relative_path` VARCHAR(512) NOT NULL COMMENT '存储相对路径',
  `mime_type` VARCHAR(100) COMMENT 'MIME 类型',
  `file_size` BIGINT COMMENT '文件大小(字节)',
  `uploader_id` INT COMMENT '上传者用户ID',
  `team_id` INT COMMENT '所属团队ID',
  `related_type` VARCHAR(20) COMMENT '关联父内容类型: IMAGE/WORD',
  `related_id` INT COMMENT '关联父内容ID',
  `status` ENUM('PENDING','PUBLISHED','REJECTED','ARCHIVED') DEFAULT 'PENDING',
  `reject_reason` VARCHAR(512) COMMENT '驳回原因',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_team_id`(`team_id`),
  INDEX `idx_related`(`related_type`, `related_id`),
  INDEX `idx_status`(`status`)
);
```

### team_page_images（新增列）

```sql
ALTER TABLE `team_page_images`
  ADD COLUMN `team_id` INT,
  ADD COLUMN `status` ENUM('PENDING','PUBLISHED','REJECTED','ARCHIVED') DEFAULT 'PENDING',
  ADD COLUMN `reject_reason` VARCHAR(512),
  ADD COLUMN `log_date` DATE;
```

### team_page_word（新增列，注意旧列名小写 userid）

```sql
ALTER TABLE `team_page_word`
  ADD COLUMN `team_id` INT,
  ADD COLUMN `status` ENUM('PENDING','PUBLISHED','REJECTED','ARCHIVED') DEFAULT 'PENDING',
  ADD COLUMN `reject_reason` VARCHAR(512),
  ADD COLUMN `log_date` DATE;
```

---

## 4. 状态机

```
                  上传
                   ↓
               PENDING
              /   |   \
    发布 ↓    ↓    ↓    ↓ 归档
        PUBLISHED  REJECTED  ARCHIVED
```

### 级联规则

| 父内容操作 | 关联 media 行为 |
|-----------|----------------|
| 图片/文字 归档（→ARCHIVED） | 级联归档关联 media（→ARCHIVED） |
| 图片/文字 发布（→PUBLISHED） | 级联同步 media 状态（→PUBLISHED） |
| 图片/文字 驳回（→REJECTED） | 级联同步 media 状态（→REJECTED） |

**下载接口防御**：media 自身 PUBLISHED 但父内容 REJECTED → 返回 404（级联检查作为防御性兜底）。

## 图片生命周期与配额

- `/team-content/members`、`/team-content/photos` 的直接文件上传和前端“先暂存、后保存”两种方式都进入 `PublicImageLifecycleService`，不存在绕开配额的正式入口。
- 新图片保存为 `PENDING`，物理文件位于 `images_pending/<userId>/`；只有 `PUBLISHED` 位于 `images/<userId>/`。`REJECTED`、`ARCHIVED` 仍在私有目录。
- PENDING、PUBLISHED、REJECTED、ARCHIVED 只要物理文件仍存在，就都占用账号的永久文件数和容量；审核状态切换不释放配额。
- `public_image_asset.asset_id` 是稳定资源编号。文件在私有和公开目录之间移动时只更新 `relative_path`，所有者、字节数和配额不会丢失。
- 只有管理员彻底删除图片文件与记录时才释放配额；持久删除任务先确认物理文件已经删除，再删除资源记录并释放账本额度。磁盘删除或数据库清理失败时任务会保留并重试，普通“归档”也只隐藏内容、不删除文件。
- 业务事务会先把稳定 `asset_id` 写入 `file_deletion_task`。提交后立即尝试，失败则保留任务、错误、次数和下次执行时间并自动退避重试；文件已经不存在按幂等成功继续清理账本。
- 存量 Banner、News、User 和团队风采图片必须先经过应用侧预检。共享路径、磁盘缺失、非标准本地路径、未知所有者、越界链接、孤儿账本或孤儿文件都会形成阻断项；不允许再以 0 字节登记未知文件。
- 旧本地图片未迁入账本时，替换、删除、纯文字更新和发布会被明确拒绝，不再静默遗漏物理文件。HTTP(S) 外部图片不属于本地文件生命周期。

## 附件生命周期、配额与磁盘保护

- `team_media_quota` 按上传账号原子限制数量和累计字节，`team_media_global_quota` 使用单例行限制服务器总量；默认分别为 50 个/2GB 与 2000 个/20GB。
- 所有上传事务固定先锁全局账本、再锁账号账本；条件更新在 InnoDB 中重新判断，多实例不能靠同时上传绕过上限。
- Multipart 解析前的过滤器先验证 Bearer Token 和 0/1 级角色，匿名、失效账号和学生请求不会进入请求体解析。
- 过滤器先锁 `team_media_global_quota` 协调行，再把 `Content-Length` 写入 `team_media_upload_reservation`。所有实例共享在途字节、并发数和单账号分钟频率；检查与预留属于同一事务。请求结束在 `finally` 释放，进程中断由 TTL 回收。
- 临时目录和正式目录按实际 `FileStore` 去重：同一设备只计算一次在途请求和较大的安全余量；不同设备分别保护。Service 在正式复制前还会进行第二次物理余量检查，两处默认至少保留 1GB。
- 关联附件上传在最终登记事务内使用 `SELECT ... FOR UPDATE` 锁 IMAGE/WORD 父记录，再按“父记录 → 全局配额 → 账号配额 → 附件记录”顺序处理。父内容归档使用同一行锁，不能产生逃逸级联的非归档附件。
- PENDING、PUBLISHED、REJECTED、ARCHIVED 全部占额度。归档只开始保留期，不释放空间；管理员 purge 或默认 30 天保留期结束后才创建 `TEAM_MEDIA` 删除任务。
- 删除任务先确认物理文件消失，再删除 `team_media` 行并按“全局→账号”顺序释放额度。任一步失败都会保留任务并重试。

---

## 5. 权限矩阵

| 角色 | 权限 |
|------|------|
| 未登录 | 仅可访问公开端（GET /team-content/public/{teamId}、/download） |
| level=1（团队） | 团队端全部操作（仅自己团队内容） |
| level=0（管理员） | 团队端 + 管理员端（可操作任意团队） |
| level=2（学生） | 仅可访问公开端 |

---

## 6. 文件类型白名单

| 类别 | 扩展名 | 最大大小 | 魔数校验 |
|------|--------|---------|---------|
| IMAGE | jpg, jpeg, png, webp | 10MB | ✅ JPEG/PNG/WEBP |
| VIDEO | mp4, mov | 200MB | ✅ ftyp box |
| DOCUMENT | pdf, doc, docx, ppt, pptx, zip | 200MB | ✅ PDF |

**伪装检测**：扩展名 + MIME + 魔数三重校验，`.exe` 伪装 `.jpg` 会被魔数校验拒绝。

---

## 7. 旧数据迁移说明

执行 `database/patches/11_team_content.sql`：

1. **主回填**：通过 `pageId` JOIN `team_page` → `team_id = team_page.userId`
2. **兜底回填**：`pageId` 为 NULL 但旧 `userId` 有值 → `team_id = userId`
3. **最终检查**：输出 `team_id IS NULL` 的记录数（需人工处理）

**注意**：`team_page_word` 旧列名为小写 `userid`，SQL 中用反引号包裹。

### 公共图片维护窗口迁移（补丁 13 后必做）

1. 备份数据库和整个上传目录，停止外部写入；完整执行 `00 → 10 → 11 → 12 → 13 → 14 → 20 → 30 → 40`。
2. 保持 `TEAM_PUBLIC_IMAGE_MIGRATION_ENABLED=false` 启动后端，以管理员调用 `GET /admin/public-image-migration/preflight`。
3. 逐项清零报告：共享路径需复制或替换为独立文件；缺失文件需从备份恢复或更换；未知所有者/非标准路径需人工迁入账号目录；孤儿资产和文件需确认后处理。
4. 关闭所有业务写入，临时设置 `TEAM_PUBLIC_IMAGE_MIGRATION_ENABLED=true` 并重启；调用 `POST /admin/public-image-migration/migrate`。
5. 再次预检，必须同时得到 `migrationAllowed=true`、`consistent=true`，并确认业务引用数、账本数和磁盘文件数相等。
6. 立即恢复 `TEAM_PUBLIC_IMAGE_MIGRATION_ENABLED=false` 并重启，再逐一验证旧 Banner、旧 News、旧头像和旧团队图片的不换图更新、换图更新、删除及失败重试。

迁移接口不会自动删除或复制有歧义的文件；任何阻断项都会令迁移事务拒绝执行。这样可以避免删除共享文件破坏另一条业务记录。

---

## 8. 大文件上传配置

### application.properties.example

```properties
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=210MB
spring.servlet.multipart.file-size-threshold=10MB
spring.servlet.multipart.location=${GANLU_MULTIPART_TEMP_DIR:${java.io.tmpdir}/ganlu-multipart}
team.media.owner-max-files=50
team.media.owner-max-total-mb=2048
team.media.global-max-files=2000
team.media.global-max-total-mb=20480
team.media.upload-min-free-disk-mb=1024
team.media.multipart-min-free-disk-mb=1024
team.media.archived-retention-days=30
team.media.max-concurrent-uploads=4
team.media.max-requests-per-user-per-minute=12
team.media.upload-reservation-ttl-minutes=120
team.public-image.migration-enabled=false
```

### Nginx 运维提示

```nginx
client_max_body_size 210M;
proxy_request_buffering off;  # 关闭缓冲，实现真实前端上传进度
client_body_timeout 60m;      # 必须小于应用的 120 分钟预留 TTL
proxy_read_timeout 60m;
```

---

## 9. "提交审核"语义说明

上传即 PENDING（等价于提交审核），无独立 submit-for-review 端点。

---

## 10. "受控下载地址"语义说明

当前 `/download` 为 `@PublicEndpoint` + `Content-Disposition: attachment`，"受控"指强制下载（非在线播放）。若未来需鉴权，用 URL Query Token 或 Cookie。

---

## 11. 视频下载接口鉴权限制

HTML `<a download>` 无法携带自定义 Authorization 请求头。管理页当前通过 Axios Blob 下载。若未来必须支持普通链接：
- 方案 A：后端签发仅限单个附件的一次性票据（不可使用完整登录 JWT，有效期不超过 5 分钟）
- 方案 B：使用 Cookie 鉴权（`<a>` 自动携带 Cookie）

---

## 12. team.owner_user_id 字段说明

`resolveTeamId()` 通过 `team.owner_user_id` 字段查找当前用户负责的小队：

```java
private Integer resolveTeamId(UserEntity user) {
    TeamEntity team = teamMapper.findPublishedTeamIdsByOwnerUserId(user.getId());
    return team != null ? team.getId() : null;
}
```

关系链：`user.id` → `team.owner_user_id` → `team.id` → `team_page.team_id`

若用户未绑定小队（`resolveTeamId` 返回 null），团队端接口返回 400。

---

## 13. GET /team-content/mine 状态过滤说明

返回所有状态（含 ARCHIVED），前端团队后台视角需展示"已归档"标签。`GET /team-content/public/{teamId}` 只返回 PUBLISHED。

---

## 14. 数据库 patch 应用机制

当前项目无 Flyway/Liquibase，手动执行。固定顺序为：
`00 → 10 → 11 → 12 → 13 → 14 → 20 → 30 → 40`。

`13_public_image_quota.sql` 只创建结构，不能读取磁盘，因此禁止 SQL 直接按 0 字节回填；必须执行本页的应用侧预检/迁移。

`14_team_media_lifecycle.sql` 会回填旧附件账本，并创建跨实例在途上传记录；若旧附件无法确定上传账号会主动停止，必须先人工修复，不允许带着少算的额度继续上线。

需在本地和运维环境分别执行。

---

## 15. 孤儿 Media 清理说明

无关联父内容的附件仍是合法的独立附件，同样计入个人和服务器配额。团队端可逻辑归档；管理员可立即 purge。归档超过默认 30 天后，定时任务会自动送入持久化删除队列。

---

## 16. 旧接口废弃说明

以下接口保留但标注 `@Deprecated`，内部改为按 `team_id` 列查询，前端不再调用：

| 旧接口 | 新接口替代 |
|--------|-----------|
| POST /fengcai/words | GET /team-content/public/{teamId} |
| POST /fengcai/images | GET /team-content/public/{teamId} |
| POST /fengcai/addImage | POST /team-content/members / photos |
| POST /fengcai/addWord | POST /team-content/logs / honors |
| POST /fengcai/deleteImage | POST /team-content/image/{id}/delete |
| POST /fengcai/deleteWord | POST /team-content/word/{id}/delete |
| POST /fengcai/uploadImage | POST /team-content/members / photos |

---

## 17. 文件清单

### 新建文件

| 文件 | 说明 |
|------|------|
| `database/patches/11_team_content.sql` | 增量 SQL |
| `backend/.../actions/TeamContentAction.java` | 新控制器 |
| `backend/.../entitys/TeamMediaEntity.java` | 视频/附件实体 |
| `backend/.../mappers/TeamMediaMapper.java` | 媒体 Mapper 接口 |
| `backend/.../resources/mapper/TeamMediaMapper.xml` | 媒体 Mapper XML |
| `backend/.../service/TeamMediaService.java` | 媒体 Service 接口 |
| `backend/.../service/impl/TeamMediaServiceImpl.java` | 媒体 Service 实现 |
| `frontend/src/views/TeamContentManage.vue` | 统一管理页 |
| `frontend/src/components/fengcai/ContentStatusTag.vue` | 状态标签组件 |
| `frontend/src/components/fengcai/TeamAttachmentUpload.vue` | 附件上传组件 |
| `backend/.../actions/TeamContentActionTests.java` | 控制器测试 |
| `backend/.../utils/FileStorageUtilTests.java` | 文件校验测试 |
| `docs/integration/team-content.md` | 本集成文档 |

### 修改文件

| 文件 | 修改内容 |
|------|---------|
| `TeamPageImageEntity.java` | 追加 teamId, status, rejectReason, logDate |
| `TeamPageWordEntity.java` | 同上 |
| `TeamPageImageMapper.java` + XML | 新增 findByTeamId, archiveById 等 |
| `TeamPageWordMapper.java` + XML | 同上对称 |
| `TeamPageImageService.java` + Impl | 新增 findByTeamId, archiveById 等 |
| `TeamPageWordService.java` + Impl | 同上对称 |
| `FileStorageUtil.java` | 新增文件校验（白名单 + 魔数 + validate） |
| `FengCaiAction.java` | 旧接口标注 @Deprecated + 改为按 team_id 查询 |
| `application.properties.example` | 调整 multipart 配置 |
| `application-prod.properties.example` | 调整 multipart 配置 |
| `fengcaiAPI.js` | 新增 team-content 系列函数 |
| `path.js` | 新增路径常量 |
| `UploadPhotos.vue` | 移除硬编码 userId，调用新 API |
| `UploadLogHonor.vue` | 同上 + 附件关联 |
| `DataPhotos.vue` | 数据源改为 getMyTeamContent + 状态列 |
| `DataLogHonor.vue` | 同上对称 |
| `FengCaiDetail.vue` | 改用 getPublicTeamContent |
| `router/index.js` | 新增/删除路由 |

### 删除文件

| 文件 | 原因 |
|------|------|
| `stores/PhotoStore.js` | 死代码（无引用） |
| `stores/LogHonorStore.js` | 死代码（无引用） |
| `views/Photos.vue` | 被 TeamContentManage 取代 |
| `views/LogHonor.vue` | 被 TeamContentManage 取代 |
