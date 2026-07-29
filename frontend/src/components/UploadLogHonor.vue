<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {uploadWholeWord} from "@/apis/fengcaiAPI"
import {userinfoStore} from '@/stores/userStore'

const formRef = ref(null)
const submitting = ref(false)
const userinfo=userinfoStore()

const form = ref({
  userId:'',
  type: '团队日志',
  caption: '',
  content: ''
})

const datatype= [
    {
        value: 4,
        label: '团队日志',
    },
    {
        value: 3,
        label: '团队荣誉',
    }
]

const datatype_value=ref(datatype[0].value)

const rules = {
  caption: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const submitForm = async () => {
  try {
    const valid = await formRef.value.validate()
    if (!valid) return
    
    submitting.value = true
    form.value.type=datatype_value.value
    form.value.userId=userinfo.user.content.id
    const d=await uploadWholeWord(form.value)

    // 重置表单
    formRef.value.resetFields()
    ElMessage.success('提交成功')
  } catch (error) {
    ElMessage.error('提交失败: ' + error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
    <div class="upload-section">       
        <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="文件类型" prop="type">
            <el-select 
          v-model="datatype_value" 
          placeholder="请选择文件类型"
          style="width: 100%"
        >
          <el-option
            v-for="item in datatype"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        </el-form-item>
        
        <el-form-item label="标题" prop="caption">
            <el-input v-model="form.caption" placeholder="请输入标题"></el-input>
        </el-form-item>
        
        <el-form-item label="内容" prop="content">
            <el-input
            type="textarea"
            :rows="5"
            v-model="form.content"
            placeholder="请输入详细内容"
            ></el-input>
        </el-form-item>
        
        <el-form-item>
            <el-button 
            type="primary" 
            @click="submitForm"
            :loading="submitting"
            >提交内容</el-button>
        </el-form-item>
        </el-form>
    </div>
</template>

<style scoped>
.upload-section {
  background-color: #fff;
  padding: 20px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
</style>