# 团队风采公开端前端集成说明

## 交付范围

本分支已完成团队风采“年份 → 当年小队 → 小队详情”三级公开页面。公开页只发起基于 `teamId/pageId` 的 GET 请求，不读取登录用户身份，不提供上传、审核或删除入口，也不会把视频嵌入播放器。

本次未直接修改以下共享文件：

- `frontend/src/router/index.js`
- `frontend/src/components/Top.vue`
- `frontend/src/components/Foot.vue`
- `frontend/src/utils/http.js`
- `frontend/src/utils/path.js`

## 赵友为需要合并的路由

请把下面路由合入 `frontend/src/router/index.js`。建议让新的详情页使用 `DefaultLayout`，以保持顶部导航、页脚和全站背景一致。

```js
{
  path: '/fengcai',
  name: 'fengcai',
  component: () => import('@/views/FengCai.vue'),
  meta: { layout: 'DefaultLayout', hideBanner: true },
},
{
  path: '/fengcai/:year(\\d{4})',
  name: 'fengcai-year',
  component: () => import('@/views/FengCaiTeamList.vue'),
  meta: { layout: 'DefaultLayout', hideBanner: true },
},
{
  path: '/fengcai/team/:teamId',
  name: 'fengcai-team-detail',
  component: () => import('@/views/FengCaiDetail.vue'),
  meta: { layout: 'DefaultLayout', hideBanner: true },
},
{
  path: '/fengcaidetail/:id/:name?',
  redirect: (to) => `/fengcai/team/${to.params.id}`,
},
```

集成时还需确认生产服务器已经为 Vue History 路由配置回退到 `index.html`，否则直接刷新详情 URL 会由 Web 服务器返回 404。

## 公开接口依赖

### 年份列表

`GET /teams/years`

建议响应：

```json
{
  "code": 200,
  "content": [
    {
      "year": "2025",
      "coverUrl": "/uploads/teams/2025-cover.webp",
      "teamCount": 2
    }
  ]
}
```

字段：

| 字段 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `year` | string / number | 是 | 四位年份；后端按倒序返回，前端也会再次倒序。 |
| `coverUrl` | string | 否 | 年份封面相对或绝对 URL；缺失时显示渐变占位。 |
| `teamCount` | number | 是 | 该年份已发布小队数量。 |

### 按年份查询小队

`GET /teams?year=2025&page=1&size=9`

建议响应：

```json
{
  "code": 200,
  "content": {
    "records": [
      {
        "id": 101,
        "year": "2025",
        "name": "甘露一队",
        "region": "云南省昆明市",
        "school": "示例小学",
        "description": "小队简介",
        "coverUrl": "/uploads/teams/101-cover.webp",
        "status": "PUBLISHED"
      }
    ],
    "total": 2
  }
}
```

字段：

| 字段 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `id` | number / string | 是 | 业务团队 `teamId`。 |
| `name` | string | 是 | 小队名称。 |
| `region` | string | 否 | 支教地区。 |
| `school` | string | 否 | 支教小学。 |
| `description` | string | 否 | 小队简介摘要。 |
| `coverUrl` | string | 否 | 小队封面；缺失或加载失败时显示占位。 |
| `status` | string | 是 | 公开接口只应返回 `PUBLISHED`；前端还会再次过滤。 |
| `total` | number | 是 | 当前年份已发布小队总数，用于分页。 |

列表数组可以使用 `records`、`items`、`list`、`content` 或 `teams` 包裹，前端已兼容这些常见格式。

### 团队基本详情

`GET /teams/{teamId}`

至少返回：

| 字段 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `id` / `teamId` | number / string | 是 | 必须是业务团队 ID，不是 `userId`。 |
| `pageId` | number / string | 是 | 唯一团队详情页 ID，供内容服务关联。 |
| `year` | string / number | 是 | 支教年份。 |
| `name` | string | 是 | 小队名称，详情页刷新后从接口恢复。 |
| `region` | string | 否 | 支教地区。 |
| `school` | string | 否 | 支教小学。 |
| `description` | string | 否 | 卡片/顶部摘要。 |
| `overview` | string | 否 | 团队概况长文本；缺失时使用 `description`。 |
| `coverUrl` | string | 否 | 团队详情封面。 |
| `status` | string | 是 | 必须为 `PUBLISHED` 才可由公开接口返回。 |

### 已发布内容

`GET /team-content/public/{teamId}`

建议响应：

```json
{
  "code": 200,
  "content": {
    "members": [],
    "honors": [],
    "regionPhotos": [],
    "teachingPhotos": [],
    "logs": [],
    "attachments": []
  }
}
```

内容字段：

| 分类 | 推荐字段 |
| --- | --- |
| 队员 `members` | `id`, `name`, `role`, `bio`, `photoUrl`, `status` |
| 荣誉 `honors` | `id`, `honorDate`, `title`, `description`, `attachments`, `status` |
| 地区照片 `regionPhotos` | `id`, `imageUrl`, `caption`, `description`, `status` |
| 教学照片 `teachingPhotos` | `id`, `imageUrl`, `caption`, `description`, `status` |
| 课堂日志 `logs` | `id`, `logDate`, `title`, `summary`, `body`, `attachments`, `status` |
| 其他附件 `attachments` | `id` / `mediaId`, `fileName`, `mimeType`, `fileSize`, `downloadUrl`, `relatedType`, `relatedId`, `status` |

