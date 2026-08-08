<script setup>
import { siteContent } from '@/config/siteContent'

const contacts = [
  { label: '地址', value: siteContent.contact.address, icon: 'Location' },
  { label: '电话', value: siteContent.contact.phone, icon: 'Phone' },
  { label: '邮箱', value: siteContent.contact.email, icon: 'Message' },
]
</script>

<template>
  <div class="contact-page">
    <section class="contact-intro">
      <span>CONTACT US</span>
      <h1>联系我们</h1>
      <p>{{ siteContent.contact.note }}</p>
    </section>
    <section class="contact-grid">
      <article v-for="item in contacts" :key="item.label">
        <div class="contact-icon"><el-icon><component :is="item.icon" /></el-icon></div>
        <div><h2>{{ item.label }}</h2><p>{{ item.value }}</p></div>
      </article>
    </section>
    <section class="notice-card">
      <el-icon><InfoFilled /></el-icon>
      <p>页面中带“待负责人确认”的内容不是正式联系方式。请在项目负责人确认后修改 <code>src/config/siteContent.js</code>。</p>
    </section>
  </div>
</template>

<style scoped>
.contact-page { max-width: 1040px; margin: 0 auto; padding: 72px 24px 24px; }
.contact-intro { max-width: 700px; }
.contact-intro>span { color:#1f6fdf;font-size:13px;font-weight:750;letter-spacing:.14em; }
.contact-intro h1 { margin:12px 0 18px;color:#123b70;font-size:clamp(40px,7vw,68px);font-weight:800; }
.contact-intro p { color:#617c9f;font-size:18px;line-height:1.9; }
.contact-grid { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px;margin-top:48px; }
.contact-grid article { display:flex;gap:18px;min-height:170px;padding:28px;background:rgba(255,255,255,.94);border:1px solid #dceafc;border-radius:22px;box-shadow:0 14px 32px rgba(35,91,166,.06); }
.contact-icon { display:flex;width:46px;height:46px;flex:none;align-items:center;justify-content:center;color:#2273e5;background:#eaf3ff;border-radius:14px;font-size:22px; }
.contact-grid h2 { margin:3px 0 12px;color:#244e81;font-size:18px;font-weight:700; }
.contact-grid p { margin:0;color:#7087a5;line-height:1.7;word-break:break-word; }
.notice-card { display:flex;gap:12px;margin-top:24px;padding:18px 22px;color:#46688f;background:#f1f7ff;border:1px solid #d7e8ff;border-radius:14px; }
.notice-card p { margin:0;line-height:1.7; }.notice-card code{font-size:13px;}
@media(max-width:820px){.contact-grid{grid-template-columns:1fr}.contact-grid article{min-height:0}}
</style>
