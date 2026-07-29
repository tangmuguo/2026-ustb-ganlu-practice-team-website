<script setup>
import UploadForm from '@/components/UploadPhotos.vue'
import DataList from '@/components/DataPhotos.vue'
import {access} from '@/utils/access'
import { onMounted } from 'vue'

onMounted(()=>{
  access([0, 1])
})
</script>

<template>
    <div class="log-honor-container">
        <div class="mb-8 text-center">
            <h class="text-[clamp(1.75rem,3vw,2.5rem)] font-bold text-gray-800 mb-2">照片上传</h>
        </div>    
        <el-row :gutter="20">
        <el-col :span="12">
            <UploadForm />
        </el-col>
        <el-col :span="12">
            <DataList ></DataList>
        </el-col>
        </el-row>
  </div>
</template>

<style scoped>
.log-honor-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  text-align: center;
  margin-bottom: 30px;
}
.el-row {
  margin-bottom: 20px;
}

.el-col {
  border-radius: 4px;
}
</style>
