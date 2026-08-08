<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowRight, Location, Picture } from '@element-plus/icons-vue'
import { resolveMediaUrl } from '@/utils/fengcai'

const props = defineProps({
  team: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['click'])
const imageFailed = ref(false)

const name = computed(() => props.team.name || props.team.teamName || props.team.teamname || '未命名小队')
const region = computed(() => props.team.region || props.team.teachingRegion || props.team.location || '')
const school = computed(() => props.team.school || props.team.primarySchool || props.team.teachingSchool || '')
const description = computed(() => props.team.description || props.team.summary || props.team.introduction || '更多团队故事，等待与你分享。')
const coverUrl = computed(() => resolveMediaUrl(
  props.team.coverUrl || props.team.cover || props.team.imageUrl || props.team.thumbnailUrl,
))

watch(coverUrl, () => {
  imageFailed.value = false
})

function openDetail() {
  emit('click', props.team)
}
</script>

<template>
  <article
    class="team-card"
    role="link"
    tabindex="0"
    :aria-label="`查看${name}详情`"
    @click="openDetail"
    @keydown.enter="openDetail"
    @keydown.space.prevent="openDetail"
  >
    <div class="team-card__media">
      <img
        v-if="coverUrl && !imageFailed"
        :src="coverUrl"
        :alt="`${name}封面`"
        @error="imageFailed = true"
      >
      <div v-else class="team-card__placeholder">
        <el-icon><Picture /></el-icon>
        <span>暂无团队封面</span>
      </div>
    </div>

    <div class="team-card__body">
      <div class="team-card__heading">
        <h2>{{ name }}</h2>
        <span class="team-card__arrow" aria-hidden="true"><el-icon><ArrowRight /></el-icon></span>
      </div>

      <div v-if="region || school" class="team-card__location">
        <el-icon><Location /></el-icon>
        <span>{{ [region, school].filter(Boolean).join(' · ') }}</span>
      </div>

      <p>{{ description }}</p>
    </div>
  </article>
</template>

<style scoped>
.team-card {
  overflow: hidden;
  border: 1px solid #e2edf7;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(32, 84, 132, 0.08);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.team-card:hover,
.team-card:focus-visible {
  transform: translateY(-5px);
  border-color: #b7d9f4;
  box-shadow: 0 18px 42px rgba(32, 84, 132, 0.16);
  outline: none;
}

.team-card__media {
  height: 220px;
  overflow: hidden;
  background: #edf6fd;
}

.team-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
}

.team-card:hover .team-card__media img {
  transform: scale(1.035);
}

.team-card__placeholder {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  color: #6f96b7;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.65), transparent),
    #dceefa;
}

.team-card__placeholder .el-icon {
  font-size: 38px;
}

.team-card__body {
  padding: 22px;
}

.team-card__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

h2 {
  margin: 0;
  color: #123f69;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
}

.team-card__arrow {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 50%;
  color: #1776bc;
  background: #e8f4fd;
}

.team-card__location {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 14px;
  color: #47718f;
  font-size: 14px;
}

.team-card__location .el-icon {
  flex: 0 0 auto;
  margin-top: 3px;
}

.team-card__body > p {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 14px;
  color: #63788a;
  line-height: 1.75;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

@media (max-width: 480px) {
  .team-card__media {
    height: 195px;
  }

  .team-card__body {
    padding: 19px;
  }
}
</style>
