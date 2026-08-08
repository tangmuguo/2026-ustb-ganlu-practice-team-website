# 团队风采核心后端集成说明

## 交付范围

本模块建立统一的 `teamId -> pageId` 关系，提供“年份 → 小队 → 详情”公开查询和管理员建团、修改、归档接口。本模块不修改 `FengCaiAction.java`，也不负责照片、日志、荣誉和附件内容的管理。

公开端不再将路由中的小队 ID 当作 `userId`。团队内容模块应使用详情接口返回的 `pageId` 关联 `team_page_images` 和 `team_page_word`。

## 数据库迁移

执行顺序：

```text
mysql -u <local-user> -p
mysql> USE ganlu;
mysql> SOURCE D:/path/to/ganlu_webpage/ganlu.sql;
mysql> SOURCE D:/path/to/ganlu_webpage/database/patches/10_team_core.sql;
```

`10_team_core.sql` 的处理规则：

- 在改表前检查年份、同年同名、团队账号映射和详情页映射。
- 兼容旧 `team_page.userId` 存放 `team.id` 或 `user.id` 的两种情况；不能唯一判定时直接中止，不猜测和不删数据。
- 改表前生成 `team_core_backup_team_20260730` 和 `team_core_backup_team_page_20260730`。
- `team.owner_user_id` 通过团队名与旧 `user.teamname` 唯一匹配，且只接受 `level=1` 账号。
- `team_page.team_id` 建立唯一索引和外键，确保一个小队最多一个详情页。
- 重复执行会以明确错误中止，不会静默覆盖备份或重复改表。

如迁移报“无法按团队名唯一绑定”，先在本地数据库修正旧 `user.teamname`；如报 `team_page.userId` 无法唯一映射，先根据旧数据确认其真实指向。脚本不会自动删除冲突记录。

## 状态口径

| 业务状态 | `team.status` | `team_page.status` |
|---|---|---|
| 草稿 | `DRAFT` | `草稿` |
| 已发布 | `PUBLISHED` | `展示` |
| 已归档 | `ARCHIVED` | `归档` |

公开接口只返回 `team.status=PUBLISHED` 的小队。修改小队会更新已有详情页的标题和状态；不会创建第二个 `team_page`。`DELETE` 管理接口实际是逻辑归档。

## 公开接口

### `GET /teams/years`

按年份倒序返回已发布小队的年份摘要。年份封面取该年第一个非空小队封面。

```json
{
  "code": 200,
  "message": "查询成功",
  "content": [
    {
      "year": "2025",
      "coverUrl": "/uploads/team/2025-cover.jpg",
      "publishedTeamCount": 3
    }
  ]
}
```

### `GET /teams?year=2025&page=1&size=12`

- `year`：必填，4 位数字，范围 `1900-2100`。
- `page`：默认 `1`，最小 `1`。
- `size`：默认 `12`，范围 `1-100`。

```json
{
  "code": 200,
  "message": "查询成功",
  "content": {
    "items": [
      {
        "id": 10,
        "year": "2025",
        "name": "星火小队",
        "region": "甘肃陇南",
        "school": "希望小学",
        "description": "小队简介",
        "coverUrl": "/uploads/team/team-10.jpg",
        "status": "PUBLISHED",
        "pageId": 20,
        "createdAt": "2026-07-30T08:00:00.000+00:00",
        "updatedAt": "2026-07-30T08:00:00.000+00:00"
      }
    ],
    "page": 1,
    "size": 12,
    "total": 1,
    "totalPages": 1
  }
}
```

### `GET /teams/{teamId}`

以真实 `team.id` 查询，返回小队基本信息和唯一 `pageId`。小队不存在、是草稿或已归档时都返回 HTTP `404`。

## 管理员接口

下列接口必须携带 `Authorization: Bearer <token>`，且服务端校验后的当前用户必须为 `level=0`。请求体不接收操作人 `userId`。

- `POST /admin/teams`
- `PUT /admin/teams/{teamId}`
- `DELETE /admin/teams/{teamId}`：仅归档，不物理删除。

创建与更新请求体：

```json
{
  "year": "2025",
  "name": "星火小队",
  "ownerUserId": 7,
  "region": "甘肃陇南",
  "school": "希望小学",
  "description": "小队简介",
  "coverUrl": "/uploads/team/team-10.jpg",
  "status": "DRAFT"
}
```

字段规则：

- `year`、`name`、`ownerUserId`、`region`、`school` 必填。
- `ownerUserId` 必须对应现存的 `level=1` 团队账号。
- `status` 可为 `DRAFT`、`PUBLISHED` 或 `ARCHIVED`，不传时默认 `DRAFT`。
- `name` 最长 100，`region` 最长 100，`school` 最长 150，`description` 最长 2000，`coverUrl` 最长 512。

