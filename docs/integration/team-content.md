# 团队风采内容管理 — 集成文档

## 1. 概述

本模块为甘露网站"团队风采"模块构建完整的内容管理闭环（上传 → 待审核 → 发布/驳回），并新增视频/附件表 `team_media`。

**核心设计原则：**
- 不 DROP 旧表，增量 patch 新增列
- 团队端接口不含 teamId，从 Token 推导（防越权）
- 删除 = 逻辑删除（status=ARCHIVED），保留审计追溯
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

### 管理员端 @RequireRoles({0})

| Method | Path | 说明 |
|--------|------|------|
| GET | `/admin/team-content?teamId=&status=` | 按团队+状态筛选 |
| POST | `/admin/team-content/{type}/{id}/publish` | 发布 |
| POST | `/admin/team-content/{type}/{id}/reject?reason=` | 驳回（reason 必填） |
| POST | `/admin/team-content/{type}/{id}/archive` | 归档 |

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

---

## 8. 大文件上传配置

### application.properties.example

```properties
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=210MB
spring.servlet.multipart.file-size-threshold=10MB
spring.servlet.multipart.location=${java.io.tmpdir}/ganlu-uploads
```

### Nginx 运维提示

```nginx
client_max_body_size 210M;
proxy_request_buffering off;  # 关闭缓冲，实现真实前端上传进度
```

---

## 9. "提交审核"语义说明

上传即 PENDING（等价于提交审核），无独立 submit-for-review 端点。

---

## 10. "受控下载地址"语义说明

当前 `/download` 为 `@PublicEndpoint` + `Content-Disposition: attachment`，"受控"指强制下载（非在线播放）。若未来需鉴权，用 URL Query Token 或 Cookie。

---

## 11. 视频下载接口鉴权限制

HTML `<a download>` 无法携带自定义 Authorization 请求头。若未来需要限制下载权限：
- 方案 A：后端签发一次性临时 Token 放在 URL Query 参数中（`?token=xxx`，有效期 5 分钟）
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

当前项目无 Flyway/Liquibase，手动执行。执行顺序：
1. `ganlu.sql`（初始 schema）
2. `database/patches/11_team_content.sql`（本 patch）

需在本地和运维环境分别执行。

---

## 15. 孤儿 Media 清理说明

用户中途取消上传附件时可能产生无关联父内容的 media 记录。当前可接受（团队端可见并手动删除），后续可考虑定时清理任务。

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
