-- =====================================================================
-- Patch 11/12/15 手工验证脚本（供复审参考）
--
-- 用途：验证 patch 11（owner 预检 + team_id 回填）、patch 12（owner 唯一约束）、
--      patch 15（cutoff 双确认 + 历史回填 + 文件搬迁清单）在多种升级路径下的正确性。
--
-- exy v5 调整：
--   - patch 11 不再做历史 PENDING→PUBLISHED（已挪到 patch 15）
--   - 场景 B/C 的历史回填验证目标改挂 patch 15
--   - 新增"旧版 patch 11 已执行 → 跑 patch 15"的升级路径场景
--   - 新增 Item 1 文件搬迁验证（patch 15 第 4 节 + 配套搬迁脚本）
--
-- exy v6 调整（修复复审 v6 的 P1/P2）：
--   - patch 15 改为"运维显式 cutoff + 两步确认"模式，杜绝误公开上线后真实 PENDING
--   - patch 15 前置校验扩展到完整 schema（表/列/索引/唯一约束），不再只查两对象
--   - patch 15 文件搬迁引入持久化清单表 _patch15_image_migration，覆盖 Windows 反斜杠路径
--   - 搬迁脚本改为清单驱动，missing/conflict/后置失败均 exit 1，密码走 --defaults-extra-file
--   - 新增场景 F（cutoff 不误公开新行）、G（Windows 反斜杠路径）、H（搬迁失败退出码）、
--          I（部分 schema 前置校验）、J（dry-run/确认双步流程）
--
-- ⚠️ 维护模式（Item 4 exy v5）：执行 patch 10/11/12/15 期间必须停止应用写入
--    team / team_page / team_page_images / team_page_word 表。
--
-- 前置条件：一个干净的 ganlu 库（用项目根 ganlu.sql 重建），尚未执行任何 patch。
-- =====================================================================
-- ⚠️ 该脚本会修改 ganlu 库结构，仅用于本地/测试库，禁止在生产执行。
-- =====================================================================


-- #####################################################################
-- 场景 A（Item 3）：patch 11 的 owner 正向重复预检
--   构造：1 个 owner_user_id 绑 2 个 team
--   断言：跑 patch 11 → 3.5 节 SIGNAL 中止；team_page_images / team_page_word 零行回填
-- #####################################################################
-- A.1 构造重复 owner（假设 patch 10 已执行，team 表有 owner_user_id 列）
INSERT INTO `user` (id, level, teamname, password) VALUES (9001, 1, 'dup-owner-team', 'x');
INSERT INTO team (id, year, name, owner_user_id, status) VALUES
  (5001, '2025', 'A队-场景A', 9001, 'PUBLISHED'),
  (5002, '2025', 'B队-场景A', 9001, 'PUBLISHED');
INSERT INTO team_page_images (id, userId, pageId, imageUrl, caption, type) VALUES
  (6001, 9001, NULL, 'images_pending/a.jpg', 'A场景-图1', 2);

-- A.2 执行 patch 11 —— 期望在 3.5 节 owner 预检 SIGNAL 中止
--     手工执行：SOURCE database/patches/11_team_content.sql
--     期望错误：存在同一负责人账号(owner_user_id)绑定多个小队...

-- A.3 断言：内容表零行回填（team_id 仍为 NULL）
SELECT 'A.3 断言：内容表零行回填' AS step;
SELECT id, team_id, status FROM team_page_images WHERE id = 6001;  -- 期望 team_id 仍为 NULL


-- #####################################################################
-- 场景 B（Item 4 失败重跑 → patch 15 完成回填）
--   构造：1 个 orphan（team_id 无法映射）→ patch 11 orphan 校验 SIGNAL →
--        修复 orphan → 跑 patch 11（通过）→ 跑 patch 15（完成历史回填）
-- #####################################################################
-- B.1 清理重建：重新 SOURCE ganlu.sql + patch 10
INSERT INTO `user` (id, level, teamname, password) VALUES (9002, 1, 'orphan-test-team', 'x');
INSERT INTO team (id, year, name, owner_user_id, status) VALUES
  (5003, '2025', '正常队-场景B', 9002, 'PUBLISHED');
INSERT INTO team_page_images (id, userId, pageId, imageUrl, caption, type) VALUES
  (6002, 9002, NULL, 'images_pending/b.jpg', 'B场景-正常图', 2),
  (6099, 9999, NULL, 'images_pending/orphan.jpg', 'B场景-orphan图', 2);  -- orphan：userId=9999 不存在

