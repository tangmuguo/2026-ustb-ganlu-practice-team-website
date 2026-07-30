<script setup>
import { computed, ref } from 'vue'
import { ArrowDown, ArrowUp, Download } from '@element-plus/icons-vue'
import { formatDate, resolveMediaUrl, uniqueItems } from '@/utils/fengcai'

const props = defineProps({
  log: {
    type: Object,
    required: true,
  },
  attachments: {
    type: Array,
    default: () => [],
  },
})

const expanded = ref(false)
const logId = computed(() => props.log.id ?? props.log.contentId)
const title = computed(() => props.log.title || props.log.caption || '课堂日志')
const body = computed(() => props.log.body || props.log.content || props.log.description || '')
const summary = computed(() => {
  if (props.log.summary) return props.log.summary
  return body.value.length > 105 ? `${body.value.slice(0, 105)}…` : body.value
})
const canExpand = computed(() => body.value && body.value !== summary.value)
const date = computed(() => formatDate(
  props.log.logDate || props.log.date || props.log.classDate || props.log.createdAt,
))
const relatedAttachments = computed(() => uniqueItems([
  ...(props.log.attachments || props.log.media || props.log.files || []),
  ...props.attachments.filter((attachment) => {
    const relatedId = attachment.relatedId ?? attachment.contentId ?? attachment.logId
    const relatedType = String(attachment.relatedType || attachment.contentType || '').toUpperCase()
    return String(relatedId) === String(logId.value)
      && (!relatedType || relatedType.includes('LOG'))
  }),
]))

function attachmentName(attachment) {
  return attachment.fileName || attachment.originalName || attachment.name || attachment.title || '下载附件'
}

function attachmentUrl(attachment) {
  return resolveMediaUrl(attachment.downloadUrl || attachment.fileUrl || attachment.url || attachment.path)
}
</script>

<template>
  <article class="log-card">
    <div class="log-card__date">
      <span>{{ date || '日期待补充' }}</span>
    </div>
    <div class="log-card__content">
      <h3>{{ title }}</h3>
      <p v-if="body" class="log-card__body">{{ expanded ? body : summary }}</p>
      <p v-else class="log-card__body log-card__body--muted">日志正文待补充</p>

      <button v-if="canExpand" class="log-card__toggle" type="button" @click="expanded = !expanded">
        {{ expanded ? '收起正文' : '展开正文' }}
        <el-icon><ArrowUp v-if="expanded" /><ArrowDown v-else /></el-icon>
      </button>

      <div v-if="relatedAttachments.length" class="log-card__files">
        <a
          v-for="(attachment, index) in relatedAttachments"
          :key="attachment.id ?? `log-file-${index}`"
          :href="attachmentUrl(attachment)"
          target="_blank"
          rel="noopener"
          download
        >
          <el-icon><Download /></el-icon>
          {{ attachmentName(attachment) }}
        </a>
      </div>
    </div>
  </article>
</template>

<style scoped>
.log-card {
  display: grid;
  grid-template-columns: 128px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid #e2edf5;
  border-radius: 18px;
  background: #fff;
}

.log-card__date {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 23px 16px;
  color: #146da9;
  background: #eef8fe;
}

.log-card__date span {
  font-size: 14px;
  font-weight: 650;
}

.log-card__content {
  padding: 22px 24px;
}

h3 {
  margin: 0;
  color: #173f61;
  font-size: 20px;
  font-weight: 700;
}

.log-card__body {
  margin-top: 10px;
  color: #62798b;
  line-height: 1.8;
  white-space: pre-wrap;
}

.log-card__body--muted {
  color: #9aabb8;
}

.log-card__toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 10px;
  padding: 0;
  border: 0;
  color: #176fae;
  font: inherit;
  cursor: pointer;
  background: transparent;
}

.log-card__files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.log-card__files a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 7px 10px;
  border-radius: 9px;
  color: #176fae;
  font-size: 13px;
  text-decoration: none;
  overflow-wrap: anywhere;
  background: #eaf5fd;
}

@media (max-width: 640px) {
  .log-card {
    grid-template-columns: 1fr;
  }

  .log-card__date {
    justify-content: flex-start;
    padding: 13px 18px;
  }

  .log-card__content {
    padding: 18px;
  }
}
</style>