成功时返回的 `content` 为小队详情 DTO，包含新建或已绑定的 `pageId`。

## 错误码

| HTTP / `code` | 场景 |
|---:|---|
| `400` | 年份、分页、必填字段、负责人账号或状态不合法 |
| `401` | 管理接口未登录或 Token 无效 |
| `403` | 已登录但不是系统管理员 |
| `404` | 小队不存在，或公开详情尚未发布 |
| `409` | 同一年份下已存在同名小队 |
| `500` | 建团、详情页绑定或其他服务端操作失败 |

新接口的业务响应统一为：

```json
{
  "code": 400,
  "message": "年份必须是4位数字",
  "content": null
}
```

## 本分支验证记录

| # | 验证项 | 方式 | 结果 |
|---:|---|---|---|
| 1 | 游客请求年份列表 | MockMvc `GET /teams/years` | 通过；返回 `code=200` 和年份摘要 |
| 2 | 未发布或不存在的详情 | MockMvc `GET /teams/9` | 通过；HTTP / `code` 均为 `404` |
| 3 | 团队账号调用建团接口 | MockMvc + Bearer Token，`level=1` | 通过；拦截为 `403`，Service 未被调用 |
| 4 | 管理员创建小队 | MockMvc + Bearer Token，`level=0` | 通过；返回 `teamId=10`、`pageId=20` |
| 5 | 同年同名重复创建 | MockMvc / Service | 通过；返回 `409`，未执行 insert |
| 6 | 将学生账号绑为负责人 | Service，`owner.level=2` | 通过；校验失败，未执行 insert |
| 7 | 更新小队时重用详情页 | Service + Mockito | 通过；只执行 `updateMetadataByTeamId`，不执行第二次 insert |
| 8 | 归档小队 | Service + Mockito | 通过；执行状态更新并同步页面，无物理 delete |
| 9 | 旧 `team_page.userId=user.id` 迁移 | MySQL 8.0.46 隔离临时库 | 通过；`OWNER=101`、`PAGE TEAM=201`、状态映射为 `PUBLISHED/展示` |
| 10 | 迁移备份和唯一索引 | MySQL 8.0.46 隔离临时库 | 通过；2 张备份表存在，`uk_team_page_team_id` 存在 |
| 11 | 重复执行迁移 | MySQL 8.0.46 隔离临时库 | 通过；以含脚本名的明确错误拒绝 |
| 12 | 旧 `team_page.userId=team.id` 迁移 | MySQL 8.0.46 第二个隔离临时库 | 通过；详情页正确绑定 `teamId=401` |
| 13 | 数据库硬约束 | MySQL 8.0.46 隔离临时库 | 通过；同年同名、第二详情页、非法年份均被拒绝 |
| 14 | 管理员登录并创建中文小队 | 实际 Spring Boot + MySQL，UTF-8 HTTP 请求 | 通过；返回 `teamId=1`、`pageId=1`，中文字段往返正确 |
| 15 | 公开三级查询 | 实际 HTTP `years/list/detail` | 通过；年份、分页总数、`pageId` 均正确 |
| 16 | 重复建团与越权建团 | 实际 HTTP + Bearer Token | 通过；分别返回 `409` 和 `403` |
| 17 | 更新小队 | 实际 HTTP + MySQL 回查 | 通过；`pageId` 不变，中文学校名更新，详情页数仍为 1 |
| 18 | 归档小队 | 实际 HTTP + MySQL 回查 | 通过；公开详情返回 `404`，原记录仍存在且状态为 `ARCHIVED` |

完整命令 `backend\\mvnw.cmd test` 已通过：共 22 项测试，0 失败、0 错误。MySQL 迁移和端到端 HTTP 验证均使用新建的隔离临时库，完成后已停止测试服务并删除临时库，未修改现有 `ganlu` 数据库。

## 合并提示

- 赵友为：按顺序合并 `database/patches/10_team_core.sql`，不需要把本分支直接写入根目录 `ganlu.sql`。Mapper XML 会被现有 `mybatis.mapper-locations=mapper/*.xml` 自动加载，不需要新增共享配置。
- 孙木文：前端路由层只传 `teamId`，详情页内容请使用返回的 `pageId`。
- 李嘉辉：内容表继续通过 `pageId` 关联 `team_page`；权限判断时从服务端当前用户反查 `team.owner_user_id`，不信任请求体中的 `userId`。