-- B.2 执行 patch 11 —— 期望第 5 节 orphan 校验 SIGNAL（6099 无法映射）
--     SOURCE database/patches/11_team_content.sql
--     期望错误：存在 team_id IS NULL 的历史内容记录...

-- B.3 首次失败后：6002 仍为 PENDING（patch 11 不做历史回填了）
SELECT 'B.3 首次失败后：6002 仍为 PENDING' AS step;
SELECT id, team_id, status FROM team_page_images WHERE id = 6002;

-- B.4 修复 orphan
DELETE FROM team_page_images WHERE id = 6099;

-- B.5 重跑 patch 11（orphan 校验通过）→ 跑 patch 12 → 跑 patch 15
--     SOURCE database/patches/11_team_content.sql
--     SOURCE database/patches/12_team_owner_unique.sql
--     SOURCE database/patches/15_team_content_history_publish.sql

-- B.6 断言：patch 15 把 6002 回填为 PUBLISHED
SELECT 'B.6 断言：6002 已回填为 PUBLISHED' AS step;
SELECT id, team_id, status FROM team_page_images WHERE id = 6002;
-- 期望：team_id = 5003，status = 'PUBLISHED'


-- #####################################################################
-- 场景 C（Item 4 边界）：patch 15 成功后重跑不误伤上线后新数据
--   延续场景 B，插入新 PENDING → 重跑 patch 15 → 新行不被误改
-- #####################################################################
-- C.1 模拟上线后应用插入的新 PENDING 内容（id 不在快照里）
INSERT INTO team_page_images (id, userId, pageId, team_id, imageUrl, caption, type, status) VALUES
  (6100, 9002, NULL, 5003, 'images_pending/c.jpg', 'C场景-上线后新内容', 2, 'PENDING');

-- C.2 重跑 patch 15
--     SOURCE database/patches/15_team_content_history_publish.sql

-- C.3 断言：新行 6100 不被误改 PUBLISHED（id 6100 不在 _patch15_hist_images 快照里）
SELECT 'C.3 断言：新行 6100 保持 PENDING' AS step;
SELECT id, status FROM team_page_images WHERE id = 6100;
-- 期望：status = 'PENDING'

-- 对比：原历史行 6002 仍为 PUBLISHED
SELECT id, status FROM team_page_images WHERE id = 6002;
-- 期望：status = 'PUBLISHED'


-- #####################################################################
-- 场景 D（正常路径）：干净库 10 → 11 → 12 → 15 全链路
-- #####################################################################
-- D.1 清理重建：SOURCE ganlu.sql，依次：
--     SOURCE database/patches/10_team_core.sql
--     SOURCE database/patches/11_team_content.sql
--     SOURCE database/patches/12_team_owner_unique.sql
--     SOURCE database/patches/15_team_content_history_publish.sql
--     执行配套文件搬迁：bash database/patches/15_migrate_images_files.sh ...

-- D.2 断言：patch 12 的唯一约束建立
SELECT 'D.2 uk_team_owner_user 约束存在' AS step;
SELECT COUNT(*) AS uk_exists FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'team' AND index_name = 'uk_team_owner_user';
-- 期望：1

-- D.3 断言：patch 15 的快照表建立
SELECT 'D.3 _patch15_hist_* 快照表存在' AS step;
SELECT COUNT(*) AS snap_images FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = '_patch15_hist_images';
-- 期望：1（快照表恒建，无论是否有历史数据需回填）


-- #####################################################################
-- 场景 E（exy v5 升级路径）：旧版 patch 11（d9873b1）已执行 → 跑 patch 15
--   模拟：库已执行过 d9873b1 版 patch 11（team_media + status 列在，历史已 PUBLISHED）
--   断言：跑 patch 15 → 识别"无 PENDING 历史行"→ 幂等跳过，不崩溃
-- #####################################################################
-- E.1 模拟旧版 patch 11 已执行的库状态：
--     team_media 表存在、status 列存在、历史内容已 PUBLISHED（d9873b1 的 status-列判断回填过）
--     （实际操作：在一个已跑过 d9873b1 的库上直接跑 patch 15）

-- E.2 执行 patch 15
--     SOURCE database/patches/15_team_content_history_publish.sql
--     期望：三态识别通过（team_media + status 都在），完成度判断为"无 PENDING"，
--          幂等跳过，不崩溃（快照表恒建，无需回填时为空表——第 5 节 UPDATE JOIN 空表，0 行变更）

-- E.3 断言：patch 15 幂等跳过，历史内容保持 PUBLISHED
SELECT 'E.3 断言：升级路径幂等' AS step;
SELECT COUNT(*) AS still_pending FROM team_page_images
 WHERE team_id IS NOT NULL AND status = 'PENDING';
