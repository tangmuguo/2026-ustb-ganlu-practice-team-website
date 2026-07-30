<script setup>
import { computed } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import { resolveMediaUrl } from '@/utils/fengcai'

const props = defineProps({
  member: {
    type: Object,
    required: true,
  },
})

const name = computed(() => props.member.name || props.member.realName || props.member.caption || '甘露队员')
const role = computed(() => props.member.role || props.member.duty || props.member.responsibility || '')
const bio = computed(() => props.member.bio || props.member.description || props.member.content || '')
const imageUrl = computed(() => resolveMediaUrl(
  props.member.photoUrl || props.member.avatarUrl || props.member.imageUrl || props.member.url,
))
</script>

<template>
  <article class="member-card">
    <el-image
      v-if="imageUrl"
      class="member-card__avatar"
      :src="imageUrl"
      :alt="`${name}的队员照片`"
      :preview-src-list="[imageUrl]"
      :initial-index="0"
      fit="cover"
      preview-teleported
      hide-on-click-modal
    >
      <template #error>
        <div class="member-card__avatar member-card__placeholder"><el-icon><UserFilled /></el-icon></div>
      </template>
    </el-image>
    <div v-else class="member-card__avatar member-card__placeholder"><el-icon><UserFilled /></el-icon></div>

    <div class="member-card__content">
      <h3>{{ name }}</h3>
      <p v-if="role" class="member-card__role">{{ role }}</p>
      <p v-if="bio" class="member-card__bio">{{ bio }}</p>
      <p v-else class="member-card__bio member-card__bio--muted">简介待补充</p>
    </div>
  </article>
</template>

<style scoped>
.member-card {
  overflow: hidden;
  border: 1px solid #e4edf5;
  border-radius: 18px;
  background: #fff;
}

.member-card__avatar {
  display: block;
  width: 100%;
  height: 210px;
}

.member-card__placeholder {
  display: grid;
  place-items: center;
  color: #7fa6c3;
  font-size: 52px;
  background: linear-gradient(145deg, #eef7fd, #dceefa);
}

.member-card__content {
  padding: 18px;
}

h3 {
  margin: 0;
  color: #173f61;
  font-size: 20px;
  font-weight: 700;
}

.member-card__role {
  display: inline-block;
  margin-top: 8px;
  padding: 4px 10px;
  border-radius: 999px;
  color: #126aa9;
  font-size: 13px;
  background: #e9f5fe;
}

.member-card__bio {
  margin-top: 12px;
  color: #667b8d;
  line-height: 1.7;
  white-space: pre-wrap;
}

.member-card__bio--muted {
  color: #9aabb8;
}

@media (max-width: 480px) {
  .member-card__avatar {
    height: 190px;
  }
}
</style>
