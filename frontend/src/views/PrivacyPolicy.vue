<script setup>
import { siteContent } from '@/config/siteContent'
</script>

<template>
  <main class="privacy-page">
    <header>
      <p>PRIVACY &amp; MINOR PROTECTION</p>
      <h1>隐私与未成年人保护说明</h1>
      <span>版本：2026-08-15 · 如有隐私相关问题，请通过本页公示的联系方式咨询。</span>
    </header>

    <section>
      <h2>我们收集什么</h2>
      <p>为创建和管理账号，系统可能处理账号名、头像、团队或学校相关资料、联系方式及学生管理所需字段。公开互动页只展示审核通过的内容和最小化公开名称，不展示学生真实姓名、手机号、学校或年级。</p>
    </section>
    <section>
      <h2>未成年人账号与互动</h2>
      <p>学生账号不提供公开自助注册。账号须由管理员或已绑定团队创建；在安全负责人完成线下身份核验和监护人授权记录前，学生账号不能发布留言或回复。系统不要求在普通账号资料中上传或长期保存身份证件影像。</p>
    </section>
    <section>
      <h2>内容审核、举报与留存</h2>
      <p>新留言和回复默认进入待审核状态，审核通过后才公开。用户可对已公开的留言或回复提交举报；管理员可记录处置结果。为保障服务安全与依法协助，必要的操作审计和处置记录按系统保留策略保存，不应直接通过“注销”或“删除”请求破坏关联证据。</p>
    </section>
    <section>
      <h2>更正、撤回与投诉</h2>
      <p>内容举报可通过登录后的互动页提交。涉及个人资料更正、删除或撤回授权，请通过以下正式联系方式联系我们：</p>
      <ul>
        <li>地址：{{ siteContent.contact.address }}</li>
        <li>电话：{{ siteContent.contact.phone }}</li>
        <li>邮箱：{{ siteContent.contact.email }}</li>
      </ul>
    </section>
    <section>
      <h2>第三方与更新</h2>
      <p>当前上线源码不包含 AI 小助手或第三方大模型调用。政策或实际处理方式变更前，应先由负责人完成评估、更新版本号并重新取得需要的授权。</p>
    </section>
  </main>
</template>

<style scoped>
.privacy-page { max-width: 900px; margin: 0 auto; padding: 64px 24px; color: #31485d; }
header { padding-bottom: 30px; border-bottom: 1px solid #dce8f6; } header p { margin: 0 0 9px; color: #2675dd; font-size: 12px; font-weight: 800; letter-spacing: .13em; } h1 { margin: 0; color: #173d72; font-size: clamp(30px, 5vw, 45px); } header span { display: block; margin-top: 13px; color: #70839a; line-height: 1.7; }
section { margin-top: 28px; padding: 22px 24px; border: 1px solid #e1ebf6; border-radius: 16px; background: #fff; box-shadow: 0 8px 20px rgb(25 73 126 / 4%); } h2 { margin: 0 0 12px; color: #244f82; font-size: 20px; } p, li { line-height: 1.85; } p { margin: 0; } ul { margin: 12px 0 0; padding-left: 20px; color: #647d98; }
</style>
