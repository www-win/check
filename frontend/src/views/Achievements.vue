<script setup>
import { ref, onMounted } from 'vue'
import { getAchievements } from '../api'
import { toast } from '../toast'

const data = ref(null)

async function load() {
  try {
    const d = await getAchievements()
    data.value = d
    if (d.newlyUnlocked && d.newlyUnlocked.length) {
      toast('🎉 解锁 ' + d.newlyUnlocked.length + ' 个新徽章')
    }
  } catch (e) {
    toast(e.message)
  }
}
onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">我的成就</h2>
      <p class="page-sub" v-if="data">已解锁 {{ data.unlockedCount }} / {{ data.totalCount }}</p>
    </div>

    <div class="page" style="padding-top: 8px">
      <div v-if="data" class="badge-grid">
        <div
          v-for="b in data.badges"
          :key="b.code"
          :class="['badge', { locked: !b.unlocked }]"
        >
          <div class="badge-ic">{{ b.icon }}</div>
          <div class="badge-title">{{ b.title }}</div>
          <div class="badge-desc">{{ b.unlocked ? ('+' + b.rewardPoints + ' 积分') : b.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.badge-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.badge {
  background: #fff;
  border-radius: 16px;
  padding: 18px 8px;
  text-align: center;
  box-shadow: 0 4px 16px rgba(45, 140, 85, 0.06);
}
.badge.locked { opacity: 0.45; filter: grayscale(1); }
.badge-ic { font-size: 36px; }
.badge-title { font-size: 13px; font-weight: 700; margin-top: 8px; }
.badge-desc { font-size: 11px; color: var(--c-muted); margin-top: 4px; line-height: 1.3; }
</style>
