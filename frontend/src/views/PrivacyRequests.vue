<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createPrivacyRequest,
  getMyPrivacyRequests,
  getPrivacyRequests,
  processPrivacyRequest,
} from '@/apis/privacyRequestAPI'
import { userinfoStore } from '@/stores/userStore'

const userStore = userinfoStore()
const isAdmin = computed(() => Number(userStore.currentUser?.level) === 0)
const activeTab = ref('mine')
const loading = ref(false)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const statusFilter = ref('')
const form = ref({
  requestType: 'CORRECTION',
  consentType: 'GUARDIAN',
  scope: 'PROFILE',
  description: '',
})

const typeLabels = {
  CORRECTION: '资料更正',
  DELETION: '资料删除评估',
  WITHDRAW_CONSENT: '撤回同意',
}

const statusLabels = {
  OPEN: '待受理',
  PROCESSING: '处理中',
  APPROVED: '已批准',
  REJECTED: '已驳回',
}

function responseContent(response) {
  if (response?.data?.code !== 200) throw new Error(response?.data?.message || '请求失败')
  return response.data.content || {}
}

async function loadMine() {
  loading.value = true
  try {
    const content = responseContent(await getMyPrivacyRequests(page.value, pageSize.value))
    items.value = Array.isArray(content.items) ? content.items : []
    total.value = Number(content.total) || 0
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '工单加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAdmin() {
  loading.value = true
  try {
    const content = responseContent(await getPrivacyRequests(statusFilter.value, page.value, 50))
    items.value = Array.isArray(content.items) ? content.items : []
    total.value = Number(content.total) || 0
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '管理员工单加载失败')
  } finally {
    loading.value = false
  }
}

async function load() {
  if (isAdmin.value && activeTab.value === 'admin') return loadAdmin()
  return loadMine()
}

function resetPageAndLoad() {
  page.value = 1
  load()
}

async function submitRequest() {
  const description = form.value.description.trim()
  if (!description) {
    ElMessage.warning('请填写申请说明')
    return
  }
  try {
    await createPrivacyRequest({
      requestType: form.value.requestType,
      consentType: form.value.requestType === 'WITHDRAW_CONSENT' ? form.value.consentType : undefined,
      scope: form.value.scope || undefined,
      description,
    })
    ElMessage.success('工单已提交，请保存返回的工单编号')
    form.value.description = ''
    activeTab.value = 'mine'
    resetPageAndLoad()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '工单提交失败')
  }
}

async function processItem(item, status) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写处置理由。删除申请只记录工单和保全判断，不会在此处直接删除账号、内容或文件。',
      `处理隐私工单 #${item.id}`,
      {
        inputPattern: /\S+/,
        inputErrorMessage: '处置理由不能为空',
        confirmButtonText: '保存处理结果',
        cancelButtonText: '取消',
      },
    )
    await processPrivacyRequest(item.id, {
      status,
      decisionCode: status === 'APPROVED' ? 'MANUAL_REVIEW_APPROVED' : status === 'REJECTED' ? 'MANUAL_REVIEW_REJECTED' : 'REVIEW_STARTED',
      decisionReason: value,
      retentionDecision: item.requestType === 'DELETION' ? 'PRESERVE_UNTIL_REVIEW' : undefined,
    })
    ElMessage.success('工单处理结果已保存')
    await loadAdmin()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || error.message || '工单处理失败')
    }
  }
}

function typeLabel(value) { return typeLabels[value] || value || '—' }
function statusLabel(value) { return statusLabels[value] || value || '—' }

onMounted(async () => {
  if (!userStore.isLoggedIn) return
  await load()
})
</script>

