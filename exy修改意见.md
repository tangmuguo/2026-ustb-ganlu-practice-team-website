# exy PR #7 Code Review 修改意见

评审对象：`tangmuguo/2026-ustb-ganlu-practice-team-website` PR #7  
PR 标题：`feat: 实现团队风采内容管理完整闭环`  
本次评审提交：`5e4161cc341455ca371c0ac6c285499e5dfa9046`  
目标分支基线：`main@a8a03480d642ba642652f43c90f0517ae80dff74`  
评审结论：**暂不建议合并。** 当前实现存在数据库迁移无法按声明依赖顺序执行、公开内容跨团队错配、归档团队内容仍可公开访问、旧接口泄露未审核内容、附件跨团队绕过审核、媒体上传未执行服务端白名单校验等阻塞问题。

## 一、必须修复的阻塞问题

### 1. [P0] Patch 11 与其声明依赖的 PR #5 / Patch 10 不兼容，迁移会直接失败

涉及位置：

- `database/patches/11_team_content.sql` 第 64—84 行
- PR #5 的 `database/patches/10_team_core.sql` 第 154—190 行

PR #7 明确声明依赖 PR #5，并要求先执行 Patch 10、再执行 Patch 11。可是 Patch 10 会把 `team_page.userId` 迁移成 `team_page.team_id`，随后在第 190 行删除 `userId`；Patch 11 仍执行：

```sql
JOIN team_page page ON img.pageId = page.id
SET img.team_id = page.userId
```

日志/荣誉回填也使用了同样的 `page.userId`。因此按文档顺序执行时，Patch 11 会报 `Unknown column 'page.userId'`，整个功能无法部署。

建议：

- 先等待 PR #5 合并并将 PR #7 rebase 到最新 `main`；当前两支在 `TeamMapper.java` 和 `TeamMapper.xml` 上已有文本冲突。
- 主回填改为使用 Patch 10 生成的 `team_page.team_id`。
- 对 `pageId IS NULL` 的旧记录，不要直接把内容表的 `userId` 写进 `team_id`；应兼容 `userId = team.id` 与 `userId = team.owner_user_id` 两种旧关系，并在映射不唯一时中止迁移、提示人工处理。
- 在独立 MySQL 数据库中真实执行 `ganlu.sql → 10_team_core.sql → 11_team_content.sql`，并把该顺序加入自动化验收。

### 2. [P1] 公开详情混用 user.id 与 team.id，ID 碰撞时会展示另一个团队的内容

涉及位置：

- `backend/src/main/java/com/vihu/ganlu/actions/TeamContentAction.java` 第 176—194 行
- `frontend/src/views/FengCaiDetail.vue` 第 32—38 行
- 仍在使用旧团队账号列表的 `frontend/src/views/FengCai.vue` 第 42—57 行
- `database/patches/11_team_content.sql` 第 64—84 行

当前风采列表仍从 `/user/teams` 获取 `UserEntity`，点击详情时把团队账号的 `user.id` 放进路由。新公开接口却先把该值当作真实 `team.id` 查询，只有三类内容全为空时才尝试按 `owner_user_id` 回退。

当“团队 A 的账号 user.id”恰好等于“团队 B 的 team.id”，且 B 已有任意已发布内容时，回退不会发生，访问 A 的详情页会直接看到 B 的内容。小整数自增主键发生这种碰撞的概率很高。迁移脚本又把部分旧内容的 `team_id` 回填成 user.id，使同一列同时存在两种 ID 语义。

建议：统一使用 PR #5 定义的真实 `team.id`：风采列表改接 `/teams/years`、`/teams?year=...` 等新接口，路由和公开内容接口只接受 teamId，删除“先按 teamId、查空再按 userId”的歧义回退；迁移时把所有旧记录转换成真实 team.id。

### 3. [P1] 团队归档后，其已发布子内容和媒体仍可公开查询、下载

涉及位置：

