<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getMaterialPreview } from '@/apis/materialsAPI'
import { userinfoStore } from '@/stores/userStore'

const props = defineProps({
  material: { type: Object, required: true }
})

const userStore = userinfoStore()
const previewUrl = ref('')
const loading = ref(false)
const failed = ref(false)
const extension = computed(() => (props.material.fileExtension || '').toLowerCase())
const isImage = computed(() => ['jpg', 'jpeg', 'png', 'webp'].includes(extension.value))

const clearPreview = () => {
  if (previewUrl.value.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

const loadPreview = async () => {
  clearPreview()
  failed.value = false
  if (!userStore.isLoggedIn || !props.material.id || props.material.previewStatus !== 'READY') return
  loading.value = true
  try {
    const response = await getMaterialPreview(props.material.id)
    previewUrl.value = URL.createObjectURL(response.data)
  } catch (error) {
    failed.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.material.id, props.material.previewStatus, userStore.isLoggedIn],
  loadPreview,
  { immediate: true }
)
onBeforeUnmount(clearPreview)
</script>

<template>
  <div class="file-preview">
    <el-alert
      v-if="material.previewStatus === 'FAILED'"
      title="预览生成失败，登录后仍可下载原文件"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="!userStore.isLoggedIn"
      title="登录后可以在线预览和下载课件"
      type="info"
      :closable="false"
      show-icon
    />
    <div v-else-if="loading" v-loading="true" class="preview-loading" />
    <el-empty v-else-if="failed" description="预览加载失败，请稍后重试" />
    <img v-else-if="isImage && previewUrl" :src="previewUrl" :alt="material.title" class="image-preview">
    <iframe
      v-else-if="previewUrl"
      :src="previewUrl"
      class="pdf-preview"
      :title="`${material.title}预览`"
    />
    <el-empty v-else description="暂无可用预览" />
  </div>
</template>

<style scoped>
.file-preview { width: 100%; min-height: 360px; }
.preview-loading { min-height: 360px; }
.pdf-preview { width: 100%; height: min(78vh, 900px); border: 0; border-radius: 8px; background: #f5f7fa; }
.image-preview { display: block; max-width: 100%; max-height: 78vh; margin: 0 auto; object-fit: contain; }
</style>
