<script setup>
import CourseList from '@/components/CourseList.vue'
import { ref, computed, onMounted } from 'vue'
import {findAllCourse} from '@/apis/materialsAPI'

// 分页相关变量
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

onMounted(()=>{
  LoadCourses()
})

// 课程类型映射
const courseTypes = {
  1: '民族特色文化课程',
  2: '美育系列课程',
  3: '科技系列课程',
  4: '红色系列课程',
  5: '基础课程',
  6: '联动的新课程',
  7: '素质拓展课程'
}
// 模拟课程数据
const courses = ref([])

const selectedCategory = ref('全部')

// 计算属性，根据筛选条件过滤课程
const filteredCourses = computed(() => {
  // if (selectedCategory.value === '全部') {
  //   return courses.value
  // }
  // // 找到选中的类型对应的数字值
  // const typeValue = Object.keys(courseTypes).find(
  //   key => courseTypes[key] === selectedCategory.value
  // )
  
  // return courses.value.filter(course => 
  //   course.courseId === Number(typeValue)
  // )
  let filtered = courses.value
  
  if (selectedCategory.value !== '全部') {
    // 找到选中的类型对应的数字值
    const typeValue = Object.keys(courseTypes).find(
      key => courseTypes[key] === selectedCategory.value
    )
    filtered = filtered.filter(course => 
      course.courseId === Number(typeValue)
    )
  }
  
  // 更新总条数
  total.value = filtered.length
  
  // 返回当前页的数据
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filtered.slice(start, end)
  
})

// 清除筛选条件
const clearFilter = () => {
  selectedCategory.value = '全部'
}

// 处理页码变化
const handleCurrentChange = (val) => {
  currentPage.value = val
}

// 处理每页条数变化
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1 // 重置页码
}

async function LoadCourses() {
  const d=await findAllCourse();
  console.log(d.data.content)
  courses.value=d.data.content
}
</script>

<template>
  <div class="course-filter-container">
    <!-- 筛选条件区域 -->
    <div class="filter-section">
      <div class="filter-title">课程种类：</div>
      <div class="filter-options">
        <el-radio-group v-model="selectedCategory" @change="filterCourses">
          <el-radio-button label="全部"></el-radio-button>
          <el-radio-button 
            v-for="(name, value) in courseTypes" 
            :key="value"
            :label="name"
          ></el-radio-button>
        </el-radio-group>
      </div>
      
      <div class="selected-conditions" v-if="selectedCategory !== '全部'">
        <span>已选条件：</span>
        <el-tag type="info" closable @close="clearFilter">{{ selectedCategory }}</el-tag>
      </div>
    </div>
    
    <!-- 学科资料列表 -->
    <div class="course-list-section">    
      <CourseList 
      :courses="filteredCourses" 
      title="学科资料列表"
      />
      <!-- 分页控件 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 20, 50]"
          :small="false"
          :background="true"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.course-filter-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  background-color: white;
}
/* 移除所有边框 */
:deep(.el-radio-button__inner) {
  border: none !important;
  box-shadow: none !important;
}

/* 移除选中状态的边框 */
:deep(.el-radio-button__orig-radio:checked+.el-radio-button__inner) {
  box-shadow: none !important;
  border: none !important;
}

.filter-section {
  margin-bottom: 30px;
  padding: 20px;
  border-radius: 4px;
}

.filter-title {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 15px;
}

.filter-options {
  margin-bottom: 15px;
}

.selected-conditions {
  margin-top: 15px;
  font-size: 14px;
}

.selected-conditions .el-tag {
  margin-left: 10px;
}

.course-list-section h3 {
  margin-bottom: 20px;
  font-size: 18px;
  color: #333;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}

.course-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.course-image-placeholder {
  width: 100%;
  height: 150px;
  background-color: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 10px;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.image-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f0f2f5;
  color: #c0c4cc;
}

.course-name {
  font-size: 14px;
  text-align: center;
  color: #606266;
}
</style>