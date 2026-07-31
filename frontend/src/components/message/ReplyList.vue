<script setup>
import { Delete } from '@element-plus/icons-vue'
import { formatMessageTime } from '@/utils/date'

defineProps({
  replies: {
    type: Array,
    default: () => []
  },
  canDelete: {
    type: Boolean,
    default: false
  },
  deletingReplyId: {
    type: [Number, String],
    default: null
  }
})

defineEmits(['delete'])

function getLevel(item) {
  const value = item?.userLevel ?? item?.level ?? item?.user?.level
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : null
}

function getDisplayName(item) {
  return item?.displayName
    || item?.teamname
    || item?.realname
    || item?.username
    || '已注销用户'
}

function getInitial(item) {
  return Array.from(getDisplayName(item))[0] || '甘'
}

function getRoleName(item) {
  const level = getLevel(item)
  if (level === 0) return '系统管理员'
  if (level === 1) return item?.teamname || '甘露团队'
  if (level === 2) return '学生账号'
  if (item?.teamname) return item.teamname
  return '注册用户'
}

function getRoleClass(item) {
  const level = getLevel(item)
  if (level === 0) return 'admin'
  if (level === 1 || item?.teamname) return 'team'
  if (level === 2) return 'student'
  return 'member'
}
</script>

<template>
  <section v-if="replies.length" class="reply-section" aria-label="回复列表">
    <div class="reply-title">
      <span>共 {{ replies.length }} 条回复</span>
      <span class="reply-line" aria-hidden="true"></span>
    </div>

    <div class="reply-list">
      <article v-for="reply in replies" :key="reply.id" class="reply-item">
        <div class="reply-avatar" :class="getRoleClass(reply)" aria-hidden="true">
          {{ getInitial(reply) }}
        </div>
        <div class="reply-main">
          <div class="reply-meta">
            <div class="reply-author">
              <strong>{{ getDisplayName(reply) }}</strong>
              <span class="role-tag" :class="getRoleClass(reply)">
                {{ getRoleName(reply) }}
              </span>
            </div>
            <div class="reply-side">
              <time>{{ formatMessageTime(reply.createTime) }}</time>
              <el-button
                v-if="canDelete"
                type="danger"
                text
                :icon="Delete"
                :loading="deletingReplyId === reply.id"
                :disabled="deletingReplyId !== null && deletingReplyId !== reply.id"
                aria-label="删除回复"
                @click="$emit('delete', reply.id)"
              >
                删除
              </el-button>
            </div>
          </div>
          <p class="reply-content">{{ reply.content }}</p>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.reply-section {
  margin-top: 19px;
  padding-left: 16px;
  border-left: 2px solid #dbeafe;
}

.reply-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.reply-line {
  height: 1px;
  flex: 1;
  background: linear-gradient(90deg, #dbeafe, transparent);
}

.reply-list {
  display: grid;
  gap: 10px;
}

.reply-item {
  display: flex;
  gap: 12px;
  min-width: 0;
  padding: 13px 14px;
  border: 1px solid #edf2f7;
  border-radius: 13px;
  background: rgb(248 250 252 / 82%);
}

.reply-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 10px;
  color: #1d4ed8;
  background: #dbeafe;
  font-size: 13px;
  font-weight: 800;
}

.reply-avatar.admin {
  color: #7c2d12;
  background: #ffedd5;
}

.reply-avatar.team {
  color: #166534;
  background: #dcfce7;
}

.reply-avatar.student {
  color: #6b21a8;
  background: #f3e8ff;
}

.reply-main {
  min-width: 0;
  flex: 1;
}

.reply-meta,
.reply-author,
.reply-side {
  display: flex;
  align-items: center;
}

.reply-meta {
  justify-content: space-between;
  gap: 12px;
}

.reply-author {
  min-width: 0;
  flex-wrap: wrap;
  gap: 7px;
}

.reply-author strong {
  max-width: 210px;
  overflow: hidden;
  color: #1e293b;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reply-side {
  flex: 0 0 auto;
  gap: 4px;
  color: #94a3b8;
  font-size: 11px;
}

.reply-side :deep(.el-button) {
  height: 28px;
  padding: 4px 7px;
  font-size: 12px;
}

.role-tag {
  padding: 2px 7px;
  border-radius: 999px;
  color: #475569;
  background: #e2e8f0;
  font-size: 10px;
  font-weight: 700;
}

.role-tag.admin {
  color: #9a3412;
  background: #ffedd5;
}

.role-tag.team {
  color: #15803d;
  background: #dcfce7;
}

.role-tag.student {
  color: #7e22ce;
  background: #f3e8ff;
}

.reply-content {
  margin: 7px 0 0;
  color: #3f4f64;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

@media (max-width: 640px) {
  .reply-section {
    padding-left: 10px;
  }

  .reply-item {
    gap: 9px;
    padding: 12px 10px;
  }

  .reply-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }

  .reply-side {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
