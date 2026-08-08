<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PublicImageUploadWidget from '@/components/PublicImageUploadWidget.vue'
import { 
  getBannerList, 
  addBanner, 
  updateBanner, 
  deleteBanner,
  updateBannerSort,
  updateBannerStatus,
  updateBannerLink
} from '@/apis/bannerAPI'

const bannerList = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加轮播图')
const currentBanner = ref({
  id: null,
  title: '',
  imageUrl: '',
  imageUploadToken: '',
  linkUrl: '',
  sortOrder: 1,
  isVisible: 1
})
const uploadWidget = ref(null)
const isEditing = ref(false)

// 加载轮播图列表
const loadBannerList = async () => {
  try {
    const { data } = await getBannerList()
    bannerList.value = data.content.sort((a, b) => b.sortOrder - a.sortOrder)
    // 检查是否超过5张
    if (bannerList.value.length >= 5) {
      ElMessage.warning('轮播图最多只能添加5张')
    }
  } catch (error) {
    ElMessage.error('获取轮播图列表失败')
  }
}

// 添加轮播图
const handleAddBanner = () => {
  if (bannerList.value.length >= 5) {
    ElMessage.warning('轮播图最多只能添加5张')
    return
  }
  currentBanner.value = {
    id: null,
    title: '',
    imageUrl: '',
    imageUploadToken: '',
    linkUrl: '',
    sortOrder: bannerList.value.length > 0 
      ? Math.max(...bannerList.value.map(b => b.sortOrder)) + 1 
      : 1,
    isVisible: 1
  }
  dialogTitle.value = '添加轮播图'
  isEditing.value = false
  dialogVisible.value = true
}

// 编辑轮播图
const handleEditBanner = (banner) => {
  currentBanner.value = { ...banner, imageUploadToken: '' }
  dialogTitle.value = '编辑轮播图'
  isEditing.value = true
  dialogVisible.value = true
}

// 删除轮播图
const handleDeleteBanner = async (banner) => {
  try {
    await ElMessageBox.confirm('确定要删除该轮播图吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteBanner(banner.id)
    ElMessage.success('删除成功')
    loadBannerList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 图片上传成功处理
const handleImageUpload = (stagedImage) => {
  currentBanner.value.imageUploadToken = stagedImage?.token || ''
}

// 提交表单
const handleSubmit = async () => {
  if (!currentBanner.value.imageUrl && !currentBanner.value.imageUploadToken) {
    ElMessage.warning('请上传图片')
    return
  }

  try {
    if (isEditing.value) {
      const response = await updateBanner(currentBanner.value)
      if (response.data.code !== 200) throw new Error(response.data.message || '更新失败')
      ElMessage.success('更新成功')
    } else {
      const response = await addBanner(currentBanner.value)
      if (response.data.code !== 200) throw new Error(response.data.message || '添加失败')
      ElMessage.success('添加成功')
    }
    uploadWidget.value?.markConsumed()
    dialogVisible.value = false
    loadBannerList()
  } catch (error) {
    ElMessage.error(isEditing.value ? '更新失败' : '添加失败')
  }
}

const handleDialogClosed = () => {
  uploadWidget.value?.clearFile()
}

// 排序改变
const handleSortChange = async (banner) => {
  try {
    await updateBannerSort(banner.id, banner.sortOrder)
    loadBannerList()
  } catch (error) {
    ElMessage.error('更新排序失败')
  }
}

// 状态改变
const handleStatusChange = async (banner) => {
  try {
    await updateBannerStatus(banner.id, banner.isVisible)
    ElMessage.success('状态更新成功')
  } catch (error) {
    ElMessage.error('状态更新失败')
  }
}

// 链接改变
const handleLinkChange = async (banner) => {
  try {
    await updateBannerLink(banner.id, banner.linkUrl)
    ElMessage.success('链接更新成功')
  } catch (error) {
    ElMessage.error('链接更新失败')
  }
}

const getFullImageUrl = (relativePath) => {
  if (!relativePath) return ''
  // 移除相对路径开头的斜杠（如果有）
  const cleanPath = relativePath.startsWith('/') 
    ? relativePath.substring(1) 
    : relativePath
  return `${import.meta.env.VITE_API_BASE_URL}${cleanPath}`
}

onMounted(() => {
  loadBannerList()
})
</script>

<template>
  <div class="banner-management">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>轮播图管理</span>
          <el-button type="primary" @click="handleAddBanner">添加轮播图</el-button>
        </div>
      </template>

      <el-table :data="bannerList" row-key="id" border>
        <el-table-column prop="sortOrder" label="排序" width="80">
          <template #default="{row}">
            <el-input-number 
              v-model="row.sortOrder" 
              :min="1" 
              :max="100" 
              controls-position="right" 
              size="small"
              @change="handleSortChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="180"/>
        <el-table-column prop="imageUrl" label="图片">
          <template #default="{row}">
            <el-image 
              style="width: 100px; height: 60px"
              :src="getFullImageUrl(row.imageUrl)" 
              :preview-src-list="[row.imageUrl]"
              fit="cover"
              hide-on-click-modal
            />
          </template>
        </el-table-column>
        <el-table-column prop="linkUrl" label="跳转链接">
          <template #default="{row}">
            <el-input 
              v-model="row.linkUrl" 
              placeholder="请输入跳转链接" 
              size="small"
              @blur="handleLinkChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="isVisible" label="状态" width="100">
          <template #default="{row}">
            <el-switch
              v-model="row.isVisible"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{row}">
            <el-button size="small" @click="handleEditBanner(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDeleteBanner(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑轮播图对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="dialogTitle" 
      width="50%"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form :model="currentBanner" label-width="100px">
        <el-form-item label="轮播图标题">
          <el-input v-model="currentBanner.title" placeholder="请输入标题"/>
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="currentBanner.linkUrl" placeholder="请输入跳转链接"/>
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="currentBanner.sortOrder" :min="1" :max="100"/>
        </el-form-item>
        <el-form-item label="上传图片">
          <PublicImageUploadWidget
            ref="uploadWidget"
            accept=".jpg,.jpeg,.png,.webp"
            tipText="请上传图片文件，大小不超过5MB"
            :max-size-mb="5"
            @upload="handleImageUpload"
          />
        </el-form-item>
        <el-form-item label="是否显示">
          <el-switch v-model="currentBanner.isVisible" :active-value="1" :inactive-value="0"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.banner-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.box-card {
  margin-bottom: 20px;
}

.el-image {
  border-radius: 4px;
  cursor: pointer;
}
</style>
