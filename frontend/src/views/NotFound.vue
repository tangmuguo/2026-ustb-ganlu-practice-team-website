<template>
  <div class="not-found">
    <span>404</span>
    <h1>没有找到这个页面</h1>
    <p>地址可能已经变化，也可能输入有误。你可以返回首页继续浏览。</p>
    <RouterLink to="/">返回首页</RouterLink>
  </div>
</template>

<style scoped>
.not-found { display:flex;min-height:62vh;align-items:center;justify-content:center;flex-direction:column;padding:48px 24px;text-align:center; }
.not-found span { color:#c7dce4;font-size:clamp(88px,20vw,180px);font-weight:900;line-height:1; }
.not-found h1 { margin:22px 0 12px;color:#173646;font-size:32px;font-weight:780; }
.not-found p { max-width:520px;margin:0;color:#71818b;line-height:1.8; }
.not-found a { margin-top:28px;padding:12px 24px;color:white;background:#0f7089;border-radius:999px;font-weight:700; }
</style>
