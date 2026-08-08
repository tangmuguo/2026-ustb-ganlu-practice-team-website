<script setup>
import CourseCard from '@/components/CourseCard.vue'

defineProps({
  courses: { type: Array, required: true },
  loading: { type: Boolean, default: false }
})
</script>

<template>
  <div v-loading="loading" class="course-list">
    <el-empty v-if="!loading && courses.length === 0" description="没有找到符合条件的课件" />
    <div v-else class="course-grid">
      <CourseCard v-for="course in courses" :key="course.id" :course="course" />
    </div>
  </div>
</template>

<style scoped>
.course-list { min-height: 260px; }
.course-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 20px; }
@media (max-width: 1100px) { .course-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 760px) { .course-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 480px) { .course-grid { grid-template-columns: 1fr; } }
</style>
