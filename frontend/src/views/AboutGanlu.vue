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
        <h2>以钢铁之魂铸求知火种，以山川为卷写青春作答</h2>
      </div>
      <ul>
        <li v-for="item in siteContent.about.philosophy" :key="item">
          <el-icon><CircleCheckFilled /></el-icon>{{ item }}
        </li>
      </ul>
    </section>

    <section class="two-column">
      <div class="content-card">
        <span class="section-kicker section-kicker-large">服务地区</span>
        <div class="tag-list service-region-list">
          <span v-for="region in siteContent.about.serviceRegions" :key="region">{{ region }}</span>
        </div>
      </div>
      <div class="content-card">
        <span class="section-kicker section-kicker-large">发展历程</span>
        <div class="timeline">
          <div v-for="item in siteContent.about.timeline" :key="item.label" class="timeline-item">
            <strong>{{ item.label }}</strong>
            <div><p>{{ item.description }}</p></div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.public-page { max-width: 1120px; margin: 0 auto; padding: 64px 24px 24px; }
.page-hero { max-width: 760px; margin-bottom: 48px; }
.page-hero span,.section-kicker { color: #1f6fdf; font-size: 13px; font-weight: 750; letter-spacing: .14em; }
.page-hero h1 { margin: 12px 0 18px; color: #123b70; font-size: clamp(40px,7vw,68px); font-weight: 800; }
.page-hero p { margin: 0; color: #617c9f; font-size: 18px; line-height: 1.9; }
.content-card { padding: 36px; background: rgba(255,255,255,.94); border: 1px solid #dceafc; border-radius: 24px; box-shadow: 0 14px 36px rgba(35,91,166,.08); }
.content-card h2 { margin: 10px 0 24px; color: #1c477d; font-size: 28px; font-weight: 750; }
.philosophy-card { display: flex; align-items: flex-start; justify-content: space-between; gap: 40px; }
.philosophy-card h2 { font-size: 25px; }
.philosophy-card ul { display: grid; min-width: 300px; gap: 16px; margin: 0; padding: 0; list-style: none; }
.philosophy-card li { display: flex; align-items: center; gap: 10px; color: #42648d; }
.philosophy-card li .el-icon { color: #2c84ec; }
.two-column { display: grid; grid-template-columns: .8fr 1.2fr; gap: 24px; margin-top: 24px; }
.section-kicker-large { display: block; font-size: 35px; line-height: 1.2; letter-spacing: 0; }
.tag-list { display: flex; flex-wrap: wrap; gap: 10px; }
.service-region-list { margin-top: 68px; }
.tag-list span { padding: 9px 13px; color: #35659a; background: #ebf4ff; border: 1px solid #d8e9ff; border-radius: 999px; }
.timeline { display: grid; gap: 24px; margin-top: 68px; }
.timeline-item { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 18px; }
.timeline-item strong { color: #2375e6; white-space: nowrap; }
.timeline-item > div { min-width: 0; }
.timeline-item p { margin: 0; color: #7087a5; line-height: 1.7; overflow-wrap: anywhere; }
@media(max-width:760px){.philosophy-card{flex-direction:column}.philosophy-card ul{min-width:0}.two-column{grid-template-columns:1fr}.content-card{padding:26px}.service-region-list{margin-top:28px}.timeline{margin-top:28px}.timeline-item{grid-template-columns:1fr;gap:8px}.timeline-item strong{white-space:normal}}
</style>
