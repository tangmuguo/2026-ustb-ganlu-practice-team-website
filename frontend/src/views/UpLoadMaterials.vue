<script setup>
import { onBeforeMount, ref,reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {userinfoStore} from '@/stores/userStore'
import UploadWidget from '@/components/UploadWidget.vue'
import { useRouter } from 'vue-router'
import { uploadWholeMaterial } from "@/apis/materialsAPI"
import {access} from '@/utils/access'
const userinfo = userinfoStore()
const userRouter =useRouter()
const courses = [
  {
    value: 1,
    label: '民族特色文化课程',
  },
  {
    value: 2,
    label: '美育系列课程',
  },
  {
    value: 3,
    label: '科技系列课程',
  },
  {
    value: 4,
    label: '红色系列课程',
  },
  {
    value: 5,
    label: '基础课程',
  },
  {
    value: 6,
    label: '联动创新课程',
  },
  {
    value: 7,
    label: '素质拓展课程',
  }
]
const course_value = ref(courses[0].value)
// 新增：上传状态
const uploadStatus = reactive({
  imageProgress: 0,
  fileProgress: 0
})

const SelectChange = (value)=>{  
  course_value.value=value
  console.trace(course_value.value)
}

const form = ref({
  title: '',
  courseType:'1',
  imageUrl: '',
  imageFile:'',
  fileUrl: '',
  courseFile:'',
  courseId:''
})


onBeforeMount(()=>{
  access([0, 1])
})

const handleImageUpload = async (file) => {
  try {
    form.value.thumbnailUrl = file
    ElMessage.success('图片上传成功')
  } catch (error) {
    ElMessage.error('图片上传失败')
  }
}

const handleFileUpload = async (file) => {
  try {    
    form.value.files = file
    //form.value.files = fileUrl.data.content
    ElMessage.success('文件上传成功')
  } catch (error) {
    ElMessage.error('文件上传失败')
  }
}

const submitForm = async () => {
  if (!form.value.thumbnailUrl || !form.value.files) {
    ElMessage.warning('请先完成文件上传')
    return
  }
  form.value.courseId=course_value.value
  form.value.author = userinfo.user.content.id
  const d=await uploadWholeMaterial(form.value);

  if(d.data.code==200){
    ElMessage.success('提交成功')
  }else{
    ElMessage.error('提交失败')
  }
  
}

// 新增：进度回调
const onFileProgress = (progress) => {
  uploadStatus.fileProgress = progress
}
</script>

<template>
  <main class="flex-grow container mx-auto px-4 py-8">
    <div class="max-w-3xl mx-auto">
        <!-- 页面标题 -->
        <div class="mb-8 text-center">
            <h1 class="text-[clamp(1.75rem,3vw,2.5rem)] font-bold text-gray-800 mb-2">学科资料上传</h1>
        </div>
        <div class="bg-white rounded-xl shadow-lg p-6 md:p-8 mb-8">
          <el-form  :model="form" label-width="auto" label-position="top" style="width: 100%">
              
              <el-form-item label="课程类型">
                <el-radio-group v-model="form.courseType">
                  <el-radio value="1" size="large">通识课程</el-radio>
                  <el-radio value="2" size="large">特色课程</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="标题" hidden>
                <el-input v-model="form.title" placeholder="请输入标题"/>
              </el-form-item>
              <el-form-item label="封面图片">
                  <UploadWidget
                    accept="image/*"
                    tip-text="支持 JPG/PNG 格式,大小不超过2MB"
                    preview-icon="el-icon-picture"
                    upload-text="上传图片"
                    :max-size-mb="2"
                    @upload="handleImageUpload"
                  />
              </el-form-item>
              <el-form-item label="封面">
                <el-input v-model="form.thumbnailUrl"/>
              </el-form-item>            
              <el-form-item label="上传文件">
                  <UploadWidget
                    accept=".ppt,.pptx,.pdf,.doc,.docx,.mp4"
                    tip-text="支持 PPT/PDF/DOC/MP4 格式,大小不超过200MB"
                    upload-text="上传文件"
                    :max-size-mb="200"
                    @upload="handleFileUpload"
                    @progress="onFileProgress"
                  />
              </el-form-item>
              <el-form-item label="ppt">
                <el-input v-model="form.files"/>
              </el-form-item> 
              <el-form-item label="具体课程">
                <el-select
                  v-model="course_value"
                  placeholder="Select"
                  size="large"
                  style="width: 100%"
                  @change="SelectChange"
                >
                  <el-option
                    v-for="item in courses"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>

              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="large" @click="submitForm" style="width: 100%;">提交上传</el-button>
              </el-form-item>                          
            </el-form> 
        </div>  
        
    </div>
</main>
</template>

<style scoped>
.upload-container {
  width: 100%;
  margin: 0 auto;
  padding: 20px;
}

.avatar-uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: border-color 0.3s;
}

.avatar-uploader:hover {
  border-color: #409EFF;
}

.image-preview {
  position: relative;
  width: 100%;
  height: 200px;
}

.avatar {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.hover-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  color: white;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.hover-mask i {
  font-size: 24px;
  margin-bottom: 8px;
}

.image-preview:hover .hover-mask {
  opacity: 1;
}

.uploader-default {
  padding: 40px 0;
  text-align: center;
}

.el-icon-upload {
  font-size: 48px;
  color: #8c939d;
  margin-bottom: 16px;
}

.el-upload__text {
  color: #606266;
  font-size: 14px;
  margin-bottom: 8px;
}

.el-upload__text em {
  color: #409EFF;
  font-style: normal;
}

.el-upload__tip {
  color: #909399;
  font-size: 12px;
}

.upload-actions {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 15px;
}

.upload-result {
  margin-left: 10px;
  font-size: 14px;
}

.upload-result.success {
  color: #67C23A;
}

.upload-result.error {
  color: #F56C6C;
}
</style>
