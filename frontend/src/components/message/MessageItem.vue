<script setup>
import { computed } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import ReplyComposer from '@/components/message/ReplyComposer.vue'
import ReplyList from '@/components/message/ReplyList.vue'
import { formatMessageTime } from '@/utils/date'

const props = defineProps({
  message: {
    type: Object,
    required: true
  },
  canReply: {
    type: Boolean,
    default: false
  },
  canDelete: {
    type: Boolean,
    default: false
  },
  replyDraft: {
    type: String,
    default: ''
  },
  replyLoading: {
    type: Boolean,
    default: false
  },
  deletingMessageId: {
    type: [Number, String],
    default: null
  },
  deletingReplyId: {
    type: [Number, String],
    default: null
  }
})

defineEmits([
  'update:replyDraft',
  'submitReply',
  'deleteMessage',
  'deleteReply'
])

const replies = computed(() => (
  Array.isArray(props.message.replies) ? props.message.replies : []
))

const level = computed(() => {
  const value = props.message?.userLevel
    ?? props.message?.level
    ?? props.message?.user?.level
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : null
})

const displayName = computed(() => (
  props.message?.displayName
  || props.message?.teamname
  || props.message?.realname
  || props.message?.username
  || '已注销用户'
))

const initial = computed(() => Array.from(displayName.value)[0] || '甘')

const roleName = computed(() => {
  if (level.value === 0) return '系统管理员'
  if (level.value === 1) return props.message?.teamname || '甘露团队'
  if (level.value === 2) return '学生账号'
  if (props.message?.teamname) return props.message.teamname
  return '注册用户'
})

const roleClass = computed(() => {
  if (level.value === 0) return 'admin'
  if (level.value === 1 || props.message?.teamname) return 'team'
  if (level.value === 2) return 'student'
  return 'member'
})
</script>

<template>
  <article class="message-card" :class="roleClass">
    <header class="message-header">
      <div class="author-avatar" :class="roleClass" aria-hidden="true">
        {{ initial }}
      </div>

      <div class="author-info">
        <div class="author-name-row">
          <h3>{{ displayName }}</h3>
          <span class="role-tag" :class="roleClass">{{ roleName }}</span>
        </div>
        <div class="message-meta">
          <span v-if="message.username && message.username !== displayName">
            @{{ message.username }}
          </span>
          <time>{{ formatMessageTime(message.createTime) }}</time>
        </div>
      </div>

      <el-button
        v-if="canDelete"
        class="delete-message"
        type="danger"
        plain
        :icon="Delete"
        :loading="deletingMessageId === message.id"
        :disabled="deletingMessageId !== null && deletingMessageId !== message.id"
        @click="$emit('deleteMessage', message.id)"
      >
        删除留言
      </el-button>
    </header>

    <p class="message-content">{{ message.content }}</p>

    <ReplyList
      :replies="replies"
      :can-delete="canDelete"
      :deleting-reply-id="deletingReplyId"
      @delete="$emit('deleteReply', $event)"
    />

    <ReplyComposer
      v-if="canReply"
      :model-value="replyDraft"
      :loading="replyLoading"
      @update:model-value="$emit('update:replyDraft', $event)"
      @submit="$emit('submitReply', message.id)"
    />
  </article>
</template>

<style scoped>
.message-card {
  position: relative;
  min-width: 0;
  overflow: hidden;
  padding: 23px 24px 24px;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background:
    linear-gradient(135deg, rgb(255 255 255 / 100%), rgb(251 253 255 / 100%));
  box-shadow:
    0 12px 34px rgb(15 23 42 / 5%),
    0 1px 3px rgb(15 23 42 / 3%);
  transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.message-card::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: #93c5fd;
  content: '';
}

.message-card.admin::before {
  background: linear-gradient(180deg, #fb923c, #fdba74);
}

.message-card.team::before {
  background: linear-gradient(180deg, #22c55e, #86efac);
}

.message-card.student::before {
  background: linear-gradient(180deg, #a855f7, #d8b4fe);
}

.message-card:hover {
  border-color: #cbdff8;
  box-shadow:
    0 18px 44px rgb(30 64 175 / 9%),
    0 2px 6px rgb(15 23 42 / 3%);
  transform: translateY(-1px);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 13px;
  min-width: 0;
}

.author-avatar {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  place-items: center;
  border-radius: 14px;
  color: #1d4ed8;
  background: linear-gradient(145deg, #eff6ff, #bfdbfe);
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 70%);
  font-size: 17px;
  font-weight: 800;
}

.author-avatar.admin {
  color: #9a3412;
  background: linear-gradient(145deg, #fff7ed, #fed7aa);
}

.author-avatar.team {
  color: #166534;
  background: linear-gradient(145deg, #f0fdf4, #bbf7d0);
}

.author-avatar.student {
  color: #7e22ce;
  background: linear-gradient(145deg, #faf5ff, #e9d5ff);
}

.author-info {
  min-width: 0;
  flex: 1;
}

.author-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.author-name-row h3 {
  max-width: 300px;
  margin: 0;
  overflow: hidden;
  color: #172554;
  font-size: 15px;
  letter-spacing: -0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-tag {
  padding: 3px 9px;
  border-radius: 999px;
  color: #475569;
  background: #e2e8f0;
  font-size: 11px;
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

.message-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  margin-top: 5px;
  color: #94a3b8;
  font-size: 12px;
}

.delete-message {
  flex: 0 0 auto;
  border-radius: 9px;
  background: #fffafa;
}

.message-content {
  margin: 19px 0 0;
  color: #27364b;
  font-size: 15px;
  line-height: 1.82;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

@media (max-width: 640px) {
  .message-card {
    padding: 18px 14px 19px;
    border-radius: 17px;
  }

  .message-card:hover {
    transform: none;
  }

  .message-header {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .author-avatar {
    width: 40px;
    height: 40px;
    flex-basis: 40px;
    border-radius: 12px;
  }

  .author-info {
    width: calc(100% - 53px);
    flex: 1 1 calc(100% - 53px);
  }

  .author-name-row h3 {
    max-width: 180px;
  }

  .delete-message {
    margin-left: 53px;
  }
}
</style>
