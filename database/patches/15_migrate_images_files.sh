#!/usr/bin/env bash
# =====================================================================
# Patch 15 配套：历史团队风采图片物理文件搬迁（images/ → images_pending/）
#
# 前置：先执行 15_team_content_history_publish.sql（确认运行阶段）。
#   该 SQL 已把需要搬迁的记录写入持久化清单表 _patch15_image_migration
#   （含 id, old_url, new_url），并把 DB 的 imageUrl 改指 images_pending/。
#   本脚本从清单表读取记录，把实际物理文件从 images/{basename} 搬到
#   images_pending/{basename}。
#
# 幂等可重入：逐文件检查
#   - 源存在 + 目标不存在 → mv
#   - 源和目标都存在 → 内容相同（cmp）则删除源（清理公开目录残留），否则计冲突
#   - 源不存在 + 目标存在 → 跳过（已搬过）
#   - 源和目标都不存在 → 计缺失（DB 引用悬空）
#
# 退出码（exy v6 P1#3）：
#   - 全部成功或仅跳过 → exit 0
#   - 任一 missing（源目标都不存在，DB 引用悬空）或 conflict（目标已存在且内容不同）
#     → exit 1，防止部署系统把"部分成功/数据悬空"误判为成功
#   - 搬迁结束后执行后置校验：清单内每条记录的目标文件必须存在，否则 exit 1
#
# 安全：images/ 与课件缩略图混存，本脚本只搬 _patch15_image_migration 清单表
#       引用的文件，不整目录 mv。
#
# 凭据（exy v6 P2#7）：密码不再放进程命令行（-p 会暴露在 argv），改用
#   --defaults-extra-file 指向权限 0600 的临时 my.cnf，脚本退出时清理。
#
# 用法：
#   MYSQL_HOST=... MYSQL_PORT=... MYSQL_USER=... MYSQL_PASS=... MYSQL_DB=ganlu \
#   UPLOAD_DIR=/path/to/uploads bash 15_migrate_images_files.sh
# =====================================================================
set -euo pipefail

: "${MYSQL_HOST:=127.0.0.1}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:=root}"
: "${MYSQL_PASS:?需要设置 MYSQL_PASS}"
: "${MYSQL_DB:=ganlu}"
: "${MYSQL_SOCKET:=}"
: "${UPLOAD_DIR:?需要设置 UPLOAD_DIR（文件上传根目录）}"

IMAGES_DIR="${UPLOAD_DIR%/}/images"
PENDING_DIR="${UPLOAD_DIR%/}/images_pending"

echo "=== 团队风采图片搬迁（清单驱动）==="
echo "源目录:   $IMAGES_DIR"
echo "目标目录: $PENDING_DIR"
echo ""

# exy v6 P2#7：用权限 0600 的临时 --defaults-extra-file 传递凭据，避免密码进 argv。
umask 077
CNF_FILE="$(mktemp "${TMPDIR:-/tmp}/ganlu-migrate.XXXXXX.cnf")"
trap 'rm -f "$CNF_FILE"' EXIT
# MYSQL_SOCKET 非空时走 socket 连接（本地开发/单机部署常见），否则走 TCP host:port。
if [ -n "$MYSQL_SOCKET" ]; then
cat > "$CNF_FILE" <<EOF
[client]
socket=${MYSQL_SOCKET}
user=${MYSQL_USER}
password=${MYSQL_PASS}
EOF
else
cat > "$CNF_FILE" <<EOF
[client]
host=${MYSQL_HOST}
port=${MYSQL_PORT}
user=${MYSQL_USER}
password=${MYSQL_PASS}
EOF
fi
# 再次收紧权限（mktemp 默认 0600，双保险）
chmod 600 "$CNF_FILE"

mkdir -p "$PENDING_DIR"

