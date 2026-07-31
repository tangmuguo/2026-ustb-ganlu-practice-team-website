<script setup>
import { computed } from 'vue'
import { ChatLineRound } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const contentLength = computed(() => Array.from(props.modelValue || '').length)
const remaining = computed(() => Math.max(0, 300 - contentLength.value))
const canSubmit = computed(() => Boolean(props.modelValue.trim()) && contentLength.value <= 300)

function updateValue(value) {
  emit('update:modelValue', Array.from(value || '').slice(0, 300).join(''))
}

function submit() {
  if (!props.loading && canSubmit.value) {
    emit('submit')
  }
}
</script>

<template>
  <div class="reply-composer">
    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="2"
      resize="none"
      placeholder="写下你的回复……"
      aria-label="回复内容"
      @update:model-value="updateValue"
      @keydown.ctrl.enter.prevent="submit"
    />
    <div class="reply-actions">
      <span :class="{ warning: remaining <= 30 }">还可输入 {{ remaining }} 字</span>
      <el-button
        type="primary"
        plain
        :icon="ChatLineRound"
        :loading="loading"
        :disabled="!canSubmit || loading"
        @click="submit"
      >
        回复
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.reply-composer {
  margin-top: 18px;
  padding: 13px;
  border: 1px solid #dbe7f5;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fbff, #f8fafc);
}

:deep(.el-textarea__inner) {
  padding: 12px 14px;
  border-radius: 10px;
  color: #334155;
  background: #fff;
  box-shadow:
    0 0 0 1px #dbe4f0 inset,
    0 4px 12px rgb(15 23 42 / 3%);
  line-height: 1.65;
}

:deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px #60a5fa inset, 0 0 0 3px rgb(96 165 250 / 10%);
}

.reply-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  margin-top: 10px;
  color: #94a3b8;
  font-size: 12px;
}

.reply-actions .warning {
  color: #d97706;
  font-weight: 700;
}

.reply-actions :deep(.el-button) {
  min-width: 86px;
  border-radius: 9px;
  font-weight: 700;
}
</style>
