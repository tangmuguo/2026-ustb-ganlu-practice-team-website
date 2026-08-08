<script setup>
import { siteContent } from '@/config/siteContent'
</script>

<template>
  <footer class="site-footer">
    <div class="footer-grid">
      <section>
        <h2>{{ siteContent.name }}</h2>
        <p class="footer-intro">{{ siteContent.shortDescription }}</p>
      </section>

      <section>
        <h2>网站导航</h2>
        <div class="footer-links">
          <RouterLink to="/about">关于甘露</RouterLink>
          <RouterLink to="/contact">联系我们</RouterLink>
          <RouterLink to="/join">加入甘露</RouterLink>
          <RouterLink to="/showm">课件共享</RouterLink>
          <RouterLink to="/fengcai">团队风采</RouterLink>
          <RouterLink to="/messageboard">互动留言</RouterLink>
        </div>
      </section>

      <section>
        <h2>联系信息</h2>
        <ul class="contact-list">
          <li><strong>地址：</strong>{{ siteContent.contact.address }}</li>
          <li><strong>电话：</strong>{{ siteContent.contact.phone }}</li>
          <li><strong>邮箱：</strong>{{ siteContent.contact.email }}</li>
        </ul>
      </section>
    </div>
    <div class="copyright">
      <span>© {{ new Date().getFullYear() }} 甘露支教</span>
      <span>备案信息待负责人确认</span>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: 72px;
  color: #d7e4ea;
  background: #123747;
}

.footer-grid {
  display: grid;
  max-width: 1200px;
  grid-template-columns: 1.2fr 1fr 1.4fr;
  gap: 56px;
  margin: 0 auto;
  padding: 56px 24px 44px;
}

h2 {
  margin: 0 0 18px;
  color: white;
  font-size: 17px;
  font-weight: 700;
}

.footer-intro {
  max-width: 360px;
  margin: 0;
  color: #a9c0ca;
  line-height: 1.9;
}

.footer-links {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
}

.footer-links a {
  color: #a9c0ca;
}

.footer-links a:hover {
  color: #f4d35e;
}

.contact-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  color: #a9c0ca;
  list-style: none;
  line-height: 1.7;
}

.contact-list strong {
  color: #d7e4ea;
}

.copyright {
  display: flex;
  max-width: 1200px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 0 auto;
  padding: 22px 24px;
  color: #7896a3;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 13px;
}

@media (max-width: 800px) {
  .footer-grid {
    grid-template-columns: 1fr;
    gap: 32px;
    padding-top: 44px;
  }

  .copyright {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
