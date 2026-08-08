<script setup>
import { siteContent } from '@/config/siteContent'
</script>

<template>
  <div class="public-page">
    <section class="page-hero">
      <span>ABOUT GANLU</span>
      <h1>关于甘露</h1>
      <p>{{ siteContent.about.introduction }}</p>
    </section>

    <section class="content-card philosophy-card">
      <div>
        <span class="section-kicker">我们的理念</span>
        <h2>让教育志愿服务留下可持续的价值</h2>
      </div>
      <ul>
        <li v-for="item in siteContent.about.philosophy" :key="item">
          <el-icon><CircleCheckFilled /></el-icon>{{ item }}
        </li>
      </ul>
    </section>

    <section class="two-column">
      <div class="content-card">
        <span class="section-kicker">服务地区</span>
        <h2>我们去往哪里</h2>
        <div class="tag-list">
          <span v-for="region in siteContent.about.serviceRegions" :key="region">{{ region }}</span>
        </div>
      </div>
      <div class="content-card">
        <span class="section-kicker">发展历程</span>
        <h2>我们如何走到今天</h2>
        <div class="timeline">
          <div v-for="item in siteContent.about.timeline" :key="`${item.year}-${item.title}`" class="timeline-item">
            <strong>{{ item.year }}</strong>
            <div><h3>{{ item.title }}</h3><p>{{ item.description }}</p></div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.public-page { max-width: 1120px; margin: 0 auto; padding: 64px 24px 24px; }
.page-hero { max-width: 760px; margin-bottom: 48px; }
.page-hero span,.section-kicker { color: #0f7c8f; font-size: 13px; font-weight: 750; letter-spacing: .14em; }
.page-hero h1 { margin: 12px 0 18px; color: #173646; font-size: clamp(40px,7vw,68px); font-weight: 800; }
.page-hero p { margin: 0; color: #667985; font-size: 18px; line-height: 1.9; }
.content-card { padding: 36px; background: white; border: 1px solid #e7edf0; border-radius: 24px; box-shadow: 0 14px 36px rgba(36,63,74,.06); }
.content-card h2 { margin: 10px 0 24px; color: #213f4d; font-size: 28px; font-weight: 750; }
.philosophy-card { display: flex; align-items: flex-start; justify-content: space-between; gap: 40px; }
.philosophy-card ul { display: grid; min-width: 300px; gap: 16px; margin: 0; padding: 0; list-style: none; }
.philosophy-card li { display: flex; align-items: center; gap: 10px; color: #3c5967; }
.philosophy-card li .el-icon { color: #2d967c; }
.two-column { display: grid; grid-template-columns: .8fr 1.2fr; gap: 24px; margin-top: 24px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 10px; }
.tag-list span { padding: 9px 13px; color: #315b69; background: #edf7f8; border-radius: 999px; }
.timeline { display: grid; gap: 24px; }
.timeline-item { display: grid; grid-template-columns: 78px 1fr; gap: 18px; }
.timeline-item strong { color: #0f7089; }
.timeline-item h3 { margin: 0 0 6px; color: #2a4653; font-weight: 700; }
.timeline-item p { margin: 0; color: #72818a; line-height: 1.7; }
@media(max-width:760px){.philosophy-card{flex-direction:column}.philosophy-card ul{min-width:0}.two-column{grid-template-columns:1fr}.content-card{padding:26px}}
</style>