# 从持久化清单表读取需搬迁记录（id, old_url, new_url）。
# exy v6 P1#2：旧版查所有 imageUrl LIKE 'images_pending/%' 会漏 Windows 反斜杠路径，
#   现直接消费 SQL 生成的清单表，精确匹配每条记录。
# -r（--raw）：禁用 mysql 批处理模式对 \、\n、\t 等的转义，保证 old_url 中的 Windows
#   反斜杠路径（images\hist.jpg）原样输出，与文件系统 basename 精确对应。
# 查询失败（连接/鉴权/清单表不存在）必须大声失败（set -e 下 mysql 非零退出中止）：
#   若此处静默吞错，文件永远不搬，历史公开图全部 404 且无人察觉。
MIGRATION_RECORDS="$(mysql --defaults-extra-file="$CNF_FILE" -D "$MYSQL_DB" -N -B -r -e \
  "SELECT id, old_url, new_url FROM _patch15_image_migration ORDER BY id")"

if [ -z "$MIGRATION_RECORDS" ]; then
    echo "迁移清单为空（无历史 images/ 或 images\\ 记录需搬迁，或 patch 15 SQL 未执行确认阶段）。"
    exit 0
fi

moved=0
skipped=0
missing=0
conflict=0

while IFS=$'\t' read -r rec_id old_url new_url; do
    [ -z "$rec_id" ] && continue
    # 从 old_url 取 basename 作为源（images/xxx.jpg 或 images\xxx.jpg → xxx.jpg），
    # 从 new_url 取 basename 作为目标（images_pending/xxx.jpg → xxx.jpg）。
    # SQL 的 SUBSTRING 已保证 new_url 永远是 images_pending/<basename>。
    old_base="${old_url#*/}"        # 去掉首个 / 之前部分（处理 images/xxx.jpg）
    case "$old_url" in
      *'\'*) old_base="${old_url#*\\}" ;;  # 处理 Windows images\xxx.jpg
    esac
    new_base="${new_url#*/}"        # images_pending/xxx.jpg → xxx.jpg
    src="$IMAGES_DIR/$old_base"
    dst="$PENDING_DIR/$new_base"

    if [ ! -f "$src" ]; then
        # 源不存在：可能已搬过，或本就缺失
        if [ -f "$dst" ]; then
            echo "[跳过-已搬] id=$rec_id $old_base"
            skipped=$((skipped + 1))
        else
            echo "[缺失]    id=${rec_id} ${old_base}（源和目标都不存在，DB 引用悬空）"
            missing=$((missing + 1))
        fi
        continue
    fi

    if [ -f "$dst" ]; then
        # 目标已存在：必须比较内容，仅在确认相同时清理公开目录源文件（exy v6 P1#3）
        if cmp -s "$src" "$dst"; then
            echo "[冲突-内容相同] id=$rec_id $old_base → 删除公开目录残留源文件"
            rm -f "$src"
            moved=$((moved + 1))
        else
            echo "[冲突-内容不同] id=${rec_id} ${old_base}（目标已存在且内容不同，需人工核查）"
            conflict=$((conflict + 1))
        fi
        continue
    fi

    mv "$src" "$dst"
    echo "[搬迁]    id=$rec_id $old_base"
    moved=$((moved + 1))
done <<< "$MIGRATION_RECORDS"

# exy v6 P1#3：后置校验——清单内每条记录的目标文件必须实际存在，否则视为失败。
postcheck_failed=0
while IFS=$'\t' read -r rec_id old_url new_url; do
    [ -z "$rec_id" ] && continue
    new_base="${new_url#*/}"
    dst="$PENDING_DIR/$new_base"
    if [ ! -f "$dst" ]; then
        echo "[后置校验失败] id=$rec_id 目标文件不存在: $dst"
        postcheck_failed=$((postcheck_failed + 1))
    fi
done <<< "$MIGRATION_RECORDS"

echo ""
echo "=== 完成 ==="
echo "搬迁(含去重): ${moved}"
echo "跳过(已搬):   ${skipped}"
echo "缺失(悬空):   ${missing}（DB 引用悬空，需人工核查）"
echo "冲突(内容异): ${conflict}（需人工核查）"
echo "后置校验失败: ${postcheck_failed}"

# 任一缺失/冲突/后置失败 → 非零退出，防止部署系统误判成功（exy v6 P1#3）
if [ "$missing" -gt 0 ] || [ "$conflict" -gt 0 ] || [ "$postcheck_failed" -gt 0 ]; then
    echo "❌ 存在缺失/冲突/后置校验失败，搬迁不完整，退出码 1" >&2
    exit 1
fi
echo "✅ 全部清单记录搬迁成功"
exit 0
