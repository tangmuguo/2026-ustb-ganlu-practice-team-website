<script setup>
import { computed, reactive, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Object, required: true },
  categories: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'search', 'reset'])

const local = reactive({ keyword: '', courseType: null, courseId: null, year: null })
const currentYear = new Date().getFullYear()
const years = computed(() => Array.from({ length: 10 }, (_, index) => currentYear - index))

watch(() => props.modelValue, (value) => {
  Object.assign(local, { keyword: '', courseType: null, courseId: null, year: null }, value || {})
}, { immediate: true, deep: true })

watch(() => local.courseType, (value) => {
  if (value === 2) local.courseId = null
})

const search = () => {
  const next = { ...local, keyword: local.keyword.trim() }
  emit('update:modelValue', next)
  emit('search', next)
}

const reset = () => {
  Object.assign(local, { keyword: '', courseType: null, courseId: null, year: null })
  const next = { ...local }
  emit('update:modelValue', next)
  emit('reset', next)
}
</script>

<template>
  <el-form class="filters" :inline="true" @submit.prevent="search">
    <el-form-item label="关键词">
      <el-input v-model="local.keyword" clearable placeholder="标题、科目或上传团队" @keyup.enter="search" />
    </el-form-item>
    <el-form-item label="课程类型">
      <el-select v-model="local.courseType" clearable placeholder="全部" style="width: 130px">
        <el-option label="通识课程" :value="1" />
        <el-option label="特色课程" :value="2" />
      </el-select>
    </el-form-item>
    <el-form-item label="科目">
      <el-select
        v-model="local.courseId"
        clearable
        :disabled="local.courseType === 2"
        placeholder="全部"
        style="width: 130px"
      >
        <el-option v-for="category in categories" :key="category.id" :label="category.courseName" :value="category.id" />
      </el-select>
    </el-form-item>
    <el-form-item label="年份">
      <el-select v-model="local.year" clearable placeholder="最近十年" style="width: 130px">
        <el-option v-for="year in years" :key="year" :label="`${year} 年`" :value="year" />
      </el-select>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<style scoped>
.filters {
  padding: 18px 18px 0;
  background: #f7f9fc;
  border-radius: 10px;
}
</style>
