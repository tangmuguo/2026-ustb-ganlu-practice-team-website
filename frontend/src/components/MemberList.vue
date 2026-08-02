<script setup>
import MemberCard from '@/components/MemberCard.vue'

defineProps({
  members: {
    type: Array,
    default: () => [],
  },
})
</script>

<template>
  <div v-if="members.length" class="member-list">
    <MemberCard
      v-for="(member, index) in members"
      :key="member.id ?? `member-${index}`"
      :member="member"
    />
  </div>
  <p v-else class="empty-copy">暂无已发布的队员信息</p>
</template>

<style scoped>
.member-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(240px, 100%), 1fr));
  gap: 20px;
}

.empty-copy {
  padding: 28px 0;
  color: #7d92a4;
  text-align: center;
}
</style>
