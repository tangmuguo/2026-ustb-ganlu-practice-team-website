<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import UploadWidget from '@/components/UploadWidget.vue'
import { createMaterial, getMaterialCategories } from '@/apis/materialsAPI'
import { userinfoStore } from '@/stores/userStore'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  categories: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'uploaded'])
const userStore = userinfoStore()
const submitting = ref(false)
const localCategories = ref([])
const coverWidget = ref()
const fileWidget = ref()
const coverFile = ref()
const materialFile = ref()
const submissionCompleted = ref(false)
const currentYear = new Date().getFullYear()
const years = Array.from({ length: 10 }, (_, index) => currentYear - index)

const form = reactive({
  title: '',
  courseType: 1,
  courseId: null,
  customSubject: '',
  year: currentYear
})

const visible = computed({
  get: () => props.modelValue,
  set: (value) => {
    if (!value && submitting.value) return
    emit('update:modelValue', value)
  }
})
const availableCategories = computed(() => props.categories.length ? props.categories : localCategories.value)
const uploaderName = computed(() => userStore.currentUser?.teamname
  || userStore.currentUser?.realname
  || userStore.currentUser?.username
  || '')

watch(() => props.modelValue, async (opened) => {
  if (opened && !props.categories.length) {
    try {
      const response = await getMaterialCategories()
      localCategories.value = response.data.content || []
    } catch (error) {
      ElMessage.error('科目加载失败')
    }
  }
})

watch(() => form.courseType, () => {
  form.courseId = null
  form.customSubject = ''
})

const validate = () => {
  const title = form.title.trim()
  if (title.length < 2 || title.length > 100) return '标题长度应为 2～100 字'
  if (form.courseType === 1 && !form.courseId) return '请选择通识课程科目'
  if (form.courseType === 2) {
    const subject = form.customSubject.trim()
    if (subject.length < 2 || subject.length > 30) return '特色课程科目长度应为 2～30 字'
  }
  if (!coverFile.value?.token) return '请先上传 JPG/PNG 封面'
  if (!materialFile.value?.token) return '请先上传课件文件'
  return ''
}

const submit = async () => {
  const validationMessage = validate()
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  submitting.value = true
  submissionCompleted.value = false
  try {
    const response = await createMaterial({
      title: form.title.trim(),
      courseType: form.courseType,
      courseId: form.courseType === 1 ? form.courseId : null,
      customSubject: form.courseType === 2 ? form.customSubject.trim() : null,
      year: form.year,
      coverToken: coverFile.value.token,
      fileToken: materialFile.value.token
    })
    ElMessage.success(response.data.message || '课件上传成功')
    submissionCompleted.value = true
    coverWidget.value?.markConsumed()
    fileWidget.value?.markConsumed()
    submitting.value = false
    emit('uploaded', response.data.content)
    visible.value = false
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存课件失败')
  } finally {
    submitting.value = false
  }
}

const reset = () => {
  if (submitting.value) return
  const cancelRemote = !submissionCompleted.value
  Object.assign(form, { title: '', courseType: 1, courseId: null, customSubject: '', year: currentYear })
  coverFile.value = null
  materialFile.value = null
  coverWidget.value?.clearFile({ cancelRemote })
  fileWidget.value?.clearFile({ cancelRemote })
  submissionCompleted.value = false
}

const beforeClose = (done) => {
  if (submitting.value) {
    ElMessage.warning('正在保存课件，请等待操作完成')
    return
  }
  done()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="上传课件"
    width="min(720px, 94vw)"
    destroy-on-close
    :before-close="beforeClose"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    @closed="reset"
  >
    <el-form label-position="top">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="请输入课件标题" />
      </el-form-item>
      <el-form-item label="封面（JPG/PNG）" required>
        <UploadWidget
          ref="coverWidget"
          purpose="COVER"
          accept=".jpg,.jpeg,.png"
          :max-size-mb="10"
          tip-text="支持 JPG、JPEG、PNG，最大 10MB"
          upload-text="上传封面"
          @upload="coverFile = $event"
        />
      </el-form-item>
      <el-form-item label="上传者">
        <el-input :model-value="uploaderName" disabled />
      </el-form-item>
      <el-form-item label="课程类型" required>
        <el-radio-group v-model="form.courseType">
          <el-radio :value="1">通识课程</el-radio>
          <el-radio :value="2">特色课程</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.courseType === 1" label="具体科目" required>
        <el-select v-model="form.courseId" placeholder="请选择科目" style="width: 100%">
          <el-option
            v-for="category in availableCategories"
            :key="category.id"
            :label="category.courseName"
            :value="category.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-else label="具体科目" required>
        <el-input v-model="form.customSubject" maxlength="30" show-word-limit placeholder="请输入特色课程科目" />
      </el-form-item>
      <el-form-item label="年份" required>
        <el-select v-model="form.year" style="width: 100%">
          <el-option v-for="year in years" :key="year" :label="`${year} 年`" :value="year" />
        </el-select>
      </el-form-item>
      <el-form-item label="课件文件" required>
        <UploadWidget
          ref="fileWidget"
          purpose="MATERIAL"
          accept=".pdf,.ppt,.pptx,.jpg,.jpeg,.png,.webp"
          :max-size-mb="200"
          tip-text="支持 PDF、PPT/PPTX、JPG/PNG/WebP，最大 200MB"
          upload-text="上传课件"
          @upload="materialFile = $event"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存课件</el-button>
    </template>
  </el-dialog>
</template>