兼容内容类型：图片 `MEMBER`、`REGION_PHOTO`、`TEACHING_PHOTO`；文字 `HONOR`、`CLASS_LOG`。每条团队及内容数据必须返回状态，前端只显示 `PUBLISHED`；状态缺失或为其他值时不公开。年份聚合记录不属于内容数据，可以不返回状态。关联到荣誉/日志的附件请返回 `relatedType + relatedId`，没有关联对象的文件会进入“其他附件”。

下载地址可以直接放在 `downloadUrl`；如果只返回 `mediaId`，前端会使用 `/team-content/media/{mediaId}/download`。视频与普通文件均只生成下载链接，页面没有 `<video>` 元素。

## HTTP 与静态资源依赖

- `frontend/src/apis/fengcaiAPI.js` 新增 `getTeamYears`、`getTeamsByYear`、`getTeamDetail`、`getPublicTeamContent`。
- 接口失败时需要保留 HTTP 404/500 状态；如果统一返回 HTTP 200，则响应体 `code` 也应使用对应错误码和明确 `message`。
- 图片/文件 URL 可以是完整 URL，也可以是相对 `VITE_API_BASE_URL` 的路径。
- 赵友为合并 `utils/http.js` 时，请确保它最终读取 `VITE_API_BASE_URL`，否则相对媒体地址和 API 地址可能不一致。

## 联调检查清单

- [ ] 准备至少 2024、2025 两个年份，每年各 2 支 `PUBLISHED` 小队。
- [ ] `/fengcai` 按年份倒序显示封面、年份和小队数。
- [ ] 点击年份进入 `/fengcai/{year}`，分页总数与接口一致。
- [ ] 点击小队进入 `/fengcai/team/{teamId}`；复制 URL 后直接刷新，名称和内容不丢失。
- [ ] `DRAFT`、`PENDING`、`REJECTED`、`ARCHIVED` 内容在游客页面不可见。
- [ ] 缺图显示占位；点击已加载图片可站内预览大图。
- [ ] 无日志/无附件时显示明确空状态。
- [ ] 团队或内容接口返回 404/500 时显示错误说明和重试按钮。
- [ ] MP4/MOV 和普通附件都只有“下载”，DOM 中没有视频播放器。
- [ ] 在桌面、平板和 375px 手机宽度验证年份页、小队页、详情页。
- [ ] 游客无需登录即可请求全部四个公开接口。
- [ ] `npm run build` 通过，浏览器控制台无运行错误。

## 页面截图

已使用 2 个年份、每年 2 支已发布小队的本地模拟接口完成验证，截图存放于：

- `docs/integration/screenshots/fengcai-years.png`
- `docs/integration/screenshots/fengcai-teams-2025.png`
- `docs/integration/screenshots/fengcai-team-detail.png`

## 本分支测试记录

测试日期：2026-07-30

| 场景 | 预期 | 实际结果 |
| --- | --- | --- |
| 正式前端构建 | `npm run build` 成功 | 通过；Vite 7 共转换 1592 个模块。 |
| 独立编译三个风采页面 | 未合入共享路由时也能检查新页面语法 | 通过；年份页、小队页、详情页均生成 ES 模块。 |
| 三级公开浏览 | 2025/2024 → 每年 2 小队 → teamId=101 详情 | 通过；详情直接刷新后仍从接口恢复名称和内容。 |
| 发布状态过滤 | `DRAFT` 小队、`PENDING` 附件不可见 | 通过；页面只渲染 `PUBLISHED`。 |
| 图片预览 | 点击内容图片出现站内大图层 | 通过；预览层可见且 Esc 可关闭。 |
| 日志展开 | 摘要可切换为完整正文 | 通过；按钮在“展开正文/收起正文”之间切换。 |
| 视频规则 | 页面只显示下载，不出现播放器 | 通过；详情 DOM 中 `<video>` 数量为 0。 |
| 空数据 | 无小队时显示清楚的空状态 | 通过；2023 年显示“暂时没有已发布的小队”。 |
| 404 | 提示资源不存在并提供重试/返回 | 通过。 |
| 500 | 提示服务暂不可用并提供重试/返回 | 通过。 |
| 响应式 | 桌面、768px、375px 均无横向溢出 | 通过；768px 为双列卡片，375px 为单列卡片和单列详情顶部。 |

正常风采页面自身没有控制台错误。使用当前共享 `Top.vue` 预览时存在一条已知 Vue 警告：`el-menu` 的 `ellipsis="false"` 被当作字符串；赵友为合并公共导航时应改为 `:ellipsis="false"`。该警告来自本任务边界外的共享组件，本分支未直接修改。

## 当前联调风险

当前 `MuYue_part` 分支中的后端仍只有旧的 `POST /fengcai/images`、`POST /fengcai/words`，并按 `userId` 查询；上述新 GET 接口需合入王嘉阳、李嘉辉的分支后才能进行真实数据联调。公开页面不会回退调用旧接口，以免继续混用团队 ID 和用户 ID。
