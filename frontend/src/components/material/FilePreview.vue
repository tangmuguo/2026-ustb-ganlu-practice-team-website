<script setup>
import { computed } from 'vue'
import { resolveMaterialAssetUrl } from '@/apis/materialsAPI'

const props = defineProps({
  material: { type: Object, required: true }
})

const previewUrl = computed(() => resolveMaterialAssetUrl(props.material.previewUrl || props.material.previewFilePath))
const extension = computed(() => (props.material.fileExtension || '').toLowerCase())
const isImage = computed(() => ['jpg', 'jpeg', 'png', 'webp'].includes(extension.value))
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
.pdf-preview { width: 100%; height: min(78vh, 900px); border: 0; border-radius: 8px; background: #f5f7fa; }
.image-preview { display: block; max-width: 100%; max-height: 78vh; margin: 0 auto; object-fit: contain; }
</style>
