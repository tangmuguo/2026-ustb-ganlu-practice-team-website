<script setup>
defineProps({
  message: {
    type: Object,
    required: true,
  },
  retryable: {
    type: Boolean,
    default: false,
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['retry'])
</script>

<template>
  <article
    class="message-row"
    :class="`message-row--${message.role}`"
    :aria-label="message.role === 'user' ? '你的消息' : 'AI 小助手的回答'"
  >
    <div class="avatar" aria-hidden="true">
      {{ message.role === 'user' ? '你' : 'AI' }}
    </div>

    <div class="message-column">
      <span class="speaker">{{ message.role === 'user' ? '你' : 'AI 小助手' }}</span>
      <div class="message-bubble">
        <p class="message-content">{{ message.content }}</p>
      </div>

      <div
        v-if="message.role === 'user' && ['error', 'stopped'].includes(message.status)"
        class="message-status"
      >
        <span>{{ message.status === 'stopped' ? '已停止生成' : '发送失败' }}</span>
        <el-button
          v-if="retryable"
          link
          type="primary"
          :disabled="loading"
          @click="$emit('retry', message.id)"
        >
          重试
        </el-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
}

.message-row--user {
  flex-direction: row-reverse;
}

.avatar {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(145deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.2);
}

.message-row--user .avatar {
  background: linear-gradient(145deg, #22a06b, #138a57);
  box-shadow: 0 6px 16px rgba(19, 138, 87, 0.18);
}

.message-column {
  display: flex;
  min-width: 0;
  max-width: min(78%, 720px);
  flex-direction: column;
  align-items: flex-start;
}

.message-row--user .message-column {
  align-items: flex-end;
}

.speaker {
  margin: 0 4px 5px;
  color: #6b7280;
  font-size: 12px;
}

.message-bubble {
  max-width: 100%;
  padding: 13px 16px;
  border: 1px solid #e5eaf0;
  border-radius: 5px 18px 18px 18px;
  background: #fff;
  box-shadow: 0 4px 16px rgba(30, 64, 175, 0.06);
}

.message-row--user .message-bubble {
  border-color: #d6f1e4;
  border-radius: 18px 5px 18px 18px;
  background: #eaf8f1;
  box-shadow: none;
}

.message-content {
  overflow-wrap: anywhere;
  color: #263142;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.message-status {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;
  color: #c2413b;
  font-size: 12px;
}

@media (max-width: 640px) {
  .message-row {
    gap: 8px;
  }

  .avatar {
    flex-basis: 32px;
    width: 32px;
    height: 32px;
    border-radius: 10px;
  }

  .message-column {
    max-width: 84%;
  }

  .message-bubble {
    padding: 11px 13px;
  }
}
</style>
