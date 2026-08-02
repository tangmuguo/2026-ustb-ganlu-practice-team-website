<script setup>
import { computed } from 'vue'
import { Promotion } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  },
  displayName: {
    type: String,
    default: '注册用户'
  },
  roleName: {
    type: String,
    default: '注册用户'
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const contentLength = computed(() => Array.from(props.modelValue || '').length)
const remaining = computed(() => Math.max(0, 500 - contentLength.value))
const canSubmit = computed(() => Boolean(props.modelValue.trim()) && contentLength.value <= 500)

function updateValue(value) {
  emit('update:modelValue', Array.from(value || '').slice(0, 500).join(''))
}

function submit() {
  if (!props.loading && canSubmit.value) {
    emit('submit')
  }
}
</script>

<template>
  <section class="composer-card" aria-labelledby="message-composer-title">
    <div class="composer-heading">
      <div>
        <p class="composer-kicker">发布新留言</p>
        <h2 id="message-composer-title">把想说的话留在这里</h2>
      </div>
      <div class="identity-chip">
        <span class="identity-dot" aria-hidden="true"></span>
        <span>{{ displayName }}</span>
        <small>{{ roleName }}</small>
      </div>
    </div>

    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="4"
      resize="none"
      placeholder="分享支教见闻、学习疑问，或者给甘露团队留下一句鼓励……"
      aria-label="留言内容"
      @update:model-value="updateValue"
      @keydown.ctrl.enter.prevent="submit"
    />

    <div class="composer-footer">
      <div class="composer-hint">
        <span>Ctrl + Enter 快速发布</span>
        <span :class="{ warning: remaining <= 50 }">还可输入 {{ remaining }} 字</span>
      </div>
      <el-button
        type="primary"
        size="large"
        :icon="Promotion"
        :loading="loading"
        :disabled="!canSubmit || loading"
        @click="submit"
      >
        发布留言
      </el-button>
    </div>
  </section>
</template>

<style scoped>
.composer-card {
  position: relative;
  overflow: hidden;
  padding: 24px 25px;
  border: 1px solid #dfe7e4;
  border-radius: 22px;
  background: rgb(255 255 255 / 96%);
  box-shadow:
    0 20px 54px rgb(47 76 67 / 9%),
    0 2px 8px rgb(32 52 45 / 3%);
}

.composer-card::before {
  position: absolute;
  top: 0;
  right: 24px;
  left: 24px;
  height: 3px;
  border-radius: 0 0 999px 999px;
  background: linear-gradient(90deg, #1e88e5 0 67%, #e4ad46 67% 82%, #459a75 82%);
  content: '';
}

.composer-card::after {
  position: absolute;
  top: -58px;
  right: -48px;
  width: 112px;
  height: 112px;
  border: 19px solid rgb(226 173 70 / 6%);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}

.composer-heading {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.composer-kicker {
  margin: 0 0 4px;
  color: #1e88e5;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

h2 {
  margin: 0;
  color: #203c4d;
  font-size: 22px;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.identity-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid #d7e7e1;
  border-radius: 999px;
  color: #315568;
  background: #f4faf7;
  box-shadow: 0 5px 14px rgb(57 151 111 / 5%);
  font-size: 13px;
  font-weight: 650;
}

.identity-chip > span:not(.identity-dot) {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-chip small {
  color: #64748b;
  font-size: 11px;
  font-weight: 500;
}

.identity-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #39976f;
  box-shadow: 0 0 0 3px rgb(57 151 111 / 14%);
}

:deep(.el-textarea__inner) {
  min-height: 116px !important;
  padding: 16px 17px;
  border-radius: 14px;
  color: #1e293b;
  background: #fbfcfa;
  box-shadow:
    0 0 0 1px #dfe7e4 inset,
    0 5px 16px rgb(15 23 42 / 3%);
  font-size: 15px;
  line-height: 1.7;
  transition: box-shadow 0.2s, background 0.2s;
}

:deep(.el-textarea__inner:focus) {
  background: #fff;
  box-shadow: 0 0 0 1px #1e88e5 inset, 0 0 0 4px rgb(30 136 229 / 9%);
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 16px;
}

.composer-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  color: #94a3b8;
  font-size: 12px;
}

.composer-hint .warning {
  color: #d97706;
  font-weight: 700;
}

.composer-footer :deep(.el-button) {
  min-width: 132px;
  border-radius: 11px;
  font-weight: 700;
}

.composer-footer :deep(.el-button--primary) {
  border: 0;
  background: linear-gradient(105deg, #1878c5, #2d94df);
  box-shadow: 0 9px 20px rgb(30 136 229 / 17%);
}

.composer-footer :deep(.el-button--primary.is-disabled) {
  background: #a8cef8;
  box-shadow: none;
}

@media (max-width: 640px) {
  .composer-card {
    padding: 19px 15px;
    border-radius: 18px;
  }

  .composer-card::before {
    right: 16px;
    left: 16px;
  }

  .composer-heading,
  .composer-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .identity-chip {
    width: fit-content;
    max-width: 100%;
  }

  .composer-footer :deep(.el-button) {
    width: 100%;
  }
}
</style>
