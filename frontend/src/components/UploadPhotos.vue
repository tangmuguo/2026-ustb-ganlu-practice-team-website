<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadMember, uploadPhotoNew } from '@/apis/fengcaiAPI'

const emit = defineEmits(['uploaded'])

const formRef = ref(null)
const submitting = ref(false)

const form = ref({
  type: 1, // 默认队员照片
  caption: '',
  content: '',
  logDate: '',
  rawFile: null,
})

const datatype = [
  { value: 1, label: '队员照片' },
  { value: 2, label: '支教照片' },
  { value: 3, label: '地区照片' },
]

const datatype_value = ref(datatype[0].value)

const rules = {
  caption: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

function handleFileChange(uploadFile) {
  if (uploadFile && uploadFile.raw) {
    form.value.rawFile = uploadFile.raw
  }
}

function handleFileRemove() {
  form.value.rawFile = null
}

const submitForm = async () => {
  if (!form.value.rawFile) {
    ElMessage.warning('请先选择图片文件')
    return
  }

  try {
    const valid = await formRef.value.validate()
    if (!valid) return

    submitting.value = true
    form.value.type = datatype_value.value

    const extra = {
      caption: form.value.caption,
      content: form.value.content,
      logDate: form.value.logDate || undefined,
    }
    const file = form.value.rawFile
    const res = (form.value.type === 1)
      ? await uploadMember(file, extra)
      : await uploadPhotoNew(file, extra)

    if (res.data.code === 200) {
      ElMessage.success('提交成功，等待审核')
      // 重置表单
      formRef.value.resetFields()
      form.value.rawFile = null
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
</script>

<template>
  <div class="upload-section">
    <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
      <el-form-item label="文件类型" prop="type">
        <el-select
          v-model="datatype_value"
          placeholder="请选择文件类型"
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

      <el-form-item label="图片上传" required>
        <el-upload
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :limit="1"
          :show-file-list="true"
          accept="image/*"
        >
          <el-button type="primary">选择图片</el-button>
          <template #tip>
            <div class="el-upload__tip">支持 JPG/PNG/WEBP，不超过 10MB</div>
          </template>
        </el-upload>
      </el-form-item>

      <el-form-item label="照片标题" prop="caption">
        <el-input v-model="form.caption" placeholder="请输入标题" />
      </el-form-item>

      <el-form-item label="说明" prop="content">
        <el-input
          type="textarea"
          :rows="4"
          v-model="form.content"
          placeholder="请输入详细说明"
        />
      </el-form-item>

      <el-form-item label="拍摄日期">
        <el-date-picker
          v-model="form.logDate"
          type="date"
          placeholder="选择拍摄日期（可选）"
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
  </div>
</template>

<style scoped>
.upload-section {
  background-color: #fff;
  padding: 20px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}
</style>
