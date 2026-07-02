<script setup>
import { ref, nextTick } from 'vue'
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { getMessages, sendMessage, markChatRead, toast } from '../../utils/request'

const POLL_MS = 3000
const peerId = ref(null)
const peerName = ref('好友')
const myName = ref(uni.getStorageSync('nickname') || '我')
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scrollTop = ref(0)
let timer = null

onLoad((q) => {
  peerId.value = Number(q.peerId)
  peerName.value = q.name ? decodeURIComponent(q.name) : '好友'
  uni.setNavigationBarTitle({ title: peerName.value })
  firstLoad()
})

// 取首字作字母头像;中英文都取第一个字符
function initial(name) {
  return (name || '?').trim().slice(0, 1) || '?'
}

// 后端 LocalDateTime 序列化为 "2026-07-02T14:30:00";用正则取 HH:mm,避免 new Date 解析 NaN
function fmtTime(s) {
  if (!s) return ''
  const m = String(s).match(/T(\d{2}:\d{2})/)
  return m ? m[1] : ''
}

// 页面显示时启动轮询(切前台/首次进入);先兜底清理避免定时器叠加
onShow(() => {
  if (timer) clearInterval(timer)
  if (peerId.value) timer = setInterval(poll, POLL_MS)
})

// 切后台/被上层页面覆盖时停止轮询,避免空转耗电与无谓请求
onHide(() => { if (timer) { clearInterval(timer); timer = null } })

onUnload(() => { if (timer) { clearInterval(timer); timer = null } })

function maxId() {
  return messages.value.length ? messages.value[messages.value.length - 1].id : 0
}

function scrollToBottom() {
  nextTick(() => { scrollTop.value = messages.value.length * 100000 })
}

function firstLoad() {
  getMessages(peerId.value).then((list) => {
    messages.value = list || []
    scrollToBottom()
    if (messages.value.length) markChatRead(peerId.value)
  }).catch((e) => toast(e.message))
}

function poll() {
  getMessages(peerId.value, maxId()).then((list) => {
    if (list && list.length) {
      messages.value = messages.value.concat(list)
      scrollToBottom()
      markChatRead(peerId.value)
    }
  }).catch(() => {})
}

function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  sending.value = true
  sendMessage(peerId.value, text).then((msg) => {
    messages.value.push(msg)
    draft.value = ''
    scrollToBottom()
  }).catch((e) => toast(e.message)).finally(() => { sending.value = false })
}
</script>

<template>
  <view class="chat-page">
    <scroll-view class="msg-list" scroll-y :scroll-top="scrollTop" scroll-with-animation>
      <view v-if="!messages.length" class="empty">打个招呼吧 👋</view>
      <view v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.mine }">
        <view class="avatar" :class="{ mine: m.mine }">{{ initial(m.mine ? myName : peerName) }}</view>
        <view class="bubble-wrap">
          <view class="bubble">{{ m.content }}</view>
          <view class="time">{{ fmtTime(m.createdAt) }}</view>
        </view>
      </view>
    </scroll-view>
    <view class="input-bar">
      <input class="input" v-model="draft" placeholder="说点什么..." confirm-type="send" @confirm="send" />
      <text class="send-btn" :class="{ disabled: !draft.trim() || sending }" @tap="send">发送</text>
    </view>
  </view>
</template>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100vh; background: #F1FAF4; }
.msg-list { flex: 1; padding: 28rpx 28rpx 12rpx; box-sizing: border-box; }
.empty { text-align: center; color: var(--c-muted, #8A9A90); font-size: 26rpx; padding: 80rpx 0; }

.msg-row { display: flex; align-items: flex-start; gap: 16rpx; margin-bottom: 28rpx; }
.msg-row.mine { flex-direction: row-reverse; }

.avatar { flex-shrink: 0; width: 68rpx; height: 68rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 30rpx; font-weight: 700;
  background: linear-gradient(135deg, #7FB3F5, #4C82D6); }
.avatar.mine { background: linear-gradient(135deg, #6FD299, #2E9E5B); }

.bubble-wrap { display: flex; flex-direction: column; max-width: 66%; }
.msg-row.mine .bubble-wrap { align-items: flex-end; }

.bubble { padding: 20rpx 26rpx; border-radius: 24rpx 24rpx 24rpx 8rpx; font-size: 30rpx; line-height: 1.45;
  background: #fff; color: #222; word-break: break-word;
  box-shadow: 0 2rpx 12rpx rgba(46, 110, 74, .06); }
.msg-row.mine .bubble { background: #2E9E5B; color: #fff; border-radius: 24rpx 24rpx 8rpx 24rpx; }

.time { font-size: 20rpx; color: var(--c-muted, #A3B2A9); margin: 8rpx 6rpx 0; }

.input-bar { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff; border-top: 1rpx solid #E5EDE8; }
.input { flex: 1; height: 76rpx; padding: 0 28rpx; background: #F0F2F1; border-radius: 38rpx; font-size: 30rpx; }
.send-btn { padding: 0 34rpx; height: 76rpx; line-height: 76rpx; border-radius: 38rpx;
  background: #2E9E5B; color: #fff; font-size: 28rpx; font-weight: 600; }
.send-btn.disabled { background: #B8C6BE; }
</style>
