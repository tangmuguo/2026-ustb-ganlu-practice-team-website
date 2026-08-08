<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { submitVolunteerApplication } from '@/apis/applicationAPI'

const formRef = ref()
const submitting = ref(false)
const submitted = ref(false)

const form = reactive({
  name: '',
  phone: '',
  organization: '',
  gradeOrMajor: '',
  preferredRegion: '',
  skills: '',
  introduction: '',
  privacyAgreed: false,
})

const phonePattern = /^1[3-9]\d{9}$/
const rules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 30, message: '姓名长度应为 2～30 个字符', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: phonePattern, message: '请输入正确的 11 位手机号', trigger: 'blur' },
  ],
  organization: [
    { required: true, message: '请输入学校或单位', trigger: 'blur' },
    { max: 100, message: '学校或单位不能超过 100 个字符', trigger: 'blur' },
  ],
  gradeOrMajor: [{ max: 100, message: '年级或专业不能超过 100 个字符', trigger: 'blur' }],
  preferredRegion: [{ max: 100, message: '意向地区不能超过 100 个字符', trigger: 'blur' }],
  skills: [{ max: 300, message: '擅长方向不能超过 300 个字符', trigger: 'blur' }],
  introduction: [
    { required: true, message: '请简单介绍自己和报名原因', trigger: 'blur' },
    { min: 10, max: 1000, message: '自我介绍应为 10～1000 个字符', trigger: 'blur' },
  ],
  privacyAgreed: [{
    validator: (_rule, value, callback) => value ? callback() : callback(new Error('请先阅读并同意隐私说明')),
    trigger: 'change',
  }],
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const response = await submitVolunteerApplication({ ...form })
    if (response.data?.code !== 200) throw new Error(response.data?.message || '提交失败')
    submitted.value = true
    ElMessage.success('报名信息已提交')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="join-page">
    <section class="join-copy">
      <span>JOIN GANLU</span>
      <h1>加入甘露</h1>
      <p>填写以下信息即可完成志愿者意向报名。提交不代表最终录取，负责人会在核实信息后与你联系。</p>
      <div class="privacy-note">
        <el-icon><Lock /></el-icon>
        <div>
          <strong>隐私说明</strong>
          <p>信息仅用于志愿者招募联系和内部审核，不在公开页面展示。正式隐私文本仍需项目负责人确认。</p>
        </div>
      </div>
    </section>

    <section class="form-card">
      <el-result v-if="submitted" icon="success" title="提交成功" sub-title="请留意负责人后续联系。">
        <template #extra><RouterLink class="back-home" to="/">返回首页</RouterLink></template>
      </el-result>
      <template v-else>
        <h2>志愿者报名表</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <div class="form-grid">
            <el-form-item label="姓名" prop="name"><el-input v-model="form.name" maxlength="30" /></el-form-item>
            <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" maxlength="11" inputmode="numeric" /></el-form-item>
            <el-form-item label="学校 / 单位" prop="organization"><el-input v-model="form.organization" maxlength="100" /></el-form-item>
            <el-form-item label="年级 / 专业" prop="gradeOrMajor"><el-input v-model="form.gradeOrMajor" maxlength="100" /></el-form-item>
            <el-form-item label="意向地区" prop="preferredRegion"><el-input v-model="form.preferredRegion" maxlength="100" placeholder="可填写“服从安排”" /></el-form-item>
            <el-form-item label="擅长方向" prop="skills"><el-input v-model="form.skills" maxlength="300" placeholder="如数学、音乐、摄影、组织协调" /></el-form-item>
          </div>
          <el-form-item label="自我介绍与报名原因" prop="introduction">
            <el-input v-model="form.introduction" type="textarea" :rows="6" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item prop="privacyAgreed">
            <el-checkbox v-model="form.privacyAgreed">我已阅读并同意上述隐私说明</el-checkbox>
          </el-form-item>
          <el-button class="submit-button" type="primary" native-type="submit" :loading="submitting">提交报名</el-button>
        </el-form>
      </template>
    </section>
  </div>
</template>

<style scoped>
.join-page { display:grid;max-width:1120px;grid-template-columns:.8fr 1.2fr;gap:48px;align-items:start;margin:0 auto;padding:72px 24px 24px; }
.join-copy { position:sticky;top:120px; }
.join-copy>span { color:#1f6fdf;font-size:13px;font-weight:750;letter-spacing:.14em; }
.join-copy h1 { margin:12px 0 18px;color:#123b70;font-size:clamp(40px,6vw,64px);font-weight:800; }
.join-copy>p { color:#617c9f;font-size:17px;line-height:1.9; }
.privacy-note { display:flex;gap:14px;margin-top:32px;padding:20px;color:#45688f;background:#edf5ff;border:1px solid #dbeaff;border-radius:18px; }
.privacy-note>.el-icon { flex:none;margin-top:4px;color:#2375e6;font-size:22px; }
.privacy-note strong { color:#234f84; }.privacy-note p{margin:6px 0 0;font-size:14px;line-height:1.75;}
.form-card { padding:38px;background:rgba(255,255,255,.95);border:1px solid #dceafc;border-radius:26px;box-shadow:0 20px 50px rgba(35,91,166,.1); }
.form-card h2 { margin:0 0 28px;color:#234f84;font-size:27px;font-weight:760; }
.form-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px; }
.submit-button { width:100%;height:48px;margin-top:8px;background:#2375e6;border-color:#2375e6;font-size:16px;font-weight:700; }
.back-home { display:inline-block;padding:11px 20px;color:white;background:#2375e6;border-radius:12px; }
@media(max-width:860px){.join-page{grid-template-columns:1fr}.join-copy{position:static}.form-card{padding:28px}}
@media(max-width:560px){.form-grid{grid-template-columns:1fr}.join-page{padding-top:48px}.form-card{padding:22px}}
</style>
