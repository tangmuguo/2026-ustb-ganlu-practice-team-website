# 互动留言板后端对接说明

## 返回格式

所有留言板接口统一返回：

```json
{
  "code": 200,
  "message": "查询成功",
  "content": {}
}
```

认证失败由统一拦截器返回 `401`，角色无权限返回 `403`。

## 接口

### 游客分页查询留言

`GET /message/list?page=1&pageSize=10`

- 公开接口，不需要 token。
- `page >= 1`。
- `pageSize` 范围为 `1-50`。
- 只返回 `status=1` 的留言和回复。
- 排序：留言 `create_time DESC, id DESC`，回复 `create_time ASC, id ASC`。

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
- `401`：未登录或 token 无效。
- `403`：当前角色无删除权限。
- `404`：留言或回复不存在，或已被逻辑删除。

## SQL 执行顺序

1. 先导入根目录 `ganlu.sql`。
2. 执行 `database/patches/20_message_board.sql` 中的两个孤儿数据检查 `SELECT`。
3. 如果检查有返回行，先修复对应历史数据。
4. 再执行索引和外键 `ALTER TABLE`。

外键使用 `ON DELETE RESTRICT`，避免删除用户时级联物理删除历史留言/回复审计数据。

## 测试账号建议

本地准备 3 个账号：

- 管理员：`level=0`
- 团队账号：`level=1`
- 学生账号：`level=2`

验收时确认：三类账号都能新增留言和回复；管理员/团队可删除；学生即使在请求体伪造 `userId/userid` 也不能删除。
