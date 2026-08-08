<script setup>
const examples = [
  {
    title: '解释知识点',
    question: '给三年级学生解释什么是光合作用',
    icon: '光',
  },
  {
    title: '设计课堂活动',
    question: '帮我设计一个适合小学生的科学课堂小游戏',
    icon: '课',
  },
  {
    title: '优化教学表达',
    question: '如何用简单的比喻向小学生解释分数？',
    icon: '问',
  },
]

defineEmits(['select'])
</script>

<template>
  <section class="welcome" aria-labelledby="ai-welcome-title">
    <div class="welcome-mark" aria-hidden="true">AI</div>
    <h2 id="ai-welcome-title">今天想一起解决什么问题？</h2>
    <p>可以询问备课思路、知识讲解或课堂活动设计。选择一个示例开始吧。</p>

    <div class="example-grid">
      <button
        v-for="example in examples"
        :key="example.question"
        class="example-card"
        type="button"
        @click="$emit('select', example.question)"
      >
        <span class="example-icon" aria-hidden="true">{{ example.icon }}</span>
        <span class="example-copy">
          <strong>{{ example.title }}</strong>
          <span>{{ example.question }}</span>
        </span>
        <span class="example-arrow" aria-hidden="true">→</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.welcome {
  display: flex;
  width: min(100%, 760px);
  margin: auto;
  padding: 26px 0 18px;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.welcome-mark {
  display: grid;
  width: 58px;
  height: 58px;
  margin-bottom: 18px;
  place-items: center;
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 20px;
  background: linear-gradient(145deg, #eff6ff, #e5f8f0);
  color: #2563eb;
  font-size: 17px;
  font-weight: 800;
  box-shadow: 0 12px 30px rgba(37, 99, 235, 0.1);
}

h2 {
  color: #1f2937;
  font-size: clamp(22px, 3vw, 28px);
  font-weight: 700;
  letter-spacing: -0.02em;
}

.welcome > p {
  max-width: 590px;
  margin-top: 8px;
  color: #667085;
  line-height: 1.7;
}

.example-grid {
  display: grid;
  width: 100%;
  margin-top: 28px;
  gap: 10px;
}

.example-card {
  display: grid;
  width: 100%;
  padding: 14px 16px;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 13px;
  border: 1px solid #e4eaf1;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.example-card:hover,
.example-card:focus-visible {
  border-color: #9bc5ff;
  box-shadow: 0 9px 24px rgba(37, 99, 235, 0.09);
  outline: none;
  transform: translateY(-1px);
}

.example-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  background: #eef5ff;
  color: #2563eb;
  font-size: 14px;
  font-weight: 700;
}

.example-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.example-copy strong {
  color: #273346;
  font-size: 14px;
  font-weight: 700;
}

.example-copy span {
  overflow: hidden;
  margin-top: 2px;
  color: #7a8495;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.example-arrow {
  color: #9aa4b2;
  font-size: 18px;
}

@media (max-width: 640px) {
  .welcome {
    justify-content: flex-start;
    padding-top: 8px;
  }

  .welcome-mark {
    width: 50px;
    height: 50px;
    margin-bottom: 12px;
    border-radius: 16px;
  }

  .welcome > p {
    font-size: 13px;
  }

  .example-grid {
    margin-top: 18px;
  }

  .example-card {
    padding: 12px;
  }
}
</style>
