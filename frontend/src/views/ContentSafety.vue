<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getContentReports,
  getPendingContent,
  resolveContentReport,
  reviewContent,
} from '@/apis/contentSafetyAPI'
import { access } from '@/utils/access'

const contentType = ref('MESSAGE')
const pendingItems = ref([])
const reports = ref([])
const pendingTotal = ref(0)
const reportTotal = ref(0)
const loadingPending = ref(false)
const loadingReports = ref(false)

const contentTypeLabel = computed(() => contentType.value === 'MESSAGE' ? '留言' : '回复')

function responseContent(response) {
  if (response?.data?.code !== 200) throw new Error(response?.data?.message || '请求失败')
  return response.data.content || {}
}

async function loadPending() {
  loadingPending.value = true
  try {
    const content = responseContent(await getPendingContent(contentType.value))
    pendingItems.value = Array.isArray(content.items) ? content.items : []
    pendingTotal.value = Number(content.total) || 0
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '审核队列加载失败')
  } finally {
    loadingPending.value = false
  }
}

async function loadReports() {
  loadingReports.value = true
  try {
    const content = responseContent(await getContentReports('OPEN'))
    reports.value = Array.isArray(content.items) ? content.items : []
    reportTotal.value = Number(content.total) || 0
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '举报工单加载失败')
  } finally {
    loadingReports.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadPending(), loadReports()])
}

async function decide(item, decision) {
  try {
    const { value: reasonCode } = await ElMessageBox.prompt(
      `请填写${decision === 'APPROVED' ? '通过' : decision === 'REJECTED' ? '驳回' : '移除'}原因代码。原文不会被覆盖，处置历史将保留。`,
      `处置${item.contentType === 'MESSAGE' ? '留言' : '回复'}`,
      {
        inputValue: decision === 'APPROVED' ? 'MANUAL_REVIEW' : '',
        inputPattern: /^[A-Z][A-Z0-9_]{1,63}$/,
        inputErrorMessage: '请输入大写英文、数字或下划线组成的原因代码',
        confirmButtonText: '保存处置',
        cancelButtonText: '取消',
      },
    )
    await reviewContent({
      contentType: item.contentType,
      contentId: item.contentId,
      decision,
      reasonCode,
      note: '',
    })
    ElMessage.success('审核处置已保存')
    await refreshAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || error.message || '保存审核处置失败')
    }
  }
}

async function handleReport(report, status) {
  try {
    const { value: resolutionCode } = await ElMessageBox.prompt(
      '请填写处置代码，例如 REVIEW_STARTED、CONTENT_REMOVED 或 NO_VIOLATION。',
      '处理举报工单',
      {
        inputPattern: /^[A-Z][A-Z0-9_]{1,63}$/,
        inputErrorMessage: '请输入大写英文、数字或下划线组成的处置代码',
        confirmButtonText: '保存处理结果',
        cancelButtonText: '取消',
      },
    )
    await resolveContentReport(report.id, { status, resolutionCode, resolutionNote: '' })
    ElMessage.success('工单处理结果已保存')
    await loadReports()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || error.message || '工单处理失败')
    }
  }
}

onMounted(async () => {
  if (!access([0])) return
  await refreshAll()
})
</script>

<template>
  <main class="content-safety-page">
    <header class="page-header">
      <div>
        <p>CONTENT SAFETY</p>
        <h1>内容审核与举报工单</h1>
        <span>仅系统管理员可访问。审核、移除与举报处置均写入受控审计记录；请不要在备注中填写身份证号、电话或完整敏感材料。</span>
      </div>
      <el-button @click="refreshAll">刷新队列</el-button>
    </header>

    <section class="safety-notice">
      新提交的留言和回复默认不会公开。审核通过后才会显示在互动页；移除内容会保留原文和处置理由，供安全负责人依法查询。
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h2>待审核{{ contentTypeLabel }}</h2>
          <p>当前待处理 {{ pendingTotal }} 条</p>
        </div>
        <el-radio-group v-model="contentType" @change="loadPending">
          <el-radio-button label="MESSAGE">留言</el-radio-button>
          <el-radio-button label="REPLY">回复</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="pendingItems" v-loading="loadingPending" border>
        <el-table-column prop="contentId" label="ID" width="88" />
        <el-table-column prop="displayName" label="公开名称" min-width="130" />
        <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="createTime" label="提交时间" min-width="175" />
        <el-table-column label="审核" min-width="250" fixed="right">
          <template #default="{ row }">
            <el-button text type="success" @click="decide(row, 'APPROVED')">通过</el-button>
            <el-button text type="warning" @click="decide(row, 'REJECTED')">驳回</el-button>
            <el-button text type="danger" @click="decide(row, 'REMOVED')">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h2>待处理举报</h2>
          <p>当前开放工单 {{ reportTotal }} 条</p>
        </div>
      </div>
      <el-table :data="reports" v-loading="loadingReports" border>
        <el-table-column prop="id" label="工单" width="88" />
        <el-table-column prop="targetType" label="目标类型" width="110" />
        <el-table-column prop="targetId" label="目标 ID" width="100" />
        <el-table-column prop="category" label="分类" width="125" />
        <el-table-column prop="description" label="举报说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="提交时间" min-width="175" />
        <el-table-column label="处理" min-width="230" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="handleReport(row, 'PROCESSING')">处理中</el-button>
            <el-button text type="success" @click="handleReport(row, 'RESOLVED')">已解决</el-button>
            <el-button text type="warning" @click="handleReport(row, 'REJECTED')">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </main>
</template>

<style scoped>
.content-safety-page { max-width: 1260px; margin: 0 auto; padding: 48px 24px; }
.page-header { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 20px; }
.page-header p { margin: 0 0 7px; color: #2675dd; font-size: 12px; font-weight: 800; letter-spacing: .13em; }
.page-header h1 { margin: 0; color: #173d72; font-size: 30px; }.page-header span { display: block; max-width: 850px; margin-top: 9px; color: #687d96; line-height: 1.7; }
.safety-notice { margin-bottom: 20px; padding: 14px 16px; color: #735514; background: #fff8df; border: 1px solid #f4df96; border-radius: 12px; line-height: 1.7; }
.panel { margin-top: 22px; padding: 22px; border: 1px solid #dce8f7; border-radius: 18px; background: #fff; box-shadow: 0 10px 28px rgb(22 64 111 / 5%); }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 16px; }.panel h2 { margin: 0; color: #1b426f; font-size: 20px; }.panel p { margin: 6px 0 0; color: #7a91aa; font-size: 13px; }
@media (max-width: 720px) { .page-header, .panel-heading { align-items: start; flex-direction: column; }.panel { padding: 15px; } }
</style>
