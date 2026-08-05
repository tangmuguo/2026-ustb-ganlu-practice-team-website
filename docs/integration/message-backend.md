# 互动留言板后端交接文档

## 交接范围

本次交接对应 `docs/任务分工/08-方亦琳-互动留言板后端.md`，只修改留言板后端、留言板测试、留言板 SQL patch 和本对接文档。

未修改内容：

- 不修改 Vue 页面。
- 不修改共享认证类。
- 不修改根目录总 SQL。
- 不修改前端路由和公共请求封装。

## 实现摘要

- 游客可分页读取公开留言和回复。
- level `0/1/2` 登录用户均可新增留言和回复。
- level `0/1` 可逻辑删除留言和回复。
- level `2` 删除返回 `403`。
- 请求体中的 `userId/userid` 不作为可信身份，后端只使用认证上下文中的当前用户。
- 留言和回复正文在 Service 中 `trim()` 后校验长度。
- 留言列表按 `create_time DESC, id DESC` 稳定排序。
- 同一留言下回复按 `create_time DESC, id DESC` 稳定排序。
- 当前页回复使用 `WHERE message_id IN (...)` 批量查询，避免逐条查询回复。
- 留言和回复用户名通过 SQL JOIN 带回；用户缺失时返回 `用户#<userId>` 占位。
- `MessageAction` 局部处理畸形 JSON、分页参数类型错误和不支持的 Content-Type，返回统一 `{code,message,content}`；不支持的 Content-Type 返回 `415`，没有新增接管全站的异常处理器。
- 非预期内部异常只向客户端返回通用 `服务器内部错误`，详细异常写入后端日志，不回传 SQL 或连接错误文本。

## 修改文件

- `backend/src/main/java/com/vihu/ganlu/actions/MessageAction.java`
- `backend/src/main/java/com/vihu/ganlu/service/impl/MessageServiceImpl.java`
- `backend/src/main/java/com/vihu/ganlu/mappers/ReplyMapper.java`
- `backend/src/main/resources/mapper/MessageMapper.xml`
- `backend/src/main/resources/mapper/ReplyMapper.xml`
- `backend/src/main/java/com/vihu/ganlu/entitys/DeleteReplyEntity.java`
- `backend/pom.xml`
- `backend/src/test/java/com/vihu/ganlu/GanluApplicationTests.java`
- `backend/src/test/resources/application-test.properties`

## 新增文件

- `backend/src/main/java/com/vihu/ganlu/entitys/message/MessageCreateRequest.java`
- `backend/src/main/java/com/vihu/ganlu/entitys/message/ReplyCreateRequest.java`
- `backend/src/main/java/com/vihu/ganlu/entitys/message/DeleteContentRequest.java`
- `database/patches/20_message_board.sql`
- `backend/src/test/java/com/vihu/ganlu/actions/MessageActionTests.java`
- `backend/src/test/java/com/vihu/ganlu/service/MessageServiceTests.java`
- `backend/src/test/java/com/vihu/ganlu/mappers/MessageMapperIntegrationTests.java`
- `docs/integration/message-backend.md`

## 返回格式

留言板接口由 `MessageAction` 返回时统一使用：

```json
{
  "code": 200,
  "message": "查询成功",
  "content": {}
}
```

说明：未登录或 token 无效的 `401` 由共享 `AuthInterceptor` 返回。当前任务按交付边界未修改共享认证类；如需让所有模块的 `401/403` 也稳定包含 `content`，建议由赵友为在公共认证模块统一处理。

## 接口

### 游客分页查询留言

`GET /message/list?page=1&pageSize=10`

- 公开接口，不需要 token。
- `page >= 1`。
- `pageSize` 范围为 `1-50`。
- 只返回 `status=1` 的留言和回复。
- 排序：留言 `create_time DESC, id DESC`，同一留言下回复 `create_time DESC, id DESC`。

响应示例：

```json
{
  "code": 200,
  "message": "查询成功",
  "content": {
    "messages": [
      {
        "id": 1,
        "userId": 2,
        "content": "留言正文",
        "username": "team-user",
        "teamname": "甘露团队",
        "replies": [
          {
            "id": 3,
            "messageId": 1,
            "userId": 5,
            "content": "回复正文",
            "username": "student-user",
            "teamname": null
          }
        ]
      }
    ],
    "total": 11,
    "page": 1,
    "pageSize": 10
  }
}
```

### 新增留言

`POST /message/add`

需要 `Authorization: Bearer <token>`，level `0/1/2` 均允许。

请求体只需要正文；即使前端传 `userId/userid`，后端也会忽略。

```json
{
  "content": "留言正文"
}
```

校验：trim 后 `1-500` 字，按纯文本保存，不接收 HTML 特权语义。

### 新增回复

`POST /message/addReply`

需要 `Authorization: Bearer <token>`，level `0/1/2` 均允许。

```json
{
  "messageId": 1,
  "content": "回复正文"
}
```

校验：`messageId` 必须存在且未删除；正文 trim 后 `1-300` 字。

### 删除留言

`POST /message/deleteMessage`

