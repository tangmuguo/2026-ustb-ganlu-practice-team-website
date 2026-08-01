<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import MaterialUploadDialog from '@/components/material/MaterialUploadDialog.vue'
import { userinfoStore } from '@/stores/userStore'

const router = useRouter()
const userStore = userinfoStore()
const visible = ref(false)
const completed = ref(false)

onMounted(() => {
  if ([0, 1].includes(userStore.currentUser?.level)) visible.value = true
})

watch(visible, (opened, previous) => {
  if (previous && !opened && !completed.value) router.replace('/showm')
})

const uploaded = (material) => {
  completed.value = true
  router.replace(`/mdetail/${material.id}`)
}
</script>

<template>
  <main class="upload-page">
    <el-empty description="请在上传弹窗中填写课件信息">
      <el-button type="primary" @click="visible = true">打开上传弹窗</el-button>
    </el-empty>
    <MaterialUploadDialog v-model="visible" @uploaded="uploaded" />
  </main>
</template>

<style scoped>
.upload-page { min-height: 65vh; display: grid; place-items: center; padding: 32px 20px; }
</style>
