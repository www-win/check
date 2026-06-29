<script setup>
import { computed } from 'vue'

const props = defineProps({
  goal: { type: Object, default: null } // { content, targetDate } 或 null
})
defineEmits(['edit'])

function pad(n) {
  return String(n).padStart(2, '0')
}
const todayStr = (() => {
  const n = new Date()
  return n.getFullYear() + '-' + pad(n.getMonth() + 1) + '-' + pad(n.getDate())
})()

// 倒计时（按天）
const countdown = computed(() => {
  if (!props.goal || !props.goal.targetDate) return null
  const t = new Date(props.goal.targetDate + 'T00:00:00')
  const today = new Date(todayStr + 'T00:00:00')
  const diff = Math.round((t - today) / 86400000)
  if (diff > 0) return { text: '距目标还有 ' + diff + ' 天', over: false }
  if (diff === 0) return { text: '今天就是目标日', over: false }
  return { text: '已超 ' + -diff + ' 天，继续加油', over: true }
})
</script>

<template>
  <div class="goalcard">
    <div v-if="goal" class="goal-set">
      <div class="goal-head">
        <span class="goal-label">🎯 我的学习目标</span>
        <button class="goal-edit-btn" @click="$emit('edit')">编辑</button>
      </div>
      <div class="goal-text">{{ goal.content }}</div>
      <div v-if="countdown" :class="['goal-countdown', { over: countdown.over }]">
        ⏳ {{ countdown.text }}
      </div>
    </div>

    <button v-else class="goal-empty" @click="$emit('edit')">
      ＋ 设定你的学习目标
    </button>
  </div>
</template>
