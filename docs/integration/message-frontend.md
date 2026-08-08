# 互动留言板前端联调说明

## 1. 实现边界

本次只修改互动留言板前端及本说明，不修改后端、数据库、登录模块、共享路由或共享请求封装。

- 游客可浏览留言与回复，发布区显示登录引导。
- `level=0` 系统管理员可发布、回复、删除留言和回复。
- `level=1` 甘露团队账号可发布、回复、删除留言和回复。
- `level=2` 学生账号可发布、回复，前端不显示删除入口。
- 留言限制 1～500 个 Unicode 字符，回复限制 1～300 个 Unicode 字符。
- 支持加载骨架、空状态、失败重试、分页、删除确认和 375px 手机端。

## 2. 最终接口约定

所有写接口仅通过 `Authorization: Bearer <token>` 识别当前用户。前端不发送 `userId`、`userid`、`author` 或账号等级。

| 功能 | 方法 | 路径 | 请求参数 |
| --- | --- | --- | --- |
| 分页查询 | GET | `/message/list` | `page`, `pageSize` |
| 发布留言 | POST | `/message/add` | `{ "content": "..." }` |
| 发布回复 | POST | `/message/addReply` | `{ "messageId": 1, "content": "..." }` |
| 删除留言 | POST | `/message/deleteMessage` | `{ "id": 1 }` |
| 删除回复 | POST | `/message/deleteReply` | `{ "id": 1 }` |

分页查询成功响应：

```json
{
  "code": 200,
  "message": "查询成功",
  "content": {
    "messages": [
      {
        "id": 101,
        "content": "这是一条留言",
        "createTime": "2026-08-01T10:00:00+08:00",
        "userLevel": 1,
        "displayName": "甘露一队",
        "username": "ganlu_team_1",
        "teamname": "甘露一队",
        "replies": [
          {
            "id": 201,
            "messageId": 101,
            "content": "这是一条回复",
            "createTime": "2026-08-01T10:05:00+08:00",
            "userLevel": 2,
            "displayName": "张同学",
            "username": "student_1",
            "teamname": null
          }
        ]
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 10
  }
}
```

作者展示字段固定如下：

| 字段 | 必填 | 说明 |
| --- | :---: | --- |
| `userLevel` | 是 | 数字 `0/1/2`，分别表示系统管理员、甘露团队、学生账号 |
| `displayName` | 是 | 后端确定的公开展示名称，前端不再从其他字段猜测 |
| `username` | 否 | 账号名；存在且不同于 `displayName` 时显示为辅助信息 |
| `teamname` | 否 | 团队账号的团队名；`userLevel=1` 时可作为角色标签 |

前端只使用 `userLevel` 映射角色，不读取旧的 `level`、`user.level` 或通过 `teamname` 猜测角色。若后端缺少约定字段，页面显示“已注销用户 / 账号类型待同步”，避免把管理员或学生错误标为“注册用户”。

失败约定：

- 未登录调用写接口返回 HTTP `401`。
- `level=2` 调用删除接口返回 HTTP `403`。
- 其他失败返回合适 HTTP 状态码，并在响应 `message` 中提供可展示提示。
- `level=0/1` 的删除权限必须由后端根据 Token 再次校验。

## 3. 本轮审查修复

1. 发布者角色只按 `userLevel/displayName/teamname/username` 约定展示，不再兼容性猜测多个等级字段。
2. 留言列表请求使用递增序号，只允许最新请求写入列表、总数、错误及 loading 状态。
3. 回复提交状态改为按留言 ID 独立记录，两个留言同时回复不会互相解除防重复保护。
4. 删除包含可识别未成年人的外部照片，改为页面内纯 CSS 插画。

## 4. 前端验收记录

验收环境：

- 日期：2026-08-01
- Node.js：`22.23.2`
- 前端：Vite 7，本地接口模拟服务
- 桌面视口：`1440×1000`
- 手机视口：`375×812`

| 编号 | 场景 | 结果 | 状态 |
| --- | --- | --- | :---: |
| T01 | 游客访问 | 可读取留言和回复；显示登录引导；无发布、回复、删除入口 | 通过 |
| T02 | `userLevel=0` 管理员响应 | 显示“系统管理员”；可发布、回复、删除 | 通过 |
| T03 | `userLevel=1` 团队响应 | 显示 `teamname`；可发布、回复、删除 | 通过 |
| T04 | `userLevel=2` 学生响应 | 显示“学生账号”；可发布、回复，无删除入口 | 通过 |
| T05 | 仅含旧 `level/teamname` 的响应 | 不猜测角色，明确显示“账号类型待同步” | 通过 |
| T06 | 1 条留言 | 仅一页，不显示分页器 | 通过 |
| T07 | 10 条留言 | 仅一页，不显示分页器 | 通过 |
| T08 | 11 条留言 | 第 1 页 10 条、第 2 页 1 条 | 通过 |
| T09 | 删除第 2 页最后一条 | 总数变为 10，自动回到第 1 页 | 通过 |
| T10 | 游客调用写接口 | HTTP `401`，草稿保留 | 通过 |
| T11 | 学生调用删除接口 | HTTP `403`，不误报成功 | 通过 |
| T12 | 列表接口失败后恢复 | 显示错误与“重新加载”；重试后恢复 | 通过 |
| T13 | 第 2 页慢响应、第 3 页快响应 | 最终保持第 3 页内容，旧响应不覆盖 | 通过 |
| T14 | 留言 A、B 交叉提交回复 | A、B 各自保持 loading，均不能重复提交 | 通过 |
| T15 | 375px 手机端 | 文档 `scrollWidth=360`、`clientWidth=360`，无横向滚动 | 通过 |
| T16 | 作者字段及并发状态自动检查 | 3 项 Node 测试全部通过 | 通过 |
| T17 | 生产构建 | Vite 7，1599 modules transformed，构建成功 | 通过 |

以上为前端模拟响应验收。真实后端四角色复测必须在第 5 节字段依赖合入后执行，不能用模拟结果冒充真实联调。

## 5. 后端联调依赖

截至 `origin/main@a8a0348`，`MessageEntity`、`ReplyEntity` 及对应 Mapper 的列表查询仅返回 `username/teamname`，尚未返回 `userLevel/displayName`。因此真实角色标签联调仍被后端字段阻塞。

后端 PR 需要保证每条留言及回复都返回：

- `userLevel`
- `displayName`
- `username`
- `teamname`

字段合入后需使用游客、管理员、团队、学生四种真实状态复测 T01～T04，并把真实响应结果补入本说明。该依赖属于互动留言板后端任务，不在本前端 PR 中修改。

## 6. 视觉素材与授权

页面不再使用外部照片，也不包含任何真实人物或未成年人肖像。右侧视觉为 `MessageBoard.vue` 中的原创 CSS 插画，由渐变、山形、书本及问答气泡组成：

- 无外部图片来源；
- 无第三方版权许可依赖；
- 无肖像权或隐私授权要求。

此前本地演示截图包含已删除照片，已从本次提交范围排除，不能作为公开交付物。

## 7. 构建与检查命令

```powershell
cd frontend
npx -y node@22 --test tests/messageAuthor.test.js tests/messageState.test.js
npx -y node@22 node_modules/vite/bin/vite.js build
```

构建输出仅有项目现存的 Browserslist 数据过期及大 chunk 警告，无新增错误。
