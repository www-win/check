<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getStatus, getAchievements, updateNickname, toast } from '../../utils/request'

const status = ref(null)
const nickname = ref(uni.getStorageSync('nickname') || '同学')
const ach = ref(null)

onShow(() => {
  getStatus().then((d) => (status.value = d)).catch((e) => toast(e.message))
  getAchievements().then((d) => (ach.value = d)).catch(() => {})
})

function goCouple() {
  uni.navigateTo({ url: '/pages/couple/couple' })
}

function goAchievements() {
  uni.navigateTo({ url: '/pages/achievements/achievements' })
}

function editName() {
  uni.showModal({
    title: '修改昵称',
    editable: true,
    placeholderText: '请输入昵称',
    content: nickname.value,
    success: (r) => {
      if (!r.confirm) return
      updateNickname(r.content)
        .then((d) => {
          nickname.value = d.nickname
          uni.setStorageSync('nickname', d.nickname)
          toast('昵称已更新')
        })
        .catch((e) => toast(e.message))
    }
  })
}

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
      <view class="name">{{ nickname }} <text class="edit-name" @tap="editName">编辑</text></view>
    </view>

    <view class="card rows">
      <view class="row"><text>🔥 当前连续</text><text class="v">{{ status ? status.currentStreak : 0 }} 天</text></view>
      <view class="row"><text>🏆 历史最长</text><text class="v">{{ status ? status.maxStreak : 0 }} 天</text></view>
      <view class="row"><text>📆 累计打卡</text><text class="v">{{ status ? status.totalDays : 0 }} 天</text></view>
      <view class="row last"><text>⭐ 积分</text><text class="v">{{ status ? status.points : 0 }}</text></view>
    </view>

    <view class="card entry" @tap="goAchievements">
      <text>🏅 我的成就</text>
      <text class="arrow"><text v-if="ach" style="margin-right:8rpx">{{ ach.unlockedCount }}/{{ ach.totalCount }}</text>›</text>
    </view>

    <view class="card entry" @tap="goCouple">
      <text>💑 情侣空间</text>
      <text class="arrow">›</text>
    </view>

    <button class="btn btn-ghost logout" @tap="logout">退出登录</button>
  </view>
</template>

<style scoped>
.hero { display: flex; flex-direction: column; align-items: center; padding: 40rpx 0 20rpx; }
.avatar { width: 140rpx; height: 140rpx; border-radius: 50%; background: linear-gradient(135deg, #6FD299, #2E9E5B); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 64rpx; }
.name { font-size: 38rpx; font-weight: 700; margin-top: 20rpx; }
.edit-name { font-size: 24rpx; color: var(--c-primary-d); background: rgba(46,158,91,.1); padding: 4rpx 18rpx; border-radius: 28rpx; margin-left: 12rpx; font-weight: 600; }
.rows { margin-top: 24rpx; padding: 0 32rpx; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 30rpx 0; border-bottom: 2rpx solid var(--c-line); font-size: 30rpx; }
.row.last { border-bottom: none; }
.row .v { font-weight: 700; color: var(--c-primary-d); }
.logout { margin-top: 40rpx; }
.entry { display: flex; justify-content: space-between; align-items: center; margin-top: 24rpx; padding: 32rpx; font-size: 30rpx; font-weight: 600; }
.entry .arrow { color: var(--c-muted); font-size: 40rpx; }
</style>
