<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { cancelPublicImageUpload, stagePublicImage } from '@/apis/fengcaiAPI'
import {
  isPublicImageAcceptValid,
  normalizePublicImageUploadInfo,
  parsePublicImageAccept
} from '@/utils/publicImageUpload'

const props = defineProps({
  accept: {
    type: String,
    required: true,
    validator: isPublicImageAcceptValid
  },
  tipText: { type: String, default: '支持 JPG、PNG、WebP 图片' },
  maxSizeMb: { type: Number, default: 5 }
})

const emit = defineEmits(['upload', 'error'])
const uploadRef = ref()
const currentFile = ref()
const previewUrl = ref('')
const progress = ref(0)
const uploading = ref(false)
const stagedImage = ref()

const acceptedExtensions = computed(() => parsePublicImageAccept(props.accept))

const displaySize = computed(() => {
  if (!currentFile.value) return ''
  const megabytes = currentFile.value.size / 1024 / 1024
  return megabytes >= 1 ? `${megabytes.toFixed(1)} MB` : `${Math.ceil(currentFile.value.size / 1024)} KB`
})

const validateFile = (file) => {
  const rawExtension = file.name.split('.').pop()?.toLowerCase() || ''
  const extension = rawExtension === 'jpeg' ? 'jpg' : rawExtension
  if (!acceptedExtensions.value.includes(extension)) {
    ElMessage.error(`仅支持 ${props.accept} 格式`)
    return false
  }
  if (file.size <= 0 || file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.error(`文件大小不能超过 ${props.maxSizeMb}MB`)
    return false
  }
  return true
}

const handleChange = (uploadFile) => {
  const file = uploadFile?.raw
  if (!file || !validateFile(file)) {
    uploadRef.value?.clearFiles()
    return
  }
  releasePreview()
  currentFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  progress.value = 0
  stagedImage.value = undefined
  emit('upload', null)
}

const startUpload = async () => {
  if (!currentFile.value || uploading.value || stagedImage.value) return
  uploading.value = true
  try {
    const response = await stagePublicImage(currentFile.value, (event) => {
      progress.value = event.total ? Math.min(99, Math.round(event.loaded / event.total * 100)) : 0
    })
    stagedImage.value = normalizePublicImageUploadInfo(response.data?.content)
    progress.value = 100
    emit('upload', stagedImage.value)
    ElMessage.success('图片已暂存，请继续保存表单')
  } catch (error) {
    const message = error.response?.data?.message || error.message || '图片上传失败'
    ElMessage.error(message)
    emit('error', error)
  } finally {
    uploading.value = false
  }
}

const clearFile = async (options = {}) => {
  const token = stagedImage.value?.token
  const cancelRemote = options.cancelRemote !== false
  stagedImage.value = undefined
  currentFile.value = undefined
  progress.value = 0
  releasePreview()
  uploadRef.value?.clearFiles()
  emit('upload', null)
  if (cancelRemote && token) {
    try {
      await cancelPublicImageUpload(token)
    } catch (error) {
      // Server-side TTL cleanup remains the fallback when the browser is offline.
    }
  }
}

const markConsumed = () => {
  stagedImage.value = undefined
}

const releasePreview = () => {
  if (previewUrl.value.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

onBeforeUnmount(() => clearFile())
defineExpose({ clearFile, markConsumed })
</script>

<template>
  <div class="public-image-upload">
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :show-file-list="false"
      :limit="1"
      :accept="accept"
      :disabled="uploading"
      :on-change="handleChange"
      :on-exceed="() => ElMessage.warning('一次只能选择一张图片')"
    >
      <div v-if="previewUrl" class="preview">
        <img :src="previewUrl" alt="待上传图片预览">
        <strong>{{ currentFile?.name }}</strong>
        <span>{{ displaySize }}</span>
      </div>
      <div v-else class="empty-upload">
        <el-icon :size="48" color="#8c939d"><UploadFilled /></el-icon>
        <div>拖放图片到这里，或点击选择</div>
        <small>{{ tipText }}</small>
      </div>
    </el-upload>
    <el-progress v-if="progress > 0" :percentage="progress" :status="progress === 100 ? 'success' : undefined" />
    <div class="actions">
      <el-button type="primary" :loading="uploading" :disabled="!currentFile || !!stagedImage" @click="startUpload">
        {{ stagedImage ? '已暂存' : '上传图片' }}
      </el-button>
      <el-button v-if="currentFile" :disabled="uploading" @click="clearFile()">清除</el-button>
    </div>
  </div>
</template>

<style scoped>
.public-image-upload { width: 100%; }
.preview, .empty-upload {
  min-height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.preview img { max-width: 100%; max-height: 150px; object-fit: contain; }
.preview span, .empty-upload small { color: #909399; }
.actions { display: flex; gap: 10px; margin-top: 12px; }
.el-progress { margin-top: 12px; }
</style>
