<script setup>
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import EmptyLayout from '@/layouts/EmptyLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'

const route = useRoute()
const layouts = { DefaultLayout, EmptyLayout, AdminLayout }

const layoutComponent = computed(() => {
  return layouts[route.meta.layout] || DefaultLayout
})
</script>

<template>
  <component :is="layoutComponent">
    <RouterView />
  </component>
</template>
