<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import SparkMD5 from 'spark-md5' // 新增：用于生成文件标识
import { uploadChunk, mergeChunks, checkFileExist } from "@/apis/materialsAPI"

const props = defineProps({
  accept: {
    type: String,
    required: true
  },
  tipText: {
    type: String,
    default: '请上传符合格式的文件'
  },
  previewIcon: {
    type: String,
    default: 'el-icon-document'
  },
  uploadText: {
    type: String,
    default: '确认上传'
  },
  maxSizeMB: {
    type: Number,
    default: 10
  }
})

const emit = defineEmits(['upload', 'update:modelValue','progress'])

const uploadRef = ref(null)
const previewUrl = ref('')
const currentFile = ref(null)
const isUploading = ref(false)
const uploadProgress = ref(0) // 新增：上传进度
const fileIdentifier = ref('') // 新增：文件唯一标识

// 生成文件标识（基于文件内容哈希）
const calculateFileHash = (file) => {
  return new Promise((resolve) => {
    const chunkSize = 2 * 1024 * 1024 // 2MB采样
    const chunks = Math.ceil(file.size / chunkSize)
    const spark = new SparkMD5.ArrayBuffer()
    const fileReader = new FileReader()
    let currentChunk = 0

    fileReader.onload = (e) => {
      spark.append(e.target.result)
      currentChunk++
      if (currentChunk < chunks) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }

    const loadNext = () => {
      const start = currentChunk * chunkSize
      const end = Math.min(start + chunkSize, file.size)
      fileReader.readAsArrayBuffer(file.slice(start, end))
    }

    loadNext()
  })
}

const isImage = computed(() => props.accept.includes('image'))
const displayName = computed(() => {
  if (!currentFile.value) return ''
  return currentFile.value.name.length > 20 
    ? `${currentFile.value.name.substring(0, 15)}...${currentFile.value.name.split('.').pop()}`
    : currentFile.value.name
})
const fileSize = computed(() => {
  if (!currentFile.value) return ''
  const size = currentFile.value.size / 1024
  return size > 1024 
    ? `${(size / 1024).toFixed(1)} MB` 
    : `${Math.round(size)} KB`
})

const beforeUpload = (file) => {
  // 文件类型验证
  const extension = file.name.split('.').pop().toLowerCase()
  const acceptedTypes = props.accept.split(',')
    .map(item => item.replace('.', '').trim())
    .filter(Boolean)
  
  const isValidType = isImage.value
    ? file.type.startsWith('image/')
    : acceptedTypes.includes(extension)

  if (!isValidType) {
    ElMessage.error(`仅支持 ${props.accept} 格式的文件`)
    return false
  }

  // 文件大小验证
  if (file.size > props.maxSizeMB * 1024 * 1024) {
    ElMessage.error(`文件大小不能超过 ${props.maxSizeMB}MB`)
    return false
  }

  return true
}

const handleChange =async (file) => {
  if (!file || !file.raw) return
  
  // 计算文件哈希标识
  fileIdentifier.value = await calculateFileHash(file.raw)

  // 清除旧预览
  if (previewUrl.value && previewUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(previewUrl.value)
  }

  // 生成预览
  if (isImage.value) {
    previewUrl.value = URL.createObjectURL(file.raw)
  } else {
    previewUrl.value = file.name
  }
  
  currentFile.value = file.raw
}

const handleExceed = () => {
  ElMessage.warning(`只能上传一个文件，请先移除当前文件`)
}

const emitUpload =async () => {
  if (!currentFile.value || isUploading.value) return

  isUploading.value = true
  const CHUNK_SIZE = 5 * 1024 * 1024 // 5MB分片
  const totalChunks = Math.ceil(currentFile.value.size / CHUNK_SIZE)
  uploadProgress.value = 0

  try {
    // 检查文件是否已存在
    const { data: checkData } = await checkFileExist(fileIdentifier.value)
    if (checkData.exist) {
      emit('upload', checkData.path)
      ElMessage.success('文件已存在，秒传成功')
      return
    }

    // 上传所有分片
    for (let i = 0; i < totalChunks; i++) {
      const chunk = currentFile.value.slice(
        i * CHUNK_SIZE,
        Math.min((i + 1) * CHUNK_SIZE, currentFile.value.size)
      )

      const formData = new FormData()
      formData.append('file', chunk)
      formData.append('chunkNumber', i + 1)
      formData.append('totalChunks', totalChunks)
      formData.append('identifier', fileIdentifier.value)
      formData.append('filename', currentFile.value.name)

      await uploadChunkWithRetry(formData, i, totalChunks)
    }

    // 合并分片
    const { data } = await mergeChunks({
      filename: currentFile.value.name,
      identifier: fileIdentifier.value,
      totalChunks: totalChunks
    })

    emit('upload', data.path)
    ElMessage.success('上传成功')
  } catch (error) {
    ElMessage.error(`上传失败: ${error.message}`)
  } finally {
    isUploading.value = false
  }

  //emit('upload', currentFile.value)
}