<template>
  <main class="privacy-requests-page">
    <header class="page-header">
      <div>
        <p>PRIVACY RIGHTS</p>
        <h1>隐私权利工单</h1>
        <span>可提交资料更正、删除评估或撤回同意申请。撤回提交后立即停止后续公开/发布权限并使旧会话失效；请勿填写密码、验证码、Token、身份证号原件或完整证件材料。</span>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>

    <el-tabs v-if="isAdmin" v-model="activeTab" @tab-change="resetPageAndLoad">
      <el-tab-pane label="我的申请" name="mine" />
      <el-tab-pane label="管理员工单" name="admin" />
    </el-tabs>

    <section v-if="activeTab === 'mine' || !isAdmin" class="panel">
      <div class="panel-heading">
        <div>
          <h2>提交新的权利申请</h2>
          <p>服务器根据登录身份绑定工单，不接受客户端提交的用户编号。</p>
        </div>
      </div>
      <el-form label-position="top" @submit.prevent="submitRequest">
        <div class="form-grid">
          <el-form-item label="申请类型">
            <el-select v-model="form.requestType">
              <el-option label="资料更正" value="CORRECTION" />
              <el-option label="资料删除评估" value="DELETION" />
              <el-option label="撤回同意" value="WITHDRAW_CONSENT" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.requestType === 'WITHDRAW_CONSENT'" label="撤回类型">
            <el-select v-model="form.consentType">
              <el-option label="监护人授权" value="GUARDIAN" />
              <el-option label="隐私政策同意" value="PRIVACY" />
            </el-select>
          </el-form-item>
          <el-form-item v-else label="涉及范围">
            <el-input v-model="form.scope" maxlength="64" placeholder="例如 PROFILE 或 ACCOUNT" />
          </el-form-item>
        </div>
        <el-form-item label="申请说明">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="请说明需要更正、评估或撤回的范围和原因。" />
        </el-form-item>
        <el-button type="primary" native-type="submit">提交工单</el-button>
      </el-form>
    </section>

    <section v-if="activeTab === 'admin' && isAdmin" class="panel">
      <div class="panel-heading">
        <div>
          <h2>管理员处理队列</h2>
          <p>仅显示必要工单字段；申请人姓名、电话、学校和授权凭据不在此接口回显。</p>
        </div>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable @change="resetPageAndLoad">
          <el-option label="待受理" value="OPEN" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已批准" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </div>
      <el-table :data="items" v-loading="loading" border>
        <el-table-column prop="id" label="工单" width="88" />
        <el-table-column prop="requesterUserId" label="申请人编号" width="110" />
        <el-table-column label="类型" width="140">
          <template #default="{ row }">{{ typeLabel(row.requestType) }}</template>
        </el-table-column>
        <el-table-column prop="consentType" label="授权类型" width="120" />
        <el-table-column prop="scope" label="范围" width="130" />
        <el-table-column prop="description" label="申请说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" min-width="175" />
        <el-table-column label="处理" min-width="245" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OPEN'" text type="primary" @click="processItem(row, 'PROCESSING')">开始处理</el-button>
            <el-button v-if="row.status === 'OPEN' || row.status === 'PROCESSING'" text type="success" @click="processItem(row, 'APPROVED')">批准</el-button>
            <el-button v-if="row.status === 'OPEN' || row.status === 'PROCESSING'" text type="warning" @click="processItem(row, 'REJECTED')">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="activeTab === 'mine' || !isAdmin" class="panel">
      <div class="panel-heading">
        <div>
          <h2>我的工单</h2>
          <p>共 {{ total }} 条。处理结果会保留必要审计记录。</p>
        </div>
      </div>
      <el-table :data="items" v-loading="loading" border>
        <el-table-column prop="id" label="工单" width="88" />
        <el-table-column label="类型" width="150">
          <template #default="{ row }">{{ typeLabel(row.requestType) }}</template>
        </el-table-column>
        <el-table-column prop="scope" label="范围" width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="decisionReason" label="处理说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="提交时间" min-width="175" />
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadMine" /></div>
    </section>
  </main>
</template>

<style scoped>
.privacy-requests-page { max-width: 1260px; margin: 0 auto; padding: 48px 24px; }
.page-header { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 20px; }
.page-header p { margin: 0 0 7px; color: #2675dd; font-size: 12px; font-weight: 800; letter-spacing: .13em; }
.page-header h1 { margin: 0; color: #173d72; font-size: 30px; }
.page-header span { display: block; max-width: 850px; margin-top: 9px; color: #687d96; line-height: 1.7; }
.panel { margin-top: 22px; padding: 22px; border: 1px solid #dce8f7; border-radius: 18px; background: #fff; box-shadow: 0 10px 28px rgb(22 64 111 / 5%); }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.panel h2 { margin: 0; color: #1b426f; font-size: 20px; }
.panel p { margin: 6px 0 0; color: #7a91aa; font-size: 13px; }
.form-grid { display: grid; grid-template-columns: minmax(180px, 260px) minmax(180px, 320px); gap: 18px; }
.el-select { width: 100%; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
@media (max-width: 720px) { .page-header, .panel-heading { align-items: start; flex-direction: column; }.panel { padding: 15px; }.form-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
