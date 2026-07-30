<script setup>
import { computed, ref, watch } from 'vue'
import { Calendar, Location, OfficeBuilding, Picture } from '@element-plus/icons-vue'
import { resolveMediaUrl } from '@/utils/fengcai'

const props = defineProps({
  team: {
    type: Object,
    required: true,
  },
})

const imageFailed = ref(false)
const coverUrl = computed(() => resolveMediaUrl(
  props.team.coverUrl || props.team.cover || props.team.imageUrl || props.team.thumbnailUrl,
))

watch(coverUrl, () => {
  imageFailed.value = false
})
</script>

<template>
  <section class="team-hero">
    <div class="team-hero__media">
      <img
        v-if="coverUrl && !imageFailed"
        :src="coverUrl"
        :alt="`${team.name}团队封面`"
        @error="imageFailed = true"
      >
      <div v-else class="team-hero__placeholder">
        <el-icon><Picture /></el-icon>
        <span>暂无团队封面</span>
      </div>
    </div>

    <div class="team-hero__content">
      <p class="team-hero__eyebrow">TEAM STORY</p>
      <h1>{{ team.name }}</h1>
      <div class="team-hero__meta">
        <span v-if="team.year"><el-icon><Calendar /></el-icon>{{ team.year }} 年</span>
        <span v-if="team.region"><el-icon><Location /></el-icon>{{ team.region }}</span>
        <span v-if="team.school"><el-icon><OfficeBuilding /></el-icon>{{ team.school }}</span>
      </div>
      <p class="team-hero__description">{{ team.description || '团队简介正在整理中，敬请期待。' }}</p>
    </div>
  </section>
</template>

<style scoped>
.team-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.85fr);
  overflow: hidden;
  min-height: 410px;
  border-radius: 28px;
  color: #fff;
  background: #0e5f9d;
  box-shadow: 0 22px 52px rgba(21, 81, 128, 0.2);
}

.team-hero__media {
  min-height: 410px;
  overflow: hidden;
}

.team-hero__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.team-hero__placeholder {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: inherit;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 15px;
  background:
    radial-gradient(circle at 24% 24%, rgba(102, 200, 255, 0.75), transparent 30%),
    linear-gradient(145deg, #1679bd, #083f75);
}

.team-hero__placeholder .el-icon {
  font-size: 60px;
}

.team-hero__content {
  display: flex;
  justify-content: center;
  flex-direction: column;
  padding: clamp(32px, 5vw, 60px);
  background:
    radial-gradient(circle at 100% 0, rgba(93, 190, 248, 0.46), transparent 38%),
    linear-gradient(145deg, #126eac, #094774);
}

.team-hero__eyebrow {
  color: #bee7ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.17em;
}

h1 {
  margin: 8px 0 0;
  font-size: clamp(34px, 5vw, 52px);
  font-weight: 750;
  line-height: 1.2;
}

.team-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin-top: 22px;
}

.team-hero__meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.86);
  font-size: 14px;
}

.team-hero__description {
  margin-top: 24px;
  color: rgba(255, 255, 255, 0.86);
  font-size: 16px;
  line-height: 1.85;
  white-space: pre-wrap;
}

@media (max-width: 860px) {
  .team-hero {
    grid-template-columns: 1fr;
  }

  .team-hero__media {
    min-height: 300px;
  }
}

@media (max-width: 480px) {
  .team-hero,
  .team-hero__media {
    min-height: 0;
  }

  .team-hero__media {
    height: 230px;
  }

  .team-hero__content {
    padding: 27px 22px 30px;
  }
}
</style>
