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
          <RouterLink class="primary-action" to="/about">了解我们</RouterLink>
        </div>
        <div class="intro-tags" aria-label="甘露支教服务方向">
          <span><i></i>资源共建</span>
          <span><i></i>团队归档</span>
          <span><i></i>陪伴成长</span>
        </div>
      </div>
      <div class="intro-card" aria-label="甘露支教理念">
        <div class="intro-card__badge"><span></span>教育志愿服务</div>
        <div class="drop-mark">甘露</div>
        <div class="intro-card__copy">
          <strong>让每一次出发<br>都有回响</strong>
          <p>每一份认真准备的课件、每一次耐心回答和每一段真实记录，都能成为下一次出发的养分。</p>
        </div>
        <div class="intro-card__footer">资源 · 连接 · 成长 <el-icon><Right /></el-icon></div>
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
  </div>
</template>

<style scoped>
.home-page {
  position: relative;
  overflow: hidden;
}

.intro-section,
.content-section {
  max-width: 1200px;
  margin: 0 auto;
  padding-right: 24px;
  padding-left: 24px;
}

.intro-section {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(300px, 0.8fr);
  gap: 56px;
  align-items: center;
  padding-top: 76px;
  padding-bottom: 84px;
}

.eyebrow,
.section-heading > span,
.section-heading div > span,
.join-section span {
  color: #1f6fdf;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 11px;
  background: #eaf3ff;
  border: 1px solid #d5e7ff;
  border-radius: 999px;
  letter-spacing: 0.1em;
}

.eyebrow::before {
  width: 7px;
  height: 7px;
  background: #398cf8;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(57, 140, 248, 0.14);
  content: '';
}

.intro-copy h1 {
  margin: 18px 0 22px;
  color: #123b70;
  font-size: clamp(38px, 5vw, 66px);
  font-weight: 800;
  letter-spacing: -0.045em;
  line-height: 1.13;
}

.intro-copy p {
  max-width: 620px;
  margin: 0;
  color: #617c9f;
  font-size: 18px;
  line-height: 1.9;
}

.intro-actions {
  display: flex;
  gap: 14px;
  margin-top: 34px;
}

.primary-action {
  display: inline-flex;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
  border-radius: 13px;
  font-weight: 750;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.primary-action {
  color: white;
  background: linear-gradient(135deg, #418ff8, #1d65d6);
  box-shadow: 0 13px 28px rgba(35, 114, 232, 0.25);
}

.primary-action:hover {
  transform: translateY(-2px);
}

.primary-action:hover {
  color: white;
  box-shadow: 0 17px 32px rgba(35, 114, 232, 0.32);
}

.intro-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 9px 18px;
  margin-top: 26px;
  color: #58769d;
  font-size: 13px;
  font-weight: 700;
}

.intro-tags span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.intro-tags i {
  width: 7px;
  height: 7px;
  background: #4b96ff;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(75, 150, 255, 0.13);
}

.intro-card {
  position: relative;
  display: flex;
  min-height: 386px;
  flex-direction: column;
  justify-content: space-between;
  padding: 31px 34px 27px;
  color: white;
  background:
    radial-gradient(circle at 85% 18%, rgba(152, 211, 255, 0.6), transparent 18%),
    radial-gradient(circle at 10% 105%, rgba(30, 132, 255, 0.56), transparent 37%),
    linear-gradient(145deg, #0e3c80, #1766d3 64%, #2587ee);
  border: 1px solid rgba(190, 224, 255, 0.48);
  border-radius: 30px;
  box-shadow: 0 28px 64px rgba(20, 83, 173, 0.28);
  isolation: isolate;
  overflow: hidden;
}

.intro-card::before,
.intro-card::after {
  position: absolute;
  border: 1px solid rgba(255, 255, 255, 0.19);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}

.intro-card::before {
  top: -115px;
  right: -100px;
  width: 285px;
  height: 285px;
}

.intro-card::after {
  right: -62px;
  bottom: -82px;
  width: 205px;
  height: 205px;
  border-width: 25px;
  opacity: 0.32;
}

.intro-card__badge,
.intro-card__copy,
.intro-card__footer,
.drop-mark {
  position: relative;
  z-index: 1;
}

.intro-card__badge {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  color: #e4f3ff;
  background: rgba(255, 255, 255, 0.13);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.intro-card__badge span {
  width: 6px;
  height: 6px;
  background: #d9f2ff;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(218, 244, 255, 0.14);
}

.drop-mark {
  display: flex;
  width: 90px;
  height: 100px;
  align-items: center;
  justify-content: center;
  color: #2168d2;
  background: linear-gradient(145deg, #f8fcff, #dceeff);
  border-radius: 58% 42% 55% 45% / 68% 54% 46% 32%;
  font-size: 22px;
  font-weight: 800;
  box-shadow: 0 16px 28px rgba(7, 42, 104, 0.2);
}

.intro-card__copy strong {
  display: block;
  margin-bottom: 10px;
  color: #fff;
  font-size: clamp(25px, 3vw, 34px);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.18;
}

.intro-card p {
  margin: 0;
  color: rgba(235, 247, 255, 0.9);
  font-size: 14px;
  line-height: 1.75;
}

.intro-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: rgba(233, 246, 255, 0.78);
  border-top: 1px solid rgba(255, 255, 255, 0.2);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
  padding-top: 17px;
}

.content-section {
  padding-top: 72px;
  padding-bottom: 72px;
}

.feature-section {
  max-width: none;
  padding-right: max(24px, calc((100vw - 1152px) / 2));
  padding-left: max(24px, calc((100vw - 1152px) / 2));
  background: linear-gradient(180deg, rgba(234, 244, 255, 0.76), rgba(255, 255, 255, 0.94));
  border-top: 1px solid rgba(208, 226, 249, 0.7);
  border-bottom: 1px solid rgba(208, 226, 249, 0.68);
}

.section-heading {
  margin-bottom: 34px;
}

.section-heading h2 {
  margin: 9px 0 0;
  color: #133d72;
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
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #dbe9fa;
  border-radius: 20px;
  box-shadow: 0 10px 25px rgba(53, 107, 174, 0.05);
  transition: transform 0.25s ease, border-color 0.25s ease, box-shadow 0.25s ease;
}

.feature-card:hover {
  border-color: #b7d5fb;
  box-shadow: 0 20px 40px rgba(39, 102, 181, 0.14);
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

.feature-icon.blue { color: #1d68dc; background: #e8f1ff; }
.feature-icon.green { color: #207bc5; background: #e5f4ff; }
.feature-icon.gold { color: #346dba; background: #eaf3ff; }
.feature-icon.purple { color: #5567c9; background: #eef0ff; }

.feature-card h3 {
  margin: 0 0 12px;
  color: #1c477d;
  font-size: 21px;
  font-weight: 740;
}

.feature-card p {
  margin: 0;
  color: #6983a2;
  line-height: 1.75;
}

.feature-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: auto;
  padding-top: 24px;
  color: #1f70dd;
  font-weight: 700;
}

.news-section {
  padding-top: 88px;
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

  .feature-grid {
    grid-template-columns: 1fr;
  }

  .feature-card {
    min-height: 260px;
  }

}
</style>
