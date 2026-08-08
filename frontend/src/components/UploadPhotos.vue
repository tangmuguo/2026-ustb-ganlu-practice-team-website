<script setup>
import { ref } from 'vue'
import PublicImageUploadWidget from '@/components/PublicImageUploadWidget.vue'
import { ElMessage } from 'element-plus'
import {uploadWholeImage} from "@/apis/fengcaiAPI"
import {userinfoStore} from '@/stores/userStore'

const formRef = ref(null)
const submitting = ref(false)
const userinfo=userinfoStore()
const uploadWidgetRef=ref(null)
const emit = defineEmits(['uploaded'])

const form = ref({
  userId:'',
  type: '团队日志',
  caption: '',
  content: '',
  imageUrl:'',
  imageUploadToken: '',
})

const datatype= [
    {
        value: 1,
        label: '队员照片',
    },
    {
        value: 2,
        label: '执教照片',
    }
]

const datatype_value=ref(datatype[0].value)

const rules = {
  caption: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const handleImageUpload = (stagedImage) => {
  form.value.imageUploadToken = stagedImage?.token || ''
}

const submitForm = async () => {
  if (!form.value.imageUploadToken) {
    ElMessage.warning('请先完成文件上传')
    return
  }

  try {
    const valid = await formRef.value.validate()
    if (!valid) return
    
    submitting.value = true
    form.value.type=datatype_value.value
    form.value.userId=userinfo.user.content.id
    const response = await uploadWholeImage(form.value)
    if (response.data?.code !== 200) {
      throw new Error(response.data?.message || '提交失败')
    }
    
    // 重置表单
    uploadWidgetRef.value.markConsumed()
    formRef.value.resetFields()
    await uploadWidgetRef.value.clearFile({ cancelRemote: false })
    ElMessage.success('提交成功')
    emit('uploaded')
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

        <el-form-item label="图片上传">
            <PublicImageUploadWidget
            ref="uploadWidgetRef"
            accept=".jpg,.jpeg,.png,.webp"
            tip-text="支持 JPG、PNG、WebP，大小不超过2MB"
            :max-size-mb="2"
            @upload="handleImageUpload"
            />
        </el-form-item>
        <el-form-item label="照片标题" prop="caption">
            <el-input v-model="form.caption" placeholder="请输入标题"></el-input>
        </el-form-item>
        
        <el-form-item label="备注" prop="content">
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
