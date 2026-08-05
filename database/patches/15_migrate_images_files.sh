#!/usr/bin/env bash
# =====================================================================
# Patch 15 配套：历史团队风采图片物理文件搬迁（images/ → images_pending/）
#
# 前置：先执行 15_team_content_history_publish.sql（它已把 DB 的 imageUrl 从
#        images/ 改指 images_pending/）。本脚本负责把实际物理文件搬过去。
#
# 幂等可重入：逐文件检查
#   - 源 images/{file} 存在 且 目标 images_pending/{file} 不存在 → mv
#   - 目标已存在 → 跳过（已搬过，或同名冲突需人工核查）
#   - 源不存在 → 跳过（已搬过，或本就不存在）
#
# 安全：images/ 与课件缩略图混存，本脚本只搬 team_page_images 表引用的文件，
#       通过 SQL 查询imageUrl 清单精确匹配，不整目录 mv。
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
: "${UPLOAD_DIR:?需要设置 UPLOAD_DIR（文件上传根目录）}"

IMAGES_DIR="${UPLOAD_DIR%/}/images"
PENDING_DIR="${UPLOAD_DIR%/}/images_pending"

echo "=== 团队风采图片搬迁 ==="
echo "源目录:   $IMAGES_DIR"
echo "目标目录: $PENDING_DIR"
echo ""

mkdir -p "$PENDING_DIR"

# 查询需要搬迁的文件清单（DB 的 imageUrl 已指 images_pending/，提取纯文件名）
# 排除已搬过的（imageUrl 仍指 images/ 的说明 patch 15 SQL 没跑到，不该在此阶段）
# 查询失败（连接/鉴权/表不存在）必须大声失败：patch 15 SQL 已把 imageUrl 标记为
# images_pending/，若此处静默吞错，文件永远不搬，历史公开图全部 404 且无人察觉。
# （set -e 下 mysql 非零退出会中止脚本，stderr 保留给操作员。）
FILE_LIST=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" -D"$MYSQL_DB" -N -e \
  "SELECT REPLACE(imageUrl, 'images_pending/', '') FROM team_page_images WHERE imageUrl LIKE 'images_pending/%'")

if [ -z "$FILE_LIST" ]; then
    echo "DB 无 images_pending/ 引用，无需搬迁（或 patch 15 SQL 未执行）。"
    exit 0
fi

moved=0
skipped=0
missing=0

while IFS= read -r filename; do
    [ -z "$filename" ] && continue
    src="$IMAGES_DIR/$filename"
    dst="$PENDING_DIR/$filename"

    if [ ! -f "$src" ]; then
        # 源不存在：可能已搬过，或本就缺失
        if [ -f "$dst" ]; then
            echo "[跳过-已搬] $filename"
            skipped=$((skipped + 1))
        else
            echo "[缺失]    $filename（源和目标都不存在，DB 引用悬空）"
            missing=$((missing + 1))
        fi
        continue
    fi

    if [ -f "$dst" ]; then
        echo "[跳过-冲突] $filename（目标已存在，需人工核查是否同名）"
        skipped=$((skipped + 1))
        continue
    fi

    mv "$src" "$dst"
    echo "[搬迁]    $filename"
    moved=$((moved + 1))
done <<< "$FILE_LIST"

echo ""
echo "=== 完成 ==="
echo "搬迁: $moved"
echo "跳过: $skipped"
echo "缺失: $missing（DB 引用悬空，需人工核查）"
