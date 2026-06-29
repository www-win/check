<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getStatus, toast } from '../../utils/request'

const status = ref(null)
const nickname = ref(uni.getStorageSync('nickname') || '同学')

onShow(() => {
  getStatus().then((d) => (status.value = d)).catch((e) => toast(e.message))
})

function logout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: (r) => {
      if (!r.confirm) return
      uni.removeStorageSync('token')
      uni.removeStorageSync('nickname')
      uni.reLaunch({ url: '/pages/login/login' })
    }
  })
}
</script>

<template>
  <view class="page-body">
    <view class="hero">
      <view class="avatar">{{ nickname.slice(0, 1) }}</view>
      <view class="name">{{ nickname }}</view>
    </view>

    <view class="card rows">
      <view class="row"><text>🔥 当前连续</text><text class="v">{{ status ? status.currentStreak : 0 }} 天</text></view>
      <view class="row"><text>🏆 历史最长</text><text class="v">{{ status ? status.maxStreak : 0 }} 天</text></view>
      <view class="row"><text>📆 累计打卡</text><text class="v">{{ status ? status.totalDays : 0 }} 天</text></view>
      <view class="row last"><text>⭐ 积分</text><text class="v">{{ status ? status.points : 0 }}</text></view>
    </view>

    <button class="btn btn-ghost logout" @tap="logout">退出登录</button>
  </view>
</template>

<style scoped>
.hero { display: flex; flex-direction: column; align-items: center; padding: 40rpx 0 20rpx; }
.avatar { width: 140rpx; height: 140rpx; border-radius: 50%; background: linear-gradient(135deg, #6FD299, #2E9E5B); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 64rpx; }
.name { font-size: 38rpx; font-weight: 700; margin-top: 20rpx; }
.rows { margin-top: 24rpx; padding: 0 32rpx; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 30rpx 0; border-bottom: 2rpx solid var(--c-line); font-size: 30rpx; }
.row.last { border-bottom: none; }
.row .v { font-weight: 700; color: var(--c-primary-d); }
.logout { margin-top: 40rpx; }
</style>
