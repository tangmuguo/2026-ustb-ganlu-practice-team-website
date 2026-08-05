-- =====================================================================
-- Patch 11/12/15 手工验证脚本（供复审参考）
--
-- 用途：验证 patch 11（owner 预检 + team_id 回填）、patch 12（owner 唯一约束）、
--      patch 15（三态识别 + 历史回填 + 文件搬迁标记）在多种升级路径下的正确性。
--
-- exy v5 调整：
--   - patch 11 不再做历史 PENDING→PUBLISHED（已挪到 patch 15）
--   - 场景 B/C 的历史回填验证目标改挂 patch 15
--   - 新增"旧版 patch 11 已执行 → 跑 patch 15"的升级路径场景
--   - 新增 Item 1 文件搬迁验证（patch 15 第 4 节 + 配套搬迁脚本）
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