- `backend/src/main/java/com/vihu/ganlu/actions/TeamContentAction.java` 第 176—225 行
- `backend/src/main/resources/mapper/TeamMapper.xml` 第 44—49 行

PR #5 的团队归档语义是：`team.status = 'ARCHIVED'` 后公开详情返回 404。PR #7 的 `/team-content/public/{teamId}` 只查子表状态，不检查团队本身是否为 `PUBLISHED`；下载接口也只检查媒体及父内容状态。结果是归档团队的照片、日志和附件仍能通过 PR #7 新接口直接访问。

此外，名为 `findPublishedTeamIdsByOwnerUserId` 的 SQL 实际没有 `status = 'PUBLISHED'` 条件，会继续为归档团队解析 teamId。

建议：公开查询和媒体下载均先验证所属团队为 `PUBLISHED`，最好在 Mapper 层通过 `JOIN team ... AND team.status = 'PUBLISHED'` 一次完成；下载校验还应确认父内容与媒体属于同一 team。

### 4. [P1] 旧公开接口会返回 PENDING / REJECTED 内容，审核流程可被绕过

涉及位置：

- `backend/src/main/java/com/vihu/ganlu/actions/FengCaiAction.java` 第 109—141 行
- `backend/src/main/resources/mapper/TeamPageImageMapper.xml` 第 28—31 行
- `backend/src/main/resources/mapper/TeamPageWordMapper.xml` 第 30—33 行

`/fengcai/words` 与 `/fengcai/images` 仍标注为 `@PublicEndpoint`，但改成调用 `findByTeamId()`；该查询只排除 `ARCHIVED`，会把 `PENDING` 和 `REJECTED` 一并返回。即使新前端不再调用，任何匿名请求仍可读取尚未审核或已经驳回的内容。

建议：在删除旧接口前，至少让它们只调用 `findByTeamIdAndStatus(teamId, "PUBLISHED")`；同时增加匿名请求不可见 PENDING/REJECTED 的 MockMvc 或集成测试。

### 5. [P1] 附件关联不校验所属团队，级联发布可跨团队绕过媒体审核

涉及位置：

- `backend/src/main/java/com/vihu/ganlu/actions/TeamContentAction.java` 第 116—139 行
- `backend/src/main/java/com/vihu/ganlu/service/impl/TeamPageImageServiceImpl.java` 第 87—94 行
- `backend/src/main/java/com/vihu/ganlu/service/impl/TeamPageWordServiceImpl.java` 第 70—77 行
- `backend/src/main/resources/mapper/TeamMediaMapper.xml` 第 65—76 行

上传接口原样接受客户端的 `relatedType` / `relatedId`，既不校验类型和值是否成对出现，也不校验父内容存在且属于当前 team。管理员发布某个图片或文字时，又会按 `related_type + related_id` 将所有匹配媒体级联改成 `PUBLISHED`，SQL 没有 team 条件。

可复现场景：团队 A 上传附件，把 `relatedId` 指向团队 B 的内容；管理员审核并发布 B 的父内容后，A 的附件会被自动发布。公开下载的父内容检查也不比较 teamId，因此该附件通过检查，实际绕过了媒体的独立审核和团队隔离。

建议：上传时只允许 `IMAGE` / `WORD`，要求父内容存在、未归档且 `parent.team_id == currentTeamId`；级联更新必须带 `team_id` 条件，并在事务内执行；增加跨团队关联必须返回 403/400 的集成测试。

### 6. [P1] 媒体上传只检查大小，新增的服务端扩展名/魔数校验没有被调用

涉及位置：

- `backend/src/main/java/com/vihu/ganlu/actions/TeamContentAction.java` 第 128—137 行
- `backend/src/main/java/com/vihu/ganlu/service/impl/TeamMediaServiceImpl.java` 第 20—38 行
- `backend/src/main/java/com/vihu/ganlu/utils/FileStorageUtil.java` 第 186—302 行

