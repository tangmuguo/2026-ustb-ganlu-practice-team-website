<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/apis/userAPI'
import { userinfoStore } from '@/stores/userStore'

const formRef = ref()
const loading = ref(false)
const router = useRouter()
const route = useRoute()
const userStore = userinfoStore()
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 72, message: '密码长度应为 6～72 个字符', trigger: 'blur' },
  ],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const { data } = await login(form)
    if (data?.code !== 200) throw new Error(data?.message || '账号或密码错误')

    const session = data.content || {}
    userStore.setSession({
      token: session.token || data.token,
      user: session.user || (session.id ? session : data.user),
    })

    if (!userStore.isLoggedIn) throw new Error('登录响应缺少 Token 或用户信息')
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/'
    router.replace(redirect)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-story">
      <span>WELCOME BACK</span>
      <h1>欢迎回到<br>甘露支教</h1>
      <p>登录后可参与互动，并根据账号权限管理课件和团队内容。</p>
    </section>
    <section class="login-card">
      <h2>账号登录</h2>
      <p class="card-description">请输入管理员、团队或学生账号。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号" prop="username"><el-input v-model="form.username" size="large" autocomplete="username" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" @keyup.enter="submit" /></el-form-item>
        <el-button class="login-submit" type="primary" size="large" native-type="submit" :loading="loading">登录</el-button>
      </el-form>
      <div class="register-link">学生账号请联系已核验团队或管理员线下完成核验和开通。</div>
    </section>
  </div>
</template>

<style scoped>
.login-page { display:grid;max-width:1000px;min-height:68vh;grid-template-columns:1fr 1fr;align-items:center;margin:0 auto;padding:56px 24px; }
.login-story { position:relative;align-self:stretch;padding:64px 48px;color:white;overflow:hidden;background:radial-gradient(circle at 84% 17%,rgba(162,215,255,.42),transparent 23%),linear-gradient(145deg,#103c7a,#1d68d7);border-radius:28px 0 0 28px; }
.login-story::after{position:absolute;right:-54px;bottom:-72px;width:210px;height:210px;border:26px solid rgba(255,255,255,.12);border-radius:50%;content:''}
.login-story span,.login-story h1,.login-story p{position:relative;z-index:1}.login-story span { color:#d9eeff;font-size:12px;font-weight:750;letter-spacing:.14em; }.login-story h1{margin:20px 0;color:white;font-size:48px;font-weight:800;line-height:1.15}.login-story p{color:#d7eaff;line-height:1.9}
.login-card { align-self:stretch;padding:58px 48px;background:rgba(255,255,255,.96);border:1px solid #dceafc;border-left:0;border-radius:0 28px 28px 0;box-shadow:0 20px 50px rgba(35,91,166,.1); }
.login-card h2 { margin:0;color:#234f84;font-size:30px;font-weight:770; }.card-description{margin:10px 0 30px;color:#7188a6}.login-submit{width:100%;margin-top:8px;background:#2375e6;border-color:#2375e6;font-weight:700}.register-link{margin-top:24px;color:#7188a6;text-align:center}.register-link a{color:#2375e6;font-weight:700}
@media(max-width:760px){.login-page{grid-template-columns:1fr}.login-story{padding:38px 30px;border-radius:24px 24px 0 0}.login-story h1{font-size:38px}.login-card{padding:38px 28px;border:1px solid #dceafc;border-top:0;border-radius:0 0 24px 24px}}
</style>
