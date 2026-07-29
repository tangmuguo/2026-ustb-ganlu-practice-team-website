<script setup>
import { ref } from 'vue'
const form = ref({
  username: '',
  password: ''
})
</script>

<template>
  <main class="flex-grow container mx-auto px-4 py-8">
        <div class="max-w-md mx-auto bg-white rounded-xl shadow-lg p-8">
            <h2 class="text-2xl font-bold mb-6 text-center">小队账户注册</h2>
            <el-form :model="form" label-width="auto" label-position="top" style="max-width: 600px">
              <el-form-item label="团队名称(简称)">
                <el-input v-model="form.username" placeholder="请输入团队名称(简称)"/>
              </el-form-item>
              <el-form-item label="支教地">
                <el-input v-model="form.username" placeholder="请输入支教地"/>
              </el-form-item>
              <el-form-item label="支教小学">
                <el-input v-model="form.username" placeholder="请输入支教小学"/>
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="form.username" placeholder="请输入手机号"/>
              </el-form-item>
              <el-form-item label="设置密码">
                <el-input v-model="form.username" placeholder="请设置密码" show-password type="password"/>
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="form.password" placeholder="请再次输入密码" show-password type="password"/>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="large" @click="onSubmit" style="width: 100%;">登录</el-button>
              </el-form-item>
              <el-form-item>
                <div class="link-container">
                  <el-link type="primary" style="">已有账号，立即登录</el-link>
                  <el-link type="primary" style="">切换到学生账户注册</el-link>
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