<script setup>
import { ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import {
  getFriends, addFriend, acceptFriend, rejectFriend, cancelFriend, removeFriend,
  getConversations, toast
} from '../../utils/request'

const CONV_POLL_MS = 10000
const data = ref(null)
const convMap = ref({}) // peerUserId -> { lastContent, unread }
let convTimer = null

function load() {
  getFriends().then((d) => (data.value = d)).catch((e) => toast(e.message))
}

function loadConversations() {
  getConversations().then((list) => {
    const map = {}
    for (const c of (list || [])) map[c.peerUserId] = c
    convMap.value = map
  }).catch(() => {})
}

onShow(() => {
  if (convTimer) clearInterval(convTimer)
  load()
  loadConversations()
  convTimer = setInterval(loadConversations, CONV_POLL_MS)
})

onHide(() => { if (convTimer) clearInterval(convTimer) })

function openChat(f) {
  uni.navigateTo({
    url: '/pages/chat/chat?peerId=' + f.userId + '&name=' + encodeURIComponent(f.nickname)
  })
}

function copyCode() {
  if (!data.value) return
  uni.setClipboardData({ data: data.value.myInviteCode, success: () => toast('邀请码已复制') })
}

function addByCode() {
  uni.showModal({
    title: '添加好友',
    editable: true,
    placeholderText: '输入对方邀请码',
    success: (r) => {
      if (!r.confirm) return
      const code = (r.content || '').trim()
      if (!code) return toast('请输入邀请码')
      addFriend(code).then(() => { toast('请求已发送'); load() }).catch((e) => toast(e.message))
    }
  })
}

function accept(id) { acceptFriend(id).then(() => { toast('已添加'); load() }).catch((e) => toast(e.message)) }
function reject(id) { rejectFriend(id).then(load).catch((e) => toast(e.message)) }
function cancelReq(id) { cancelFriend(id).then(load).catch((e) => toast(e.message)) }
function del(userId) {
  uni.showModal({
    title: '删除好友',
    content: '确定删除该好友？',
    success: (r) => {
      if (!r.confirm) return
      removeFriend(userId).then(() => { toast('已删除'); load() }).catch((e) => toast(e.message))
    }
  })
}
</script>

<template>
  <view class="page-body">
    <!-- 我的邀请码 -->
    <view class="card code-card">
      <view class="code-label">我的邀请码</view>
      <view class="code-val">{{ data ? data.myInviteCode : '...' }}</view>
      <view class="code-actions">
        <text class="mini-btn" @tap="copyCode">复制</text>
        <text class="mini-btn primary" @tap="addByCode">输入邀请码加好友</text>
      </view>
    </view>

    <!-- 收到的请求 -->
    <block v-if="data && data.incoming.length">
      <view class="section-title">新的好友请求</view>
      <view class="card" v-for="r in data.incoming" :key="'in' + r.requestId">
        <view class="frow">
          <view class="fname">{{ r.nickname }}</view>
          <view class="fbtns">
            <text class="mini-btn primary" @tap="accept(r.requestId)">同意</text>
            <text class="mini-btn" @tap="reject(r.requestId)">拒绝</text>
          </view>
        </view>
      </view>
    </block>

    <!-- 我发出的请求 -->
    <block v-if="data && data.outgoing.length">
      <view class="section-title">等待对方同意</view>
      <view class="card" v-for="r in data.outgoing" :key="'out' + r.requestId">
        <view class="frow">
          <view class="fname">{{ r.nickname }}</view>
          <view class="fbtns">
            <text class="mini-btn" @tap="cancelReq(r.requestId)">撤回</text>
          </view>
        </view>
      </view>
    </block>

    <!-- 好友列表 -->
    <view class="section-title">我的好友 ({{ data ? data.friends.length : 0 }})</view>
    <view v-if="data && !data.friends.length" class="empty">还没有好友,用邀请码加一个吧</view>
    <view class="card" v-for="f in (data ? data.friends : [])" :key="'f' + f.userId" @tap="openChat(f)">
      <view class="frow">
        <view class="fmain">
          <view class="fname">
            {{ f.nickname }}
            <text v-if="convMap[f.userId] && convMap[f.userId].unread" class="badge">{{ convMap[f.userId].unread }}</text>
          </view>
          <view v-if="convMap[f.userId]" class="fpreview">{{ convMap[f.userId].lastContent }}</view>
        </view>
        <view class="fbtns">
          <text class="mini-btn primary" @tap.stop="openChat(f)">💬 聊天</text>
          <text class="mini-btn danger" @tap.stop="del(f.userId)">删除</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.code-card { padding: 32rpx; }
.code-label { font-size: 26rpx; color: var(--c-muted); }
.code-val { font-size: 48rpx; font-weight: 800; color: var(--c-primary-d); letter-spacing: 4rpx; margin: 12rpx 0 20rpx; }
.code-actions { display: flex; gap: 20rpx; }
.section-title { font-size: 28rpx; font-weight: 700; margin: 32rpx 8rpx 16rpx; }
.card { padding: 28rpx 32rpx; margin-bottom: 16rpx; }
.frow { display: flex; justify-content: space-between; align-items: center; }
.fname { font-size: 30rpx; font-weight: 600; }
.fbtns { display: flex; gap: 16rpx; }
.mini-btn { font-size: 26rpx; padding: 8rpx 24rpx; border-radius: 30rpx; background: #F0F2F1; color: var(--c-text); }
.mini-btn.primary { background: var(--c-primary, #2E9E5B); color: #fff; }
.mini-btn.danger { color: #E06A5B; background: rgba(224,106,91,.1); }
.empty { text-align: center; color: var(--c-muted); font-size: 26rpx; padding: 30rpx 0; }
.fmain { flex: 1; min-width: 0; }
.fpreview { font-size: 24rpx; color: var(--c-muted, #8A9A90); margin-top: 6rpx;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.badge { display: inline-block; min-width: 32rpx; height: 32rpx; line-height: 32rpx; text-align: center;
  padding: 0 8rpx; margin-left: 12rpx; border-radius: 16rpx; background: #E06A5B; color: #fff; font-size: 22rpx; }
</style>
