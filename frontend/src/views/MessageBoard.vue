<script setup>
import { ref, onMounted,computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getMessages, addMessage, deleteMessage, addReply, deleteReply } from '@/apis/messageAPI'
import { userinfoStore } from '@/stores/userStore'
import { formatTime } from '@/utils/date'

const userStore = userinfoStore()
const userLevel = computed(() => userStore.user.content?.level ?? 3)
const canPublish = computed(() => userStore.isLoggedIn && [0, 1, 2].includes(userLevel.value))
const canReply = computed(() => userStore.isLoggedIn && [0, 1, 2].includes(userLevel.value))
const canDelete = computed(() => userStore.isLoggedIn && [0, 1].includes(userLevel.value))
const messages = ref([])
const newMessage = ref('')
const replyContents = ref({})
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 获取留言列表
const fetchMessages = async () => {
  try {
    const res = await getMessages(currentPage.value, pageSize.value)
    messages.value = res.data.content.messages
    total.value = res.data.content.total
  } catch (error) {
    ElMessage.error(error.message || '获取留言失败')
  }
}

// 提交留言
const submitMessage = async () => {
  if (!newMessage.value.trim()) {
    ElMessage.warning('请输入留言内容')
    return
  }
  try {
    await addMessage({ content: newMessage.value })
    newMessage.value = ''
    ElMessage.success('留言成功')
    fetchMessages()
  } catch (error) {
    ElMessage.error(error.message || '留言失败')
  }
}

// 提交回复
const submitReply = async (messageId) => {
  const content = replyContents.value[messageId]
  if (!content || !content.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  try {
    await addReply({ messageId: messageId, content: content })
    replyContents.value[messageId] = ''
    ElMessage.success('回复成功')
    fetchMessages()
  } catch (error) {
    ElMessage.error(error.message || '回复失败')
  }
}

// 删除留言
const deleteMsg = async (id) => {
  try {
    await deleteMessage(id)
    ElMessage.success('删除成功')
    fetchMessages()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

// 删除回复
const deleteRpl = async (id) => {
  try {
    await deleteReply(id)
    ElMessage.success('删除成功')
    fetchMessages()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => {
  fetchMessages()

  console.log(userLevel)
})
</script>

<template>
  <div class="message-board">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>留言板</span>
        </div>
      </template>
      
      <!-- 留言表单 -->
      <div v-if="canPublish" class="message-form">
        <el-input
          v-model="newMessage"
          :rows="3"
          type="textarea"
          placeholder="请输入留言内容"
          resize="none"
        />
        <div class="form-actions">
          <el-button type="primary" @click="submitMessage">提交留言</el-button>
        </div>
      </div>
      
      <!-- 留言列表 -->
      <div class="message-list">
        <div v-for="message in messages" :key="message.id" class="message-item">
          <el-card shadow="hover">
            <template #header>
              <div class="message-header">
                <span class="username">{{ message.username }}</span>
                <span class="teamname" v-if="message.teamname">({{ message.teamname }})</span>
                <span class="time">{{ formatTime(message.createTime) }}</span>
                <el-button 
                  v-if="canDelete" 
                  type="danger" 
                  size="small" 
                  @click="deleteMsg(message.id)"
                >
                  删除
                </el-button>
              </div>
            </template>
            <div class="message-content">{{ message.content }}</div>
            
            <!-- 回复列表 -->
            <div class="reply-list">
              <div v-for="reply in message.replies" :key="reply.id" class="reply-item">
                <div class="reply-header">
                  <span class="username">{{ reply.username }}</span>
                  <span class="teamname" v-if="reply.teamname">({{ reply.teamname }})</span>
                  <span class="time">{{ formatTime(reply.createTime) }}</span>
                  <el-button 
                    v-if="canDelete" 
                    type="danger" 
                    size="small" 
                    @click="deleteRpl(reply.id)"
                  >
                    删除
                  </el-button>
                </div>
                <div class="reply-content">{{ reply.content }}</div>
              </div>
            </div>
            
            <!-- 回复表单 -->
            <div v-if="canReply" class="reply-form">
              <el-input
                v-model="replyContents[message.id]"
                :rows="2"
                type="textarea"
                placeholder="请输入回复内容"
                resize="none"
              />
              <div class="form-actions">
                <el-button type="primary" size="small" @click="submitReply(message.id)">提交回复</el-button>
              </div>
            </div>
          </el-card>
        </div>
      </div>
      
      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchMessages"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.message-board {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}

.message-form {
  margin-bottom: 30px;
}

.form-actions {
  margin-top: 10px;
  text-align: right;
}

.message-item {
  margin-bottom: 20px;
}

.message-header {
  display: flex;
  align-items: center;
}

.username {
  font-weight: bold;
  margin-right: 5px;
}

.teamname {
  color: #666;
  margin-right: 10px;
}

.time {
  color: #999;
  font-size: 12px;
  margin-right: auto;
}

.message-content {
  padding: 10px 0;
  line-height: 1.6;
}

.reply-list {
  margin-top: 15px;
  padding-left: 20px;
  border-left: 2px solid #eee;
}

.reply-item {
  margin-bottom: 15px;
  padding: 10px;
  background-color: #f9f9f9;
  border-radius: 4px;
}

.reply-header {
  display: flex;
  align-items: center;
  margin-bottom: 5px;
}

.reply-content {
  color: #555;
  line-height: 1.5;
}

.reply-form {
  margin-top: 15px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
