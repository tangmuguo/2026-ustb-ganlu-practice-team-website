<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createContentReport } from '@/apis/contentSafetyAPI'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  targets: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:modelValue', 'submitted'])

const targetKey = ref('')
const category = ref('OTHER')
const description = ref('')
const submitting = ref(false)

const targetOptions = computed(() => (Array.isArray(props.targets) ? props.targets : []).filter((target) => (
  target && target.targetType && Number.isInteger(Number(target.targetId)) && Number(target.targetId) > 0
)))

const selectedTarget = computed(() => targetOptions.value.find((target) => target.key === targetKey.value))

function syncTarget() {
  if (!selectedTarget.value) targetKey.value = targetOptions.value[0]?.key || ''
}

watch(() => props.modelValue, (visible) => {
  if (visible) syncTarget()
})

watch(targetOptions, syncTarget, { immediate: true })

function close() {
  if (!submitting.value) emit('update:modelValue', false)
}

async function submit() {
  if (!selectedTarget.value || submitting.value) return

  submitting.value = true
  try {
    const payload = {
      targetType: selectedTarget.value.targetType,
      targetId: Number(selectedTarget.value.targetId),
      category: category.value,
    }
    const trimmedDescription = description.value.trim()
    if (trimmedDescription) payload.description = trimmedDescription

    const response = await createContentReport(payload)
    const body = response?.data
    if (Number(body?.code) !== 200) throw new Error(body?.message || '举报提交失败')

    ElMessage.success('举报已受理，请保存工单编号')
    emit('submitted', body?.content?.ticketId)
    emit('update:modelValue', false)
    description.value = ''
    category.value = 'OTHER'
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '举报提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="举报公开内容"
    width="min(520px, calc(100vw - 28px))"
    :close-on-click-modal="false"
    @close="close"
  >
    <p class="privacy-note">
      举报只记录必要的目标、分类和说明；不会要求填写电话、姓名或其他联系方式。
    </p>

    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="举报目标" required>
        <el-select v-model="targetKey" class="full-width" placeholder="请选择公开内容">
          <el-option
            v-for="target in targetOptions"
            :key="target.key"
            :label="target.label"
            :value="target.key"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="问题分类" required>
        <el-select v-model="category" class="full-width">
          <el-option label="骚扰或仇恨" value="HARASSMENT" />
          <el-option label="有害或违法内容" value="HARMFUL" />
          <el-option label="隐私问题" value="PRIVACY" />
          <el-option label="欺诈或误导" value="FRAUD" />
          <el-option label="版权问题" value="COPYRIGHT" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>

      <el-form-item label="补充说明（可选）">
        <el-input
          v-model="description"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="请描述需要审核的具体问题，不要填写身份证号、电话或其他不必要的个人信息。"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="close">取消</el-button>
      <el-button type="warning" :loading="submitting" :disabled="!selectedTarget" @click="submit">
        提交举报
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.privacy-note {
  margin: 0 0 18px;
  padding: 10px 12px;
  border: 1px solid #f2dfb1;
  border-radius: 8px;
  color: #795c25;
  background: #fffaf0;
  font-size: 13px;
  line-height: 1.65;
}

.full-width {
  width: 100%;
}
</style>