控制器仅判断文件是否大于 200 MB，随后直接 `storeFile`。前端的扩展名限制可以被直接 HTTP 请求绕过，所以任意扩展名、任意内容都能写入上传目录。PR 描述所称“文件类型白名单 + 魔数校验（防伪装）”目前只对图片上传生效，对新媒体/附件接口没有生效，而且测试中也没有媒体上传拒绝非法文件的用例。

建议：在写盘前调用统一的服务端验证，并根据验证结果只允许 VIDEO 或 DOCUMENT；不要信任客户端 MIME；存储名只保留服务器生成的 UUID 与验证后的扩展名，原始文件名仅作为元数据保存。`doc/docx/ppt/pptx/zip` 当前直接返回 `true`，也应至少校验 OLE/ZIP 头并区分格式。读取文件头时使用限长流，避免为 200 MB 文件调用 `getBytes()` 把整个文件载入堆内存。

### 7. [P1] 迁移后所有历史内容默认变成 PENDING，新公开页会把现有风采全部隐藏

涉及位置：

- `database/patches/11_team_content.sql` 第 38—59 行
- `backend/src/main/java/com/vihu/ganlu/actions/TeamContentAction.java` 第 181—193 行
- `frontend/src/views/FengCaiDetail.vue` 第 32—38 行

给旧表新增 `status` 时默认值是 `PENDING`，脚本只回填 teamId，没有迁移历史记录的发布状态。部署后前端立即切换到只读取 `PUBLISHED` 的新接口，因此所有原先在公开站点可见的历史照片、日志和荣誉都会消失，必须由管理员逐条重新发布。

如果这是有意的重新审核策略，需要在上线方案和 PR 描述中明确，并提供批量审核能力；否则应在迁移中把既有公开内容回填为 `PUBLISHED`，仅把部署后新上传内容设为 `PENDING`，并增加迁移前后公开内容数量核对。

## 二、应一并修复的功能和数据一致性问题

### 8. [P2] 新增日志/荣誉后没有回填主键，前端永远无法出现“关联附件”入口

涉及位置：

- `backend/src/main/resources/mapper/TeamPageWordMapper.xml` 第 47—55 行
- `frontend/src/components/UploadLogHonor.vue` 第 53—59、126—133 行

前端依赖响应中的 `content.id` 设置 `lastUploadedId`，但 `insertTeamWord` 没有 `useGeneratedKeys="true" keyProperty="id"`，插入后返回实体的 id 仍为 null，附件区域不会显示。

建议为插入配置生成主键回填，并增加一个覆盖“创建文字 → 响应带 id → 上传关联附件”的真实 Mapper/接口测试。

### 9. [P2] “地区照片”选项实际始终按 type=2 的支教照片保存

涉及位置：

- `frontend/src/components/UploadPhotos.vue` 第 19—23、61—63 行
- `backend/src/main/java/com/vihu/ganlu/actions/TeamContentAction.java` 第 88—96 行

前端允许选择 type=3“地区照片”，但除 type=1 外都调用 `/team-content/photos`，后端该接口固定传入 type=2。因此 type=3 永远不会写入数据库，选择“地区照片”后仍被分类为“支教照片”。

建议新增明确的地区照片接口，或在服务端接收并严格校验 `type ∈ {1,2,3}`，同时补充三种类型的契约测试。

### 10. [P2] 媒体“删除”执行物理删库但不删文件，既违背逻辑归档语义又会持续占用磁盘

涉及位置：

- `backend/src/main/java/com/vihu/ganlu/actions/TeamContentAction.java` 第 160—165 行
- `backend/src/main/resources/mapper/TeamMediaMapper.xml` 第 79—94 行

图片和文字删除会设置 `ARCHIVED`，媒体删除却直接 `DELETE FROM team_media`，磁盘文件没有删除。用户可以反复上传并删除最大 200 MB 的附件，使数据库看不到记录但上传目录持续增长；管理员界面的“删除”也走这条物理删除路径。

