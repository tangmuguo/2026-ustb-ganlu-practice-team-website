-- =====================================================================
-- Patch 11 / 12 手工验证脚本（供复审参考）
--
-- 用途：在本地 MySQL 上验证 patch 11 的两处 P1 修复（Item 3 owner 预检、Item 4 历史回填）
--      在失败重跑/上线后重跑等边界场景下的正确性。本脚本不是迁移脚本本身，
--      而是构造测试数据 + 断言的"测试夹具"，供复审在本地执行并核对输出。
--
-- 前置条件：一个干净的 ganlu 库（用项目根 ganlu.sql 重建），尚未执行任何 patch。
-- 执行方式：按场景分段执行，每个场景前会清理重置。
-- =====================================================================
-- ⚠️ 该脚本会修改 ganlu 库结构，仅用于本地/测试库，禁止在生产执行。
-- =====================================================================


-- #####################################################################
-- 场景 A（Item 3）：重复 owner 在内容回填前被预检拦截
--   构造：1 个 owner_user_id 绑 2 个 team（patch 10 的缺口）
--   断言：跑 patch 11 → SIGNAL 中止；team_page_images / team_page_word 零行回填
-- #####################################################################
-- 准备：先执行 ganlu.sql → patch 10，建好 team 表与 owner_user_id 列

-- A.1 构造 2 个 team 指向同一 owner_user_id（user 表先插一个 level=1 账号）
--     假设 patch 10 已执行，team 表已有 owner_user_id 列
INSERT INTO `user` (id, level, teamname, password) VALUES (9001, 1, 'dup-owner-team', 'x');
INSERT INTO team (id, year, name, owner_user_id, status) VALUES
  (5001, '2025', 'A队-场景A', 9001, 'PUBLISHED'),
  (5002, '2025', 'B队-场景A', 9001, 'PUBLISHED');

-- A.2 插入几条历史内容（images/word），这些是"绝不能被错误归属"的目标
--     注意：imageUrl 在基线 schema 为 NOT NULL，必须给占位值（迁移逻辑不依赖它）
INSERT INTO team_page_images (id, userId, pageId, imageUrl, caption, type) VALUES
  (6001, 9001, NULL, 'images_pending/a.jpg', 'A场景-图1', 2);
INSERT INTO team_page_word (id, userid, pageId, caption, content, type) VALUES
  (7001, 9001, NULL, 'A场景-日志', '内容', 4);

-- A.3 记录回填前的内容表行数与 team_id 状态
SELECT 'A.3 回填前 images team_id 状态' AS step;
SELECT id, userId, team_id, status FROM team_page_images WHERE id = 6001;

-- A.4 执行 patch 11 —— 期望在 3.5 节 owner 预检处 SIGNAL 中止
--     手工执行：SOURCE database/patches/11_team_content.sql
--     期望错误信息：存在同一负责人账号(owner_user_id)绑定多个小队...

-- A.5 断言：SIGNAL 后内容表零行回填（team_id 仍为 NULL）
--     注意：此时 _patch11_hist_images / _patch11_hist_word 快照表已被创建（在脚本顶部），
--     这是预期行为，不算失败；只断言内容表 team_id 未被回填。
SELECT 'A.5 断言：内容表零行回填' AS step;
SELECT id, team_id, status FROM team_page_images WHERE id = 6001;  -- 期望 team_id 仍为 NULL
SELECT id, team_id, status FROM team_page_word WHERE id = 7001;    -- 期望 team_id 仍为 NULL


-- #####################################################################
-- 场景 B（Item 4 失败重跑）：orphan 校验失败后重跑，历史内容仍能正确回填
--   构造：1 个 orphan（team_page_images.team_id 无法映射）
--   断言：首次 patch 11 在第 5 节 orphan 校验 SIGNAL → 修复 orphan → 重跑 →
--        快照里的历史内容全部变成 PUBLISHED
-- #####################################################################
-- B.1 清理场景 A 残留，重建干净库（重新 SOURCE ganlu.sql + patch 10）
--     然后构造 1 个正常 owner + 1 条可回填内容 + 1 条 orphan
INSERT INTO `user` (id, level, teamname, password) VALUES (9002, 1, 'orphan-test-team', 'x');
INSERT INTO team (id, year, name, owner_user_id, status) VALUES
  (5003, '2025', '正常队-场景B', 9002, 'PUBLISHED');
