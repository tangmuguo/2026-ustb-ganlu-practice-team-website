# AI 小助手后端接口说明

## 赵友为合并指引

在 `application-dev.properties` / `application-prod.properties` 末尾追加以下内容（来自 `application-ai.example.properties`）：

```properties
ai.enabled=${AI_ENABLED:true}
ai.base-url=${DEEPSEEK_BASE_URL:https://api.deepseek.com}
ai.api-key=${DEEPSEEK_API_KEY:}
ai.model=${DEEPSEEK_MODEL:deepseek-v4-flash}
ai.connect-timeout=10000
ai.read-timeout=60000
```

## 环境变量

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek API Key（必填） | 无 |
| `DEEPSEEK_BASE_URL` | API 基础地址 | `https://api.deepseek.com` |
| `DEEPSEEK_MODEL` | 模型名 | `deepseek-v4-flash` |

## 接口

### POST /ai/chat

**请求头**：`Authorization: Bearer <token>`（必填，未登录返回 401）

**请求体**：
```json
{
  "messages": [
    { "role": "user", "content": "给三年级学生解释什么是光合作用" }
  ]
}
```

| 字段 | 约束 |
|---|---|
| `messages` | 数组，1～20 条 |
| `role` | 仅允许 `user` / `assistant` |
| `content` | 1～2000 字 |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "success",
  "content": {
    "answer": "光合作用是植物利用阳光合成养分的过程。",
    "requestId": "a1b2c3d4"
  }
}
```

**错误响应**：
```json
{
  "code": 503,
  "message": "AI 服务未配置，请联系管理员设置 DeepSeek API Key。",
  "content": null
}
```

**HTTP 状态码**：

| HTTP | code | 含义 |
|---|---|---|
| 200 | 200 | 成功 |
| 400 | 400 | 输入校验失败 |
| 401 | 401 | 未登录（由 AuthInterceptor 返回） |
| 429 | 429 | 请求频率超限 |
| 502 | 502 | AI 上游认证失败或错误 |
| 503 | 503 | AI 服务未配置或上游不可用 |
| 504 | 504 | AI 上游超时 |

## 测试

```powershell
.\mvnw.cmd test
```

测试使用 `MockRestServiceServer` 模拟 DeepSeek，不消耗真实额度。覆盖场景：正常回答、空消息、超长消息、超 20 条、未配置 Key、上游 401/429/5xx、空 choices、API Key 不泄露。

## 给乔轩轲（前端）

- 接口：`POST /ai/chat`
- 请求格式：`{ "messages": [{ "role": "user", "content": "..." }] }`
- 读取成功响应的 `content.answer`
- 对 400 显示输入提示，对 401 引导登录，对 429 显示"请求较多，请稍后再试"，对 502/504 显示"AI 服务暂时不可用"
- 请求中只发送 `role` 和 `content`，不发送 API Key、模型名或 userId
