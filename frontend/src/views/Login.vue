<script  setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { login } from "@/apis/userAPI"
import {userinfoStore} from "@/stores/userStore"

const loginFormRef = ref(null)
const loading = ref(false)
const userinfo=userinfoStore()
const formRef=ref(null)
const activeName = ref('login'); // 默认激活第一个选项卡（name="login"的tab）
const router=useRouter()
const form = ref({
  username: '',
  password: ''
})

function onSubmit(){
  formRef.value.validate(async (res)=>{
    if(res){
      console.log(form.value)
      const r=await login(form.value)
      
      if(r.data.code==200){
        ElMessage({
          message: '登录成功',
          type: 'success',
        })
        userinfo.setUser(r.data)
        router.replace("/")
      }else{
        ElMessage({
          message: '登录失败，账号或密码错误',
          type: 'fail',
        })
      }   
    }
  })
}

const rules={
  username:[
    { required:true , message:"账号不能为空", trigger:'blur'}
  ],
  password:[
    {required:true , message:"密码不能为空", trigger:'blur'},
    {min:6,max:20, message:"密码长度6-20", trigger:'blur'}
  ]  
}

function onRegStudent(){
  router.push('/regs')
}
</script>

<template>
  <main class="flex-grow container mx-auto px-4 py-8">
        <div class="max-w-6xl mx-auto bg-white rounded-xl shadow-lg overflow-hidden">
            <div class="flex flex-col md:flex-row">
                <!-- 左侧图片部分 -->
                <div class="md:w-1/2 bg-primary h-64 md:h-auto relative">
                    <!--<img src="在这里整一个同文件夹的背景图片，或者图片的网址" alt="甘露支教" class="w-full h-full object-cover opacity-90">-->
                    <div class="absolute inset-0 bg-primary/30 flex flex-col justify-center items-center text-white p-8 text-center">
                        <h2 class="text-3xl font-bold mb-4">甘露支教</h2>
                    </div>
                </div>

                <!-- 右侧登录注册部分 -->
                <div class="md:w-1/2 p-8 md:p-12">
                  <el-tabs v-model="activeName" class="demo-tabs" @tab-click="handleClick">
                    <el-tab-pane label="登录" name="login">
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
                        <el-form-item label="密码" prop="password">
                          <el-input v-model="form.password" placeholder="请输入密码" show-password type="password"/>
                        </el-form-item>
                        <el-form-item>
                          <el-link type="primary" style="">忘记密码？</el-link>
                        </el-form-item>
                        <el-form-item>
                          <el-button type="primary" size="large" @click="onSubmit" style="width: 100%;">登录</el-button>
                        </el-form-item>
                      </el-form>
                    </el-tab-pane>

                    <el-tab-pane label="注册" name="register">                        
                        <div class="flex flex-col space-y-6">                          
                          <el-button type="primary" size="large" @click="onRegStudent" style="width: 100%;margin-left: 0px;">
                            学生账户注册</el-button>                       
                        </div>
                        <div class="text-center text-gray-500 text-sm mt-4">
                            已有账号? <span id="switch-to-login" class="text-primary cursor-pointer hover:underline">立即登录</span>
                        </div>                     
                    </el-tab-pane>
                  </el-tabs>
                    
                    

                    <!-- 登录表单 -->
                    

                    <!-- 注册表单 -->
                    
                </div>
            </div>
        </div>
    </main>
</template>


<style scoped>
.demo-tabs > .el-tabs__content {
  padding: 32px;
  color: #6b778c;
  font-size: 32px;
  font-weight: 600;
}

</style>