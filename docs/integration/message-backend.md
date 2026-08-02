# 留言板后端集成说明

本集成分支在正式成员成果尚不可见时补齐了临时留言板后端合同：游客分页读取；管理员、团队、学生发布和回复；管理员、团队逻辑删除。方亦琳的正式实现现已提交在依赖 PR #10（head `1`，提交 `87c954b`）；本文件描述的版本只作为临时最小实现，最终保留、替换和冲突处理由孙木文在联调时决定。所有写接口仅从 Bearer Token 获取当前用户，不读取请求体中的 `userId` 或作者名称。

接口与 `docs/integration/message-frontend.md` 一致：

- `GET /message/list?page=1&pageSize=10`
- `POST /message/add`，请求 `{ "content": "..." }`
- `POST /message/addReply`，请求 `{ "messageId": 1, "content": "..." }`
- `POST /message/deleteMessage`，请求 `{ "id": 1 }`
- `POST /message/deleteReply`，请求 `{ "id": 1 }`

列表中的留言与回复均返回 `userLevel`、`displayName`、`username`、`teamname`。回复使用一次批量查询，不再对每条留言和回复逐条查询用户。

数据库在基线后执行 `database/patches/20_message_board.sql`。脚本只增加查询索引，可重复执行。
