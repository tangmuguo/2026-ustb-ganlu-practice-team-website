<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadLog, uploadHonor } from '@/apis/fengcaiAPI'
import TeamAttachmentUpload from '@/components/fengcai/TeamAttachmentUpload.vue'

const emit = defineEmits(['uploaded'])

const formRef = ref(null)
const submitting = ref(false)

const form = ref({
  type: 4, // 默认团队日志
  caption: '',
  content: '',
  logDate: '',
})

const datatype = [
  { value: 4, label: '团队日志' },
  { value: 3, label: '团队荣誉' },
]

const datatype_value = ref(datatype[0].value)

const rules = {
  caption: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

// 上传日志/荣誉后返回的 id，用于关联附件
const lastUploadedId = ref(null)

const submitForm = async () => {
  try {
    const valid = await formRef.value.validate()
    if (!valid) return

    submitting.value = true
    form.value.type = datatype_value.value

    const payload = {
      caption: form.value.caption,
      content: form.value.content,
      logDate: form.value.logDate || undefined,
    }

    // type=4 → logs，type=3 → honors
    const res = (form.value.type === 4)
      ? await uploadLog(payload)
      : await uploadHonor(payload)

    if (res.data.code === 200) {
      ElMessage.success('提交成功，等待审核')
      lastUploadedId.value = res.data.content?.id || null
      // 重置表单
      formRef.value.resetFields()
      form.value.logDate = ''
      emit('uploaded')
    } else {
      ElMessage.error(res.data.message || '提交失败')
    }
  } catch (error) {
    ElMessage.error('提交失败: ' + error.message)
  } finally {
    submitting.value = false
  }
}

function handleAttachmentUploaded(media) {
  ElMessage.success('附件已关联')
}
</script>

<template>
  <div class="upload-section">
    <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
      <el-form-item label="类型" prop="type">
        <el-select
          v-model="datatype_value"
          placeholder="请选择类型"
          style="width: 100%"
        >
          <el-option
            v-for="item in datatype"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="标题" prop="caption">
        <el-input v-model="form.caption" placeholder="请输入标题" />
      </el-form-item>

      <el-form-item label="内容" prop="content">
        <el-input
          type="textarea"
          :rows="5"
          v-model="form.content"
          placeholder="请输入详细内容"
        />
      </el-form-item>

      <el-form-item label="日期">
        <el-date-picker
          v-model="form.logDate"
          type="date"
          placeholder="选择日期（可选）"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          @click="submitForm"
          :loading="submitting"
        >提交内容</el-button>
      </el-form-item>
    </el-form>

    <!-- 附件上传：需先提交日志/荣誉拿到 id 后才能关联 -->
    <div class="attachment-section" v-if="lastUploadedId">
      <h4>关联附件（日志ID: {{ lastUploadedId }}）</h4>
      <TeamAttachmentUpload
        relatedType="WORD"
        :relatedId="lastUploadedId"
        @uploaded="handleAttachmentUploaded"
      />
    </div>
    <div class="attachment-section" v-else>
      <el-alert
        title="请先提交日志/荣誉内容后再上传附件"
        type="info"
        :closable="false"
        show-icon
      />
    </div>
  </div>
</template>

<style scoped>
.upload-section {
  background-color: #fff;
  padding: 20px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
.attachment-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}
.attachment-section h4 {
  margin-bottom: 10px;
  color: #606266;
}
</style>
