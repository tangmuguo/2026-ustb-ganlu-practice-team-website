<script setup>
import NewsList from '@/components/NewsList.vue'
import { siteContent } from '@/config/siteContent'

const features = [
  { title: '课件共享', description: '按年份、科目和关键词查找教学资源，登录后可预览与下载。', to: '/showm', icon: 'Reading', tone: 'blue' },
  { title: '团队风采', description: '按年份浏览支教小队，查看队员、照片、日志与荣誉。', to: '/fengcai', icon: 'UserFilled', tone: 'green' },
  { title: '互动留言', description: '让教师、志愿者和学生围绕教学与成长持续交流。', to: '/messageboard', icon: 'ChatDotRound', tone: 'gold' },
  { title: 'AI 小助手', description: '登录后获得备课思路、课堂活动和知识讲解建议。', to: '/ai', icon: 'MagicStick', tone: 'purple' },
]
</script>

<template>
  <div class="home-page">
    <section class="intro-section">
      <div class="intro-copy">
        <span class="eyebrow">甘露支教 · 教育志愿服务</span>
        <h1>把知识分享出去，<br>让成长彼此发生</h1>
        <p>{{ siteContent.shortDescription }}</p>
        <div class="intro-actions">
          <RouterLink class="primary-action" to="/join">加入甘露</RouterLink>
          <RouterLink class="secondary-action" to="/about">了解我们</RouterLink>
        </div>
      </div>
      <div class="intro-card" aria-label="甘露支教理念">
        <div class="drop-mark">甘露</div>
        <p>每一份认真准备的课件、每一次耐心回答和每一段真实记录，都能成为下一次出发的养分。</p>
      </div>
    </section>

    <section class="content-section feature-section">
      <div class="section-heading">
        <span>从这里开始</span>
        <h2>连接资源、团队与成长</h2>
      </div>
      <div class="feature-grid">
        <RouterLink v-for="feature in features" :key="feature.to" :to="feature.to" class="feature-card">
          <div class="feature-icon" :class="feature.tone">
            <el-icon><component :is="feature.icon" /></el-icon>
          </div>
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.description }}</p>
          <span class="feature-more">进入功能 <el-icon><Right /></el-icon></span>
        </RouterLink>
      </div>
    </section>

    <section class="content-section news-section">
      <div class="section-heading row-heading">
        <div>
          <span>最新动态</span>
          <h2>正在发生的甘露故事</h2>
        </div>
      </div>
      <NewsList />
    </section>

    <section class="join-section">
      <div>
        <span>期待与你相遇</span>
        <h2>把你的热爱，带到需要它的地方</h2>
        <p>填写志愿者报名信息后，负责人会通过确认后的正式联系方式与你沟通。</p>
      </div>
      <RouterLink to="/join">填写报名信息</RouterLink>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  overflow: hidden;
}

.intro-section,
.content-section,
.join-section {
  max-width: 1200px;
  margin: 0 auto;
  padding-right: 24px;
  padding-left: 24px;
}

.intro-section {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(300px, 0.8fr);
  gap: 64px;
  align-items: center;
  padding-top: 88px;
  padding-bottom: 92px;
}

.eyebrow,
.section-heading > span,
.section-heading div > span,
.join-section span {
  color: #0f7c8f;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.intro-copy h1 {
  margin: 18px 0 22px;
  color: #173646;
  font-size: clamp(38px, 5vw, 66px);
  font-weight: 800;
  letter-spacing: -0.045em;
  line-height: 1.13;
}

.intro-copy p {
  max-width: 620px;
  margin: 0;
  color: #647481;
  font-size: 18px;
  line-height: 1.9;
}

.intro-actions {
  display: flex;
  gap: 14px;
  margin-top: 34px;
}

.primary-action,
.secondary-action {
  display: inline-flex;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
  border-radius: 999px;
  font-weight: 700;
}

