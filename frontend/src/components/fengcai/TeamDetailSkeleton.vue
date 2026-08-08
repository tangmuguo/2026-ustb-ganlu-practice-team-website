<template>
  <div class="detail-skeleton" aria-label="团队详情加载中" aria-busy="true">
    <div class="detail-skeleton__hero">
      <el-skeleton animated>
        <template #template>
          <el-skeleton-item variant="image" class="detail-skeleton__image" />
          <div class="detail-skeleton__hero-copy">
            <el-skeleton-item variant="text" style="width: 22%" />
            <el-skeleton-item variant="h1" style="width: 58%" />
            <el-skeleton-item variant="text" style="width: 76%" />
            <el-skeleton-item variant="text" />
            <el-skeleton-item variant="text" style="width: 84%" />
          </div>
        </template>
      </el-skeleton>
    </div>

    <div v-for="index in 3" :key="index" class="detail-skeleton__section">
      <el-skeleton :rows="3" animated />
    </div>
  </div>
</template>

<style scoped>
.detail-skeleton {
  display: grid;
  gap: 24px;
}

.detail-skeleton__hero,
.detail-skeleton__section {
  overflow: hidden;
  padding: 26px;
  border: 1px solid #e7eef4;
  border-radius: 24px;
  background: #fff;
}

.detail-skeleton__hero :deep(.el-skeleton__template) {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 28px;
}

.detail-skeleton__image {
  width: 100%;
  height: 330px;
}

.detail-skeleton__hero-copy {
  display: flex;
  justify-content: center;
  flex-direction: column;
  gap: 18px;
}

@media (max-width: 720px) {
  .detail-skeleton__hero :deep(.el-skeleton__template) {
    grid-template-columns: 1fr;
  }

  .detail-skeleton__image {
    height: 230px;
  }
}
</style>
