<template>
  <div class="skeleton-list" aria-label="正在加载留言">
    <div v-for="index in 3" :key="index" class="skeleton-card">
      <div class="skeleton-header">
        <el-skeleton-item variant="circle" class="avatar" />
        <div class="identity">
          <el-skeleton-item variant="text" class="name" />
          <el-skeleton-item variant="text" class="meta" />
        </div>
      </div>
      <el-skeleton-item variant="text" class="line wide" />
      <el-skeleton-item variant="text" class="line" />
      <el-skeleton-item variant="rect" class="reply" />
    </div>
  </div>
</template>

<style scoped>
.skeleton-list {
  display: grid;
  gap: 16px;
}

.skeleton-card {
  position: relative;
  overflow: hidden;
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: linear-gradient(135deg, #fff, #fbfdff);
  box-shadow: 0 10px 30px rgb(15 23 42 / 4%);
}

.skeleton-card::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: #dbeafe;
  content: '';
}

.skeleton-header {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 22px;
}

.avatar {
  width: 46px;
  height: 46px;
}

.identity {
  display: grid;
  width: min(260px, 65%);
  gap: 8px;
}

.name {
  width: 62%;
}

.meta {
  width: 42%;
}

.line {
  width: 72%;
  margin-bottom: 10px;
}

.line.wide {
  width: 94%;
}

.reply {
  width: 100%;
  height: 64px;
  margin-top: 15px;
  border-radius: 12px;
}

@media (max-width: 640px) {
  .skeleton-card {
    padding: 18px 14px;
  }
}
</style>
