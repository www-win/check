<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, clearAuth } from '../api'
import { toast } from '../toast'

const router = useRouter()
const status = ref(null)
const nickname = localStorage.getItem('nickname') || '同学'

async function load() {
  try {
    status.value = await request('/checkin/status')
  } catch (e) {
    toast(e.message)
  }
}
onMounted(load)

function logout() {
  clearAuth()
  router.push('/login')
}
</script>

<template>
  <div>
    <div class="page">
      <div class="profile-hero">
        <div class="avatar">{{ nickname.slice(0, 1) }}</div>
        <div class="profile-name">{{ nickname }}</div>
      </div>

      <div class="card profile-rows">
        <div class="prow"><span>🔥 当前连续</span><span class="v">{{ status ? status.currentStreak : 0 }} 天</span></div>
        <div class="prow"><span>🏆 历史最长</span><span class="v">{{ status ? status.maxStreak : 0 }} 天</span></div>
        <div class="prow"><span>📆 累计打卡</span><span class="v">{{ status ? status.totalDays : 0 }} 天</span></div>
        <div class="prow" style="border-bottom: none"><span>⭐ 积分</span><span class="v">{{ status ? status.points : 0 }}</span></div>
      </div>

      <button class="btn btn-ghost" style="margin-top: 22px" @click="logout">退出登录</button>
    </div>
  </div>
</template>
