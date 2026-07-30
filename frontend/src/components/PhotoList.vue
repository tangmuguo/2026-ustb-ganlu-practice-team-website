<script setup>
import PhotoCard from '@/components/PhotoCard.vue'

defineProps({
  photos: {
    type: Array,
    default: () => [],
  },
  category: {
    type: String,
    default: '',
  },
  emptyText: {
    type: String,
    default: '暂无已发布的照片',
  },
})
</script>

<template>
  <div v-if="photos.length" class="photo-list">
    <PhotoCard
      v-for="(photo, index) in photos"
      :key="photo.id ?? `photo-${index}`"
      :photo="photo"
      :category="category"
    />
  </div>
  <p v-else class="empty-copy">{{ emptyText }}</p>
</template>

<style scoped>
.photo-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: 18px;
}

.empty-copy {
  padding: 28px 0;
  color: #7d92a4;
  text-align: center;
}
</style>
