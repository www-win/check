<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getAchievements, toast } from '../../utils/request'

const data = ref(null)

onShow(() => {
  getAchievements()
    .then((d) => {
      data.value = d
      if (d.newlyUnlocked && d.newlyUnlocked.length) {
        toast('🎉 解锁 ' + d.newlyUnlocked.length + ' 个新徽章')
      }
    })
    .catch((e) => toast(e.message))
})
</script>

<template>
  <view class="page-body">
    <view v-if="data" class="head">已解锁 {{ data.unlockedCount }} / {{ data.totalCount }}</view>
    <view v-if="data" class="grid">
      <view
        v-for="b in data.badges"
        :key="b.code"
        :class="['badge', { locked: !b.unlocked }]"
      >
        <view class="ic">{{ b.icon }}</view>
        <view class="title">{{ b.title }}</view>
        <view class="desc">{{ b.unlocked ? ('+' + b.rewardPoints + ' 积分') : b.desc }}</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.head { font-size: 28rpx; color: var(--c-muted); padding: 24rpx 8rpx; }
.grid { display: flex; flex-wrap: wrap; }
.badge { width: 33.33%; box-sizing: border-box; padding: 28rpx 10rpx; text-align: center; }
.badge.locked { opacity: 0.45; filter: grayscale(1); }
.ic { font-size: 64rpx; }
.title { font-size: 26rpx; font-weight: 700; margin-top: 10rpx; }
.desc { font-size: 22rpx; color: var(--c-muted); margin-top: 6rpx; line-height: 1.3; }
</style>
