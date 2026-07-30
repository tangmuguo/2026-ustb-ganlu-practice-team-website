<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowRight, Calendar } from '@element-plus/icons-vue'
import { resolveMediaUrl } from '@/utils/fengcai'

const props = defineProps({
  year: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['select'])
const imageFailed = ref(false)
const coverUrl = computed(() => resolveMediaUrl(props.year.coverUrl))

watch(coverUrl, () => {
  imageFailed.value = false
})

function selectYear() {
  emit('select', props.year.year)
}
</script>

<template>
  <article
    class="year-card"
    role="link"
    tabindex="0"
    :aria-label="`查看 ${year.year} 年支教团队`"
    @click="selectYear"
    @keydown.enter="selectYear"
    @keydown.space.prevent="selectYear"
  >
    <img
      v-if="coverUrl && !imageFailed"
      class="year-card__image"
      :src="coverUrl"
      :alt="`${year.year} 年团队风采封面`"
      @error="imageFailed = true"
    >
    <div v-else class="year-card__placeholder" aria-hidden="true">
      <el-icon><Calendar /></el-icon>
    </div>
    <div class="year-card__shade"></div>
    <div class="year-card__content">
      <div>
        <p class="year-card__eyebrow">GANLU TEACHING</p>
        <h2>{{ year.year }} 年</h2>
        <p>共 {{ year.teamCount }} 支已发布小队</p>
      </div>
      <span class="year-card__action">
        查看小队
        <el-icon><ArrowRight /></el-icon>
      </span>
    </div>
  </article>
</template>

<style scoped>
.year-card {
  position: relative;
  min-height: 310px;
  overflow: hidden;
  border-radius: 24px;
  background: #0d4f91;
  box-shadow: 0 18px 42px rgba(20, 74, 126, 0.16);
  cursor: pointer;
  isolation: isolate;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.year-card:hover,
.year-card:focus-visible {
  transform: translateY(-6px);
  box-shadow: 0 24px 50px rgba(20, 74, 126, 0.24);
  outline: none;
}

.year-card__image,
.year-card__placeholder,
.year-card__shade {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.year-card__image {
  object-fit: cover;
  transition: transform 0.45s ease;
}

.year-card:hover .year-card__image {
  transform: scale(1.04);
}

.year-card__placeholder {
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.65);
  font-size: 72px;
  background:
    radial-gradient(circle at 78% 20%, rgba(95, 184, 255, 0.7), transparent 32%),
    linear-gradient(145deg, #0e67b4, #083f7c 68%, #062d59);
}

.year-card__shade {
  z-index: 1;
  background: linear-gradient(180deg, rgba(3, 30, 58, 0.05) 18%, rgba(3, 30, 58, 0.9) 100%);
}

.year-card__content {
  position: absolute;
  inset: auto 0 0;
  z-index: 2;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 30px;
  color: #fff;
}

.year-card__eyebrow {
  margin-bottom: 5px;
  color: #bde5ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

h2 {
  margin: 0;
  font-size: clamp(34px, 4vw, 48px);
  font-weight: 750;
  line-height: 1.15;
}

.year-card__content p:last-child {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.82);
}

.year-card__action {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
}

@media (max-width: 600px) {
  .year-card {
    min-height: 270px;
  }

  .year-card__content {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
    padding: 24px;
  }
}
</style>