需要 `Authorization: Bearer <token>`，只允许 level `0/1`。level `2` 返回 `403`。

```json
{
  "id": 1
}
```

删除为逻辑删除：`message.status=0`。公开列表不再展示该留言，其回复也不会被展示。

### 删除回复

`POST /message/deleteReply`

需要 `Authorization: Bearer <token>`，只允许 level `0/1`。level `2` 返回 `403`。

```json
{
  "id": 3
}
```

删除为逻辑删除：`reply.status=0`。

## 错误码

- `400`：空白正文、超长正文、非法 id、`page=0`、`pageSize>50` 等参数错误。
- `400`：畸形 JSON、`page=abc` 等框架级客户端错误。
- `415`：不支持的 Content-Type。
- `401`：未登录或 token 无效。
- `403`：当前角色无删除权限。
- `404`：留言或回复不存在，或已被逻辑删除。
- `500`：服务器内部错误。客户端不展示数据库或连接异常详情。

## SQL 执行顺序

1. 先导入根目录 `ganlu.sql`。
2. 执行 `database/patches/20_message_board.sql` 中的孤儿数据诊断 `SELECT`。
3. 如果 `reply.message_id` 指向不存在的留言，先修复对应历史数据，再添加 `fk_reply_message`。
4. 执行索引和 `reply -> message` 外键迁移。脚本会通过 `information_schema` 判断索引/外键是否已存在，已存在则跳过。
5. 如果脚本中途失败，修复导致失败的历史数据后可以重新执行整份 `20_message_board.sql`，已完成的索引/外键不会重复添加。

本补丁不添加 `message.user_id -> user.id` 或 `reply.user_id -> user.id` 外键。原因是当前共享用户模块仍使用物理删除用户；如果添加用户 RESTRICT 外键，发过留言或回复的账号会删除失败并可能把数据库异常暴露成 500。历史内容保留数字 `user_id`，用户记录不存在时由后端返回 `用户#<userId>`。

`reply.message_id -> message.id` 外键使用 `ON DELETE RESTRICT`。留言板删除留言走逻辑删除，不会物理删除留言审计数据。

`20_message_board.sql` 不包含 `DROP TABLE`，不清空现有留言、回复或用户数据。MySQL 8.0 复验建议覆盖三种场景：首次执行成功、第二次整文件执行不失败、手工预先创建部分索引后仍能补齐剩余对象。

## 测试账号建议

本地准备 3 个账号：

- 管理员：`level=0`
- 团队账号：`level=1`
- 学生账号：`level=2`

验收时确认：三类账号都能新增留言和回复；管理员/团队可删除；学生即使在请求体伪造 `userId/userid` 也不能删除。

## 自动化测试

已新增三组留言板测试：

- `MessageActionTests`：覆盖游客列表、游客新增/回复 401、伪造 `userId` 新增、非法分页、`page=abc`、畸形 JSON、不支持的 Content-Type 返回 415、无 message 的内部异常、学生伪造管理员删除仍 403。
- `MessageServiceTests`：覆盖 level `0/1/2` 新增留言和回复、空白/超长正文、批量回复查询、分页第二页、极大 page 溢出保护、已删除/不存在留言回复失败、学生不可删除、管理员和团队可删除。
- `MessageMapperIntegrationTests`：使用 `application-test.properties` 的 H2 MySQL 模式真实加载 MyBatis XML，覆盖 11 条数据分页第二页、同时间 `id DESC` 稳定排序、批量回复查询、逻辑删除后公开列表不展示、用户被物理删除后历史留言仍可读取并显示 `用户#<id>`。
- `GanluApplicationTests`：使用 test profile 启动 Spring Context，验证干净检出能加载配置和 Bean。

复验命令：

```bash
cd backend
sh ./mvnw -q test
```

当前验证结果：`sh ./mvnw -q test` 通过。

Windows 环境可使用：

```powershell
cd backend
.\mvnw.cmd test
```

## 手工联调清单

建议关振卿、赵友为联调时按以下顺序检查：

1. 游客访问 `GET /message/list?page=1&pageSize=10`，应返回 `200` 和 `content.messages`。
2. 游客访问 `POST /message/add`，应返回 `401`。
3. 管理员、团队、学生分别新增留言，均应成功。
4. 管理员、团队、学生分别新增回复，均应成功。
5. 学生请求体伪造 `userId=1` 删除留言，应返回 `403`。
6. 管理员删除留言后，公开列表不再展示该留言。
7. 团队删除回复后，公开列表不再展示该回复。
8. `page=0`、`pageSize=1000`、空白正文、超长正文均返回 `400`。
9. 对已删除或不存在的 `messageId` 回复，返回 `404`。

## 前端对接提示

- 前端新增留言请求体只传 `content`。
- 前端新增回复请求体只传 `messageId` 和 `content`。
- 前端删除留言继续使用 `/message/deleteMessage`。
- 前端删除回复继续使用 `/message/deleteReply`。
- 前端不要在请求体中传可信 `userId/userid`。
- 列表数据读取 `content.messages`、`content.total`、`content.page`、`content.pageSize`。