INSERT INTO team_page_images (id, userId, pageId, imageUrl, caption, type) VALUES
  (6002, 9002, NULL, 'images_pending/b.jpg', 'B场景-正常图', 2);  -- 这条应被回填
INSERT INTO team_page_images (id, userId, pageId, imageUrl, caption, type) VALUES
  (6099, 9999, NULL, 'images_pending/orphan.jpg', 'B场景-orphan图', 2);  -- orphan：userId=9999 不存在，兜底回填映射不到

-- B.2 执行 patch 11 —— 期望在第 5 节 orphan 校验 SIGNAL（因为 6099 无法映射 team_id）
--     手工执行：SOURCE database/patches/11_team_content.sql
--     期望错误信息：存在 team_id IS NULL 的历史内容记录...

-- B.3 此时快照已采集（含 6002 和 6099），回填未执行（SIGNAL 中止）
SELECT 'B.3 首次失败后：6002 仍为 PENDING' AS step;
SELECT id, team_id, status FROM team_page_images WHERE id = 6002;  -- 期望 status=PENDING

-- B.4 修复 orphan：删除 6099（或给它绑个真实 owner）
DELETE FROM team_page_images WHERE id = 6099;

-- B.5 重跑 patch 11
--     手工执行：SOURCE database/patches/11_team_content.sql
--     期望：orphan 校验通过，快照 JOIN 回填执行，无 SIGNAL

-- B.6 断言：历史内容 6002 已正确回填为 PUBLISHED（按快照 JOIN，ID 齐全）
SELECT 'B.6 断言：6002 已回填为 PUBLISHED' AS step;
SELECT id, team_id, status FROM team_page_images WHERE id = 6002;
-- 期望：team_id = 5003（正常队），status = 'PUBLISHED'


-- #####################################################################
-- 场景 C（Item 4 边界）：成功后再跑不误伤上线后新数据（锁死快照保护）
--   构造：在场景 B 成功的基础上，插入 1 条新 PENDING（模拟上线后应用新增内容）
--   断言：重跑 patch 11 → 新行不被误改 PUBLISHED（其 id 不在快照里）
--   这是验证 migrated_at 方案做不到、快照方案能做到的关键场景
-- #####################################################################
-- C.1 延续场景 B 的库状态（patch 11 已成功执行过一次）

-- C.2 模拟上线后应用插入的新 PENDING 内容（注意 id 不在快照里）
INSERT INTO team_page_images (id, userId, pageId, team_id, imageUrl, caption, type, status) VALUES
  (6100, 9002, NULL, 5003, 'images_pending/c.jpg', 'C场景-上线后新内容', 2, 'PENDING');

-- C.3 重跑 patch 11
--     手工执行：SOURCE database/patches/11_team_content.sql

-- C.4 断言：新行 6100 不被误改 PUBLISHED（id 6100 不在 _patch11_hist_images 快照里）
SELECT 'C.4 断言：新行 6100 保持 PENDING' AS step;
SELECT id, status FROM team_page_images WHERE id = 6100;
-- 期望：status = 'PENDING'（未被快照 JOIN 回填误伤）

-- 对比：原历史行 6002 仍为 PUBLISHED（幂等，不会改回）
SELECT id, status FROM team_page_images WHERE id = 6002;
-- 期望：status = 'PUBLISHED'


-- #####################################################################
-- 场景 D（正常路径）：干净库从 ganlu.sql → 10 → 11 → 12 完整跑通
--   断言：历史内容全 PUBLISHED、team.owner_user_id 唯一约束建立
-- #####################################################################
-- D.1 清理重建：重新 SOURCE ganlu.sql，然后依次 SOURCE patch 10/11/12
--     手工执行：
--       SOURCE ganlu.sql
--       SOURCE database/patches/10_team_core.sql
--       SOURCE database/patches/11_team_content.sql
--       SOURCE database/patches/12_team_owner_unique.sql

-- D.2 断言：patch 12 的唯一约束已建立
SELECT 'D.2 断言：uk_team_owner_user 约束存在' AS step;
SELECT COUNT(*) AS uk_exists FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'team' AND index_name = 'uk_team_owner_user';
-- 期望：1

-- D.3 断言：_patch11_hist_* 快照表存在（常驻表）
SELECT 'D.3 断言：快照表为常驻表' AS step;
SELECT COUNT(*) AS snap_images_exists FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = '_patch11_hist_images';
SELECT COUNT(*) AS snap_word_exists FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = '_patch11_hist_word';
-- 期望：均为 1
