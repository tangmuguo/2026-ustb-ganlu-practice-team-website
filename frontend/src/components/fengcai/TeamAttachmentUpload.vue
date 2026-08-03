<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadMedia } from '@/apis/fengcaiAPI'

const props = defineProps({
  relatedType: { type: String, default: '' },
  relatedId: { type: Number, default: null }
})

const emit = defineEmits(['uploaded'])

const uploading = ref(false)
const progress = ref(0)
const fileList = ref([])

// 允许的扩展名
const ALLOWED_EXT = ['mp4', 'mov', 'pdf', 'doc', 'docx', 'ppt', 'pptx', 'zip']
const MAX_SIZE = 200 * 1024 * 1024 // 200MB

function getExt(filename) {
  const dot = filename.lastIndexOf('.')
  return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : ''
}

function beforeUpload(file) {
  const ext = getExt(file.name)
  if (!ALLOWED_EXT.includes(ext)) {
    ElMessage.error(`不支持的文件类型: .${ext}`)
    return false
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('文件大小超过 200MB 限制')
    return false
  }
  return true
}

async function handleFileChange(file, list) {
  if (!file || !file.raw) return
  if (!beforeUpload(file.raw)) {
    fileList.value = []
    return
  }

  const formFile = file.raw
  uploading.value = true
  progress.value = 0

  try {
    const extra = {}
    if (props.relatedType) extra.relatedType = props.relatedType
    if (props.relatedId != null) extra.relatedId = props.relatedId

    const res = await uploadMedia(formFile, extra)
    if (res.data.code === 200) {
      ElMessage.success('上传成功')
      emit('uploaded', res.data.content)
      fileList.value = []
    } else {
      ElMessage.error(res.data.message || '上传失败')
    }
  } catch (error) {
    ElMessage.error('上传失败: ' + (error.message || '未知错误'))
  } finally {
    uploading.value = false
    progress.value = 0
  }
}
</script>

<template>
  <div class="attachment-upload">
    <el-upload
      :file-list="fileList"
      :before-upload="beforeUpload"
      :on-change="handleFileChange"
      :auto-upload="false"
      :show-file-list="true"
      :limit="1"
      accept=".mp4,.mov,.pdf,.doc,.docx,.ppt,.pptx,.zip"
      drag
    >
      <el-icon class="el-icon--upload"><i>📎</i></el-icon>
      <div class="el-upload__text">拖拽文件到此处或 <em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">
          支持 MP4/MOV/PDF/DOC/DOCX/PPT/PPTX/ZIP，单文件不超过 200MB
        </div>
      </template>
    </el-upload>

    <el-progress
      v-if="uploading"
      :percentage="progress"
      :stroke-width="10"
      style="margin-top: 10px;"
    />
  </div>
</template>

<style scoped>
.attachment-upload {
  width: 100%;
}
</style>
