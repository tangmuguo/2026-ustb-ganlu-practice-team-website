<script setup>
import { ref } from 'vue'
import {AddStudent} from '@/apis/userAPI'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
const useRoute=useRouter()
const formRef=ref(null)
const form = ref({
  username: '',
  realname:'',
  password: '',
  repassword:'',
})

async function onSubmit(){
  formRef.value.validate(async (res)=>{
    if(res){
      if(form.password !== form.repassword){
        ElMessage.success("请保持密码一致")
        return 
      }
      const d=await AddStudent(form.value)
      if(d.data.code===200){
        ElMessage.success("注册成功")
        useRoute.push('/login')
      }else{
        ElMessage.success("注册失败")
      }
    }
  })  
}

const rules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  realname: [
    { required: true, message: '请输入真名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  belongschool: [
    { required: true, message: '请输入所属学校', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur',      
    },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  repassword: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur',      
    },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  teamname: [
    { required: true, message: '请输入团队名', trigger: 'blur' },
    { min: 2, max: 30, message: '长度在 2 到 30 个字符', trigger: 'blur' }
  ]
}
</script>

<template>
  <main class="flex-grow container mx-auto px-4 py-8">
        <div class="max-w-md mx-auto bg-white rounded-xl shadow-lg p-8">
            <h2 class="text-2xl font-bold mb-6 text-center">学生账户注册</h2>
            <el-form 
              ref="formRef"
              :model="form" 
              label-width="auto" 
              label-position="top" 
              :rules="rules"
              style="max-width: 600px">
              <el-form-item label="账号" prop="username">
                <el-input v-model="form.username" placeholder="请输入账号"/>
              </el-form-item>
              <el-form-item label="学生真名" prop="realname">
                <el-input v-model="form.realname" placeholder="请输入学生真名"/>
              </el-form-item>
              <el-form-item label="所属小学" prop="belongschool">
                <el-input v-model="form.belongschool" placeholder="请输入所属小学"/>
              </el-form-item>
              <el-form-item label="年级">
                <el-input v-model="form.grade" placeholder="请输入年级"/>
              </el-form-item>              
              <el-form-item label="设置密码" prop="password">
                <el-input v-model="form.password" placeholder="请设置密码" show-password type="password"/>
              </el-form-item>
              <el-form-item label="确认密码" prop="repassword">
                <el-input v-model="form.repassword" placeholder="请再次输入密码" show-password type="password"/>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="large" @click="onSubmit" style="width: 100%;">注册</el-button>
              </el-form-item>
              <el-form-item>
                <div class="link-container">
                  <el-link type="primary" style="">已有账号，立即登录</el-link>
                </div>                
              </el-form-item>              
            </el-form>            
      </div>
  </main>
</template>

<style scoped>
.link-container {
  display: flex;
  justify-content: space-between;
  width: 100%;
}
</style>