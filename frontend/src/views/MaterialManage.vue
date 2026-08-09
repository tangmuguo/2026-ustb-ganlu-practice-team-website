<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MaterialFilters from '@/components/material/MaterialFilters.vue'
import MaterialUploadDialog from '@/components/material/MaterialUploadDialog.vue'
import {
  addMaterialCategory,
  deleteMaterial,
  getManagedMaterialCategories,
  getMaterialCategories,
  resolveMaterialAssetUrl,
  searchMaterials,
  updateMaterialCategory
} from '@/apis/materialsAPI'
import { userinfoStore } from '@/stores/userStore'

const userStore = userinfoStore()
const loading = ref(false)
const uploadVisible = ref(false)
const categoryVisible = ref(false)
const materials = ref([])
const activeCategories = ref([])
const managedCategories = ref([])
const newCategoryName = ref('')
const categorySubmitting = ref(false)
const filters = ref({ keyword: '', courseType: null, courseId: null, year: null })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const isAdministrator = computed(() => userStore.currentUser?.level === 0)

const loadCategories = async () => {
  const activeResponse = await getMaterialCategories()
  activeCategories.value = activeResponse.data.content || []
  if (isAdministrator.value) {
    const managedResponse = await getManagedMaterialCategories()
    managedCategories.value = managedResponse.data.content || []
  }
}

const loadMaterials = async () => {
  loading.value = true
  try {
    const response = await searchMaterials({
      ...filters.value,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    materials.value = response.data.content?.list || []
    pagination.total = response.data.content?.total || 0
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '课件列表加载失败')
  } finally {
    loading.value = false
  }
}

const applyFilters = (next) => {
  filters.value = next
  pagination.page = 1
  loadMaterials()
}

const removeMaterial = async (row) => {
  try {
    await ElMessageBox.confirm(
      `删除“${row.title}”后将同时清理封面、预览和原文件，是否继续？`,
      '删除课件',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteMaterial(row.id)
    ElMessage.success('课件已删除')
    if (materials.value.length === 1 && pagination.page > 1) pagination.page -= 1
    await loadMaterials()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.response?.data?.message || '删除失败')
  }
}

const addCategory = async () => {
  if (categorySubmitting.value) return
  const name = newCategoryName.value.trim()
  if (!name) return ElMessage.warning('请输入科目名称')
  categorySubmitting.value = true
  try {
    await addMaterialCategory(name)
    newCategoryName.value = ''
    ElMessage.success('科目新增成功')
    await loadCategories()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '科目新增失败')
  } finally {
    categorySubmitting.value = false
  }
}

const renameCategory = async (category) => {
  if (categorySubmitting.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入新的科目名称', '修改科目', {
      inputValue: category.courseName,
      inputPattern: /^.{1,20}$/,
      inputErrorMessage: '科目名称长度应为 1～20 字'
    })
    categorySubmitting.value = true
    await updateMaterialCategory(category.id, { courseName: value.trim(), status: category.status })
    ElMessage.success('科目名称已更新')
    await loadCategories()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.response?.data?.message || '科目更新失败')
  } finally {
    categorySubmitting.value = false
  }
}

const changeCategoryStatus = async (category, nextStatus) => {
  if (categorySubmitting.value) return
  const previousStatus = nextStatus === 1 ? 0 : 1
  categorySubmitting.value = true
  try {
    await updateMaterialCategory(category.id, { courseName: category.courseName, status: nextStatus })
    ElMessage.success(nextStatus === 1 ? '科目已启用' : '科目已停用')
    await loadCategories()
  } catch (error) {
    category.status = previousStatus
    ElMessage.error(error.response?.data?.message || '科目状态更新失败')
  } finally {
    categorySubmitting.value = false
  }
}

const subjectName = (row) => row.courseType === 1 ? row.courseName : row.customSubject
const formatSize = (size) => size >= 1024 * 1024
  ? `${(size / 1024 / 1024).toFixed(1)} MB`
  : `${Math.ceil((size || 0) / 1024)} KB`

