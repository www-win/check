<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, clearAuth, getAchievements } from '../api'
import { toast } from '../toast'

const router = useRouter()
const status = ref(null)
const ach = ref(null)
const nickname = localStorage.getItem('nickname') || '同学'

async function load() {
  try {
    status.value = await request('/checkin/status')
  } catch (e) {
    toast(e.message)
  }
  try {
    ach.value = await getAchievements()
  } catch (e) {
    // 成就拉取失败不影响个人页
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

      <button class="card couple-entry" @click="router.push('/achievements')">
        <span>🏅 我的成就</span>
        <span class="arrow"><span v-if="ach" style="margin-right:6px">{{ ach.unlockedCount }}/{{ ach.totalCount }}</span>›</span>
      </button>

      <button class="card couple-entry" @click="router.push('/couple')">
        <span>💑 情侣空间</span>
        <span class="arrow">›</span>
      </button>

      <button class="btn btn-ghost" style="margin-top: 22px" @click="logout">退出登录</button>
    </div>
  </div>
</template>

<style scoped>
.couple-entry {
  width: 100%;
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
  text-align: left;
}
.couple-entry .arrow { color: var(--c-muted); font-size: 20px; }
</style>
