<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyTeamContent, getTeamContentImage, deleteContent, adminListContent, adminListTeams, adminPublish, adminReject, adminArchive, downloadMediaOwner, downloadMediaAdmin } from '@/apis/fengcaiAPI'
import { userinfoStore } from '@/stores/userStore'
import ContentStatusTag from '@/components/fengcai/ContentStatusTag.vue'
import TeamAttachmentUpload from '@/components/fengcai/TeamAttachmentUpload.vue'
import UploadPhotos from '@/components/UploadPhotos.vue'
import UploadLogHonor from '@/components/UploadLogHonor.vue'

const userStore = userinfoStore()
const isAdmin = computed(() => userStore.currentUser?.level === 0)
// 受控图片预览（Item 2 exy v4：改 Blob，不再把登录 JWT 拼进 URL query）。
// axios 拦截器通过 Authorization header 请求 Blob；对象 URL 只在当前页面内存中存在，
// 刷新或卸载时 revoke，避免 token 进入浏览器历史/日志/Referer。
// 缓存键 = id:imageUrl：同一 (id, imageUrl) 复用已下载的对象 URL，只有新增/变化的行才重新拉取，
// 避免每次刷新都全量重下所有原图。
const imagePreviewUrls = ref({})
let previewGeneration = 0

function previewKey(row) {
  return `${row.id}:${row.imageUrl || ''}`
}

function imagePreviewUrl(row) {
  return imagePreviewUrls.value[previewKey(row)] || ''
}

function revokePreviewUrls(urls) {
  if (!urls) return
  Object.values(urls).forEach((url) => {
    if (url) URL.revokeObjectURL(url)
  })
}

// 并发拉取新增/变化图片的 Blob 并转对象 URL；已缓存的 (id, imageUrl) 直接复用。
// generation 标记防止旧请求覆盖新列表；只 revoke 被移除或作废的 URL。
async function refreshImagePreviews(rows) {
  const generation = ++previewGeneration
  const current = (rows || []).filter((row) => row && row.id != null)
  const cache = imagePreviewUrls.value
  const nextUrls = {}
  const newlyCreated = []
  const pending = []
  for (const row of current) {
    const key = previewKey(row)
    if (cache[key]) {
      nextUrls[key] = cache[key] // 复用：同一 (id, imageUrl) 不重复下载
    } else {
      pending.push(row)
    }
  }
  await Promise.all(pending.map(async (row) => {
    try {
      const response = await getTeamContentImage(row.id)
      if (generation !== previewGeneration) return // 已被新一轮覆盖，丢弃
      const url = URL.createObjectURL(response.data)
      nextUrls[previewKey(row)] = url
      newlyCreated.push(url)
    } catch {
      // 单张预览失败不影响列表，该位置显示“无预览”
    }
  }))
  if (generation !== previewGeneration) {
    // 期间又触发了新一次刷新，本次结果作废：只释放本轮新建的 URL（复用的留给新地图）
    revokePreviewUrls(newlyCreated)
    return
  }
  // 移除已不在列表中的条目并释放其 URL
  const wanted = new Set(current.map(previewKey))
  for (const key of Object.keys(cache)) {
    if (!wanted.has(key)) {
      URL.revokeObjectURL(cache[key])
    }
  }
  imagePreviewUrls.value = nextUrls
}

const activeTab = ref('images')
const loading = ref(false)
const images = ref([])
const words = ref([])
const media = ref([])

// 管理员筛选
const filterStatus = ref('')
const adminTeamId = ref(null)
const allTeams = ref([])

// 管理员加载团队列表
async function loadTeams() {
  try {
    const res = await adminListTeams()
    if (res.data.code === 200) {
      allTeams.value = res.data.content || []
    }
  } catch (error) {
    ElMessage.error('加载团队列表失败: ' + error.message)
  }
}

