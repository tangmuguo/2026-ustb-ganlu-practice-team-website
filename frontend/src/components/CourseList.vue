<script setup>
import CourseCard from '@/components/CourseCard.vue'

const props = defineProps({
  courses: {
    type: Array,
    required: true
  },
  title: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <div class="course-list">
    <h3 v-if="title">{{ title }}</h3>
    <div class="course-grid">
      <CourseCard 
        v-for="course in courses" 
        :key="course.id" 
        :course="course" 
      />
    </div>
  </div>
</template>

<style scoped>
.course-list {
  margin-top: 20px;
}

.course-list h3 {
  margin-bottom: 20px;
  font-size: 18px;
  color: #333;
}

.course-grid {
  display: grid;
  /* 修改为每行5个等宽卡片 */
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 20px;
}

/* 响应式调整 - 在较小屏幕上减少每行卡片数量 */
@media (max-width: 1200px) {
  .course-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 992px) {
  .course-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .course-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>