-- 期望：0（无历史 PENDING 残留）


-- #####################################################################
-- 场景 F（exy v6 P1#1）：cutoff 不误公开上线后真实新 PENDING
--   旧版 patch 15 用 status='PENDING' 推断历史身份，无法区分"旧版遗漏历史行"与
--   "上线后真实新 PENDING"，首次执行会把真实待审内容匿名公开。
--   v6 用 createdAt <= cutoff 限定历史身份，上线后新行不进快照、不回填。
-- #####################################################################
-- F.1 准备：干净库跑 10 → 11 → 12，插入历史 PENDING（createdAt 早）+ 上线后新 PENDING（createdAt 晚）
--   （参考场景 B 的 user/team/team_page_images 建表语句）
INSERT INTO team_page_images (userId, pageId, team_id, imageUrl, caption, type, status, createdAt) VALUES
  (7001, NULL, 5003, 'images_pending/f-old.jpg', 'F-历史行', 2, 'PENDING', '2025-06-01 10:00:00'),
  (7001, NULL, 5003, 'images_pending/f-new.jpg', 'F-上线后新行', 2, 'PENDING', '2026-12-01 10:00:00');

-- F.2 dry-run：cutoff 设在两行之间（只采集历史行）
SET @patch15_cutoff := '2026-01-01 00:00:00';
-- SOURCE database/patches/15_team_content_history_publish.sql
--   期望：preview 显示"待发布图片 1 条"（只 f-old，f-new 因 createdAt>cutoff 不进快照）
--   期望：SIGNAL 中止（@patch15_apply 未设），业务数据不变

-- F.3 确认运行
SET @patch15_apply := 1;
-- SOURCE database/patches/15_team_content_history_publish.sql

-- F.4 断言：历史行 f-old 被回填 PUBLISHED，上线后新行 f-new 保持 PENDING
SELECT 'F.4 断言：cutoff 不误公开新行' AS step;
SELECT id, imageUrl, status FROM team_page_images WHERE imageUrl LIKE 'images_pending/f-%';
-- 期望：f-old = PUBLISHED；f-new = PENDING（关键安全属性：真实待审内容不被误公开）


