<script setup>
import { onShow } from '@dcloudio/uni-app'
import { request, toast } from '../../utils/request'

onShow(() => {
  if (uni.getStorageSync('token')) {
    uni.switchTab({ url: '/pages/today/today' })
  }
})

function login() {
  uni.login({
    provider: 'weixin',
    success: (r) => {
      if (!r.code) {
        toast('微信登录失败')
        return
      }
      request('/auth/wx-login', { method: 'POST', data: { code: r.code } })
        .then((d) => {
          uni.setStorageSync('token', d.token)
          uni.setStorageSync('nickname', d.nickname || '')
          uni.switchTab({ url: '/pages/today/today' })
        })
        .catch((e) => toast(e.message))
    },
    fail: () => toast('微信登录失败')
  })
}
</script>

<template>
  <view class="login">
    <view class="brand">
      <view class="logo">🌱</view>
      <view class="title">学伴打卡</view>
      <view class="slogan">每天一点点，坚持看得见</view>
    </view>

    <button class="btn login-btn" hover-class="btn-hover" @tap="login">微信一键登录</button>
    <view class="tip">登录即同意以微信身份创建账号</view>
  </view>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 0 64rpx;
  background: linear-gradient(160deg, #EAF7EF, #F1FAF4);
}
.brand { text-align: center; margin-bottom: 80rpx; }
.logo { font-size: 120rpx; }
.title { font-size: 52rpx; font-weight: 700; margin-top: 16rpx; }
.slogan { font-size: 28rpx; color: var(--c-muted); margin-top: 12rpx; }
.login-btn { width: 100%; }
.tip { font-size: 24rpx; color: var(--c-muted); margin-top: 28rpx; }
</style>
