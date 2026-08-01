<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { Document, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SparkMD5 from 'spark-md5'
import { checkFileExist, mergeChunks, uploadChunk } from '@/apis/materialsAPI'

const props = defineProps({
  accept: { type: String, required: true },
  purpose: {
    type: String,
    required: true,
    validator: (value) => ['COVER', 'MATERIAL'].includes(value)
  },
  tipText: { type: String, default: '请上传符合格式的文件' },
  uploadText: { type: String, default: '上传文件' },
  maxSizeMb: { type: Number, default: 10 }
})

const emit = defineEmits(['upload', 'progress', 'error', 'update:modelValue'])
const uploadRef = ref()
const currentFile = ref()
const previewUrl = ref('')
const fileIdentifier = ref('')
const uploadProgress = ref(0)
const isHashing = ref(false)
const isUploading = ref(false)
const uploadComplete = ref(false)

const isImage = computed(() => props.purpose === 'COVER')
const busy = computed(() => isHashing.value || isUploading.value)
const displayName = computed(() => currentFile.value?.name || '')
const fileSize = computed(() => {
  if (!currentFile.value) return ''
  const megabytes = currentFile.value.size / 1024 / 1024
  return megabytes >= 1 ? `${megabytes.toFixed(1)} MB` : `${Math.ceil(currentFile.value.size / 1024)} KB`
})

const acceptedExtensions = computed(() => props.accept
  .split(',')
  .map((value) => value.trim().toLowerCase().replace(/^\./, ''))
  .filter(Boolean))

const validateFile = (file) => {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
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

const calculateFileHash = (file) => new Promise((resolve, reject) => {
  const hashChunkSize = 2 * 1024 * 1024
  const total = Math.ceil(file.size / hashChunkSize)
  const spark = new SparkMD5.ArrayBuffer()
  const reader = new FileReader()
  let index = 0

  reader.onload = (event) => {
    spark.append(event.target.result)
    index += 1
    if (index < total) {
      readNext()
    } else {
      resolve(spark.end())
    }
  }
  reader.onerror = () => reject(new Error('读取文件失败'))
  const readNext = () => {
    const start = index * hashChunkSize
    reader.readAsArrayBuffer(file.slice(start, Math.min(start + hashChunkSize, file.size)))
  }
  readNext()
})

const handleChange = async (uploadFile) => {
  const file = uploadFile?.raw
  if (!file || !validateFile(file)) {
    uploadRef.value?.clearFiles()
    return
  }
  clearPreview()
  currentFile.value = file
  previewUrl.value = isImage.value ? URL.createObjectURL(file) : file.name
  isHashing.value = true
  uploadComplete.value = false
  uploadProgress.value = 0
  try {
    fileIdentifier.value = await calculateFileHash(file)
  } catch (error) {
    ElMessage.error(error.message)
    clearFile()
  } finally {
    isHashing.value = false
  }
}

const uploadChunkWithRetry = async (formData, progressHandler, retries = 3) => {
  try {
    return await uploadChunk(formData, progressHandler)
  } catch (error) {
    if (retries > 0) return uploadChunkWithRetry(formData, progressHandler, retries - 1)
    throw error
  }
}

const startUpload = async () => {
  if (!currentFile.value || !fileIdentifier.value || busy.value || uploadComplete.value) return
  const file = currentFile.value
  const chunkSize = 5 * 1024 * 1024
  const totalChunks = Math.ceil(file.size / chunkSize)
  isUploading.value = true
  uploadProgress.value = 0

  try {
    const stateResponse = await checkFileExist(fileIdentifier.value, props.purpose)
    const state = stateResponse.data.content
    if (state.complete && state.file) {
      finishUpload(state.file)
      return
    }

    const uploaded = new Set(state.uploadedChunks || [])
    let completed = uploaded.size
    for (let index = 0; index < totalChunks; index += 1) {
      const chunkNumber = index + 1
      if (uploaded.has(chunkNumber)) continue

      const formData = new FormData()
      formData.append('file', file.slice(index * chunkSize, Math.min((index + 1) * chunkSize, file.size)))
      formData.append('chunkNumber', String(chunkNumber))
      formData.append('totalChunks', String(totalChunks))
      formData.append('identifier', fileIdentifier.value)
      formData.append('filename', file.name)
      formData.append('expectedSize', String(file.size))
      formData.append('purpose', props.purpose)

      await uploadChunkWithRetry(formData, (event) => {
        const currentPart = event.total ? event.loaded / event.total : 0
        const percentage = Math.min(99, Math.round(((completed + currentPart) / totalChunks) * 100))
        uploadProgress.value = percentage
        emit('progress', percentage)
      })
      completed += 1
    }

    const mergeResponse = await mergeChunks({
      filename: file.name,
      identifier: fileIdentifier.value,
      totalChunks,
      expectedSize: file.size,
      purpose: props.purpose
    })
    finishUpload(mergeResponse.data.content)
  } catch (error) {
    const message = error.response?.data?.message || error.message || '上传失败'
    ElMessage.error(message)
    emit('error', error)
  } finally {
    isUploading.value = false
  }
}

const finishUpload = (fileInfo) => {
  uploadComplete.value = true
  uploadProgress.value = 100
  emit('progress', 100)
  emit('upload', fileInfo)
  emit('update:modelValue', fileInfo)
  ElMessage.success('文件上传完成')
}

const clearPreview = () => {
  if (previewUrl.value.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

const clearFile = () => {
  clearPreview()
  currentFile.value = undefined
  fileIdentifier.value = ''
  uploadProgress.value = 0
  uploadComplete.value = false
  uploadRef.value?.clearFiles()
  emit('update:modelValue', null)
}

const handleExceed = () => ElMessage.warning('一次只能选择一个文件，请先清除当前文件')

onBeforeUnmount(clearPreview)
defineExpose({ clearFile })
</script>

<template>
  <div class="upload-widget">
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :show-file-list="false"
      :limit="1"
      :accept="accept"
      :disabled="busy"
      :on-change="handleChange"
      :on-exceed="handleExceed"
    >
      <div v-if="previewUrl" class="preview">
        <img v-if="isImage" :src="previewUrl" alt="封面预览" class="cover-preview">
        <el-icon v-else :size="48" color="#409eff"><Document /></el-icon>
        <strong>{{ displayName }}</strong>
        <span>{{ fileSize }}</span>
      </div>
      <div v-else class="empty-upload">
        <el-icon :size="48" color="#8c939d"><UploadFilled /></el-icon>
        <div>拖放文件到这里，或点击选择</div>
        <small>{{ tipText }}</small>
      </div>
    </el-upload>

    <el-progress
      v-if="busy || uploadProgress > 0"
      class="progress"
      :percentage="uploadProgress"
      :status="uploadComplete ? 'success' : undefined"
    />
    <p v-if="isHashing" class="status-text">正在计算文件校验值…</p>

    <div class="actions">
      <el-button
        type="primary"
        :loading="busy"
        :disabled="!currentFile || isHashing || uploadComplete"
        @click="startUpload"
      >
        {{ uploadComplete ? '上传完成' : uploadText }}
      </el-button>
      <el-button v-if="currentFile" :disabled="busy" @click="clearFile">清除</el-button>
    </div>
  </div>
</template>

<style scoped>
.upload-widget { width: 100%; }
.preview, .empty-upload {
  min-height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.preview span, .empty-upload small, .status-text { color: #909399; }
.cover-preview { max-width: 100%; max-height: 150px; object-fit: contain; }
.progress { margin-top: 12px; }
.status-text { margin: 6px 0 0; font-size: 13px; }
.actions { display: flex; gap: 10px; margin-top: 12px; }
</style>
