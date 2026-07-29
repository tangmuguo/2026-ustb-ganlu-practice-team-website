// stores/logHonorStore.js
import { defineStore } from 'pinia'
// import logHonorApi from '@/api/logHonorApi'

export const usePhotoStore = defineStore('photo', {
  state: () => ({
    items: []
  }),
  actions: {
    // 从服务器加载数据
    async loadItems() {
      try {
        this.items = await logHonorApi.getList()
      } catch (error) {
        console.error('加载数据失败:', error)
        throw error
      }
    },
    
    // 添加新数据到服务器
    async addItem(item) {
      try {
        const newItem = await logHonorApi.create(item)
        this.items.push(newItem)
        return newItem
      } catch (error) {
        console.error('添加数据失败:', error)
        throw error
      }
    },
    
    // 从服务器删除数据
    async removeItem(id) {
      try {
        await logHonorApi.delete(id)
        this.items = this.items.filter(item => item.id !== id)
      } catch (error) {
        console.error('删除数据失败:', error)
        throw error
      }
    }
  }
})