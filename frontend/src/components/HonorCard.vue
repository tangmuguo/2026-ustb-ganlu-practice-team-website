<script setup>
import { computed } from 'vue'
import { Download, Medal } from '@element-plus/icons-vue'
import { formatDate, resolveAttachmentUrl, uniqueItems } from '@/utils/fengcai'

const props = defineProps({
  honor: {
    type: Object,
    required: true,
  },
  attachments: {
    type: Array,
    default: () => [],
  },
})

const honorId = computed(() => props.honor.id ?? props.honor.contentId)
const title = computed(() => props.honor.title || props.honor.caption || '团队荣誉')
const description = computed(() => props.honor.description || props.honor.summary || props.honor.content || '')
const date = computed(() => formatDate(
  props.honor.honorDate || props.honor.date || props.honor.occurredAt || props.honor.createdAt,
))
const relatedAttachments = computed(() => uniqueItems([
  ...(props.honor.attachments || props.honor.media || props.honor.files || []),
  ...props.attachments.filter((attachment) => {
    const relatedId = attachment.relatedId ?? attachment.contentId ?? attachment.honorId
    const relatedType = String(attachment.relatedType || attachment.contentType || '').toUpperCase()
    return String(relatedId) === String(honorId.value)
      && (!relatedType || relatedType.includes('HONOR'))
  }),
]))

function attachmentName(attachment) {
  return attachment.fileName || attachment.originalName || attachment.name || attachment.title || '下载附件'
}

function attachmentUrl(attachment) {
  return resolveAttachmentUrl(attachment)
}
</script>

<template>
  <article class="honor-card">
    <div class="honor-card__icon"><el-icon><Medal /></el-icon></div>
    <div class="honor-card__content">
      <time v-if="date">{{ date }}</time>
      <h3>{{ title }}</h3>
      <p v-if="description">{{ description }}</p>
      <div v-if="relatedAttachments.length" class="honor-card__files">
        <a
          v-for="(attachment, index) in relatedAttachments"
          :key="attachment.id ?? `honor-file-${index}`"
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
.honor-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  border: 1px solid #e4edf5;
  border-radius: 18px;
  background: linear-gradient(145deg, #fff, #f8fbfe);
}

.honor-card__icon {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  border-radius: 14px;
  color: #cf8a13;
  font-size: 24px;
  background: #fff3d9;
}

.honor-card__content {
  min-width: 0;
}

time {
  color: #748da1;
  font-size: 13px;
}

h3 {
  margin: 3px 0 0;
  color: #173f61;
  font-size: 19px;
  font-weight: 700;
}

p {
  margin-top: 9px;
  color: #667b8d;
  line-height: 1.75;
  white-space: pre-wrap;
}

.honor-card__files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.honor-card__files a {
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
</style>