onMounted(async () => {
  if (![0, 1].includes(userStore.currentUser?.level)) return
  try {
    await Promise.all([loadCategories(), loadMaterials()])
  } catch (error) {
    ElMessage.error('课件管理数据加载失败')
  }
})
</script>

<template>
  <main class="manage-page">
    <header class="page-header">
      <div><h1>课件管理</h1><p>管理员和团队账号可管理全部课件；通识科目仅由管理员维护。</p></div>
      <div class="header-actions">
        <el-button v-if="isAdministrator" @click="categoryVisible = true">管理科目</el-button>
        <el-button type="primary" @click="uploadVisible = true">上传课件</el-button>
      </div>
    </header>

    <MaterialFilters v-model="filters" :categories="activeCategories" @search="applyFilters" @reset="applyFilters" />

    <section class="material-table-region" aria-label="课件列表">
      <el-table v-loading="loading" :data="materials" class="material-table" style="width: 100%">
        <el-table-column label="封面" width="90">
          <template #default="{ row }"><el-image :src="resolveMaterialAssetUrl(row.thumbnailUrl)" fit="cover" class="cover" /></template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型/科目" min-width="150">
          <template #default="{ row }">{{ row.courseType === 1 ? '通识' : '特色' }} · {{ subjectName(row) }}</template>
        </el-table-column>
        <el-table-column prop="year" label="年份" width="90" />
        <el-table-column prop="uploaderName" label="上传者" min-width="130" show-overflow-tooltip />
        <el-table-column label="文件" min-width="120">
          <template #default="{ row }">{{ (row.fileExtension || '').toUpperCase() }} · {{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="预览" width="100">
          <template #default="{ row }">
            <el-tag :type="row.previewStatus === 'READY' ? 'success' : 'warning'">
              {{ row.previewStatus === 'READY' ? '可预览' : '转换失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button type="danger" link @click="removeMaterial(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </section>

    <el-pagination
      v-if="pagination.total"
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.pageSize"
      class="pagination"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      :total="pagination.total"
      @current-change="loadMaterials"
      @size-change="() => { pagination.page = 1; loadMaterials() }"
    />

    <MaterialUploadDialog v-model="uploadVisible" :categories="activeCategories" @uploaded="loadMaterials" />

    <el-dialog v-if="isAdministrator" v-model="categoryVisible" title="通识科目管理" width="min(640px, 94vw)">
      <div class="category-add">
        <el-input v-model="newCategoryName" :disabled="categorySubmitting" maxlength="20" placeholder="新科目名称" @keyup.enter="addCategory" />
        <el-button type="primary" :loading="categorySubmitting" @click="addCategory">新增</el-button>
      </div>
      <el-alert title="启用科目最多 12 个；停用不会删除已有课件。" type="info" :closable="false" />
      <el-table :data="managedCategories" class="category-table">
        <el-table-column prop="courseName" label="科目" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.status" :disabled="categorySubmitting" :active-value="1" :inactive-value="0" @change="changeCategoryStatus(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button link type="primary" :disabled="categorySubmitting" @click="renameCategory(row)">改名</el-button></template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </main>
</template>

<style scoped>
.manage-page { width: 100%; max-width: 1280px; min-width: 0; margin: 0 auto; padding: 32px 20px 56px; }
.page-header { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 24px; }
.page-header h1 { margin: 0 0 8px; font-size: 32px; }
.page-header p { margin: 0; color: #606266; }
.header-actions, .category-add { display: flex; gap: 10px; }
.material-table-region { width: 100%; max-width: 100%; min-width: 0; margin-top: 24px; overflow-x: auto; }
.material-table { width: 100%; min-width: 980px; margin: 0; }
.cover { width: 58px; height: 44px; border-radius: 4px; }
.pagination { justify-content: flex-end; margin-top: 22px; }
.category-add { margin-bottom: 14px; }
.category-table { margin-top: 14px; }
@media (max-width: 640px) { .page-header { align-items: stretch; flex-direction: column; } }
</style>