// 下载媒体（按身份分流：管理员/团队端用受保护接口，可下 PENDING/REJECTED；公开页用 downloadMedia）
async function handleDownload(m) {
  try {
    const fn = isAdmin.value ? downloadMediaAdmin : downloadMediaOwner
    const res = await fn(m.id)
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    const cd = res.headers['content-disposition']
    a.download = cd ? cd.split('filename=')[1]?.replace(/"/g, '') : m.filename || 'download'
    a.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error('下载失败: ' + (error.message || '未知错误'))
  }
}

// 加载团队端数据
async function loadMyContent() {
  try {
    loading.value = true
    const res = await getMyTeamContent()
    if (res.data.code === 200) {
      images.value = res.data.content.images || []
      words.value = res.data.content.words || []
      media.value = res.data.content.media || []
      refreshImagePreviews(images.value)
    } else {
      ElMessage.error(res.data.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 管理员加载数据
async function loadAdminContent() {
  if (!adminTeamId.value) {
    // 未选团队：清空上次查询残留的数据与预览 URL，避免表格显示过期内容
    images.value = []
    words.value = []
    media.value = []
    refreshImagePreviews([])
    ElMessage.warning('请先输入团队ID')
    return
  }
  try {
    loading.value = true
    const params = { teamId: adminTeamId.value }
    if (filterStatus.value) params.status = filterStatus.value
    const res = await adminListContent(params)
    if (res.data.code === 200) {
      images.value = res.data.content.images || []
      words.value = res.data.content.words || []
      media.value = res.data.content.media || []
      refreshImagePreviews(images.value)
    } else {
      ElMessage.error(res.data.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 删除
async function handleDelete(type, id) {
  try {
    await ElMessageBox.confirm('确定要归档这条记录吗?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteContent(type, id)
    if (res.data.code === 200) {
      ElMessage.success('删除成功')
      isAdmin.value ? loadAdminContent() : loadMyContent()
    } else {
      ElMessage.error(res.data.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败: ' + error.message)
  }
}

// 管理员发布
async function handlePublish(type, id) {
  try {
    const res = await adminPublish(type, id)
    if (res.data.code === 200) {
      ElMessage.success('发布成功')
      loadAdminContent()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败: ' + error.message)
  }
}

// 管理员驳回
async function handleReject(type, id) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (v) => !v || !v.trim() ? '驳回原因不能为空' : true
    })
    const res = await adminReject(type, id, reason)
    if (res.data.code === 200) {
      ElMessage.success('驳回成功')
      loadAdminContent()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('操作失败: ' + error.message)
  }
}

// 管理员归档
async function handleArchive(type, id) {
  try {
    await ElMessageBox.confirm('确定要归档这条记录吗?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await adminArchive(type, id)
    if (res.data.code === 200) {
      ElMessage.success('归档成功')
      loadAdminContent()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('操作失败: ' + error.message)
  }
}

function convertImageType(type) {
  return { 1: '队员照片', 2: '支教照片', 3: '地区照片' }[type] || '未知'
}

function convertWordType(type) {
  return { 3: '团队荣誉', 4: '团队日志' }[type] || '未知'
}

onMounted(() => {
  if (isAdmin.value) {
    loadTeams()
  } else {
    loadMyContent()
  }
})

// 组件卸载时释放所有对象 URL，避免内存泄漏
onBeforeUnmount(() => {
  previewGeneration++ // 让进行中的刷新作废
  revokePreviewUrls(imagePreviewUrls.value)
  imagePreviewUrls.value = {}
})
</script>

<template>
  <div class="team-content-manage">
    <div class="page-header">
      <h2>团队风采内容管理</h2>
    </div>

    <!-- 管理员团队选择器 -->
    <div v-if="isAdmin" class="admin-filter">
      <el-select v-model="adminTeamId" placeholder="选择团队" clearable style="width: 200px; margin-right: 10px;">
        <el-option
          v-for="team in allTeams"
          :key="team.id"
          :label="`${team.year} - ${team.name}`"
          :value="team.id"
        />
      </el-select>
      <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width: 150px; margin-right: 10px;">
        <el-option label="待审核" value="PENDING" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <el-button type="primary" @click="loadAdminContent">查询</el-button>
    </div>

    <el-tabs v-model="activeTab" class="content-tabs">
      <!-- 图片内容 -->
      <el-tab-pane label="图片内容" name="images">
        <!-- 团队端：上传表单 -->
        <div v-if="!isAdmin" class="upload-section-wrapper">
          <UploadPhotos @uploaded="loadMyContent" />
        </div>
        <el-table :data="images" v-loading="loading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="预览" width="120">
            <template #default="{ row }">
              <el-image
                :src="imagePreviewUrl(row)"
                :preview-src-list="[imagePreviewUrl(row)]"
                :preview-teleported="true"
                fit="cover"
                style="width: 80px; height: 60px; border-radius: 4px;"
                hide-on-click-modal
              >
                <template #error>
                  <div style="width:80px;height:60px;display:flex;align-items:center;justify-content:center;background:#f5f7fa;color:#c0c4cc;font-size:12px;border-radius:4px;">无预览</div>
                </template>
                <template #placeholder>
                  <div style="width:80px;height:60px;display:flex;align-items:center;justify-content:center;background:#f5f7fa;color:#c0c4cc;font-size:12px;border-radius:4px;">加载中</div>
                </template>
              </el-image>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ convertImageType(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="caption" label="标题" />
          <el-table-column prop="content" label="说明" show-overflow-tooltip />
          <el-table-column prop="logDate" label="拍摄日期" width="120">
            <template #default="{ row }">{{ row.logDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <ContentStatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="驳回原因" width="160">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.status === 'REJECTED' && row.rejectReason"
                :content="row.rejectReason"
                placement="top"
              >
                <el-button size="small" type="danger" plain>查看原因</el-button>
              </el-tooltip>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button v-if="isAdmin" size="small" type="success" @click="handlePublish('image', row.id)">发布</el-button>
              <el-button v-if="isAdmin" size="small" type="warning" @click="handleReject('image', row.id)">驳回</el-button>
              <el-button size="small" type="danger" @click="handleDelete('image', row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 日志荣誉 -->
      <el-tab-pane label="日志荣誉" name="words">
        <!-- 团队端：上传表单 -->
        <div v-if="!isAdmin" class="upload-section-wrapper">
          <UploadLogHonor @uploaded="loadMyContent" />
        </div>
        <el-table :data="words" v-loading="loading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ convertWordType(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="caption" label="标题" />
          <el-table-column prop="content" label="内容" show-overflow-tooltip />
          <el-table-column prop="logDate" label="日期" width="120">
            <template #default="{ row }">{{ row.logDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <ContentStatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="驳回原因" width="160">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.status === 'REJECTED' && row.rejectReason"
                :content="row.rejectReason"
                placement="top"
              >
                <el-button size="small" type="danger" plain>查看原因</el-button>
              </el-tooltip>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button v-if="isAdmin" size="small" type="success" @click="handlePublish('word', row.id)">发布</el-button>
              <el-button v-if="isAdmin" size="small" type="warning" @click="handleReject('word', row.id)">驳回</el-button>
              <el-button size="small" type="danger" @click="handleDelete('word', row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 视频附件 -->
      <el-tab-pane label="视频附件" name="media">
        <div class="media-upload-section" v-if="!isAdmin">
          <TeamAttachmentUpload @uploaded="loadMyContent" />
        </div>
        <el-table :data="media" v-loading="loading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="filename" label="文件名" />
          <el-table-column label="大小" width="100">
            <template #default="{ row }">
              {{ row.fileSize ? (row.fileSize / 1024 / 1024).toFixed(2) + ' MB' : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="关联" width="120">
            <template #default="{ row }">
              {{ row.relatedType ? `${row.relatedType}#${row.relatedId}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <ContentStatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="180" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="handleDownload(row)">下载</el-button>
              <el-button v-if="isAdmin" size="small" type="success" @click="handlePublish('media', row.id)">发布</el-button>
              <el-button size="small" type="danger" @click="handleDelete('media', row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.team-content-manage {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  background-color: #fff;
}
.page-header {
  margin-bottom: 20px;
}
.admin-filter {
  margin-bottom: 20px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}
.media-upload-section {
  margin-bottom: 20px;
  padding: 15px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
}
.upload-section-wrapper {
  margin-bottom: 20px;
}
</style>
