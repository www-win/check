<script setup>
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const tabs = [
  { path: '/today', label: '今日', icon: '🌱' },
  { path: '/calendar', label: '日历', icon: '📅' },
  { path: '/profile', label: '我的', icon: '👤' }
]
</script>

<template>
  <nav class="tabbar">
    <button
      v-for="t in tabs"
      :key="t.path"
      :class="['tab', { active: route.path === t.path }]"
      @click="router.push(t.path)"
    >
      <span class="tab-ic">{{ t.icon }}</span>
      <span class="tab-lb">{{ t.label }}</span>
    </button>
  </nav>
</template>
