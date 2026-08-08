<script setup>
import { computed, ref } from 'vue'
import { AI_MAX_INPUT_LENGTH } from '@/stores/aiStore'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  loading: {
    type: Boolean,
    default: false,
  },
  hasMessages: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue', 'send', 'stop', 'clear'])
const inputRef = ref(null)
const validationTouched = ref(false)

const textLength = computed(() => props.modelValue.length)
const trimmedLength = computed(() => props.modelValue.trim().length)
const isTooLong = computed(() => trimmedLength.value > AI_MAX_INPUT_LENGTH)
const canSend = computed(
  () => !props.loading && trimmedLength.value > 0 && !isTooLong.value,
)
const showValidation = computed(
  () => isTooLong.value || (validationTouched.value && trimmedLength.value === 0),
)

function updateValue(value) {
  validationTouched.value = false
  emit('update:modelValue', value)
}

function send() {
  validationTouched.value = true
  if (!canSend.value) return
  emit('send')
}

function handleKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  send()
}

function focus() {
  inputRef.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div class="composer-wrap">
    <div class="composer" :class="{ 'composer--invalid': showValidation }">
      <el-input
        ref="inputRef"
        :model-value="modelValue"
        :disabled="loading"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        resize="none"
        placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
        aria-label="向 AI 小助手提问"
        @input="updateValue"
        @keydown="handleKeydown"
      />

      <div class="composer-toolbar">
        <div class="composer-meta" aria-live="polite">
          <span v-if="showValidation" class="validation-message">
            {{ isTooLong ? `最多输入 ${AI_MAX_INPUT_LENGTH} 字` : '请输入问题内容' }}
          </span>
          <span
            class="character-count"
            :class="{ 'character-count--over': isTooLong }"
          >
            {{ textLength }}/{{ AI_MAX_INPUT_LENGTH }}
          </span>
        </div>

        <div class="composer-actions">
          <el-button
            size="small"
            :disabled="!hasMessages && !modelValue"
            @click="emit('clear')"
          >
            清空
          </el-button>
          <el-button v-if="loading" size="small" type="danger" plain @click="emit('stop')">
            停止
          </el-button>
          <el-button v-else size="small" type="primary" :disabled="!canSend" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </div>
    <p class="privacy-note">回答仅供学习参考，请勿提交学生姓名、联系方式等隐私信息</p>
  </div>
</template>

<style scoped>
.composer-wrap {
  flex: 0 0 auto;
  padding: 12px 18px 14px;
  border-top: 1px solid #e8edf3;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
}

.composer {
  overflow: hidden;
  border: 1px solid #d8e0e9;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(31, 41, 55, 0.06);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.composer:focus-within {
  border-color: #7eb2f7;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.1);
}

.composer--invalid {
  border-color: #ef9a94;
}

:deep(.el-textarea__inner) {
  min-height: 58px !important;
  padding: 13px 14px 6px;
  border: 0;
  box-shadow: none;
  color: #263142;
  font-family: inherit;
  line-height: 1.55;
}

:deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.composer-toolbar {
  display: flex;
  min-height: 42px;
  padding: 5px 8px 8px 13px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.composer-meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: #98a2b3;
  font-size: 12px;
}

.validation-message,
.character-count--over {
  color: #d0443e;
}

.composer-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 6px;
}

.composer-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.privacy-note {
  margin-top: 7px;
  color: #8993a4;
  font-size: 11px;
  text-align: center;
}

@media (max-width: 640px) {
  .composer-wrap {
    position: relative;
    padding: 10px 10px max(10px, env(safe-area-inset-bottom));
  }

  .composer {
    border-radius: 14px;
  }

  .composer-toolbar {
    padding-left: 10px;
  }

  .validation-message {
    display: none;
  }

  .privacy-note {
    padding: 0 8px;
    line-height: 1.45;
  }
}
</style>