.primary-action {
  color: white;
  background: #0f7089;
  box-shadow: 0 12px 28px rgba(15, 112, 137, 0.24);
}

.secondary-action {
  color: #315363;
  background: white;
  border: 1px solid #d9e3e8;
}

.intro-card {
  position: relative;
  padding: 48px 38px;
  color: white;
  background: linear-gradient(145deg, #0f5b7c, #15908d);
  border-radius: 32px;
  box-shadow: 0 26px 70px rgba(19, 83, 105, 0.24);
  overflow: hidden;
}

.intro-card::after {
  position: absolute;
  right: -50px;
  bottom: -70px;
  width: 190px;
  height: 190px;
  content: '';
  background: rgba(255, 255, 255, 0.09);
  border-radius: 50%;
}

.drop-mark {
  display: flex;
  width: 100px;
  height: 112px;
  align-items: center;
  justify-content: center;
  margin-bottom: 30px;
  color: #0f5b7c;
  background: #f4d35e;
  border-radius: 58% 42% 55% 45% / 68% 54% 46% 32%;
  font-size: 22px;
  font-weight: 800;
}

.intro-card p {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: 18px;
  line-height: 1.9;
}

.content-section {
  padding-top: 72px;
  padding-bottom: 72px;
}

.feature-section {
  max-width: none;
  padding-right: max(24px, calc((100vw - 1152px) / 2));
  padding-left: max(24px, calc((100vw - 1152px) / 2));
  background: white;
}

.section-heading {
  margin-bottom: 34px;
}

.section-heading h2,
.join-section h2 {
  margin: 9px 0 0;
  color: #173646;
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 780;
  letter-spacing: -0.025em;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

.feature-card {
  display: flex;
  min-height: 310px;
  flex-direction: column;
  padding: 28px;
  background: #f8fafb;
  border: 1px solid #edf1f3;
  border-radius: 22px;
  transition: 0.25s ease;
}

.feature-card:hover {
  border-color: #c7dce4;
  box-shadow: 0 20px 40px rgba(39, 70, 83, 0.1);
  transform: translateY(-5px);
}

.feature-icon {
  display: flex;
  width: 52px;
  height: 52px;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  border-radius: 16px;
  font-size: 25px;
}

.feature-icon.blue { color: #176b91; background: #e0f0f7; }
.feature-icon.green { color: #287d69; background: #e2f2eb; }
.feature-icon.gold { color: #987116; background: #fbf0cf; }
.feature-icon.purple { color: #6f59a6; background: #eee9fa; }

.feature-card h3 {
  margin: 0 0 12px;
  color: #213f4d;
  font-size: 21px;
  font-weight: 740;
}

.feature-card p {
  margin: 0;
  color: #6c7c87;
  line-height: 1.75;
}

.feature-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: auto;
  padding-top: 24px;
  color: #0f7089;
  font-weight: 700;
}

.news-section {
  padding-top: 88px;
}

.join-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  margin-top: 44px;
  padding-top: 48px;
  padding-bottom: 48px;
  color: white;
  background: #173646;
  border-radius: 28px;
  box-shadow: 0 24px 60px rgba(23, 54, 70, 0.2);
}

.join-section h2,
.join-section span {
  color: white;
}

.join-section p {
  margin: 16px 0 0;
  color: #bed0d8;
}

.join-section > a {
  flex: none;
  padding: 14px 24px;
  color: #173646;
  background: #f4d35e;
  border-radius: 999px;
  font-weight: 750;
}

@media (max-width: 960px) {
  .intro-section {
    grid-template-columns: 1fr;
    gap: 38px;
  }

  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .intro-section,
  .content-section {
    padding-top: 54px;
    padding-bottom: 54px;
  }

  .intro-actions,
  .join-section {
    align-items: stretch;
    flex-direction: column;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }

  .feature-card {
    min-height: 260px;
  }

  .join-section {
    margin-right: 16px;
    margin-left: 16px;
  }

  .join-section > a {
    text-align: center;
  }
}
</style>
