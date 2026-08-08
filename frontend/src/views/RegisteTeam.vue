<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { AddTeam } from '@/apis/userAPI'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', teamname: '', helplocation: '', helpschool: '', phone: '', password: '', confirmPassword: '' })
const rules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }, { min: 3, max: 30, message: '账号长度应为 3～30 个字符', trigger: 'blur' }],
  teamname: [{ required: true, message: '请输入团队简称', trigger: 'blur' }, { min: 2, max: 100, message: '团队简称应为 2～100 个字符', trigger: 'blur' }],
  helplocation: [{ required: true, message: '请输入支教地', trigger: 'blur' }, { max: 100, message: '支教地不能超过 100 个字符', trigger: 'blur' }],
  helpschool: [{ required: true, message: '请输入支教小学', trigger: 'blur' }, { max: 150, message: '学校名称不能超过 150 个字符', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 8, max: 72, message: '密码长度应为 8～72 个字符', trigger: 'blur' }],
  confirmPassword: [{ validator: (_rule, value, callback) => value === form.password ? callback() : callback(new Error('两次输入的密码不一致')), trigger: ['blur', 'change'] }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const { data } = await AddTeam({ ...form })
    if (data?.code !== 200) throw new Error(data?.message || '创建失败')
    ElMessage.success('团队账号已创建')
    router.push('/muser')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '创建失败，请稍后重试')
  } finally { loading.value = false }
}
</script>

<template>
  <div class="register-page"><div class="register-card">
    <div class="register-heading"><span>ADMIN ONLY</span><h1>创建团队账号</h1><p>只有系统管理员可以创建团队账号，账号等级由后端固定为团队账号。</p></div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <div class="form-grid">
        <el-form-item label="登录账号" prop="username"><el-input v-model="form.username" maxlength="30" /></el-form-item>
        <el-form-item label="团队简称" prop="teamname"><el-input v-model="form.teamname" maxlength="100" /></el-form-item>
        <el-form-item label="支教地" prop="helplocation"><el-input v-model="form.helplocation" maxlength="100" /></el-form-item>
        <el-form-item label="支教小学" prop="helpschool"><el-input v-model="form.helpschool" maxlength="150" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" maxlength="11" inputmode="numeric" /></el-form-item><div></div>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="72" /></el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password maxlength="72" /></el-form-item>
      </div>
      <el-button class="submit-button" type="primary" native-type="submit" :loading="loading">创建团队账号</el-button>
    </el-form>
  </div></div>
</template>

<style scoped>
.register-page{max-width:900px;margin:0 auto;padding:56px 24px}.register-card{padding:42px;background:rgba(255,255,255,.95);border:1px solid #dceafc;border-radius:26px;box-shadow:0 20px 50px rgba(35,91,166,.1)}.register-heading{margin-bottom:30px}.register-heading span{color:#2375e6;font-size:12px;font-weight:750;letter-spacing:.14em}.register-heading h1{margin:10px 0;color:#234f84;font-size:34px;font-weight:780}.register-heading p{margin:0;color:#7188a6}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px}.submit-button{width:100%;height:48px;background:#2375e6;border-color:#2375e6;font-weight:700}@media(max-width:650px){.register-card{padding:26px}.form-grid{grid-template-columns:1fr}.form-grid>div:empty{display:none}}
</style>
