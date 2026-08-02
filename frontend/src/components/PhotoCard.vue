<script setup>
import { computed } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import { resolveMediaUrl } from '@/utils/fengcai'

const props = defineProps({
  photo: {
    type: Object,
    required: true,
  },
  category: {
    type: String,
    default: '',
  },
})

const imageUrl = computed(() => resolveMediaUrl(
  props.photo.imageUrl || props.photo.photoUrl || props.photo.previewUrl || props.photo.url,
))
const caption = computed(() => props.photo.caption || props.photo.title || '')
const description = computed(() => props.photo.description || props.photo.content || '')
</script>

<template>
  <figure class="photo-card">
    <el-image
      v-if="imageUrl"
      class="photo-card__image"
      :src="imageUrl"
      :alt="caption || `${category || '团队风采'}照片`"
      :preview-src-list="[imageUrl]"
      :initial-index="0"
      fit="cover"
      preview-teleported
      hide-on-click-modal
    >
      <template #error>
        <div class="photo-card__placeholder">
          <el-icon><Picture /></el-icon>
          <span>图片加载失败</span>
        </div>
      </template>
    </el-image>
    <div v-else class="photo-card__image photo-card__placeholder">
      <el-icon><Picture /></el-icon>
      <span>暂无图片</span>
    </div>
    <figcaption v-if="caption || description || category">
      <span v-if="category" class="photo-card__tag">{{ category }}</span>
      <strong v-if="caption">{{ caption }}</strong>
      <p v-if="description">{{ description }}</p>
    </figcaption>
  </figure>
</template>

<style scoped>
.photo-card {
  overflow: hidden;
  margin: 0;
  border: 1px solid #e4edf5;
  border-radius: 18px;
  background: #fff;
}

.photo-card__image {
  display: block;
  width: 100%;
  height: 225px;
}

.photo-card__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 7px;
  color: #82a2bb;
  background: #edf6fc;
}

.photo-card__placeholder .el-icon {
  font-size: 38px;
}

figcaption {
  padding: 16px 18px 18px;
}

.photo-card__tag {
  display: inline-block;
  margin-bottom: 8px;
  padding: 3px 8px;
  border-radius: 999px;
  color: #176fae;
  font-size: 12px;
  background: #eaf5fd;
}

strong {
  display: block;
  color: #173f61;
  font-size: 17px;
}

p {
  margin-top: 6px;
  color: #6b7f90;
  line-height: 1.65;
  white-space: pre-wrap;
}

@media (max-width: 480px) {
  .photo-card__image {
    height: 210px;
  }
}
</style>