// 分片上传（带重试机制）
const uploadChunkWithRetry = async (formData, currentChunk, totalChunks, retry = 3) => {
  try {
    await uploadChunk(formData, (progressEvent) => {
      const chunkProgress = Math.round(
        (progressEvent.loaded / progressEvent.total) * 100
      )
      // 计算整体进度
      uploadProgress.value = Math.round(
        (currentChunk * 100 + chunkProgress) / totalChunks
      )
      emit('progress', uploadProgress.value)
    })
  } catch (error) {
    if (retry > 0) {
      return uploadChunkWithRetry(formData, currentChunk, totalChunks, retry - 1)
    }
    throw error
  }
}

const clearFile = () => {
  if (previewUrl.value && previewUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  currentFile.value = null
  uploadProgress.value = 0
  uploadRef.value?.clearFiles()
}

defineExpose({
  clearFile,
  getFile: () => currentFile.value
})
</script>

<template>
  <div class="upload-widget-container">
    <el-upload
      ref="uploadRef"
      class="uploader"
      :auto-upload="false"
      :show-file-list="false"
      drag
      :limit="1"
      :accept="accept"
      :on-change="handleChange"
      :before-upload="beforeUpload"
      :on-exceed="handleExceed"
    >
      <!-- 预览插槽 -->
      <slot name="preview" :previewUrl="previewUrl">
        <div v-if="previewUrl" class="preview-area">
          <i v-if="!isImage" :class="previewIcon" style="font-size: 48px; color: #409EFF;"></i>
          <img v-else :src="previewUrl" class="image-preview">
          <div class="file-info">
            <span class="file-name">{{ displayName }}</span>
            <span class="file-size">{{ fileSize }}</span>
          </div>
        </div>
        <div v-else class="uploader-default">
          <i class="el-icon-upload" style="font-size: 48px; color: #8c939d;"></i>
          <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
          <div class="el-upload__tip">{{ tipText }}</div>
        </div>
      </slot>
    </el-upload>

    <!-- 新增进度显示 -->
    <el-progress 
      v-if="uploadProgress > 0"
      :percentage="uploadProgress"
      :stroke-width="15"
      :status="uploadProgress === 100 ? 'success' : ''"
    />
    <!-- 操作按钮 -->
    <div class="action-buttons">
      <el-button
        type="primary"
        size="small"
        :disabled="!previewUrl || isUploading"
        @click="emitUpload"
        :loading="isUploading"
      >
        {{ uploadProgress === 100 ? '上传完成' : uploadText }}        
      </el-button>
      <el-button
        v-if="previewUrl"
        size="small"
        :disabled="isUploading"
        @click="clearFile"
      >
        清除
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.upload-widget-container {
  width: 100%;
  margin-bottom: 20px;
}

.uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.3s;
}

.uploader:hover {
  border-color: #409EFF;
}

.preview-area {
  padding: 20px;
  text-align: center;
  position: relative;
}

.image-preview {
  max-height: 200px;
  max-width: 100%;
  display: block;
  margin: 0 auto;
}

.file-info {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
}

.file-name {
  font-size: 14px;
  color: #606266;
  word-break: break-all;
}

.file-size {
  font-size: 12px;
  color: #909399;
}

.uploader-default {
  padding: 40px 0;
  text-align: center;
}

.el-upload__text {
  color: #606266;
  font-size: 14px;
  margin: 8px 0;
}

.el-upload__text em {
  color: #409EFF;
  font-style: normal;
}

.el-upload__tip {
  color: #909399;
  font-size: 12px;
}

.action-buttons {
  margin-top: 10px;
  display: flex;
  gap: 10px;
}
</style>