建议与其他内容统一设置 `ARCHIVED`。若确实需要永久清理，应提供单独的管理员 purge 流程：先校验权限和目标路径位于 uploadRoot，再协调数据库与文件删除，并记录失败补偿；不要把 purge 混在普通“删除”接口中。

### 11. [P2] Patch 11 每次执行都会先 DROP team_media，重跑会丢失全部附件元数据

涉及位置：

- `database/patches/11_team_content.sql` 第 13—14 行

补丁使用 `DROP TABLE IF EXISTS team_media`。部署脚本重试、环境恢复或误重复执行时，会无提示删除全部媒体记录，而磁盘文件继续保留成孤儿。

建议遵循 Patch 10 的迁移风格：不删除业务表，增加版本/列存在性检查、备份和明确的重复执行结果；建表使用安全的幂等策略，不在升级补丁中 `DROP` 已有业务表。

## 三、依赖与合并状态

- PR #7 声明依赖的 PR #5 当前仍是 open、未合并状态。
- PR #7 当前显示可合并，是因为其基线还不包含 PR #5；将两支进行 merge-tree 检查后，`TeamMapper.java` 与 `TeamMapper.xml` 存在文本冲突。
- 建议先完成 PR #5，再将 PR #7 rebase 到最新 main，按 PR #5 的 TeamEntity、TeamMapper、team_page.team_id 与公开 `/teams` 契约重新集成后复审。

## 四、本次复测证据

- 完整检查了 PR #7 的 52 个变更文件；GitHub 元数据仍显示 43 个 changed files，但本地基于 base/head 的 `git diff` 为 52 个，应以实际 diff 为准。
- `git diff --check`：通过。
- 前端：使用现有锁文件的兼容安装方式 `npm ci --legacy-peer-deps` 后，`npm run build` 通过。
- 标准 `npm ci` 会因项目既有的 `@videojs-player/vue@1.0.0` 与 `video.js@8.23.3` peer 依赖冲突失败；本 PR 未修改 package 文件，本轮不把它归为 exy 的新增问题。
- 后端原始 POM：Maven Central 对新增固定版本 Lombok 1.18.30 返回 403，无法按原样完成依赖解析。
- 在临时审查副本中仅使用本机已有的兼容 Lombok 1.18.18 后，后端主代码与测试代码均编译通过；新增 `TeamContentActionTests` 14/14、`FileStorageUtilTests` 10/10 通过。
- 后端全量 33 项中 32 项通过；`GanluApplicationTests.contextLoads` 因干净环境缺少 DataSource URL 失败，这是当前项目配置基线问题，不作为本 PR 阻塞项。
- 当前测试未覆盖真实 MyBatis 生成主键、Patch 10→11 数据库迁移、跨团队附件关联、旧公开接口状态过滤、归档团队公开访问和媒体服务端非法文件拒绝，正好遗漏了上述关键风险。

## 五、复审验收建议

1. 先合并 PR #5，解决冲突并完成 PR #7 rebase。
2. 在真实 MySQL 中验证 `ganlu.sql → Patch 10 → Patch 11`，迁移后所有内容的 team_id 必须是真实 team.id。
3. 前端、路由和公开接口只使用真实 teamId，删除 userId/teamId 猜测逻辑。
4. 公开查询和下载同时校验团队、父内容、媒体均为可发布状态且 teamId 一致；旧接口不得返回 PENDING/REJECTED。
5. 媒体上传执行服务端类型、魔数、大小和文件名安全校验；跨团队父内容关联必须拒绝。
6. 修复文字主键回填、地区照片类型、媒体逻辑归档和迁移重跑数据安全。
7. 补充上述场景的 Mapper/MockMvc/MySQL 集成测试，再申请复审。

本次仅生成评审意见，未修改 PR 分支、未提交评论、未合并 PR，也未改动当前本地 `main` 工作树。