-- #####################################################################
-- 场景 G（exy v6 P1#2）：Windows 历史反斜杠路径迁移
--   旧版 patch 15 只改 imageUrl LIKE 'images/%'，漏掉 Windows 历史路径 images\xxx.jpg
--   （FileStorageUtil.loadFile 仍兼容），漏改的行物理文件滞留 /images/** 静态公开目录，
--   驳回/归档后旧 URL 仍可绕过 serveImage 状态校验。v6 用 LEFT/SUBSTRING+CHAR(92) 归一化两种前缀。
-- #####################################################################
-- G.1 插入 Windows 反斜杠历史行（注意 SQL 字面量中 \\ 表示一个反斜杠）
INSERT INTO team_page_images (userId, pageId, team_id, imageUrl, caption, type, status, createdAt) VALUES
  (7001, NULL, 5003, 'images\\g-win.jpg', 'G-Windows路径', 2, 'PENDING', '2025-06-01 10:00:00');

-- G.2 确认运行 patch 15（cutoff 覆盖该行 createdAt）
--   SET @patch15_cutoff := '2026-01-01 00:00:00'; SET @patch15_apply := 1;
--   SOURCE database/patches/15_team_content_history_publish.sql

-- G.3 断言：DB imageUrl 改指 images_pending/，清单表记录正确
SELECT 'G.3 断言：Windows 路径已归一化' AS step;
SELECT id, imageUrl FROM team_page_images WHERE caption = 'G-Windows路径';
-- 期望：imageUrl = 'images_pending/g-win.jpg'（不再是 images\g-win.jpg）
SELECT id, old_url, new_url FROM _patch15_image_migration WHERE old_url LIKE 'images%g-win.jpg';
-- 期望：old_url 含反斜杠 'images\g-win.jpg'，new_url = 'images_pending/g-win.jpg'

-- G.4 执行物理搬迁后断言：源文件删除、目标文件存在、旧 /images/... URL 404
--   （需先在 UPLOAD_DIR/images/ 放 g-win.jpg 源文件，再跑搬迁脚本）
--   bash database/patches/15_migrate_images_files.sh MYSQL_SOCKET=... MYSQL_DB=ganlu_test ...
--   期望：脚本输出 [搬迁] id=... g-win.jpg，exit 0
--   期望：UPLOAD_DIR/images/g-win.jpg 不存在；UPLOAD_DIR/images_pending/g-win.jpg 存在
--   期望：GET /images/g-win.jpg 返回 404（静态目录无此文件）
--   期望：GET /team-content/image/{id} 经 serveImage 状态校验后正常返回


-- #####################################################################
-- 场景 H（exy v6 P1#3）：搬迁脚本缺失/冲突非零退出
--   旧版脚本 missing/conflict 只计数不中断，部分成功也 exit 0，部署系统会误判成功。
--   v6 分别统计 missing/conflict/postcheck_failed，任一非零 exit 1。
-- #####################################################################
-- H.1 缺失场景：DB 引用悬空（源和目标都不存在）
--   准备：patch 15 已生成清单含某 id，但 UPLOAD_DIR/images/ 和 images_pending/ 都无该文件
--   执行：bash 15_migrate_images_files.sh ...
--   期望：输出 [缺失] 和 [后置校验失败]，exit code = 1

-- H.2 冲突场景：目标已存在且内容不同
--   准备：清单含 id=N，UPLOAD_DIR/images/x.jpg 与 images_pending/x.jpg 都存在但内容不同
--   执行：bash 15_migrate_images_files.sh ...
--   期望：输出 [冲突-内容不同]，exit code = 1，源文件保留（不误删）

-- H.3 去重场景：目标已存在且内容相同
--   准备：清单含 id=N，两目录 x.jpg 内容完全相同（cmp -s 通过）
--   执行：bash 15_migrate_images_files.sh ...
--   期望：输出 [冲突-内容相同] → 删除公开目录源文件，exit 0（若无其他失败）


-- #####################################################################
-- 场景 I（exy v6 P2#6）：部分 schema 前置校验
--   旧版只查 team_media 表 + team_page_images.status 列就认定 patch 11 完整执行，
--   缺 team_page_word.status 等依赖时会以 "Unknown column" 非预期中断。
--   v6 一次性校验全部依赖 schema，缺任一项明确 SIGNAL。
-- #####################################################################
-- I.1 模拟 patch 11 部分执行：删除 team_page_word.status 列
ALTER TABLE team_page_word DROP COLUMN status;

-- I.2 执行 patch 15
--   SET @patch15_cutoff := '2026-01-01 00:00:00';
--   SOURCE database/patches/15_team_content_history_publish.sql
--   期望：SIGNAL SQLSTATE '45000'，MESSAGE_TEXT 含
--        "patch 11 部分执行（team_page_word.status 列不存在），请补跑 patch 11"
--   （非旧版的 "Unknown column team_page_word.status" 非预期错误）

-- I.3 恢复：补回列重跑（验证恢复路径）
--   ALTER TABLE team_page_word ADD COLUMN status enum('PENDING','PUBLISHED','REJECTED','ARCHIVED') NULL DEFAULT 'PENDING' AFTER team_id;
--   （注意：补回 status 列但缺 idx 时，patch 15 同样会 SIGNAL 提示缺索引）


-- #####################################################################
-- 场景 J（exy v6 P1#1）：dry-run / 确认双步流程
--   验证两步确认安全门：首次（dry-run）只出清单不改动业务数据；确认后才回填。
-- #####################################################################
-- J.1 cutoff 缺失 → 立即 SIGNAL（第 2 节）
--   SOURCE database/patches/15_team_content_history_publish.sql
--   期望：SIGNAL "未提供 @patch15_cutoff..."

-- J.2 cutoff 提供但 @patch15_apply 未设 → dry-run 出清单后 SIGNAL（第 4 节）
--   SET @patch15_cutoff := '2026-01-01 00:00:00';
--   SOURCE database/patches/15_team_content_history_publish.sql
--   期望：输出 preview + 待发布 ID 清单，然后 SIGNAL "dry-run 完成..."
--   期望：业务数据（team_page_images.status、imageUrl）未变化；_patch15_image_migration 表不存在

-- J.3 确认运行 → 执行回填 + 文件搬迁
--   SET @patch15_apply := 1;
--   SOURCE database/patches/15_team_content_history_publish.sql
--   期望：第 5 节建清单改 imageUrl，第 6 节回填 PUBLISHED；migration_result 提示执行搬迁脚本

-- J.4 重跑确认（幂等）→ 无变化
--   SET @patch15_apply := 1;
--   SOURCE database/patches/15_team_content_history_publish.sql
--   期望：已 PUBLISHED 的行 WHERE status='PENDING' 不命中，0 行变更；已改 imageUrl 的行 old_url 不匹配，0 行变更

